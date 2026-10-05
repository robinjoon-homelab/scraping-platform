import copy
import io
import os
from pathlib import Path
import sys
import unittest
from unittest.mock import Mock, patch
from urllib.error import HTTPError

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import deploy


class DeploymentTests(unittest.TestCase):
    def setUp(self):
        self.target = deploy.identity("example/My_App", "12345")
        self.tag = "sha-0123456789abcdef"
        self.values = deploy.creation_payload(self.target, self.tag)["values"]

    def test_names_are_safe_and_distinct_for_different_repositories(self):
        self.assertEqual(self.target["app"], "app-my-app-12345")
        self.assertEqual(self.target["database"], "app_my_app_12345")
        other = deploy.identity("another/My_App", "54321")
        self.assertNotEqual(other["app"], self.target["app"])
        long_name = deploy.identity("owner/" + "X" * 100, "12345678901234567890")
        self.assertLessEqual(len(long_name["app"]), 50)
        self.assertTrue(long_name["app"].endswith("-12345678901234567890"))

    def test_invalid_repository_and_id_cannot_inject_workflow_outputs(self):
        for repository, repository_id in [("owner/name\nevil=value", "1"), ("owner/name", "1\nevil=value"), ("owner", "1")]:
            with self.subTest(repository=repository), self.assertRaises(ValueError):
                deploy.identity(repository, repository_id)

    def test_database_credentials_are_references_and_host_belongs_to_platform(self):
        container = self.values["workload"]["containers"][0]
        environment = {entry["name"]: entry for entry in container["env"]}
        self.assertNotIn("DB_HOST", environment)
        self.assertEqual(environment["DB_PORT"]["secretKeyRef"], {"name": "shared-db-app", "key": "port"})
        self.assertEqual(environment["SPRING_DATASOURCE_PASSWORD"]["secretKeyRef"], {"name": "shared-db-app", "key": "password"})
        self.assertEqual(environment["SPRING_DATASOURCE_URL"]["value"], "jdbc:postgresql://$(DB_HOST):$(DB_PORT)/app_my_app_12345")
        self.assertEqual(container["ports"][0]["containerPort"], 8080)
        self.assertEqual(self.values["ingresses"][0]["tls"]["mode"], "cert-manager")

    def test_latest_and_malformed_tags_are_rejected(self):
        for tag in ["latest", "", "tag\nnext", "a" * 129]:
            with self.subTest(tag=tag), self.assertRaises(ValueError):
                deploy.creation_payload(self.target, tag)

    def test_new_workload_waits_for_commit_without_an_extra_release(self):
        api = Mock()
        api.request.side_effect = [None, {"runId": 42}, {"state": "running"}, {"state": "committed"}, self.values]
        release = Mock()
        with patch.object(deploy.time, "sleep"):
            deploy.deploy(api, self.target, self.tag, release)
        api.request.assert_any_call("POST", "/v1/apps/app-my-app-12345", deploy.creation_payload(self.target, self.tag))
        release.assert_not_called()

    def test_existing_workload_releases_only_the_tag_and_waits_for_it(self):
        old = copy.deepcopy(self.values)
        old["workload"]["containers"][0]["image"]["tag"] = "sha-previous"
        api = Mock()
        api.request.side_effect = [old, old, self.values]
        release = Mock()
        with patch.object(deploy.time, "sleep"):
            deploy.deploy(api, self.target, self.tag, release)
        release.assert_called_once_with(self.target["app"], self.tag)
        self.assertTrue(all(call.args[0] == "GET" for call in api.request.call_args_list))

    def test_retry_of_same_image_is_idempotent(self):
        api = Mock()
        api.request.return_value = self.values
        release = Mock()
        deploy.deploy(api, self.target, self.tag, release)
        release.assert_not_called()
        self.assertEqual(api.request.call_count, 1)

    def test_existing_unrelated_image_is_never_overwritten(self):
        self.values["workload"]["containers"][0]["image"]["repository"] = "another/image"
        api = Mock()
        api.request.return_value = self.values
        release = Mock()
        with self.assertRaisesRegex(RuntimeError, "refusing"):
            deploy.deploy(api, self.target, self.tag, release)
        release.assert_not_called()

    def test_failed_creation_is_not_reported_as_success(self):
        api = Mock()
        api.request.side_effect = [None, {"runId": 42}, {"state": "failed", "message": "upstream detail"}]
        with self.assertRaisesRegex(RuntimeError, "creation failed"):
            deploy.deploy(api, self.target, self.tag, Mock())

    def test_polling_has_a_finite_timeout(self):
        api = Mock()
        api.request.return_value = {"state": "running"}
        with patch.object(deploy, "POLL_ATTEMPTS", 2), patch.object(deploy.time, "sleep"):
            with self.assertRaisesRegex(RuntimeError, "Timed out"):
                deploy.wait_for_creation(api, 42)
        self.assertEqual(api.request.call_count, 2)

    def test_only_missing_workload_is_treated_as_absent(self):
        api = deploy.DeployApi("dummy-token")
        for code in [401, 403, 409, 500]:
            api.opener = Mock()
            api.opener.open.side_effect = HTTPError("https://example.invalid", code, "error", {}, io.BytesIO(b"secret-response"))
            with self.subTest(code=code), self.assertRaisesRegex(RuntimeError, f"HTTP {code}") as raised:
                api.request("GET", "/v1/apps/example")
            self.assertNotIn("dummy-token", str(raised.exception))
            self.assertNotIn("secret-response", str(raised.exception))
        api.opener.open.side_effect = HTTPError("https://example.invalid", 404, "error", {}, io.BytesIO())
        self.assertIsNone(api.request("GET", "/v1/apps/example"))
        with self.assertRaises(RuntimeError):
            api.request("GET", "/v1/runs/42")

    def test_redirect_never_forwards_token(self):
        self.assertIsNone(deploy.NoRedirect().redirect_request(None, None, 302, "", {}, "https://example.invalid"))

    def test_pr_cannot_trigger_deployment(self):
        with patch.dict(os.environ, {"GITHUB_EVENT_NAME": "pull_request", "GITHUB_REF": "refs/heads/main"}, clear=True):
            with patch.object(deploy, "gh") as gh, self.assertRaisesRegex(RuntimeError, "restricted"):
                deploy.apply(self.target, self.tag)
        gh.assert_not_called()

    def test_stale_push_skips_workload_mutation(self):
        environment = {"GITHUB_EVENT_NAME": "push", "GITHUB_REF": "refs/heads/main", "GITHUB_REPOSITORY": "example/My_App", "GITHUB_SHA": "old", "GH_TOKEN": "dummy"}
        with patch.dict(os.environ, environment, clear=True), patch.object(deploy, "gh", return_value="new"):
            with patch.object(deploy, "DeployApi") as api, patch("builtins.print"):
                deploy.apply(self.target, self.tag)
        api.assert_not_called()


if __name__ == "__main__":
    unittest.main()

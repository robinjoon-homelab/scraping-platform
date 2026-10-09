import copy
import io
import os
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import Mock, patch
from urllib.error import HTTPError

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import deploy


class DeploymentTests(unittest.TestCase):
    def setUp(self):
        self.environment = {"APP_NAME": "my-app", "REGISTRY_HOST": "registry.homelab.robinjoon.xyz", "REGISTRY_IMAGE": "apps/my-app"}
        self.target = deploy.identity(self.environment)
        self.tag = "sha-0123456789abcdef"
        self.values = deploy.creation_payload(self.target, self.tag)["values"]

    def test_explicit_identity_preserves_existing_name_and_registry_path(self):
        target = deploy.identity({**self.environment, "APP_NAME": "notion-blog", "REGISTRY_IMAGE": "apps/notion-blog"})
        self.assertEqual(target["app"], "notion-blog")
        self.assertEqual(target["database"], "notion_blog")
        self.assertEqual(target["repository"], "registry.homelab.robinjoon.xyz/apps/notion-blog")
        moved = deploy.identity({**self.environment, "GITHUB_REPOSITORY": "another/renamed", "GITHUB_REPOSITORY_ID": "42"})
        self.assertEqual(moved, self.target)

    def test_invalid_names_and_image_coordinates_cannot_inject_outputs(self):
        invalid = {
            "APP_NAME": ["", "My_App", "a/b", "-app", "a" * 51, "app\nimage=evil"],
            "REGISTRY_HOST": ["", "registry", "https://registry.example", "registry.example/path", "user@registry.example", "a\nimage=evil"],
            "REGISTRY_IMAGE": ["", "/apps/app", "apps/App", "apps/app:latest", "apps/app@sha256:123", "apps/app\nimage=evil"],
        }
        for key, values in invalid.items():
            for value in values:
                with self.subTest(key=key, value=value), self.assertRaises(ValueError):
                    deploy.identity({**self.environment, key: value})
            missing = self.environment.copy()
            del missing[key]
            with self.subTest(missing=key), self.assertRaises(ValueError):
                deploy.identity(missing)

    def configuration(self, settings):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "deployment.json"
            path.write_text(deploy.json.dumps(settings))
            return deploy.configuration(self.environment, path)

    def test_absent_bootstrap_file_uses_standard_spring_defaults(self):
        with tempfile.TemporaryDirectory() as directory:
            target = deploy.configuration(self.environment, Path(directory) / "missing.json")
        self.assertEqual(target, self.target)

    def test_custom_bootstrap_preserves_database_domain_secret_refs_and_service_account(self):
        entries = [{"name": "DB_PASSWORD", "secretKeyRef": {"name": "shared-db-app", "key": "password"}}]
        target = self.configuration({"database": "secret_manage_system", "host": "secrets.homelab.robinjoon.xyz", "env": entries, "serviceAccountName": "sms-secret-manager"})
        payload = deploy.creation_payload(target, self.tag)
        self.assertEqual(payload["dbName"], "secret_manage_system")
        self.assertEqual(payload["values"]["ingresses"][0]["rules"][0]["host"], "secrets.homelab.robinjoon.xyz")
        self.assertEqual(payload["values"]["workload"]["serviceAccountName"], "sms-secret-manager")
        self.assertEqual(payload["values"]["workload"]["containers"][0]["env"], entries)
        payload["values"]["workload"]["containers"][0]["env"].clear()
        self.assertEqual(target["env"], entries)

    def test_bootstrap_rejects_unsupported_or_invalid_fields(self):
        for settings in [[], {"app": "other"}, {"database": "bad/name"}, {"host": "https://example.com"}, {"serviceAccountName": "bad/name"}, {"env": {}}]:
            with self.subTest(settings=settings), self.assertRaises(ValueError):
                self.configuration(settings)

    def test_bootstrap_rejects_ambiguous_env_or_platform_host_override(self):
        invalid = [
            [{"name": "DB_HOST", "value": "other"}],
            [{"name": "X", "value": "a"}, {"name": "X", "value": "b"}],
            [{"name": "BAD-NAME", "value": "a"}],
            [{"name": "X", "value": False}],
            [{"name": "X", "value": "a", "secretKeyRef": {"name": "app", "key": "x"}}],
            [{"name": "X", "secretKeyRef": {"name": "app"}}],
            [{"name": "X", "secretKeyRef": {"name": "shared-db-app", "key": "superuser-password"}}],
        ]
        for entries in invalid:
            with self.subTest(entries=entries), self.assertRaises(ValueError):
                self.configuration({"env": entries})

    def test_database_port_must_precede_values_that_expand_it(self):
        port = {"name": "DB_PORT", "secretKeyRef": {"name": "shared-db-app", "key": "port"}}
        url = {"name": "SPRING_DATASOURCE_URL", "value": "jdbc:postgresql://$(DB_HOST):$(DB_PORT)/my_app"}
        for entries in [[url], [url, port]]:
            with self.subTest(entries=entries), self.assertRaisesRegex(ValueError, "DB_PORT must precede"):
                self.configuration({"env": entries})
        self.assertEqual(self.configuration({"env": [port, url]})["env"], [port, url])

    def test_database_credentials_are_references_and_host_belongs_to_platform(self):
        container = self.values["workload"]["containers"][0]
        environment = {entry["name"]: entry for entry in container["env"]}
        self.assertNotIn("DB_HOST", environment)
        self.assertEqual(environment["DB_PORT"]["secretKeyRef"], {"name": "shared-db-app", "key": "port"})
        self.assertEqual(environment["SPRING_DATASOURCE_PASSWORD"]["secretKeyRef"], {"name": "shared-db-app", "key": "password"})
        self.assertEqual(environment["SPRING_DATASOURCE_URL"]["value"], "jdbc:postgresql://$(DB_HOST):$(DB_PORT)/my_app")
        self.assertEqual(container["ports"][0]["containerPort"], 8080)
        self.assertEqual(self.values["ingresses"][0]["tls"]["mode"], "cert-manager")

    def test_latest_and_malformed_tags_are_rejected(self):
        for tag in ["latest", "LATEST", "", "tag\nnext", "a" * 129]:
            with self.subTest(tag=tag), self.assertRaises(ValueError):
                deploy.creation_payload(self.target, tag)

    def test_new_workload_waits_for_commit_without_an_extra_release(self):
        api = Mock()
        api.request.side_effect = [None, {"runId": 42}, {"state": "running"}, {"state": "committed"}, self.values]
        release = Mock()
        with patch.object(deploy.time, "sleep"):
            deploy.deploy(api, self.target, self.tag, release)
        api.request.assert_any_call("POST", "/v1/apps/my-app", deploy.creation_payload(self.target, self.tag))
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

    def test_existing_workload_ignores_bootstrap_changes_and_preserves_its_settings(self):
        original = copy.deepcopy(self.values)
        original["database"] = {"name": "existing_database"}
        original["workload"]["serviceAccountName"] = "existing-account"
        original["ingresses"][0]["rules"][0]["host"] = "blog.homelab.robinjoon.xyz"
        old = copy.deepcopy(original)
        old["workload"]["containers"][0]["image"]["tag"] = "previous"
        target = self.configuration({"database": "different", "host": "different.example", "env": [], "serviceAccountName": "different"})
        api = Mock()
        api.request.side_effect = [old, original]
        release = Mock()
        with patch.object(deploy, "creation_payload") as create:
            actual = deploy.deploy(api, target, self.tag, release)
        create.assert_not_called()
        release.assert_called_once_with("my-app", self.tag)
        self.assertEqual(actual, original)
        self.assertTrue(all(call.args == ("GET", "/v1/apps/my-app") for call in api.request.call_args_list))
        self.assertEqual(deploy.application_urls(actual), ["https://blog.homelab.robinjoon.xyz"])

    def test_summary_does_not_invent_an_address_for_an_internal_workload(self):
        self.assertEqual(deploy.application_urls({}), [])

    def test_invalid_ingress_response_raises_a_controlled_error_after_tag_confirmation(self):
        for ingresses in [None, {}, [None], [{"rules": None}], [{"rules": [None]}], [{"rules": [{"host": "bad\nurl"}]}]]:
            with self.subTest(ingresses=ingresses), self.assertRaisesRegex(RuntimeError, "URLs are unconfirmed"):
                deploy.application_urls({"ingresses": ingresses})

    def test_retry_of_same_image_is_idempotent(self):
        api = Mock()
        api.request.return_value = self.values
        release = Mock()
        deploy.deploy(api, self.target, self.tag, release)
        release.assert_not_called()
        self.assertEqual(api.request.call_count, 1)

    def test_missing_or_ambiguous_existing_image_is_rejected(self):
        invalid = [None, [], {}, {"workload": {"containers": []}}, {"workload": {"containers": [None]}},
                   {"workload": {"containers": [*self.values["workload"]["containers"], None]}},
                   {"workload": {"containers": [*self.values["workload"]["containers"], {}]}},
                   {"workload": {"containers": [*self.values["workload"]["containers"], {"name": "sidecar", "image": {"repository": "other", "tag": None}}]}},
                   {"workload": {"containers": self.values["workload"]["containers"] * 2}}]
        for values in invalid:
            with self.subTest(values=values), self.assertRaisesRegex(RuntimeError, "refusing"):
                deploy.current_image(values, self.target)

    def test_creation_success_without_a_workload_is_not_reported_as_success(self):
        api = Mock()
        api.request.side_effect = [None, {"runId": 42}, {"state": "committed"}, None]
        release = Mock()
        with self.assertRaisesRegex(RuntimeError, "workload is missing"):
            deploy.deploy(api, self.target, self.tag, release)
        release.assert_not_called()
        self.assertEqual(api.request.call_count, 4)

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

    def test_invalid_creation_status_raises_a_controlled_error(self):
        for state in [None, [], {}]:
            api = Mock()
            api.request.return_value = {"state": state}
            with self.subTest(state=state), self.assertRaisesRegex(RuntimeError, "invalid status"):
                deploy.wait_for_creation(api, 42)

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

    def test_success_response_with_invalid_json_shape_is_not_treated_as_missing(self):
        api = deploy.DeployApi("dummy-token")
        for body in [b"null", b"[]", b"not-json"]:
            api.opener = Mock()
            api.opener.open.return_value = io.BytesIO(body)
            with self.subTest(body=body), self.assertRaisesRegex(RuntimeError, "failed"):
                api.request("GET", "/v1/apps/my-app")

    def test_release_without_observed_tag_has_a_finite_timeout(self):
        old = copy.deepcopy(self.values)
        old["workload"]["containers"][0]["image"]["tag"] = "previous"
        api = Mock()
        api.request.return_value = old
        with patch.object(deploy, "POLL_ATTEMPTS", 2), patch.object(deploy.time, "sleep"):
            with self.assertRaisesRegex(RuntimeError, "Timed out"):
                deploy.deploy(api, self.target, self.tag, Mock())
        self.assertEqual(api.request.call_count, 3)

    def test_pr_cannot_trigger_deployment(self):
        with patch.dict(os.environ, {"GITHUB_EVENT_NAME": "pull_request", "GITHUB_REF": "refs/heads/main"}, clear=True):
            with patch.object(deploy, "gh") as gh, self.assertRaisesRegex(RuntimeError, "restricted"):
                deploy.apply(self.target, self.tag)
        gh.assert_not_called()

    def test_manual_execution_on_another_branch_cannot_trigger_deployment(self):
        with patch.dict(os.environ, {"GITHUB_EVENT_NAME": "workflow_dispatch", "GITHUB_REF": "refs/heads/feature"}, clear=True):
            with patch.object(deploy, "gh") as gh, self.assertRaisesRegex(RuntimeError, "restricted"):
                deploy.apply(self.target, self.tag)
        gh.assert_not_called()

    def test_manual_execution_on_main_uses_the_same_release_path(self):
        environment = {"GITHUB_EVENT_NAME": "workflow_dispatch", "GITHUB_REF": "refs/heads/main", "GITHUB_REPOSITORY": "example/repo", "GITHUB_SHA": "a" * 40, "GITHUB_RUN_ID": "42", "GITHUB_RUN_ATTEMPT": "1", "GH_TOKEN": "source-token", "HARNESS_ACTIONS_TOKEN": "harness-token"}
        with patch.dict(os.environ, environment, clear=True), patch.object(deploy, "gh", return_value=environment["GITHUB_SHA"]):
            with patch.object(deploy, "DeployApi"), patch.object(deploy, "deploy", return_value=self.values) as release, patch("builtins.print"):
                deploy.apply(self.target, deploy.image_tag(environment))
        release.assert_called_once()

    def test_stale_push_skips_workload_mutation(self):
        environment = {"GITHUB_EVENT_NAME": "push", "GITHUB_REF": "refs/heads/main", "GITHUB_REPOSITORY": "example/My_App", "GITHUB_SHA": "a" * 40, "GITHUB_RUN_ID": "42", "GITHUB_RUN_ATTEMPT": "1", "GH_TOKEN": "dummy"}
        with patch.dict(os.environ, environment, clear=True), patch.object(deploy, "gh", return_value="b" * 40):
            with patch.object(deploy, "DeployApi") as api, patch("builtins.print"):
                deploy.apply(self.target, deploy.image_tag(environment))
        api.assert_not_called()

    def test_mismatched_commit_run_or_attempt_is_rejected_before_any_api_call(self):
        environment = {"GITHUB_EVENT_NAME": "push", "GITHUB_REF": "refs/heads/main", "GITHUB_SHA": "a" * 40, "GITHUB_RUN_ID": "42", "GITHUB_RUN_ATTEMPT": "1"}
        tags = ["sha-previous", "sha-" + "b" * 40 + "-run-42-1", "sha-" + "a" * 40 + "-run-43-1", "sha-" + "a" * 40 + "-run-42-2"]
        for tag in tags:
            with self.subTest(tag=tag), patch.dict(os.environ, environment, clear=True):
                with patch.object(deploy, "gh") as gh, patch.object(deploy, "DeployApi") as api:
                    with self.assertRaisesRegex(ValueError, "IMAGE_TAG must match"):
                        deploy.apply(self.target, tag)
                gh.assert_not_called()
                api.assert_not_called()

    def test_image_tags_reject_invalid_run_counters_and_excessive_length(self):
        environment = {"GITHUB_SHA": "a" * 40, "GITHUB_RUN_ID": "42", "GITHUB_RUN_ATTEMPT": "1"}
        for invalid in [{"GITHUB_RUN_ID": "0"}, {"GITHUB_RUN_ATTEMPT": "0"}, {"GITHUB_RUN_ID": "４２"}, {"GITHUB_RUN_ID": "1" * 100}]:
            with self.subTest(invalid=invalid), self.assertRaises(ValueError):
                deploy.image_tag({**environment, **invalid})


if __name__ == "__main__":
    unittest.main()

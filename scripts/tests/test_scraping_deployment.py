import copy
import io
import json
import os
from pathlib import Path
import sys
import tempfile
import unittest
from contextlib import redirect_stderr, redirect_stdout
from unittest.mock import Mock, patch
from urllib.error import HTTPError

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import deploy


class ScrapingDeploymentTests(unittest.TestCase):
    def setUp(self):
        self.environment = {
            "APP_NAME": "scraping-platform",
            "REGISTRY_HOST": "registry.example.invalid",
            "REGISTRY_IMAGE": "apps/scraping-platform",
            "GITHUB_REPOSITORY": "example/scraping-platform",
            "GITHUB_SHA": "a" * 40,
            "GITHUB_RUN_ID": "123",
            "GITHUB_RUN_ATTEMPT": "2",
        }
        self.assertTrue(deploy.CONFIG_PATH.is_file(), "The scraping bootstrap configuration is required.")
        self.target = deploy.configuration(self.environment)
        self.tag = deploy.image_tag(self.environment)
        self.payload = deploy.creation_payload(self.target, self.tag)
        self.values = self.payload["values"]

    def test_explicit_scraping_identity_is_independent_of_repository_name_and_id(self):
        moved = deploy.configuration({
            **self.environment,
            "GITHUB_REPOSITORY": "another/renamed-service",
            "GITHUB_REPOSITORY_ID": "999999",
        })
        self.assertEqual(moved, self.target)
        self.assertEqual(self.target["app"], "scraping-platform")
        self.assertEqual(self.target["database"], "scraping_platform")
        self.assertEqual(self.target["repository"], "registry.example.invalid/apps/scraping-platform")
        self.assertEqual(self.target["url"], "https://scraping-platform.homelab.robinjoon.xyz")

    def test_bootstrap_requires_runtime_secret_references_and_complete_database_settings(self):
        workload = self.values["workload"]
        container = workload["containers"][0]
        entries = container["env"]
        environment = {entry["name"]: entry for entry in entries}
        self.assertEqual(set(environment), {
            "SPRING_PROFILES_ACTIVE", "DB_PORT", "SPRING_DATASOURCE_URL",
            "SPRING_DATASOURCE_USERNAME", "SPRING_DATASOURCE_PASSWORD", "SCRAPING_API_TOKEN",
        })
        self.assertEqual(environment["SPRING_PROFILES_ACTIVE"], {"name": "SPRING_PROFILES_ACTIVE", "value": "prod"})
        self.assertEqual(environment["SCRAPING_API_TOKEN"], {
            "name": "SCRAPING_API_TOKEN",
            "secretKeyRef": {"name": "scraping-platform-runtime", "key": "api-token"},
        })
        for name, key in [("DB_PORT", "port"), ("SPRING_DATASOURCE_USERNAME", "username"), ("SPRING_DATASOURCE_PASSWORD", "password")]:
            self.assertEqual(environment[name], {"name": name, "secretKeyRef": {"name": "shared-db-app", "key": key}})
        self.assertEqual(environment["SPRING_DATASOURCE_URL"]["value"], "jdbc:postgresql://$(DB_HOST):$(DB_PORT)/scraping_platform")
        names = [entry["name"] for entry in entries]
        self.assertLess(names.index("DB_PORT"), names.index("SPRING_DATASOURCE_URL"))
        self.assertNotIn("DB_HOST", environment)
        self.assertNotIn("serviceAccountName", workload)
        self.assertEqual(container["name"], "app")
        self.assertEqual(container["image"], {"repository": self.target["repository"], "tag": self.tag})
        self.assertEqual(container["ports"], [{"name": "http", "containerPort": 8080}])
        self.assertEqual(self.payload["dbName"], "scraping_platform")
        self.assertEqual(self.values["ingresses"][0]["rules"][0]["host"], "scraping-platform.homelab.robinjoon.xyz")

    def test_http_404_returns_the_created_scraping_workload(self):
        api = deploy.DeployApi("test-harness-token")
        api.opener = Mock()
        api.opener.open.side_effect = [
            HTTPError(deploy.DEPLOY_API, 404, "missing", {}, io.BytesIO()),
            self.json_response({"runId": 42}),
            self.json_response({"state": "committed"}),
            self.json_response(self.values),
        ]
        release = Mock()
        actual = deploy.deploy(api, self.target, self.tag, release)
        self.assertEqual(actual, self.values)

    def test_existing_workload_retains_database_domain_runtime_environment_and_service_account(self):
        previous = self.existing_values("previous")
        original = copy.deepcopy(previous)
        expected = self.existing_values(self.tag)
        api = Mock()
        api.request.side_effect = [previous, expected]
        release = Mock()
        actual = deploy.deploy(api, self.target, self.tag, release)
        self.assertEqual(previous, original)
        self.assertEqual(actual, expected)
        self.assertEqual(actual["workload"]["containers"][0]["env"], original["workload"]["containers"][0]["env"])
        self.assertEqual(deploy.application_urls(actual), ["https://existing-scraping.example.invalid"])

    def test_existing_runtime_does_not_require_creation_environment(self):
        target = deploy.identity(self.environment)
        expected = self.existing_values(self.tag)
        api = Mock()
        api.request.return_value = expected
        release = Mock()
        self.assertEqual(deploy.deploy(api, target, self.tag, release), expected)

    def test_existing_unrelated_repository_is_refused(self):
        existing = self.existing_values("previous")
        existing["workload"]["containers"][0]["image"]["repository"] = "registry.example.invalid/apps/unrelated"
        api = Mock()
        api.request.return_value = existing
        release = Mock()
        with self.assertRaisesRegex(RuntimeError, "refusing"):
            deploy.deploy(api, self.target, self.tag, release)

    def test_invalid_success_responses_fail_the_existing_workload_lookup(self):
        lookup_failure = "Deploy API GET /v1/apps/scraping-platform failed"
        cases = [
            (b"null", lookup_failure),
            (b"[]", lookup_failure),
            (b"not-json", lookup_failure),
            (b"{}", "Existing workload has no valid container list; refusing"),
            (b'{"workload":{"containers":[]}}', "Existing workload has a different app image repository; refusing"),
        ]
        for body, message in cases:
            api = deploy.DeployApi("test-harness-token")
            api.opener = Mock()
            api.opener.open.return_value = io.BytesIO(body)
            release = Mock()
            with self.subTest(body=body), self.assertRaisesRegex(RuntimeError, message):
                deploy.deploy(api, self.target, self.tag, release)

    def test_http_errors_fail_the_workload_lookup_without_disclosing_response_secrets(self):
        for code in [401, 403, 409, 500]:
            api = deploy.DeployApi("test-harness-token")
            api.opener = Mock()
            api.opener.open.side_effect = HTTPError(
                deploy.DEPLOY_API, code, "error", {}, io.BytesIO(b"test-sensitive-response"),
            )
            release = Mock()
            message = f"Deploy API GET /v1/apps/scraping-platform returned HTTP {code}"
            with self.subTest(code=code), self.assertRaisesRegex(RuntimeError, message) as raised:
                deploy.deploy(api, self.target, self.tag, release)
            self.assertNotIn("test-sensitive-response", str(raised.exception))
            self.assertNotIn("test-harness-token", str(raised.exception))

    def test_existing_release_waits_for_the_exact_tag_and_times_out_on_old_values(self):
        api = Mock()
        previous = self.existing_values("previous")
        api.request.return_value = previous
        release = Mock()
        with patch.object(deploy, "POLL_ATTEMPTS", 2), patch.object(deploy.time, "sleep"):
            with self.assertRaisesRegex(RuntimeError, "Timed out"):
                deploy.deploy(api, self.target, self.tag, release)
        self.assertEqual(previous, self.existing_values("previous"))

    def test_creation_commit_without_observed_target_tag_is_not_success(self):
        api = Mock()
        previous = self.existing_values("previous")
        api.request.side_effect = [None, {"runId": 42}, {"state": "committed"}, previous, previous, previous]
        release = Mock()
        with patch.object(deploy, "POLL_ATTEMPTS", 2), patch.object(deploy.time, "sleep"):
            with self.assertRaisesRegex(RuntimeError, "Timed out"):
                deploy.deploy(api, self.target, self.tag, release)

    def test_plan_outputs_only_secret_references_without_reading_secret_values(self):
        output = io.StringIO()
        environment = self.credential_environment()
        with patch.dict(os.environ, environment, clear=True), patch.object(sys, "argv", ["deploy.py", "plan"]):
            with redirect_stdout(output), patch.object(deploy, "DeployApi"), patch.object(deploy, "gh"):
                deploy.main()
        payload = json.loads(output.getvalue())
        self.assertEqual(payload["values"]["workload"]["containers"][0]["env"], self.target["env"])
        self.assert_credentials_absent(output.getvalue(), environment)

    def test_metadata_outputs_explicit_app_and_immutable_image_without_credentials(self):
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "outputs"
            environment = {**self.credential_environment(), "GITHUB_OUTPUT": str(output)}
            with patch.dict(os.environ, environment, clear=True), patch.object(sys, "argv", ["deploy.py", "metadata"]):
                with patch.object(deploy, "DeployApi"), patch.object(deploy, "gh"):
                    deploy.main()
            text = output.read_text()
        self.assertEqual(text.splitlines(), [
            "app=scraping-platform", f"tag={self.tag}", f"image={self.target['repository']}:{self.tag}",
        ])
        self.assert_credentials_absent(text, environment)

    def test_apply_reports_actual_existing_ingress_without_credentials(self):
        environment = self.credential_environment()
        api = Mock()
        api.request.side_effect = [self.existing_values("previous"), self.existing_values(self.tag)]
        output = io.StringIO()
        with tempfile.TemporaryDirectory() as directory:
            summary = Path(directory) / "summary"
            environment["GITHUB_STEP_SUMMARY"] = str(summary)
            with patch.dict(os.environ, environment, clear=True), patch.object(deploy, "gh", side_effect=["a" * 40, ""]):
                with patch.object(deploy, "DeployApi", return_value=api), redirect_stdout(output), redirect_stderr(output):
                    deploy.apply(self.target, self.tag)
            summary_text = summary.read_text()
        self.assertIn("https://existing-scraping.example.invalid", summary_text)
        self.assertNotIn(self.target["url"], summary_text)
        self.assertIn("Pod readiness are not verified", summary_text)
        self.assert_credentials_absent(output.getvalue() + summary_text, environment)

    def test_pr_is_restricted_and_stale_push_skips_deployment(self):
        environment = self.credential_environment()
        with patch.dict(os.environ, {**environment, "GITHUB_EVENT_NAME": "pull_request"}, clear=True):
            with patch.object(deploy, "gh"), patch.object(deploy, "DeployApi"):
                with self.assertRaisesRegex(RuntimeError, "restricted"):
                    deploy.apply(self.target, self.tag)
        for branch in ["main", "master"]:
            output = io.StringIO()
            with self.subTest(branch=branch), patch.dict(os.environ, {**environment, "GITHUB_REF": f"refs/heads/{branch}"}, clear=True):
                with patch.object(deploy, "gh", return_value="b" * 40), patch.object(deploy, "DeployApi"):
                    with redirect_stdout(output):
                        deploy.apply(self.target, self.tag)
                self.assertEqual(output.getvalue(), "Skipping deployment because this commit is no longer the branch head.\n")

    def existing_values(self, tag):
        values = copy.deepcopy(self.values)
        values["database"] = {"name": "existing_scraping_database"}
        values["workload"]["serviceAccountName"] = "existing-scraping-account"
        values["workload"]["replicas"] = 3
        container = values["workload"]["containers"][0]
        container["image"]["tag"] = tag
        container["resources"] = {"requests": {"cpu": "200m", "memory": "256Mi"}}
        container["env"] = [
            {"name": "SPRING_PROFILES_ACTIVE", "value": "prod"},
            {"name": "SPRING_DATASOURCE_URL", "value": "jdbc:postgresql://existing-db:5432/existing_scraping_database"},
            {"name": "SCRAPING_API_TOKEN", "secretKeyRef": {"name": "existing-scraping-runtime", "key": "operator-token"}},
        ]
        values["ingresses"][0]["rules"][0]["host"] = "existing-scraping.example.invalid"
        return values

    def credential_environment(self):
        return {
            **self.environment,
            "GITHUB_EVENT_NAME": "push",
            "GITHUB_REF": "refs/heads/main",
            "GH_TOKEN": "test-source-token",
            "HARNESS_ACTIONS_TOKEN": "test-harness-token",
            "REGISTRY_USERNAME": "test-registry-user",
            "REGISTRY_PASSWORD": "test-registry-password",
            "SCRAPING_API_TOKEN": "test-runtime-token-never-read-by-deployment",
        }

    def assert_credentials_absent(self, text, environment):
        for name in ["GH_TOKEN", "HARNESS_ACTIONS_TOKEN", "REGISTRY_USERNAME", "REGISTRY_PASSWORD", "SCRAPING_API_TOKEN"]:
            self.assertNotIn(environment[name], text)

    @staticmethod
    def json_response(value):
        return io.BytesIO(json.dumps(value).encode())


if __name__ == "__main__":
    unittest.main()

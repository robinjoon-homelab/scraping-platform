#!/usr/bin/env python3
"""Render the homelab contract, or apply it from a trusted GitHub push job."""

import argparse
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import time
from urllib.error import HTTPError, URLError
from urllib.request import HTTPRedirectHandler, Request, build_opener

DEPLOY_API = "https://deploy.homelab.robinjoon.xyz"
REGISTRY = "registry.homelab.robinjoon.xyz"
HARNESS = "robinjoon-homelab/Simple-K3S-Herness"
POLL_ATTEMPTS = 60
POLL_INTERVAL = 10


def identity(repository, repository_id):
    if not re.fullmatch(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+", repository):
        raise ValueError("GITHUB_REPOSITORY must be owner/repository.")
    if not re.fullmatch(r"[1-9][0-9]{0,19}", repository_id):
        raise ValueError("GITHUB_REPOSITORY_ID must be the numeric GitHub repository ID.")
    slug = re.sub(r"[^a-z0-9]+", "-", repository.split("/")[1].lower()).strip("-") or "app"
    # Leave room for the harness resource suffixes and retain uniqueness after truncation.
    slug = slug[: 45 - len(repository_id)].rstrip("-")
    name = f"app-{slug}-{repository_id}"
    return {
        "app": name,
        "database": name.replace("-", "_"),
        "repository": f"{REGISTRY}/apps/{name}",
        "url": f"https://{name}.homelab.robinjoon.xyz",
    }


def image_tag(environment):
    sha = environment.get("GITHUB_SHA", "")
    run_id = environment.get("GITHUB_RUN_ID", "")
    attempt = environment.get("GITHUB_RUN_ATTEMPT", "")
    if not re.fullmatch(r"[0-9a-f]{40}", sha) or not run_id.isdigit() or not attempt.isdigit():
        raise ValueError("GitHub commit SHA, run ID and attempt are required for an immutable image tag.")
    return f"sha-{sha}-run-{run_id}-{attempt}"


def creation_payload(target, tag):
    if not re.fullmatch(r"[A-Za-z0-9_][A-Za-z0-9_.-]{0,127}", tag) or tag == "latest":
        raise ValueError("A specific, valid image tag is required.")
    return {
        "image": f"{target['repository']}:{tag}",
        "dbName": target["database"],
        "values": {
            "workload": {
                "replicas": 1,
                "imagePullSecrets": [{"name": "registry-credentials"}],
                "containers": [{
                    "name": "app",
                    "image": {"repository": target["repository"], "tag": tag},
                    "ports": [{"name": "http", "containerPort": 8080}],
                    "env": [
                        {"name": "SPRING_PROFILES_ACTIVE", "value": "prod"},
                        {"name": "DB_PORT", "secretKeyRef": {"name": "shared-db-app", "key": "port"}},
                        {"name": "SPRING_DATASOURCE_URL", "value": f"jdbc:postgresql://$(DB_HOST):$(DB_PORT)/{target['database']}"},
                        {"name": "SPRING_DATASOURCE_USERNAME", "secretKeyRef": {"name": "shared-db-app", "key": "username"}},
                        {"name": "SPRING_DATASOURCE_PASSWORD", "secretKeyRef": {"name": "shared-db-app", "key": "password"}},
                    ],
                }],
            },
            "services": [{"name": "web", "ports": [{"name": "http", "port": 80, "targetPort": "http"}]}],
            "ingresses": [{
                "name": "public",
                "service": "web",
                "rules": [{"host": target["url"].removeprefix("https://"), "paths": [{"path": "/", "servicePort": "http"}]}],
                "tls": {"mode": "cert-manager"},
            }],
        },
    }


class NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        # Never forward the harness token to a redirect destination.
        return None


class DeployApi:
    def __init__(self, token):
        self.token = token
        self.opener = build_opener(NoRedirect())

    def request(self, method, path, payload=None):
        body = None if payload is None else json.dumps(payload).encode()
        request = Request(
            DEPLOY_API + path,
            data=body,
            method=method,
            headers={"Authorization": f"Bearer {self.token}", "Content-Type": "application/json"},
        )
        try:
            with self.opener.open(request, timeout=30) as response:
                return json.load(response)
        except HTTPError as error:
            error.close()
            if method == "GET" and path.startswith("/v1/apps/") and error.code == 404:
                return None
            raise RuntimeError(f"Deploy API {method} {path} returned HTTP {error.code}.") from None
        except (URLError, TimeoutError, ValueError):
            raise RuntimeError(f"Deploy API {method} {path} failed; check availability and authentication.") from None


def wait_for_creation(api, run_id):
    if not isinstance(run_id, int) or isinstance(run_id, bool) or run_id <= 0:
        raise RuntimeError("Deploy API returned an invalid workflow run ID.")
    for _ in range(POLL_ATTEMPTS):
        result = api.request("GET", f"/v1/runs/{run_id}")
        state = result.get("state")
        if state in {"committed", "unchanged"}:
            return
        if state != "running":
            raise RuntimeError(f"Workload creation failed; inspect harness Actions run {run_id}.")
        time.sleep(POLL_INTERVAL)
    raise RuntimeError(f"Timed out waiting for workload creation; inspect harness Actions run {run_id}.")


def current_image(values, target):
    containers = values.get("workload", {}).get("containers", [])
    matching = [container for container in containers if container.get("name") == "app"]
    if len(matching) != 1 or matching[0].get("image", {}).get("repository") != target["repository"]:
        raise RuntimeError("Existing workload has a different app image repository; refusing to release it.")
    return matching[0]["image"].get("tag")


def deploy(api, target, tag, release):
    path = f"/v1/apps/{target['app']}"
    values = api.request("GET", path)
    if values is None:
        result = api.request("POST", path, creation_payload(target, tag))
        wait_for_creation(api, result.get("runId"))
        values = api.request("GET", path)
        if values is None:
            raise RuntimeError("Creation finished but the workload is missing from harness main.")
    if current_image(values, target) == tag:
        return
    release(target["app"], tag)
    for _ in range(POLL_ATTEMPTS):
        values = api.request("GET", path)
        if values is not None and current_image(values, target) == tag:
            return
        time.sleep(POLL_INTERVAL)
    raise RuntimeError("Timed out waiting for the image tag commit; inspect the harness release workflow.")


def gh(arguments, token):
    result = subprocess.run(
        ["gh", *arguments],
        env={**os.environ, "GH_TOKEN": token},
        capture_output=True,
        text=True,
        timeout=60,
    )
    if result.returncode:
        raise RuntimeError("GitHub CLI request failed; check repository and Actions token permissions.")
    return result.stdout.strip()


def apply(target, tag):
    if os.environ.get("GITHUB_EVENT_NAME") != "push" or os.environ.get("GITHUB_REF") not in {"refs/heads/main", "refs/heads/master"}:
        raise RuntimeError("Deployment is restricted to push jobs on main or master.")
    branch = os.environ["GITHUB_REF"].removeprefix("refs/heads/")
    latest = gh(["api", f"repos/{os.environ['GITHUB_REPOSITORY']}/git/ref/heads/{branch}", "--jq", ".object.sha"], os.environ["GH_TOKEN"])
    if latest != os.environ.get("GITHUB_SHA"):
        print("Skipping deployment because this commit is no longer the branch head.")
        return
    token = os.environ.get("HARNESS_ACTIONS_TOKEN", "")
    if not token:
        raise RuntimeError("HARNESS_ACTIONS_TOKEN is missing; register this repository/workflow/ref in SMS.")

    def release(app, image_tag):
        gh([
            "workflow", "run", "release-workload-image.yml", "--repo", HARNESS, "--ref", "main",
            "-f", f"app={app}", "-f", "container=app", "-f", f"tag={image_tag}",
        ], token)

    deploy(DeployApi(token), target, tag, release)
    message = f"Harness main now records `{target['repository']}:{tag}`.\n\nApplication URL: {target['url']}\n\nArgo CD synchronization, DNS/TLS and Pod readiness are not verified by this job.\n"
    print(message)
    if os.environ.get("GITHUB_STEP_SUMMARY"):
        with Path(os.environ["GITHUB_STEP_SUMMARY"]).open("a") as summary:
            summary.write(message)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=["metadata", "plan", "apply"])
    arguments = parser.parse_args()
    target = identity(os.environ.get("GITHUB_REPOSITORY", ""), os.environ.get("GITHUB_REPOSITORY_ID", ""))
    if arguments.command == "plan":
        print(json.dumps(creation_payload(target, os.environ.get("IMAGE_TAG", "preview")), indent=2))
    elif arguments.command == "metadata":
        tag = image_tag(os.environ)
        outputs = {"app": target["app"], "tag": tag, "image": f"{target['repository']}:{tag}", "url": target["url"]}
        with Path(os.environ["GITHUB_OUTPUT"]).open("a") as output:
            for key, value in outputs.items():
                output.write(f"{key}={value}\n")
    else:
        tag = os.environ.get("IMAGE_TAG", "")
        creation_payload(target, tag)
        apply(target, tag)


if __name__ == "__main__":
    try:
        main()
    except (KeyError, RuntimeError, ValueError, subprocess.TimeoutExpired) as error:
        print(f"Deployment failed: {error}", file=sys.stderr)
        sys.exit(1)

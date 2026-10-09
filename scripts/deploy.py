#!/usr/bin/env python3
"""Render the homelab contract, or apply it from a trusted GitHub deployment job."""

import argparse
import copy
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
HARNESS = "robinjoon-homelab/Simple-K3S-Herness"
POLL_ATTEMPTS = 60
POLL_INTERVAL = 10
CONFIG_PATH = Path(__file__).resolve().parents[1] / ".github" / "deployment.json"
DNS_LABEL = r"[a-z0-9](?:[a-z0-9-]*[a-z0-9])?"
DNS_NAME = rf"{DNS_LABEL}(?:\.{DNS_LABEL})*"


def require_pattern(value, pattern, label, limit=253):
    if not isinstance(value, str) or len(value) > limit or not re.fullmatch(pattern, value):
        raise ValueError(f"{label} is missing or invalid.")
    return value


def identity(environment):
    # Explicit names remain stable when the GitHub repository is renamed or moved.
    name = require_pattern(environment.get("APP_NAME"), DNS_LABEL, "APP_NAME", 50)
    registry = require_pattern(environment.get("REGISTRY_HOST"), DNS_NAME, "REGISTRY_HOST")
    if "." not in registry and registry != "localhost":
        raise ValueError("REGISTRY_HOST must be a qualified hostname or localhost, not a Docker Hub namespace.")
    component = r"[a-z0-9]+(?:[._-][a-z0-9]+)*"
    image = require_pattern(environment.get("REGISTRY_IMAGE"), rf"{component}(?:/{component})*", "REGISTRY_IMAGE")
    return {
        "app": name,
        "database": name.replace("-", "_"),
        "repository": f"{registry}/{image}",
        "url": f"https://{name}.homelab.robinjoon.xyz",
    }


def validate_environment(entries):
    if not isinstance(entries, list):
        raise ValueError("Bootstrap env must be an array.")
    names = set()
    for entry in entries:
        if not isinstance(entry, dict) or set(entry) not in ({"name", "value"}, {"name", "secretKeyRef"}):
            raise ValueError("Each bootstrap env entry needs name and either value or secretKeyRef.")
        name = require_pattern(entry["name"], r"[A-Za-z_][A-Za-z0-9_]*", "Environment name")
        if name == "DB_HOST" or name in names:
            raise ValueError("Bootstrap env must not duplicate names or override platform DB_HOST.")
        if "value" in entry:
            if not isinstance(entry["value"], str):
                raise ValueError("Bootstrap environment values must be strings; store credentials in Secrets.")
            if "$(DB_PORT)" in entry["value"] and "DB_PORT" not in names:
                raise ValueError("DB_PORT must precede environment values that reference it.")
        else:
            validate_secret_reference(entry["secretKeyRef"])
        names.add(name)


def validate_secret_reference(reference):
    if not isinstance(reference, dict) or set(reference) != {"name", "key"}:
        raise ValueError("A Secret reference needs exactly name and key.")
    require_pattern(reference["name"], DNS_NAME, "Secret name")
    require_pattern(reference["key"], r"[A-Za-z0-9._-]+", "Secret key")
    if reference["name"] == "shared-db-app" and reference["key"] not in {"port", "username", "password"}:
        raise ValueError("shared-db-app only exposes port, username and password to workloads.")


def configuration(environment, path=CONFIG_PATH):
    target = identity(environment)
    try:
        settings = json.loads(path.read_text()) if path.exists() else {}
    except (OSError, ValueError):
        raise ValueError("Could not read bootstrap configuration JSON.") from None
    if not isinstance(settings, dict) or set(settings) - {"database", "host", "env", "serviceAccountName"}:
        raise ValueError("Bootstrap configuration supports database, host, env and serviceAccountName only.")
    if "database" in settings:
        target["database"] = require_pattern(settings["database"], r"[a-z_][a-z0-9_]*", "Database name", 63)
    if "host" in settings:
        target["url"] = "https://" + require_pattern(settings["host"], DNS_NAME, "Ingress host")
    if "env" in settings:
        validate_environment(settings["env"])
        target["env"] = settings["env"]
    if "serviceAccountName" in settings:
        target["serviceAccountName"] = require_pattern(settings["serviceAccountName"], DNS_NAME, "ServiceAccount name")
    return target


def image_tag(environment):
    sha = environment.get("GITHUB_SHA", "")
    run_id = environment.get("GITHUB_RUN_ID", "")
    attempt = environment.get("GITHUB_RUN_ATTEMPT", "")
    if not re.fullmatch(r"[0-9a-f]{40}", sha) or not re.fullmatch(r"[1-9][0-9]*", run_id) or not re.fullmatch(r"[1-9][0-9]*", attempt):
        raise ValueError("GitHub commit SHA, run ID and attempt are required for an immutable image tag.")
    tag = f"sha-{sha}-run-{run_id}-{attempt}"
    if len(tag) > 128:
        raise ValueError("The GitHub image tag exceeds the registry limit.")
    return tag


def default_environment(target):
    return [
        {"name": "SPRING_PROFILES_ACTIVE", "value": "prod"},
        {"name": "DB_PORT", "secretKeyRef": {"name": "shared-db-app", "key": "port"}},
        {"name": "SPRING_DATASOURCE_URL", "value": f"jdbc:postgresql://$(DB_HOST):$(DB_PORT)/{target['database']}"},
        {"name": "SPRING_DATASOURCE_USERNAME", "secretKeyRef": {"name": "shared-db-app", "key": "username"}},
        {"name": "SPRING_DATASOURCE_PASSWORD", "secretKeyRef": {"name": "shared-db-app", "key": "password"}},
    ]


def creation_payload(target, tag):
    if not re.fullmatch(r"[A-Za-z0-9_][A-Za-z0-9_.-]{0,127}", tag) or tag.lower() == "latest":
        raise ValueError("A specific, valid image tag is required.")
    payload = {
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
                    "env": copy.deepcopy(target.get("env", default_environment(target))),
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
    if "serviceAccountName" in target:
        payload["values"]["workload"]["serviceAccountName"] = target["serviceAccountName"]
    return payload


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
                result = json.load(response)
                if not isinstance(result, dict):
                    raise ValueError("Expected a JSON object.")
                return result
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
        if not isinstance(state, str):
            raise RuntimeError(f"Workload creation returned an invalid status; inspect harness Actions run {run_id}.")
        if state in {"committed", "unchanged"}:
            return
        if state != "running":
            raise RuntimeError(f"Workload creation failed; inspect harness Actions run {run_id}.")
        time.sleep(POLL_INTERVAL)
    raise RuntimeError(f"Timed out waiting for workload creation; inspect harness Actions run {run_id}.")


def current_image(values, target):
    workload = values.get("workload") if isinstance(values, dict) else None
    if not isinstance(workload, dict) or not isinstance(workload.get("containers"), list):
        raise RuntimeError("Existing workload has no valid container list; refusing to release it.")
    for container in workload["containers"]:
        if not isinstance(container, dict) or not isinstance(container.get("name"), str) or not container["name"]:
            raise RuntimeError("Existing workload contains an invalid container; refusing to release it.")
        image = container.get("image")
        if not isinstance(image, dict) or any(not isinstance(image.get(key), str) or not image[key] for key in ("repository", "tag")):
            raise RuntimeError("Existing workload contains an invalid image; refusing to release it.")
    matching = [container for container in workload["containers"] if container.get("name") == "app"]
    image = matching[0].get("image") if len(matching) == 1 else None
    if not isinstance(image, dict) or image.get("repository") != target["repository"] or not isinstance(image.get("tag"), str):
        raise RuntimeError("Existing workload has a different app image repository; refusing to release it.")
    return image["tag"]


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
        return values
    release(target["app"], tag)
    for _ in range(POLL_ATTEMPTS):
        values = api.request("GET", path)
        if values is not None and current_image(values, target) == tag:
            return values
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
    if os.environ.get("GITHUB_EVENT_NAME") not in {"push", "workflow_dispatch"} or os.environ.get("GITHUB_REF") not in {"refs/heads/main", "refs/heads/master"}:
        raise RuntimeError("Deployment is restricted to push or manual jobs on main or master.")
    if tag != image_tag(os.environ):
        raise ValueError("IMAGE_TAG must match this GitHub commit, run ID and attempt.")
    branch = os.environ["GITHUB_REF"].removeprefix("refs/heads/")
    latest = gh(["api", f"repos/{os.environ['GITHUB_REPOSITORY']}/git/ref/heads/{branch}", "--jq", ".object.sha"], os.environ["GH_TOKEN"])
    if latest != os.environ.get("GITHUB_SHA"):
        print("Skipping deployment because this commit is no longer the branch head.")
        return
    token = os.environ.get("HARNESS_ACTIONS_TOKEN", "")
    if not token:
        raise RuntimeError("HARNESS_ACTIONS_TOKEN is missing; configure this workflow's harness credentials.")

    def release(app, image_tag):
        gh([
            "workflow", "run", "release-workload-image.yml", "--repo", HARNESS, "--ref", "main",
            "-f", f"app={app}", "-f", "container=app", "-f", f"tag={image_tag}",
        ], token)

    values = deploy(DeployApi(token), target, tag, release)
    urls = application_urls(values)
    address = "Application URLs: " + ", ".join(urls) if urls else "No public ingress is declared."
    message = f"Harness main now records `{target['repository']}:{tag}`.\n\n{address}\n\nArgo CD synchronization, DNS/TLS and Pod readiness are not verified by this job.\n"
    print(message)
    if os.environ.get("GITHUB_STEP_SUMMARY"):
        with Path(os.environ["GITHUB_STEP_SUMMARY"]).open("a") as summary:
            summary.write(message)


def application_urls(values):
    ingresses = values.get("ingresses", [])
    if not isinstance(ingresses, list):
        raise RuntimeError("The image tag was confirmed, but the ingress response is invalid; URLs are unconfirmed.")
    hosts = set()
    for ingress in ingresses:
        if not isinstance(ingress, dict) or not isinstance(ingress.get("rules"), list):
            raise RuntimeError("The image tag was confirmed, but ingress rules are invalid; URLs are unconfirmed.")
        for rule in ingress["rules"]:
            host = rule.get("host") if isinstance(rule, dict) else None
            if not isinstance(host, str) or len(host) > 253 or not re.fullmatch(DNS_NAME, host):
                raise RuntimeError("The image tag was confirmed, but an ingress host is invalid; URLs are unconfirmed.")
            hosts.add("https://" + host)
    return sorted(hosts)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=["metadata", "plan", "apply"])
    arguments = parser.parse_args()
    target = configuration(os.environ)
    if arguments.command == "plan":
        print(json.dumps(creation_payload(target, os.environ.get("IMAGE_TAG", "preview")), indent=2))
    elif arguments.command == "metadata":
        tag = image_tag(os.environ)
        outputs = {"app": target["app"], "tag": tag, "image": f"{target['repository']}:{tag}"}
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

#!/usr/bin/env bash
set -euo pipefail

image=${1:?Usage: scripts/verify-runtime-image.sh IMAGE}
root=$(cd "$(dirname "$0")/.." && pwd)
mkdir -p "$root/build"
# Keep bind mounts inside the shared workspace (also works with Colima on macOS).
probe=$(mktemp -d "$root/build/runtime-trust.XXXXXX")
trap 'rm -rf "$probe"' EXIT
chmod 755 "$probe"
javac --release 25 -d "$probe" "$root/scripts/security/RuntimeTrust.java"
jar --create --file "$probe/probe.jar" --main-class RuntimeTrust -C "$probe" RuntimeTrust.class

docker image inspect "$image" > "$probe/image.json"
python3 - "$probe/image.json" <<'PY'
import json
import sys

image = json.load(open(sys.argv[1]))[0]
assert (image['Os'], image['Architecture']) == ('linux', 'amd64'), 'linux/amd64 required'
assert image['Config']['User'] == '10001:10001', 'UID/GID 10001 required'
assert image['Config']['Entrypoint'] == ['java', '-jar', '/app/application.jar'], 'Unexpected application entrypoint'
PY

# Replace only the JAR, exercising the production entrypoint, user and default trust selection.
docker run --rm --platform linux/amd64 --network none --read-only -e USE_SYSTEM_CA_CERTS=1 \
  -v "$probe/probe.jar:/app/application.jar:ro" "$image" roots
docker run --rm --platform linux/amd64 --network none --read-only \
  -v "$probe/probe.jar:/app/application.jar:ro" \
  -v "$root/scripts/security/chains:/chains:ro" "$image" chains /chains

# Already-absent roots are safe; real update or truststore failures must remain fatal.
docker run --rm --platform linux/amd64 --user 0:0 --network none --entrypoint sh \
  -v "$probe/probe.jar:/probe.jar:ro" "$image" -ec \
  'java -jar /probe.jar sanitize; java -jar /probe.jar roots'

# Reintroduce every target under an unrelated alias, including roots absent in today's base.
docker run --rm --platform linux/amd64 --user 0:0 --network none --entrypoint sh \
  -v "$probe/probe.jar:/probe.jar:ro" -v "$root/scripts/security/roots:/roots:ro" "$image" -ec '
  for certificate in /roots/*.crt; do
    keytool -importcert -cacerts -storepass changeit -noprompt -alias unrelated-alias -file "$certificate"
    if java -jar /probe.jar roots > /tmp/root-check.log 2>&1; then
      echo "ERROR: reintroduced root was accepted by verifier: $certificate" >&2
      exit 1
    fi
    grep -F "Affected trust anchor in default JSSE:" /tmp/root-check.log
    java -jar /probe.jar sanitize
    java -jar /probe.jar roots
  done
  printf invalid > /tmp/broken-truststore
  if java -Djavax.net.ssl.trustStore=/tmp/broken-truststore -jar /probe.jar roots; then
    echo "ERROR: malformed truststore passed verification" >&2
    exit 1
  fi'

# Immutable vulnerable control: failure must identify unsafe trust, not a Docker/tool error.
unsafe=eclipse-temurin@sha256:3f2223fe04b750aa5a8179e29b5c9a1ddf7665fc9bb08bfe11ad01d143bc13a5
docker pull --platform linux/amd64 "$unsafe"
if docker run --rm --platform linux/amd64 --network none --entrypoint java \
  -v "$probe/probe.jar:/probe.jar:ro" "$unsafe" -jar /probe.jar roots > "$probe/unsafe.log" 2>&1; then
  echo 'ERROR: unsafe runtime passed the verifier' >&2
  exit 1
fi
grep -F 'Affected trust anchor in default JSSE:' "$probe/unsafe.log"
if docker run --rm --platform linux/amd64 --network none --entrypoint java \
  -v "$probe/probe.jar:/probe.jar:ro" -v "$root/scripts/security/chains:/chains:ro" \
  "$unsafe" -jar /probe.jar chains /chains > "$probe/chain-control.log" 2>&1; then
  echo 'ERROR: unsafe runtime did not accept the compromised chain control' >&2
  exit 1
fi
grep -F 'Compromised chain accepted:' "$probe/chain-control.log"

docker run --rm --platform linux/amd64 --read-only -v "$probe/probe.jar:/app/application.jar:ro" "$image" https
echo "PASS: runtime image verified: $image"

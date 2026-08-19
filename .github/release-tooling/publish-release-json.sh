#!/usr/bin/env bash

set -euo pipefail

VERSION="${1:?Usage: $0 <version>}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=release-config.sh
source "${SCRIPT_DIR}/release-config.sh"
RELEASE_JSON="$(release_tooling_release_json)"
BRANCH="${GITHUB_REF_NAME:-$(git rev-parse --abbrev-ref HEAD)}"

RELEASE_JSON="${RELEASE_JSON}" bash \
  "${SCRIPT_DIR}/validate-release-manifest.sh" \
  "${VERSION}"

read_field() {
  node - "${RELEASE_JSON}" "$1" <<'NODE'
const fs = require("fs");

const [filePath, field] = process.argv.slice(2);
const value = JSON.parse(fs.readFileSync(filePath, "utf8"))[field];
if (value !== undefined && value !== null) {
  process.stdout.write(`${value}\n`);
}
NODE
}

DOWNLOAD_URL="$(read_field download_url)"
SIGNATURE_URL="$(read_field signature_download_url)"
SIGNATURE_FINGERPRINT="$(read_field signature_key_fingerprint)"
SHA256="$(read_field sha256)"

APK_ATTEMPTS="${APK_ATTEMPTS:-30}"
SIGNATURE_ATTEMPTS="${SIGNATURE_ATTEMPTS:-6}"
RETRY_DELAY="${RETRY_DELAY:-10}"

wait_for_asset() {
  local url="$1"
  local attempts="$2"
  local attempt

  echo "Waiting for ${url}"
  for ((attempt = 1; attempt <= attempts; attempt++)); do
    if curl -sfIL -o /dev/null "${url}"; then
      echo "Available after ${attempt} attempt(s)"
      return 0
    fi
    if [ "${attempt}" -lt "${attempts}" ]; then
      sleep "${RETRY_DELAY}"
    fi
  done

  echo "ERROR: ${url} is still unavailable after $((attempts * RETRY_DELAY))s" >&2
  return 1
}

wait_for_asset "${DOWNLOAD_URL}" "${APK_ATTEMPTS}"
if [ -n "${SIGNATURE_URL}" ]; then
  wait_for_asset "${SIGNATURE_URL}" "${SIGNATURE_ATTEMPTS}"
fi

git config user.name "${GIT_AUTHOR_NAME:-github-actions[bot]}"
git config user.email "${GIT_AUTHOR_EMAIL:-41898282+github-actions[bot]@users.noreply.github.com}"
git add "${RELEASE_JSON}"

if git diff --cached --quiet; then
  echo "No release JSON changes to publish"
  exit 0
fi

git commit -m "chore: Publish release v${VERSION} [skip ci]"

if ! git push origin "HEAD:${BRANCH}"; then
  git pull --rebase origin "${BRANCH}"
  git push origin "HEAD:${BRANCH}"
fi

echo "Published ${RELEASE_JSON} for v${VERSION}"
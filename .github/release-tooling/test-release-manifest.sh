#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
VALIDATE_SCRIPT="${ROOT_DIR}/.github/release-tooling/validate-release-manifest.sh"
TEST_ROOT="$(mktemp -d)"
trap 'rm -rf "${TEST_ROOT}"' EXIT

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

write_manifest() {
  local path="$1"
  cat >"${path}" <<'EOF'
{
  "created_at": "2026-08-19T00:00:00.000Z",
  "description": "TemplateApp 1.0.0 stable release.",
  "download_url": "https://github.com/owner/repository/releases/download/v1.0.0/TemplateApp-1.0.0.apk",
  "signature_download_url": "https://github.com/owner/repository/releases/download/v1.0.0/TemplateApp-1.0.0.apk.asc",
  "signature_key_fingerprint": "0123456789ABCDEF0123456789ABCDEF01234567",
  "sha256": "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
  "size_bytes": 42,
  "version": "1.0.0"
}
EOF
}

run_validator() {
  local manifest="$1"
  shift

  (
    cd "$(dirname "${manifest}")"
    RELEASE_JSON="$(basename "${manifest}")" bash "${VALIDATE_SCRIPT}" "$@"
  )
}

test_accepts_valid_manifest() {
  local manifest="${TEST_ROOT}/valid.json"
  write_manifest "${manifest}"
  run_validator "${manifest}" 1.0.0
}

test_rejects_malformed_manifest() {
  local manifest="${TEST_ROOT}/malformed.json"
  printf '{ malformed json\n' >"${manifest}"
  if run_validator "${manifest}"; then
    fail "manifest validator accepted malformed JSON"
  fi
}

test_rejects_invalid_digest() {
  local manifest="${TEST_ROOT}/invalid-digest.json"
  write_manifest "${manifest}"
  sed -i 's/0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef/INVALID/' "${manifest}"
  if run_validator "${manifest}"; then
    fail "manifest validator accepted an invalid SHA-256 digest"
  fi
}

test_rejects_unpaired_signature() {
  local manifest="${TEST_ROOT}/unpaired-signature.json"
  write_manifest "${manifest}"
  sed -i '/signature_key_fingerprint/d' "${manifest}"
  if run_validator "${manifest}"; then
    fail "manifest validator accepted an unpaired signature URL"
  fi
}

test_rejects_unexpected_version() {
  local manifest="${TEST_ROOT}/unexpected-version.json"
  write_manifest "${manifest}"
  if run_validator "${manifest}" 2.0.0; then
    fail "manifest validator accepted an unexpected release version"
  fi
}

test_rejects_absolute_manifest_path() {
  local manifest="${TEST_ROOT}/absolute-path.json"
  write_manifest "${manifest}"
  if RELEASE_JSON="${manifest}" bash "${VALIDATE_SCRIPT}" 1.0.0; then
    fail "manifest validator accepted an absolute release path"
  fi
}

test_accepts_valid_manifest
test_rejects_malformed_manifest
test_rejects_invalid_digest
test_rejects_unpaired_signature
test_rejects_unexpected_version
test_rejects_absolute_manifest_path

echo "Release manifest tests passed"

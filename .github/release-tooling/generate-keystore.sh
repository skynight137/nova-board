#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=release-config.sh
source "${SCRIPT_DIR}/release-config.sh"
ROOT_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"
KEYSTORE_PATH="${KEYSTORE_PATH:-${ROOT_DIR}/$(release_tooling_keystore_path)}"
KEYSTORE_TEMP=""

fail() {
  echo "ERROR: $*" >&2
  exit 1
}

cleanup() {
  [[ -z "${KEYSTORE_TEMP}" ]] || rm -f "${KEYSTORE_TEMP}"
}
trap cleanup EXIT

ROOT_PROJECT_NAME="$(
  release_tooling_root_project_name "${ROOT_DIR}/settings.gradle.kts"
)"

KEYSTORE_DNAME="${KEYSTORE_DNAME:-CN=${ROOT_PROJECT_NAME}, OU=Release, O=${ROOT_PROJECT_NAME}, L=Unknown, ST=Unknown, C=US}"

require_env() {
  local name="$1"
  [[ -n "${!name:-}" ]] || fail "$name is not set"
}

command -v keytool >/dev/null 2>&1 ||
  fail "keytool not found; run 'source .bin/env.sh' first"

require_env KEYSTORE_PASSWORD
require_env KEYSTORE_ENTRY_ALIAS
require_env KEYSTORE_ENTRY_PASSWORD
[[ "${KEYSTORE_PASSWORD}" == "${KEYSTORE_ENTRY_PASSWORD}" ]] ||
  fail "PKCS12 keystores require KEYSTORE_PASSWORD and KEYSTORE_ENTRY_PASSWORD to match"

mkdir -p "$(dirname "${KEYSTORE_PATH}")"
umask 077
KEYSTORE_TEMP="$(mktemp "$(dirname "${KEYSTORE_PATH}")/.keystore.XXXXXX")"
rm -f "${KEYSTORE_TEMP}"

keytool -genkeypair \
  -keystore "${KEYSTORE_TEMP}" \
  -storetype PKCS12 \
  -alias "${KEYSTORE_ENTRY_ALIAS}" \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -storepass "${KEYSTORE_PASSWORD}" \
  -keypass "${KEYSTORE_ENTRY_PASSWORD}" \
  -dname "${KEYSTORE_DNAME}" \
  -noprompt >/dev/null

[[ -s "${KEYSTORE_TEMP}" ]] || fail "keytool did not create a keystore"
mv -f "${KEYSTORE_TEMP}" "${KEYSTORE_PATH}"
KEYSTORE_TEMP=""
[[ -s "${KEYSTORE_PATH}" ]] || fail "could not install ${KEYSTORE_PATH}"
echo "Generated release keystore: ${KEYSTORE_PATH}"
#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
WORKFLOW="${ROOT_DIR}/.github/workflows/release.yml"

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

validation_line="$(grep -n -- "- name: Validate release inputs" "${WORKFLOW}" | cut -d: -f1)"
keystore_line="$(grep -n -- "- name: Setup keystore" "${WORKFLOW}" | cut -d: -f1)"
[[ "${validation_line}" -lt "${keystore_line}" ]] ||
  fail "release inputs must be validated before the keystore is decoded"

validation_block="$(
  awk '
    /- name: Validate release inputs/ { capture = 1; next }
    capture && /^      - name:/ { exit }
    capture { print }
  ' "${WORKFLOW}"
)"

for name in \
  KEYSTORE_B64 \
  KEYSTORE_PASSWORD \
  KEYSTORE_ENTRY_ALIAS \
  KEYSTORE_ENTRY_PASSWORD \
  GPG_PRIVATE_KEY_B64 \
  GPG_PASSPHRASE \
  GPG_FINGERPRINT; do
  grep -Fq "${name}: \${{ secrets.${name} }}" <<<"${validation_block}" ||
    fail "release workflow does not expose ${name} to the preflight"
done
grep -Fq 'if [[ -z "${!name}" ]]' <<<"${validation_block}" ||
  fail "release workflow does not fail fast on empty inputs"

grep -Fq 'KEYSTORE_B64}" | base64 --decode >/dev/null' <<<"${validation_block}" ||
  fail "keystore base64 is not validated before setup"
grep -Fq 'GPG_PRIVATE_KEY_B64}" | base64 --decode >/dev/null' <<<"${validation_block}" ||
  fail "GPG base64 is not validated before setup"
grep -Fq 'GPG_FINGERPRINT}" =~ ^[A-Fa-f0-9]{40}$' <<<"${validation_block}" ||
  fail "GPG fingerprint format is not validated before setup"

setup_block="$(
  awk '
    /- name: Setup keystore/ { capture = 1; next }
    capture && /^      - name:/ { exit }
    capture { print }
  ' "${WORKFLOW}"
)"
grep -Fq 'KEYSTORE_B64: ${{ secrets.KEYSTORE_B64 }}' <<<"${setup_block}" ||
  fail "keystore payload is not passed through the step environment"
grep -Fq 'KEYSTORE_PATH="$(release_tooling_keystore_path)"' <<<"${setup_block}" ||
  fail "decoded keystore is not checked for non-empty output"

manifest_validation_line="$(grep -n -- "- name: Validate release manifest" "${WORKFLOW}" | cut -d: -f1 || true)"
release_action_line="$(grep -n -- "cycjimmy/semantic-release-action@" "${WORKFLOW}" | cut -d: -f1 || true)"
[[ -n "${manifest_validation_line}" && -n "${release_action_line}" ]] ||
  fail "release workflow must validate the release manifest before semantic-release"
[[ "${manifest_validation_line}" -lt "${release_action_line}" ]] ||
  fail "release manifest validation must run before semantic-release"

manifest_validation_block="$(
  awk '
    /- name: Validate release manifest/ { capture = 1; next }
    capture && /^      - name:/ { exit }
    capture { print }
  ' "${WORKFLOW}"
)"
grep -Fq 'bash .github/release-tooling/validate-release-manifest.sh' \
  <<<"${manifest_validation_block}" ||
  fail "release workflow does not use the shared release manifest validator"
grep -Fq 'RELEASE_JSON="$(release_tooling_release_json)"' <<<"${manifest_validation_block}" ||
  fail "release workflow does not support a first release without a manifest"

grep -Fq '.github/release-tooling/validate-release-manifest.sh' "${WORKFLOW}" ||
  fail "release workflow does not use the global manifest validator"
grep -Fq '.github/release-tooling/test-release-manifest.sh' "${WORKFLOW}" ||
  fail "release workflow does not use the global manifest tests"
grep -Fq '.github/release-tooling/test-release-config.sh' "${WORKFLOW}" ||
  fail "release workflow does not use the global release configuration tests"
grep -Fq '.github/release-tooling/test-release-artifacts.sh' "${WORKFLOW}" ||
  fail "release workflow does not use the global release-tooling test namespace"
grep -Fq '.github/release-tooling/test-release-keystore.sh' "${WORKFLOW}" ||
  fail "release workflow does not test the generic keystore template"
grep -Fq '.github/release-tooling/test-github-repository.sh' "${WORKFLOW}" ||
  fail "release workflow does not test the shared GitHub repository helper"
grep -Fq '.github/release-tooling/prepare-release.sh' "${ROOT_DIR}/.releaserc.cjs" ||
  fail "semantic-release config does not use the global release-tooling namespace"

if rg -n --glob '!test-release-workflow.sh' --glob '!test-release-artifacts.sh' \
  --glob '!test-release-keystore.sh' \
  'Auto ?Click|AutoClick|copy-note' "${ROOT_DIR}/.github/release-tooling" \
  "${ROOT_DIR}/.github/workflows/release.yml" "${ROOT_DIR}/.releaserc.cjs"; then
  fail "global release tooling still contains clone-specific identity"
fi

echo "Release workflow preflight tests passed"
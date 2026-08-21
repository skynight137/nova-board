#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
PREPARE_SCRIPT="${ROOT_DIR}/.github/release-tooling/prepare-release.sh"
PUBLISH_SCRIPT="${ROOT_DIR}/.github/release-tooling/publish-release-json.sh"
TEST_ROOT="$(mktemp -d)"
TEST_FINGERPRINT="0123456789ABCDEF0123456789ABCDEF01234567"
trap 'rm -rf "${TEST_ROOT}"' EXIT

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

assert_file_absent() {
  [[ ! -e "$1" ]] || fail "expected file to be absent: $1"
}

assert_file_present() {
  [[ -e "$1" ]] || fail "expected file to be present: $1"
}

create_fixture() {
  local name="$1"
  local gradle_mode="$2"
  local module="${3:-app}"
  local release_json="${4:-app-release.json}"
  local fixture="${TEST_ROOT}/${name}"

  mkdir -p "${fixture}/${module}/build/outputs/apk/release"
  mkdir -p "${fixture}/${module}" "${fixture}/.github/release-tooling" "${fixture}/bin"
  cp "${PREPARE_SCRIPT}" "${fixture}/.github/release-tooling/prepare-release.sh"
  cp "${ROOT_DIR}/.github/release-tooling/release-config.sh" "${fixture}/.github/release-tooling/"
  cp "${ROOT_DIR}/.github/release-tooling/github-repository.sh" "${fixture}/.github/release-tooling/"
  cp "${ROOT_DIR}/.github/release-tooling/validate-release-manifest.sh" "${fixture}/.github/release-tooling/"
  cp "${PUBLISH_SCRIPT}" "${fixture}/.github/release-tooling/publish-release-json.sh"

  cat >"${fixture}/settings.gradle.kts" <<'EOF'
rootProject.name = "TemplateApp"
EOF
  cat >"${fixture}/gradle.properties" <<'EOF'
release.repository=owner/repository
EOF
  cat >"${fixture}/${module}/gradle.properties" <<'EOF'
version = 0.1.0
EOF
  cat >"${fixture}/${release_json}" <<'EOF'
{
  "created_at": "2026-01-01T00:00:00.000Z",
  "description": "previous release",
  "download_url": "https://github.com/owner/repository/releases/download/v0.1.0/TemplateApp-0.1.0.apk",
  "signature_download_url": "https://github.com/owner/repository/releases/download/v0.1.0/TemplateApp-0.1.0.apk.asc",
  "sha256": "0000000000000000000000000000000000000000000000000000000000000000",
  "size_bytes": 10,
  "version": "0.1.0"
}
EOF

  cat >"${fixture}/gradlew" <<EOF
#!/usr/bin/env bash
set -euo pipefail
if [[ "\$*" == *"--stop"* ]]; then
  exit 0
fi
case "${gradle_mode}" in
  zero)
    ;;
  one)
    printf 'apk' > ${module}/build/outputs/apk/release/TemplateApp-release.apk
    ;;
  multiple)
    printf 'apk' > ${module}/build/outputs/apk/release/TemplateApp-release.apk
    printf 'stale' > ${module}/build/outputs/apk/release/Other-release.apk
    ;;
esac
EOF
  chmod +x "${fixture}/gradlew"

  cat >"${fixture}/bin/gpg" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail

if [[ "$*" == *"--import"* ]]; then
  cat >/dev/null
  exit 0
fi

output=""
previous=""
for argument in "$@"; do
  if [[ "${previous}" == "--output" ]]; then
    output="${argument}"
  fi
  previous="${argument}"
done

if [[ -n "${output}" ]]; then
  printf 'detached signature' >"${output}"
fi

if [[ "${GPG_STUB_FAIL:-0}" == "1" ]]; then
  exit 1
fi
EOF
  chmod +x "${fixture}/bin/gpg"

  printf '%s\n' "${fixture}"
}

run_prepare() {
  local fixture="$1"
  local version="${2:-1.0.0}"
  local module="${4:-app}"
  local release_json="${5:-app-release.json}"
  (
    cd "${fixture}"
    if [[ "${3:-}" == GPG_STUB_FAIL=* ]]; then
      export "${3}"
    fi
    PATH="${fixture}/bin:${PATH}" \
      GITHUB_REPOSITORY=owner/repository \
      GPG_FINGERPRINT="${TEST_FINGERPRINT}" \
      GPG_PASSPHRASE= \
      ANDROID_MODULE="${module}" \
      RELEASE_JSON="${release_json}" \
      bash .github/release-tooling/prepare-release.sh "${version}"
  )
}

test_rejects_zero_release_apks() {
  local fixture
  fixture="$(create_fixture zero-apks zero)"
  if run_prepare "${fixture}" >"${fixture}/stdout" 2>"${fixture}/stderr"; then
    fail "prepare should reject zero release APKs"
  fi
  grep -q "exactly one release APK" "${fixture}/stderr" ||
    fail "zero-APK failure did not explain exact cardinality"
}

test_rejects_multiple_release_apks() {
  local fixture
  fixture="$(create_fixture multiple-apks multiple)"
  if run_prepare "${fixture}" >"${fixture}/stdout" 2>"${fixture}/stderr"; then
    fail "prepare should reject multiple release APKs"
  fi
  grep -q "exactly one release APK" "${fixture}/stderr" ||
    fail "multiple-APK failure did not explain exact cardinality"
}

test_prepare_is_atomic_when_signing_fails() {
  local fixture before_manifest
  fixture="$(create_fixture signing-failure one)"
  before_manifest="${fixture}/manifest.before"
  cp "${fixture}/app-release.json" "${before_manifest}"

  if GPG_STUB_FAIL=1 run_prepare "${fixture}" >"${fixture}/stdout" 2>"${fixture}/stderr"; then
    fail "prepare should fail when signing fails"
  fi

  cmp -s "${before_manifest}" "${fixture}/app-release.json" ||
    fail "manifest changed even though signing failed"
  assert_file_absent "${fixture}/app/build/outputs/apk/release/TemplateApp-1.0.0.apk"
  assert_file_absent "${fixture}/app/build/outputs/apk/release/TemplateApp-1.0.0.apk.asc"
  grep -q '^version = 0.1.0$' "${fixture}/app/gradle.properties" ||
    fail "version file was not restored after signing failed"
}

test_prepare_is_atomic_when_manifest_is_malformed() {
  local fixture before_manifest
  fixture="$(create_fixture malformed-manifest one)"
  printf '{ malformed json\n' >"${fixture}/app-release.json"
  before_manifest="$(cat "${fixture}/app-release.json")"

  if run_prepare "${fixture}" >"${fixture}/stdout" 2>"${fixture}/stderr"; then
    fail "prepare should fail when the existing manifest is malformed"
  fi

  [[ "$(cat "${fixture}/app-release.json")" == "${before_manifest}" ]] ||
    fail "malformed manifest was replaced"
  assert_file_absent "${fixture}/app/build/outputs/apk/release/TemplateApp-1.0.0.apk"
  assert_file_absent "${fixture}/app/build/outputs/apk/release/TemplateApp-1.0.0.apk.asc"
  grep -q '^version = 0.1.0$' "${fixture}/app/gradle.properties" ||
    fail "version file was not restored after manifest validation failed"
}

test_prepare_publishes_only_after_signing() {
  local fixture
  fixture="$(create_fixture successful-prepare one)"
  run_prepare "${fixture}" >"${fixture}/stdout"

  assert_file_present "${fixture}/app/build/outputs/apk/release/TemplateApp-1.0.0.apk"
  assert_file_present "${fixture}/app/build/outputs/apk/release/TemplateApp-1.0.0.apk.asc"
  grep -q '"version": "1.0.0"' "${fixture}/app-release.json" ||
    fail "successful prepare did not write the release version"
}

test_prepare_generates_description_from_project_name() {
  local stable_fixture development_fixture
  stable_fixture="$(create_fixture stable-description one)"
  run_prepare "${stable_fixture}" 1.0.0 >"${stable_fixture}/stdout"
  grep -q '"description": "TemplateApp 1.0.0 stable release."' \
    "${stable_fixture}/app-release.json" ||
    fail "stable release description did not use the project name"

  development_fixture="$(create_fixture development-description one)"
  run_prepare "${development_fixture}" 1.0.0-dev.2 >"${development_fixture}/stdout"
  grep -q '"description": "TemplateApp 1.0.0-dev.2 development release."' \
    "${development_fixture}/app-release.json" ||
    fail "development release description did not use the project name"
}

test_prepare_adjusts_version_code_after_legacy_manifest() {
  local fixture
  fixture="$(create_fixture legacy-version-code one)"
  node - "${fixture}/app-release.json" <<'NODE'
const fs = require("fs");
const path = process.argv[2];
const manifest = JSON.parse(fs.readFileSync(path, "utf8"));
manifest.version_code = 10001099;
fs.writeFileSync(path, `${JSON.stringify(manifest, null, 2)}\n`);
NODE

  run_prepare "${fixture}" 1.0.0-dev.2 >"${fixture}/stdout"
  grep -q '"version_code": 10001100' "${fixture}/app-release.json" ||
    fail "release preparation did not advance beyond the previous version code"
}

test_prepare_includes_signature_fingerprint() {
  local fixture
  fixture="$(create_fixture signature-fingerprint one)"
  run_prepare "${fixture}" 1.0.0 >"${fixture}/stdout"
  grep -q "\"signature_key_fingerprint\": \"${TEST_FINGERPRINT}\"" \
    "${fixture}/app-release.json" ||
    fail "release manifest did not include the signing key fingerprint"
}

test_prepare_supports_first_release_without_existing_manifest() {
  local fixture
  fixture="$(create_fixture first-release one)"
  rm "${fixture}/app-release.json"
  run_prepare "${fixture}" >"${fixture}/stdout"
  assert_file_present "${fixture}/app-release.json"
  grep -q '"version": "1.0.0"' "${fixture}/app-release.json" ||
    fail "first release did not create release metadata"
}

test_prepare_supports_generic_android_module_and_manifest() {
  local fixture
  fixture="$(create_fixture configured-paths one mobile release-metadata.json)"
  run_prepare "${fixture}" 1.0.0 "" mobile release-metadata.json >"${fixture}/stdout"

  assert_file_present "${fixture}/mobile/build/outputs/apk/release/TemplateApp-1.0.0.apk"
  assert_file_present "${fixture}/mobile/build/outputs/apk/release/TemplateApp-1.0.0.apk.asc"
  grep -q '"version": "1.0.0"' "${fixture}/release-metadata.json" ||
    fail "configured release manifest did not receive the release version"
  grep -q '^version = 1.0.0$' "${fixture}/mobile/gradle.properties" ||
    fail "configured Android module version file was not updated"
}

test_prepare_rejects_unsafe_android_module() {
  local fixture
  fixture="$(create_fixture unsafe-module one)"
  if (
    cd "${fixture}"
    ANDROID_MODULE="../outside" \
      GITHUB_REPOSITORY=owner/repository \
      GPG_FINGERPRINT="${TEST_FINGERPRINT}" \
      bash .github/release-tooling/prepare-release.sh 1.0.0
  ) >"${fixture}/stdout" 2>"${fixture}/stderr"; then
    fail "prepare accepted an unsafe Android module path"
  fi
  grep -q "ANDROID_MODULE must be a simple Gradle module directory name" \
    "${fixture}/stderr" ||
    fail "unsafe Android module failure did not explain the validation"
}

test_publish_uses_fixed_node_program() {
  if grep -Fq "node -e" "${PUBLISH_SCRIPT}"; then
    fail "publish script still interpolates JavaScript source with node -e"
  fi
}

test_publish_handles_hostile_json_values() {
  local fixture marker
  fixture="$(create_fixture hostile-json one)"
  marker="${fixture}/should-not-exist"
  cat >"${fixture}/app-release.json" <<EOF
{
  "version": "1.0.0",
  "download_url": "https://example.invalid/\$(touch ${marker})",
  "signature_download_url": "",
  "sha256": "0000000000000000000000000000000000000000000000000000000000000000"
}
EOF

  (
    cd "${fixture}"
    PATH="${fixture}/bin:${PATH}" \
      GITHUB_REF_NAME=dev \
      APK_ATTEMPTS=1 \
      RETRY_DELAY=0 \
      bash .github/release-tooling/publish-release-json.sh 1.0.0
  ) >"${fixture}/stdout" 2>"${fixture}/stderr" || true

  assert_file_absent "${marker}"
}

test_rejects_zero_release_apks
test_rejects_multiple_release_apks
test_prepare_is_atomic_when_signing_fails
test_prepare_is_atomic_when_manifest_is_malformed
test_prepare_publishes_only_after_signing
test_prepare_generates_description_from_project_name
test_prepare_adjusts_version_code_after_legacy_manifest
test_prepare_includes_signature_fingerprint
test_prepare_supports_first_release_without_existing_manifest
test_prepare_supports_generic_android_module_and_manifest
test_prepare_rejects_unsafe_android_module
test_publish_uses_fixed_node_program
test_publish_handles_hostile_json_values

echo "Release artifact tests passed"
#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
PREPARE_SCRIPT="${ROOT_DIR}/.github/release-tooling/prepare-release.sh"
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
  local fixture="${TEST_ROOT}/${name}"

  mkdir -p "${fixture}/${module}/build/outputs/apk/release"
  mkdir -p "${fixture}/${module}" "${fixture}/.github/release-tooling" "${fixture}/bin"
  cp "${PREPARE_SCRIPT}" "${fixture}/.github/release-tooling/prepare-release.sh"
  cp "${ROOT_DIR}/.github/release-tooling/release-config.sh" "${fixture}/.github/release-tooling/"
  cp "${ROOT_DIR}/.github/release-tooling/github-repository.sh" "${fixture}/.github/release-tooling/"

  cat >"${fixture}/settings.gradle.kts" <<'EOF'
rootProject.name = "TemplateApp"
EOF
  cat >"${fixture}/gradle.properties" <<'EOF'
release.repository=owner/repository
EOF
  cat >"${fixture}/${module}/gradle.properties" <<'EOF'
version = 0.1.0
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
  local fixture
  fixture="$(create_fixture signing-failure one)"

  if GPG_STUB_FAIL=1 run_prepare "${fixture}" >"${fixture}/stdout" 2>"${fixture}/stderr"; then
    fail "prepare should fail when signing fails"
  fi

  assert_file_absent "${fixture}/app/build/outputs/apk/release/TemplateApp-1.0.0.apk"
  assert_file_absent "${fixture}/app/build/outputs/apk/release/TemplateApp-1.0.0.apk.asc"
  grep -q '^version = 0.1.0$' "${fixture}/app/gradle.properties" ||
    fail "version file was not restored after signing failed"
}

test_prepare_publishes_apk_and_signature() {
  local fixture
  fixture="$(create_fixture successful-prepare one)"
  run_prepare "${fixture}" >"${fixture}/stdout"

  assert_file_present "${fixture}/app/build/outputs/apk/release/TemplateApp-1.0.0.apk"
  assert_file_present "${fixture}/app/build/outputs/apk/release/TemplateApp-1.0.0.apk.asc"
  grep -q '^version = 1.0.0$' "${fixture}/app/gradle.properties" ||
    fail "successful prepare did not update the release version"
}

test_prepare_supports_generic_android_module() {
  local fixture
  fixture="$(create_fixture configured-paths one mobile)"
  run_prepare "${fixture}" 1.0.0 "" mobile >"${fixture}/stdout"

  assert_file_present "${fixture}/mobile/build/outputs/apk/release/TemplateApp-1.0.0.apk"
  assert_file_present "${fixture}/mobile/build/outputs/apk/release/TemplateApp-1.0.0.apk.asc"
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

test_rejects_zero_release_apks
test_rejects_multiple_release_apks
test_prepare_is_atomic_when_signing_fails
test_prepare_publishes_apk_and_signature
test_prepare_supports_generic_android_module
test_prepare_rejects_unsafe_android_module

echo "Release artifact tests passed"
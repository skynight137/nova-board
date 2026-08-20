#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=release-config.sh
source "${SCRIPT_DIR}/release-config.sh"
TEST_ROOT="$(mktemp -d)"
trap 'rm -rf "${TEST_ROOT}"' EXIT

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

settings_file="${TEST_ROOT}/settings.gradle.kts"
cat >"${settings_file}" <<'EOF'
pluginManagement {
    repositories {
        google()
    }
}

rootProject.name = "Template App"
EOF

[[ "$(release_tooling_root_project_name "${settings_file}")" == "Template App" ]] ||
  fail "root project name was not parsed from the generic settings fixture"

cat >"${settings_file}" <<'EOF'
rootProject.name = "Template App" // release identity
EOF

[[ "$(release_tooling_root_project_name "${settings_file}")" == "Template App" ]] ||
  fail "root project name with a trailing Kotlin comment was not parsed"

printf 'include(":app")\n' >"${settings_file}"
if release_tooling_root_project_name "${settings_file}" >/dev/null 2>"${TEST_ROOT}/missing.stderr"; then
  fail "missing root project name was accepted"
fi
grep -Fq "Could not determine root project name" "${TEST_ROOT}/missing.stderr" ||
  fail "missing root project name error was not descriptive"

cat >"${settings_file}" <<'EOF'
rootProject.name = "../unsafe"
EOF
if release_tooling_root_project_name "${settings_file}" >/dev/null 2>"${TEST_ROOT}/unsafe.stderr"; then
  fail "unsafe root project name was accepted"
fi
grep -Fq "unsupported APK filename characters" "${TEST_ROOT}/unsafe.stderr" ||
  fail "unsafe root project name error was not descriptive"

[[ "$(ANDROID_MODULE=mobile release_tooling_android_module)" == "mobile" ]] ||
  fail "configured Android module was not resolved"
[[ "$(RELEASE_JSON=metadata/release.json release_tooling_release_json)" == "metadata/release.json" ]] ||
  fail "configured release manifest was not resolved"
[[ "$(ANDROID_MODULE=mobile release_tooling_version_file)" == "mobile/gradle.properties" ]] ||
  fail "version file path was not derived from the configured module"
[[ "$(ANDROID_MODULE=mobile release_tooling_release_dir)" == "mobile/build/outputs/apk/release" ]] ||
  fail "release directory was not derived from the configured module"
[[ "$(ANDROID_MODULE=mobile release_tooling_keystore_path)" == "mobile/keystore.jks" ]] ||
  fail "keystore path was not derived from the configured module"

for helper in \
  release_tooling_version_file \
  release_tooling_release_dir \
  release_tooling_keystore_path; do
  if ANDROID_MODULE="../unsafe" "${helper}" >/dev/null 2>"${TEST_ROOT}/${helper}.stderr"; then
    fail "${helper} accepted an invalid Android module"
  fi
  grep -Fq "ANDROID_MODULE must be a simple Gradle module directory name" \
    "${TEST_ROOT}/${helper}.stderr" ||
    fail "${helper} did not preserve the invalid module error"
done

echo "Release configuration tests passed"

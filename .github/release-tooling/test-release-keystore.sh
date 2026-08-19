#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
GENERATOR="${ROOT_DIR}/.github/release-tooling/generate-keystore.sh"
TEST_ROOT="$(mktemp -d)"
trap 'rm -rf "${TEST_ROOT}"' EXIT

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

fixture="${TEST_ROOT}/fixture"
mkdir -p "${fixture}/mobile" "${fixture}/bin" "${fixture}/.github/release-tooling"
cp "${GENERATOR}" "${fixture}/.github/release-tooling/generate-keystore.sh"
cp "${ROOT_DIR}/.github/release-tooling/release-config.sh" \
  "${fixture}/.github/release-tooling/release-config.sh"

cat >"${fixture}/settings.gradle.kts" <<'EOF'
rootProject.name = "TemplateApp"
EOF

cat >"${fixture}/bin/keytool" <<'EOF'
#!/usr/bin/env bash

set -euo pipefail

keystore_path=""
previous=""
for argument in "$@"; do
  if [[ "${previous}" == "-keystore" ]]; then
    keystore_path="${argument}"
  fi
  previous="${argument}"
done

[[ -n "${keystore_path}" ]] || exit 1
printf '%s\n' "$*" >"${KEYTOOL_ARGS_FILE:?}"
printf 'stub keystore\n' >"${keystore_path}"
EOF
chmod +x "${fixture}/bin/keytool"

(
  cd "${fixture}"
  PATH="${fixture}/bin:${PATH}" \
    ANDROID_MODULE=mobile \
    KEYSTORE_PASSWORD=password \
    KEYSTORE_ENTRY_ALIAS=release \
    KEYSTORE_ENTRY_PASSWORD=password \
    KEYTOOL_ARGS_FILE="${fixture}/keytool-args" \
    bash ./.github/release-tooling/generate-keystore.sh
)

grep -Fq -- "-dname CN=TemplateApp, OU=Release, O=TemplateApp, L=Unknown, ST=Unknown, C=US" \
  "${fixture}/keytool-args" ||
  fail "keystore generator did not derive its default identity from rootProject.name"

if grep -Fq "Auto Click" "${fixture}/keytool-args"; then
  fail "keystore generator still contains clone-specific identity"
fi

test -s "${fixture}/mobile/keystore.jks" ||
  fail "keystore generator did not create the configured module keystore path"

echo "Release keystore template tests passed"

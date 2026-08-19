#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=github-repository.sh
source "${SCRIPT_DIR}/github-repository.sh"

assert_repository() {
  local remote="$1"
  local expected="$2"
  local actual

  actual="$(github_repository_from_remote "${remote}")"
  if [[ "${actual}" != "${expected}" ]]; then
    echo "Expected '${expected}' for remote '${remote}', got '${actual}'" >&2
    exit 1
  fi
}

assert_repository_name() {
  local input="$1"
  local expected="$2"
  local actual

  actual="$(github_repository_from_name "${input}")"
  if [[ "${actual}" != "${expected}" ]]; then
    echo "Expected '${expected}' for repository name '${input}', got '${actual}'" >&2
    exit 1
  fi
}

authenticated_remote="https://user""%3A""pass@github.com/owner/sample-app.git"
assert_repository \
  "${authenticated_remote}" \
  "owner/sample-app"
assert_repository \
  "https://github.com/owner/sample-app.git" \
  "owner/sample-app"
assert_repository \
  "git@github.com:owner/sample-app.git" \
  "owner/sample-app"
assert_repository \
  "ssh://git@github.com/owner/sample-app.git" \
  "owner/sample-app"

if github_repository_from_remote "https://example.com/other/project.git" >/dev/null; then
  echo "Expected non-GitHub remotes to be rejected" >&2
  exit 1
fi

assert_repository_name "owner/sample-app" "owner/sample-app"
if github_repository_from_name "token@gitlab.example:owner/repo" >/dev/null; then
  echo "Expected malformed repository names to be rejected" >&2
  exit 1
fi

[[ "$(github_repository_for_release "actions-owner/actions-repo" "https://wrong-owner/wrong-repo.git")" == "actions-owner/actions-repo" ]]
[[ "$(github_repository_for_release "" "${authenticated_remote}")" == "owner/sample-app" ]]
[[ "$(github_repository_for_release "" "https://example.com/other/project.git")" == "OWNER/REPOSITORY" ]]
[[ "$(github_repository_for_release "token@gitlab.example:owner/repo" "")" == "OWNER/REPOSITORY" ]]

configured_properties="$(mktemp)"
trap 'rm -f "${configured_properties}"' EXIT
printf 'release.repository = owner/sample-app\n' >"${configured_properties}"
[[ "$(github_repository_from_gradle_properties "${configured_properties}")" == "owner/sample-app" ]]
printf 'release.repository = malformed\n' >"${configured_properties}"
if github_repository_from_gradle_properties "${configured_properties}" >/dev/null; then
  echo "Expected malformed release.repository values to be rejected" >&2
  exit 1
fi

echo "GitHub repository parsing tests passed"
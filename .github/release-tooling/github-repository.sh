#!/usr/bin/env bash

github_repository_from_remote() {
  local remote="$1"
  local repository

  case "${remote}" in
    https://github.com/*)
      repository="${remote#https://github.com/}"
      ;;
    https://*@github.com/*)
      repository="${remote#*@github.com/}"
      ;;
    git@github.com:*)
      repository="${remote#git@github.com:}"
      ;;
    ssh://git@github.com/*)
      repository="${remote#ssh://git@github.com/}"
      ;;
    *)
      return 1
      ;;
  esac

  repository="${repository%.git}"
  github_repository_from_name "${repository}"
}

github_repository_from_name() {
  local repository="$1"

  [[ "${repository}" =~ ^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$ ]] || return 1
  printf '%s\n' "${repository}"
}

github_repository_for_release() {
  local github_repository="${1:-}"
  local remote="${2:-}"
  local repository

  if [[ -n "${github_repository}" ]]; then
    repository="$(github_repository_from_name "${github_repository}" 2>/dev/null || true)"
  else
    repository="$(github_repository_from_remote "${remote}" 2>/dev/null || true)"
  fi

  printf '%s\n' "${repository:-OWNER/REPOSITORY}"
}

github_repository_from_gradle_properties() {
  local properties_file="${1:-gradle.properties}"
  local repository

  [[ -f "${properties_file}" ]] || return 1
  repository="$(
    sed -n 's/^release\.repository[[:space:]]*=[[:space:]]*//p' "${properties_file}" \
      | head -1
  )"
  github_repository_from_name "${repository}"
}
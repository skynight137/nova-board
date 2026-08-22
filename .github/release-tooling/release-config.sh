#!/usr/bin/env bash

release_tooling_android_module() {
  local module="${ANDROID_MODULE:-app}"

  if [[ ! "${module}" =~ ^[A-Za-z0-9_.-]+$ ]]; then
    echo "ERROR: ANDROID_MODULE must be a simple Gradle module directory name" >&2
    return 1
  fi

  printf '%s\n' "${module}"
}

release_tooling_root_project_name() {
  local settings_file="${1:-settings.gradle.kts}"
  local project_name

  if [[ ! -f "${settings_file}" ]]; then
    echo "ERROR: settings file not found: ${settings_file}" >&2
    return 1
  fi

  project_name="$(
    sed -nE \
      's/^[[:space:]]*rootProject\.name[[:space:]]*=[[:space:]]*"([^"]+)"[[:space:]]*(\/\/.*)?$/\1/p' \
      "${settings_file}" |
      head -1
  )"

  if [[ -z "${project_name}" ]]; then
    echo "ERROR: Could not determine root project name from ${settings_file}" >&2
    return 1
  fi

  if [[ ! "${project_name}" =~ ^[A-Za-z0-9][A-Za-z0-9\ ._-]*$ ]]; then
    echo "ERROR: rootProject.name contains unsupported APK filename characters" >&2
    return 1
  fi

  printf '%s\n' "${project_name}"
}

release_tooling_version_file() {
  local module
  module="$(release_tooling_android_module)" || return 1
  printf '%s/gradle.properties\n' "${module}"
}

release_tooling_release_dir() {
  local module
  module="$(release_tooling_android_module)" || return 1
  printf '%s/build/outputs/apk/release\n' "${module}"
}

release_tooling_keystore_path() {
  local module
  module="$(release_tooling_android_module)" || return 1
  printf '%s/keystore.jks\n' "${module}"
}

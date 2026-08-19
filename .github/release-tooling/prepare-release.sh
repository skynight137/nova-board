#!/usr/bin/env bash

set -euo pipefail

VERSION="${1:?Usage: $0 <version>}"
if [[ ! "${VERSION}" =~ ^[0-9]+\.[0-9]+\.[0-9]+([+-][0-9A-Za-z.-]+)?$ ]]; then
  echo "ERROR: release version must be a semantic version" >&2
  exit 1
fi
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=release-config.sh
source "${SCRIPT_DIR}/release-config.sh"
# shellcheck source=github-repository.sh
source "${SCRIPT_DIR}/github-repository.sh"
ANDROID_MODULE="$(release_tooling_android_module)"
RELEASE_JSON="$(release_tooling_release_json)"
VERSION_FILE="$(release_tooling_version_file)"
RELEASE_DIR="$(release_tooling_release_dir)"
GPG_HOME=""
APK_TEMP=""
SIGNATURE_TEMP=""
MANIFEST_TEMP=""
VERSION_BACKUP=""
APK_DESTINATION=""
SIGNATURE_DESTINATION=""
SIGNATURE_DESTINATION_BACKUP=""
MANIFEST_DESTINATION_BACKUP=""
APK_INSTALLED=0
SIGNATURE_INSTALLED=0
MANIFEST_INSTALLED=0
RELEASE_SUCCEEDED=0

restore_destination() {
  local destination="$1"
  local backup="$2"
  local installed="$3"

  [[ "${installed}" == "1" ]] || return 0
  if [[ -n "${backup}" ]]; then
    mv -f "${backup}" "${destination}" || echo "WARNING: could not restore ${destination}" >&2
  else
    rm -f "${destination}" || echo "WARNING: could not remove ${destination}" >&2
  fi
}

cleanup() {
  if [[ "${RELEASE_SUCCEEDED}" != "1" ]]; then
    restore_destination \
      "${RELEASE_JSON}" \
      "${MANIFEST_DESTINATION_BACKUP}" \
      "${MANIFEST_INSTALLED}"
    restore_destination \
      "${SIGNATURE_DESTINATION}" \
      "${SIGNATURE_DESTINATION_BACKUP}" \
      "${SIGNATURE_INSTALLED}"
    restore_destination "${APK_DESTINATION}" "" "${APK_INSTALLED}"
  fi

  [[ -z "${GPG_HOME}" ]] || rm -rf "${GPG_HOME}"
  [[ -z "${APK_TEMP}" ]] || rm -f "${APK_TEMP}"
  [[ -z "${SIGNATURE_TEMP}" ]] || rm -f "${SIGNATURE_TEMP}"
  [[ -z "${MANIFEST_TEMP}" ]] || rm -f "${MANIFEST_TEMP}"
  [[ -z "${SIGNATURE_DESTINATION_BACKUP}" ]] ||
    rm -f "${SIGNATURE_DESTINATION_BACKUP}"
  [[ -z "${MANIFEST_DESTINATION_BACKUP}" ]] ||
    rm -f "${MANIFEST_DESTINATION_BACKUP}"

  if [[ "${RELEASE_SUCCEEDED}" != "1" && -n "${VERSION_BACKUP}" && -f "${VERSION_BACKUP}" ]]; then
    cp "${VERSION_BACKUP}" "${VERSION_FILE}"
  fi
  [[ -z "${VERSION_BACKUP}" ]] || rm -f "${VERSION_BACKUP}"
}
trap cleanup EXIT

ROOT_PROJECT_NAME="$(release_tooling_root_project_name)"

APK_NAME="${ROOT_PROJECT_NAME}-${VERSION}.apk"
APK_DESTINATION="${RELEASE_DIR}/${APK_NAME}"
REMOTE_URL="$(git config --get remote.origin.url 2>/dev/null || true)"
REPOSITORY="$(github_repository_for_release "${GITHUB_REPOSITORY:-}" "${REMOTE_URL}")"
CONFIGURED_REPOSITORY="$(github_repository_from_gradle_properties 2>/dev/null || true)"

if [[ -z "${CONFIGURED_REPOSITORY}" ]]; then
  echo "ERROR: gradle.properties must define a valid release.repository (OWNER/REPOSITORY)" >&2
  exit 1
fi

if [[ "${REPOSITORY}" != "${CONFIGURED_REPOSITORY}" ]]; then
  echo "ERROR: release.repository (${CONFIGURED_REPOSITORY}) does not match the release repository (${REPOSITORY})" >&2
  echo "Update the canonical release.repository setting before preparing a release." >&2
  exit 1
fi

if [[ ! "${GPG_FINGERPRINT:-}" =~ ^[A-Fa-f0-9]{40}$ ]]; then
  echo "ERROR: GPG_FINGERPRINT must be a 40-character hexadecimal fingerprint" >&2
  exit 1
fi

echo "Preparing ${APK_NAME}..."

VERSION_BACKUP="$(mktemp)"
cp "${VERSION_FILE}" "${VERSION_BACKUP}"
sed -i "s/^version\s*=.*/version = ${VERSION}/" "${VERSION_FILE}"
echo "Updated ${VERSION_FILE} to version ${VERSION}"

./gradlew --stop || true
./gradlew ":${ANDROID_MODULE}:assembleRelease" --stacktrace

mapfile -t APK_CANDIDATES < <(
  find "${RELEASE_DIR}" -maxdepth 1 -type f -name '*-release.apk' -print | sort
)
if [[ "${#APK_CANDIDATES[@]}" -ne 1 ]]; then
  echo "ERROR: Expected exactly one release APK in ${RELEASE_DIR}; found ${#APK_CANDIDATES[@]}" >&2
  ls -la "${RELEASE_DIR}" || true
  exit 1
fi

APK_SOURCE="${APK_CANDIDATES[0]}"
EXPECTED_APK_SOURCE="${RELEASE_DIR}/${ROOT_PROJECT_NAME}-release.apk"
if [[ "${APK_SOURCE}" != "${EXPECTED_APK_SOURCE}" ]]; then
  echo "ERROR: Expected the universal release APK at ${EXPECTED_APK_SOURCE}, found ${APK_SOURCE}" >&2
  exit 1
fi
if [[ -e "${APK_DESTINATION}" ]]; then
  echo "ERROR: Refusing to replace existing release artifact ${APK_DESTINATION}" >&2
  exit 1
fi

APK_TEMP="$(mktemp "${RELEASE_DIR}/.${APK_NAME}.XXXXXX")"
cp "${APK_SOURCE}" "${APK_TEMP}"
echo "Staged APK at ${APK_TEMP}"

APK_SHA256="$(sha256sum "${APK_TEMP}" | awk '{print $1}')"
if [[ ! "${APK_SHA256}" =~ ^[0-9a-f]{64}$ ]]; then
  echo "ERROR: Could not calculate a valid SHA-256 digest for ${APK_NAME}" >&2
  exit 1
fi
echo "Calculated SHA-256 for ${APK_NAME}: ${APK_SHA256}"
APK_SIZE_BYTES="$(wc -c < "${APK_TEMP}" | tr -d '[:space:]')"
if [[ ! "${APK_SIZE_BYTES}" =~ ^[0-9]+$ ]]; then
  echo "ERROR: Could not calculate the size for ${APK_NAME}" >&2
  exit 1
fi
echo "Calculated size for ${APK_NAME}: ${APK_SIZE_BYTES} bytes"

if ! command -v gpg >/dev/null 2>&1; then
  echo "ERROR: GPG is required to sign ${APK_NAME}; install GPG and configure a signing key." >&2
  exit 1
fi

if [[ -n "${GPG_PRIVATE_KEY_B64:-}" ]]; then
  GPG_HOME="$(mktemp -d)"
  chmod 700 "${GPG_HOME}"
  export GNUPGHOME="${GPG_HOME}"
  printf '%s' "${GPG_PRIVATE_KEY_B64}" \
    | base64 --decode \
    | gpg --batch --import >/dev/null
fi

SIGNATURE_TEMP="$(mktemp "${RELEASE_DIR}/.${APK_NAME}.asc.XXXXXX")"
rm -f "${SIGNATURE_TEMP}"
GPG_SIGN_ARGS=(
  --armor
  --detach-sign
  --yes
  --output "${SIGNATURE_TEMP}"
)

if [[ -n "${GPG_FINGERPRINT:-}" ]]; then
  GPG_SIGN_ARGS+=(--local-user "${GPG_FINGERPRINT}")
fi

if [[ -n "${GPG_PASSPHRASE:-}" ]]; then
  if ! printf '%s' "${GPG_PASSPHRASE}" \
      | gpg --batch --pinentry-mode loopback --passphrase-fd 0 \
        "${GPG_SIGN_ARGS[@]}" "${APK_TEMP}"; then
    echo "ERROR: GPG could not sign ${APK_NAME}; check the configured key and passphrase." >&2
    exit 1
  fi
elif ! gpg --batch --no-tty --pinentry-mode loopback \
    "${GPG_SIGN_ARGS[@]}" "${APK_TEMP}"; then
  echo "ERROR: GPG could not sign ${APK_NAME}; check the configured signing key." >&2
  exit 1
fi

mkdir -p "$(dirname "${RELEASE_JSON}")"
MANIFEST_TEMP="$(mktemp "${RELEASE_JSON}.XXXXXX")"
node - "${RELEASE_JSON}" "${MANIFEST_TEMP}" "${VERSION}" "${REPOSITORY}" "${APK_NAME}" "${APK_SHA256}" "${APK_SIZE_BYTES}" "${GPG_FINGERPRINT}" "${ROOT_PROJECT_NAME}" <<'NODE'
const fs = require("fs");

const [
  manifestPath,
  outputPath,
  version,
  repository,
  apkName,
  sha256,
  sizeBytes,
  signatureKeyFingerprint,
  projectName,
] =
  process.argv.slice(2);
const manifest = fs.existsSync(manifestPath)
  ? JSON.parse(fs.readFileSync(manifestPath, "utf8"))
  : {};
const releaseBase = `https://github.com/${repository}/releases/download/v${version}`;
const numericSize = Number(sizeBytes);

if (!Number.isSafeInteger(numericSize) || numericSize < 0) {
  throw new Error("invalid APK size");
}
if (!/^[A-Fa-f0-9]{40}$/.test(signatureKeyFingerprint)) {
  throw new Error("invalid signing key fingerprint");
}

manifest.created_at = new Date().toISOString();
manifest.version = version;
const isDevelopment = version.includes("-");
manifest.description = `${projectName} ${version} ${
  isDevelopment ? "development" : "stable"
} release.`;
manifest.download_url = `${releaseBase}/${apkName}`;
manifest.signature_download_url = `${releaseBase}/${apkName}.asc`;
manifest.signature_key_fingerprint = signatureKeyFingerprint.toUpperCase();
manifest.sha256 = sha256;
manifest.size_bytes = numericSize;

fs.writeFileSync(outputPath, `${JSON.stringify(manifest, null, 2)}\n`);
NODE
RELEASE_JSON="${MANIFEST_TEMP}" bash "${SCRIPT_DIR}/validate-release-manifest.sh" "${VERSION}"

SIGNATURE_DESTINATION="${APK_DESTINATION}.asc"
if [[ -e "${SIGNATURE_DESTINATION}" ]]; then
  SIGNATURE_DESTINATION_BACKUP="$(mktemp)"
  cp -p "${SIGNATURE_DESTINATION}" "${SIGNATURE_DESTINATION_BACKUP}"
fi
if [[ -e "${RELEASE_JSON}" ]]; then
  MANIFEST_DESTINATION_BACKUP="$(mktemp)"
  cp -p "${RELEASE_JSON}" "${MANIFEST_DESTINATION_BACKUP}"
fi

mv "${APK_TEMP}" "${APK_DESTINATION}"
APK_TEMP=""
APK_INSTALLED=1
mv "${SIGNATURE_TEMP}" "${SIGNATURE_DESTINATION}"
SIGNATURE_TEMP=""
SIGNATURE_INSTALLED=1
mv "${MANIFEST_TEMP}" "${RELEASE_JSON}"
MANIFEST_TEMP=""
MANIFEST_INSTALLED=1
rm -f "${APK_SOURCE}"
RELEASE_SUCCEEDED=1
echo "Updated ${RELEASE_JSON} for ${REPOSITORY} v${VERSION}"

echo "Release v${VERSION} prepared successfully."
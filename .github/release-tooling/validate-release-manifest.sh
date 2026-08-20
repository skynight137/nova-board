#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=release-config.sh
source "${SCRIPT_DIR}/release-config.sh"
RELEASE_JSON="$(release_tooling_release_json)"
EXPECTED_VERSION="${1:-}"

node - "${RELEASE_JSON}" "${EXPECTED_VERSION}" <<'NODE'
const fs = require("fs");

const [manifestPath, expectedVersion] = process.argv.slice(2);

if (!fs.existsSync(manifestPath)) {
  throw new Error(`release manifest does not exist: ${manifestPath}`);
}

let manifest;
try {
  manifest = JSON.parse(fs.readFileSync(manifestPath, "utf8"));
} catch (error) {
  throw new Error(`release manifest is not valid JSON: ${error.message}`);
}

if (!manifest || typeof manifest !== "object" || Array.isArray(manifest)) {
  throw new Error("release manifest must contain a JSON object");
}
if (typeof manifest.version !== "string" || manifest.version.length === 0) {
  throw new Error("release manifest must contain a version");
}
if (expectedVersion && manifest.version !== expectedVersion) {
  throw new Error(
    `release manifest describes '${manifest.version}', expected '${expectedVersion}'`,
  );
}
if (
  typeof manifest.download_url !== "string" ||
  !manifest.download_url.startsWith("https://")
) {
  throw new Error("release manifest must contain an HTTPS download_url");
}
if (typeof manifest.sha256 !== "string" || !/^[0-9a-f]{64}$/.test(manifest.sha256)) {
  throw new Error("release manifest must contain a lowercase SHA-256 digest");
}

const signatureUrl = manifest.signature_download_url;
const fingerprint = manifest.signature_key_fingerprint;
const hasSignatureUrl = typeof signatureUrl === "string" && signatureUrl.length > 0;
const hasFingerprint = typeof fingerprint === "string" && fingerprint.length > 0;
if (hasSignatureUrl !== hasFingerprint) {
  throw new Error(
    "release manifest signature_download_url and signature_key_fingerprint must be paired",
  );
}
if (hasSignatureUrl && !signatureUrl.startsWith("https://")) {
  throw new Error("release manifest signature_download_url must use HTTPS");
}
if (hasFingerprint && !/^[A-Fa-f0-9]{40}$/.test(fingerprint)) {
  throw new Error("release manifest signature_key_fingerprint must be hexadecimal");
}

if (manifest.size_bytes !== undefined) {
  if (
    !Number.isSafeInteger(manifest.size_bytes) ||
    manifest.size_bytes < 0
  ) {
    throw new Error("release manifest size_bytes must be a non-negative integer");
  }
}

console.log(`Release manifest valid: ${manifestPath}`);
NODE
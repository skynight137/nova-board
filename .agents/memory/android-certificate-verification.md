---
name: Android certificate verification
description: Why the release pipeline requires the Android signing certificate fingerprint
---

Keep `ANDROID_CERTIFICATE_SHA256` verification in the release pipeline even when
users install APKs manually from GitHub Releases.

**Why:** A GitHub download, APK digest, and detached GPG signature do not replace
Android's signing identity. The certificate check catches an incorrect or
replaced keystore before publication and preserves the ability to install
future releases over existing installations.

**How to apply:** Treat the Android certificate fingerprint as a required
release input. Do not remove it unless the Android application identity and
the update/signing strategy are intentionally migrated together.
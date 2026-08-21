---
name: APK certificate formatting
description: Android signing-tool certificate fingerprints can differ in colon formatting across build-tools versions.
---

Release identity checks must remove both whitespace and colons from the
configured fingerprint and the `apksigner` output before comparing them.

**Why:** Different Android build-tools versions can emit the same SHA-256
certificate digest with or without colon separators, causing a valid keystore
to fail CI-only identity verification.

**How to apply:** Normalize both sides at the comparison boundary; retain the
original secret format for configuration and documentation.
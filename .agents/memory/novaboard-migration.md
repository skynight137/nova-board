---
name: NovaBoard archive migration
description: Compatibility notes for bringing the NovaBoard archive into the release-ready Android template.
---

The archived NovaBoard app is a native View-based IME and must not retain the
template's Compose compiler plugin or audio/equalizer dependencies. Its Kotlin
source also contains interpolated symbol strings, so literal dollar signs need
escaping when compiling with the current Kotlin toolchain.

**Why:** the archive was created with an older standalone Gradle setup and
contains resource naming that is less tolerant under the current Android build.

**How to apply:** preserve the current release/signing/formatting infrastructure,
adapt only the app module identity and dependencies, and verify both the debug
APK package ID and manifest label after migration.
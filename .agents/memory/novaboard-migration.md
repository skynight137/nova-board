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

The migration is now integrated into the current `:app` module. The remaining
work should be treated as compatibility hardening: keep the native View IME
boundary, cover platform-independent keyboard behavior with JVM tests, and run
formatting plus debug packaging after UI/resource changes.

**Why:** the archive has already been adapted to the repository's current
namespace, toolchain, release/signing setup, and native View structure; re-copying
archive build configuration would reintroduce stale assumptions.

**How to apply:** use the existing app module as the source of truth. Update
this note only when a migration constraint changes, and keep user-facing gaps
in `README.md` rather than duplicating them here.
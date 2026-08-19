---
name: Android migration verification
description: Archived Android projects may carry stale signing and toolchain overrides when moved into the current template.
---

When migrating an archived Android app into this repository, preserve the current
template's Java/Kotlin target and debug-signing conventions rather than copying
archive-only overrides blindly.

**Why:** The Aurora EQ archive used Java 11 defaults and a checked-in debug
keystore path, while this repository uses JDK 24 and generated debug signing;
copying both caused packaging failures after compilation.

**How to apply:** Merge archived app behavior and dependencies into the current
Gradle/release structure, then run formatting, unit tests, and a debug assembly.
---
name: Gradle verification resource pressure
description: How to distinguish Android Gradle daemon loss from a source or task failure in this workspace.
---

Normal Gradle commands should rely on the repository's configured
`gradle.properties` settings and should not inject ad hoc worker or heap
parameters. If a combined Android gate loses its daemon without a task-level
failure, inspect the daemon tail and memory first; only then use a bounded
retry if the repository configuration does not already provide one.

**Why:** This workspace has limited memory and no swap; concurrent lint,
compilation, dexing, and packaging can kill the single-use daemon even when
the code and individual tasks are healthy. Keeping routine commands aligned
with `gradle.properties` makes verification reproducible and avoids hiding the
project's intended resource policy behind command-line overrides.

**How to apply:** Use commands such as `./gradlew check --no-daemon` without
extra `--max-workers` or `-Dorg.gradle.jvmargs` values. If a daemon-loss retry
is genuinely needed, first check `gradle.properties`; document any temporary
override and report both the original failure and constrained result rather
than treating daemon loss as a source failure.

The project setup workflow installs Java and Android tooling under `.bin` and
writes the required shell exports to `.bin/env.sh`; source that file before
running Gradle or `adb` from a fresh shell. Instrumentation also requires an
explicitly available device or emulator, which may not be present after setup.

**Why:** The toolchain is workspace-local rather than globally exposed, and
the setup workflow installs SDK command-line tools without guaranteeing a
running Android target.

**How to apply:** Run `source .bin/env.sh` before targeted Android checks, then
confirm `adb devices -l` lists a target. If it is empty, report instrumentation
as environment-blocked instead of treating the absence as a source failure.
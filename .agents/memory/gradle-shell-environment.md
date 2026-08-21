---
name: Gradle shell environment
description: Gradle verification requires loading the repository-managed Java and Android SDK paths in a fresh shell.
---

Source `.local/env.sh` before invoking Gradle from a fresh shell, then run the
narrowest task that verifies the changed area. For a Kotlin source change,
prefer the focused unit-test task over unrelated packaging, instrumentation,
or release processes.

**Why:** The base shell may not expose `java` or `JAVA_HOME`, while the
repository-managed JDK and Android SDK are available under `.local`. Running
unrelated Gradle processes makes feedback slower without adding evidence for a
localized change.

**How to apply:** Run `source .local/env.sh`, identify the smallest relevant
Gradle task, and execute only that task. Use broader builds only when the
change affects packaging, dependency resolution, build configuration, or
release behavior.
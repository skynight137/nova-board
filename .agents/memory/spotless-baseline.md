---
name: Spotless baseline
description: Repository-wide Spotless currently reports unrelated pre-existing Kotlin formatting violations.
---

Do not run a repository-wide formatter as part of a focused change unless the
resulting unrelated churn is explicitly intended. The build, tests, and diff
gates remain the required verification boundary for focused source changes.

**Why:** A focused translation change encountered violations across unrelated
Kotlin files; formatting all of them would obscure the functional diff.

**How to apply:** Treat Spotless failures outside touched files as a documented
limitation, and use `git diff --check` plus the documented Gradle verification
tasks for the focused round.
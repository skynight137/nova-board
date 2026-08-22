---
name: Scope-matched verification
description: Verification policy for changes that affect only scripts or project configuration.
---

For Bash and configuration-only changes, use Bash syntax checks, focused behavior smoke tests, configuration validation, and diff checks. Do not run unrelated Gradle compilation or Android tests.

**Why:** Gradle verification is expensive and provides no useful evidence when Java/Kotlin or Android build inputs were not changed. Keeping this rule in the repository makes it available after the project is cloned under another workspace or account.

**How to apply:** Identify the changed files first, then run the narrowest checks that exercise those files. Add Gradle checks only when Java/Kotlin, Android resources, dependencies, build configuration, or packaging behavior is affected.
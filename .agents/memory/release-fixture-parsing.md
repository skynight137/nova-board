---
name: Release fixture parsing
description: Non-obvious formatting contract for synthetic Gradle fixtures used by release-script tests.
---

Synthetic release fixtures must preserve the exact formatting expected by the
release preparation parser, including column-1 `rootProject.name` settings.

**Why:** A harmless-looking indentation change made the fixture fail before it
reached the behavior under test, producing a misleading assertion failure.

**How to apply:** When renaming or rebuilding a fixture, compare its generated
settings file with the parser's input contract before changing release logic.
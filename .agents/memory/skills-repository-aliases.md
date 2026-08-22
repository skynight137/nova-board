---
name: Skills repository aliases
description: Compatibility aliases for repository names accepted by the setup helper.
---

The skills CLI expects a real GitHub repository reference. Keep any friendly
shorthand accepted by the setup helper explicitly mapped to its canonical
`owner/repository` name before invoking the CLI.

**Why:** A historical shorthand can look like a repository but fail cloning,
preventing later skill repositories in the same command from being processed.

**How to apply:** Verify canonical repository URLs with a read-only Git remote
check when adding aliases, and retain the user-facing shorthand only as a
documented compatibility input.
---
name: Workflow entrypoint names
description: Replit run-button targets must stay synchronized with renamed workflow names.
---

When a workflow or bootstrap script is renamed, update the exact `.replit`
run-button target and all live callers in the same change. Decide explicitly
whether old entrypoints remain supported or are retired.

**Why:** A workflow can remain configured while its run button silently targets
a removed name, and an undocumented compatibility assumption can leave callers
on a retired bootstrap path.

**How to apply:** Treat workflow names, run-button values, documented commands,
and any intentionally retained or retired entrypoints as one reference set
during any bootstrap or release workflow rename.
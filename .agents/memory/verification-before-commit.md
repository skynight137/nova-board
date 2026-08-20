---
name: Verification before commit
description: Keep failed validation commands from falling through to commit operations.
---

Always run build, test, and diff gates with fail-fast shell settings before staging
or committing. A chained command without `set -e` can create a Conventional
Commit even when an earlier test fails, requiring an avoidable amend.

**Why:** Hands-off release-style work must never leave an apparently completed
commit that was created after a failed verification step.

**How to apply:** Use `set -euo pipefail` (or explicit `&&`) before all
compile/test/diff/commit sequences, and inspect the final status after commit.
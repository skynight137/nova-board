---
name: Conventional history rewrite
description: Safe repository workflow for normalizing legacy commit subjects.
---

When normalizing commit history, the rewrite script requires a clean worktree and an explicit mapping for every legacy subject before applying.

**Why:** The script fails closed on unmapped subjects, and applying a rewrite changes commit IDs and requires deliberate post-rewrite verification before any force push.

**How to apply:** Commit or stash pending changes, run the script in preview mode, add mappings for every reported legacy subject, apply only on the intended local branch, then verify the log, tests, diff check, and clean status.
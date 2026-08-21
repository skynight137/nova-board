---
name: History rewrite safety
description: Guardrails for retroactively normalizing commit messages without changing project trees.
---

Use a dry-run mapping and require an explicit apply flag before rewriting history. With
git filter-branch, passing `HEAD` rewrites the complete reachable history including the
root commit; `--root` is not a supported filter-branch option.

**Why:** An unsupported root option stopped an otherwise safe rewrite, while an explicit
preview and clean-worktree check prevented accidental partial history changes.

**How to apply:** Keep the remote untouched until the rewritten subjects and tree diff
have been verified, then use `git push --force-with-lease` rather than `--force`.

Scoped Conventional Commit subjects must be accepted by both the preview and
rewrite matchers; validate the final history with the optional-scope pattern,
not only with exact `type:` subjects.

**Why:** Valid subjects such as `feat(ui): ...` are common in this repository,
and an exact-prefix matcher can abort a complete rewrite after it has already
been previewed successfully.

**How to apply:** Keep preview and apply mappings identical, and print the
actual current branch in any force-push guidance.
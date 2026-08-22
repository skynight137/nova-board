---
name: GIF share cache lifecycle
description: The temporary GIF files exposed to target editors must remain valid for the full URI handoff window.
---

The GIF share cache may contain multiple files that are still referenced by target editors. New downloads must prune only expired files; clearing the whole directory invalidates previously committed content-provider URIs.

**Why:** Android rich-content insertion hands the target editor a URI that can be consumed asynchronously, so a later GIF selection must not revoke an earlier handoff.

**How to apply:** Preserve files younger than the configured retention period and keep provider validation restricted to the generated GIF file namespace.
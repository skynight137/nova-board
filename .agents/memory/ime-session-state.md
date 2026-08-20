---
name: IME session state
description: Input-method state and asynchronous callbacks must be scoped to the active editor session.
---

The input method must treat editor transitions and selection changes as session boundaries: clear tracked typing/autocorrect state, dismiss overlays, disarm hotkeys, stop voice recognition, and reject callbacks from older sessions.

**Why:** Android input-method callbacks and speech results can arrive after the focused editor has changed, so stale state can modify the wrong field or delete unrelated text.

**How to apply:** When adding asynchronous editing, overlay, or recognition behavior, capture the active session identity and invalidate it on input finish, editor change, or external selection movement.
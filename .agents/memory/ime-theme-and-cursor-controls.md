---
name: IME theme and cursor controls
description: Platform-specific behavior for applying settings themes and dispatching cursor-arrow touches.
---

`InputMethodService` views do not automatically follow an `AppCompatDelegate` night-mode change. Inflate the keyboard from a configuration-bound context for the selected mode, and rebuild the active input view when the stored mode changes.

**Why:** the settings activity and the separately hosted IME window can resolve different resource configurations, leaving the keyboard on system theme after a settings-only theme change.

Cursor arrow buttons should send the initial movement on touch-down, then only send repeats from the repeat scheduler. Do not call a click handler again on touch-up for the same physical touch; reserve click dispatch for non-touch activation such as accessibility.

**Why:** sending on both touch-down and touch-up makes a single arrow press move the cursor twice while appearing like a repeat bug.

**How to apply:** keep the theme context at the IME view boundary and treat touch-down as the sole initial cursor dispatch for touch interactions.
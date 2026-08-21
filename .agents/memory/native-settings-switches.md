---
name: Native settings switches
description: The settings preference toggle uses the platform Android switch appearance.
---

Settings preference toggles should use the platform `android.widget.Switch` with
the native circular thumb and track rather than custom pill-shaped drawables.

**Why:** The native control matches the expected Android switch appearance and
avoids maintaining custom state-specific track and thumb assets.

**How to apply:** Preserve the native switch for settings preferences and keep
the surrounding row as the touch-target container; only customize dimensions
when needed to retain the minimum 44dp touch target.
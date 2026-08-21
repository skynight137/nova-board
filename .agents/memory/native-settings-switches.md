---
name: Settings switch styling
description: The settings preference toggle uses the approved compact custom switch appearance.
---

Settings preference toggles should use the compact custom switch with a circular
knob, outlined rounded track, and animated state transitions. The implementation
should remain a native Android View because NovaBoard's settings UI is
View-based.

**Why:** The supplied design uses an outlined dark off-state track and animated
color/position changes that the platform switch does not provide directly.

**How to apply:** Preserve the native switch for settings preferences and keep
the surrounding row as the touch-target container. Keep the visual control at
52dp × 30dp and its touch target at 44dp high.
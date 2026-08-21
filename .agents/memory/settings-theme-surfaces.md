---
name: Settings theme surfaces
description: Material settings dialogs require explicit NovaBoard surface and text roles in both base and night themes.
---

NovaBoard keyboard colors do not automatically propagate to Material settings dialogs. Define dialog surface, on-surface, control, status-bar, and navigation-bar roles explicitly in both `values` and `values-night` themes.

**Why:** Device screenshots exposed fallback Material gray/white dialog surfaces and low-contrast controls even though the keyboard palette itself was correct.

**How to apply:** When adding or changing settings dialogs, verify the resolved `colorSurface`, `colorOnSurface`, and activated-control colors in light and dark modes rather than relying on `DayNight` defaults.
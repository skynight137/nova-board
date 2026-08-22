---
name: Tools overlay touch boundary
description: The Keyboard tools screen must fully own IME touches while it is open.
---

The Keyboard tools screen is a modal replacement for the keyboard, not a passive decoration above it. Its full-size overlay container must be clickable and focusable so taps cannot reach the hidden key renderer. The close control should use the live toggle button's position rather than a fixed overlay coordinate.

**Why:** The tools screen lives inside the active IME window, and the expandable tools row changes the toolbar's Y position. Fixed overlay coordinates make the three-dot and close controls drift apart, while a missing touch boundary can route taps through to the hidden keyboard.

**How to apply:** Keep tool actions and close navigation inside the overlay. Anchor replacement controls from the source view's window coordinates, and make every full-screen IME panel's host consume touches before relying on child views to cover the keyboard.
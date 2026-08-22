---
name: Tools overlay touch boundary
description: The Keyboard tools screen must fully own IME touches while it is open.
---

The Keyboard tools screen is a modal replacement for the keyboard, not a passive decoration above it. Its full-size overlay container must be clickable and focusable so taps cannot reach the hidden key renderer.

**Why:** The tools screen lives inside the active IME window. Without an explicit touch boundary, Android can route taps through the visible menu to the keyboard underneath, producing invisible text input.

**How to apply:** Keep tool actions and back navigation inside the overlay. When adding another full-screen IME panel, make its host consume touches before relying on child views to cover the keyboard.
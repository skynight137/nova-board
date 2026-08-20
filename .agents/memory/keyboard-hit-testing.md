---
name: Keyboard hit testing
description: Touch hit-testing rules that prevent ordinary keyboard taps from being lost.
---

When a key press starts inside a key, transient pointer movement outside the key should not clear the active hit unless the interaction is explicitly cancelled.

**Why:** finger jitter is common on touch keyboards; clearing the hit on every out-of-bounds move turns valid taps into missed keystrokes and makes typing feel intermittent.

**How to apply:** preserve the pressed key through minor movement, while keeping deliberate gesture, drag, long-press, and cancellation paths responsible for changing or cancelling the interaction.

Popup previews and long-press menus must clamp their window position to the IME window instead of relying on a fixed negative Y offset.

**Why:** the top keyboard row can be near the window origin, so a fixed upward offset renders the popup off-screen and makes enabled key popups appear broken.

**How to apply:** measure the popup content where possible and clamp its top coordinate to the keyboard window’s top edge.
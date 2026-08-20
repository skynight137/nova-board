---
name: Keyboard hit testing
description: Touch hit-testing rules that prevent ordinary keyboard taps from being lost.
---

When a key press starts inside a key, transient pointer movement outside the key should not clear the active hit unless the interaction is explicitly cancelled.

**Why:** finger jitter is common on touch keyboards; clearing the hit on every out-of-bounds move turns valid taps into missed keystrokes and makes typing feel intermittent.

**How to apply:** preserve the pressed key through minor movement, while keeping deliberate gesture, drag, long-press, and cancellation paths responsible for changing or cancelling the interaction.
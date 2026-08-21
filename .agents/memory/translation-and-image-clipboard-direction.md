---
name: Translation and image clipboard direction
description: Product boundary for native translation UI and optional image clipboard retention.
---

Translation belongs inside the keyboard: normal mode is the default, while
live-write translation is an explicit opt-in mode with cancellation and session
guards. Image clipboard history is opt-in, defaults off for new installs, and
must never capture image bytes while disabled.

**Why:** The external translation activity does not match the intended keyboard
experience, and clipboard images can contain sensitive content that should not
be retained without a clear user choice.

**How to apply:** Build the image preference/capture gate first, then the native
translation composer and normal mode, then provider integration, and only then
live-write translation. Keep target-editor cursor state separate from the
translation source-field cursor when committing pasted results.
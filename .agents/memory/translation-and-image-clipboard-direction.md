---
name: Image clipboard retention
description: Product boundary for optional image clipboard retention after translation UI removal.
---

Image clipboard history is opt-in, defaults off for new installs, and must never
capture image bytes while disabled. Translation UI is not part of the current
product surface.

**Why:** Clipboard images can contain sensitive content that should not be
retained without a clear user choice, and the unsupported translation surface
created a misleading entry point.

**How to apply:** Keep image capture behind the existing preference gate and
preserve the local, app-owned storage boundary. Do not reintroduce translation
controls without a new product and privacy decision.
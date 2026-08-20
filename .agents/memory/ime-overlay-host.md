---
name: IME overlay host
description: Hosting interactive clipboard and tool panels inside the keyboard input view.
---

Interactive panels that replace or cover the keyboard should be attached to a dedicated overlay container in the IME input view, not positioned with `PopupWindow.showAtLocation`.

**Why:** IME window anchors can have different tokens and bounds across editors and Android versions, so popup panels may fail to appear even though the click handler runs.

**How to apply:** keep panel lifecycle owned by `NovaBoardService`, clear the overlay on input-session reset, and use full-width/full-height layout parameters sized by the keyboard view.
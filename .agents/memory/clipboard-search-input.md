---
name: Clipboard search input
description: Clipboard search must provide an in-panel keyboard because NovaBoard is already the active IME.
---

The clipboard overlay must render a NovaBoard keyboard inside the panel and disable system soft-input-on-focus for its search field.

**Why:** The overlay covers the service's main keyboard, and Android cannot reliably launch the same active IME to type into an EditText hosted inside that IME.

**How to apply:** Route the embedded keyboard's character, backspace, symbol, and letter actions to the search field; reserve space for its configured row count when laying out clipboard results.
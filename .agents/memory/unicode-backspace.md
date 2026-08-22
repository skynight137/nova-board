---
name: Unicode backspace
description: Input-method deletion behavior for supplementary Unicode characters.
---

Backspace deletion must determine the UTF-16 length of the previous Unicode code point before calling `deleteSurroundingText`; deleting one code unit can leave half of an emoji surrogate pair and render a replacement glyph.

**Why:** Android text APIs expose surrounding-text counts in UTF-16 code units, while many emoji occupy two units.

**How to apply:** use a code-point-aware count for ordinary backspace and keep regression tests for supplementary characters such as `😀`.
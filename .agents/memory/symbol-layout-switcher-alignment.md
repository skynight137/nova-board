---
name: Symbol layout switcher alignment
description: Symbol-page navigation rows must follow the number-row preference so switcher touch targets remain stable.
---

Primary and secondary symbol pages must place their layout switcher and backspace keys on the same row for the active number-row preference.

**Why:** Switching from `{&=` to `123` used to move the controls vertically when the number row was enabled, making the user's touch target jump between pages.

**How to apply:** Build secondary symbols with the same number-row preference as primary symbols; keep the four-row shape and order the secondary punctuation row around the switcher row rather than adding or removing rows.
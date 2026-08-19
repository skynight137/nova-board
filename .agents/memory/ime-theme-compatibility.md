---
name: IME theme compatibility
description: Theme constraints for native keyboard views inflated inside Android IME windows.
---

Native `ImageButton` styles used by the keyboard must avoid unresolved
theme-dependent background attributes. Some Android 12 device IME windows do
not resolve `?attr/selectableItemBackgroundBorderless` during view
construction; concrete drawable or color resources are safer.

**Why:** an otherwise valid keyboard layout crashed while inflating its first
toolbar button on a Redmi Note 9 Pro, before any keyboard interaction ran.

**How to apply:** keep IME toolbar styles self-contained and validate on a real
device or emulator in addition to compiling the layout.
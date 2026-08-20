---
name: Patch-added file endings
description: The patch helper can create text files without the terminal newline required by repository checks.
---

Patch-added text files may pass focused checks but fail repository formatting
only because the file ends without a newline. Prefer updating an existing
formatted file or verify the new file ending immediately after adding it.

**Why:** Repository formatting checks enforce terminal newlines, and the patch
helper's add-file path can preserve a missing final newline.

**How to apply:** When adding a new text file, inspect its final byte and run
`git diff --check` plus the relevant formatter before committing; if the
add-file path cannot normalize the ending, fold the focused code into an
existing formatted source file.

The same formatting gate catches import ordering and line wrapping after edits
to existing files, so a successful compile is not sufficient. Run
`./gradlew spotlessApply` before the final `check :app:assembleDebug` pass when
Kotlin sources have changed.

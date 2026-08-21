# Implementation Plan: Align Emoji, Settings, and Quick-delete Contracts

## Overview

Remove misleading UI promises and make the remaining emoji/settings/quick-delete behavior explicit and testable. The result should either implement a control completely or remove/label it as unavailable; summaries must describe the actual interaction.

## Progress

- [x] **Task 1 — Emoji controls:** Static GIF/media and redundant search affordances removed; accessibility labels and search regression coverage added; media behavior remains intentionally absent.
- [~] **Task 2 — Emoji font claim:** Labels now describe the actual default and sans-serif typeface choices; bundled Google emoji remains intentionally unclaimed; unsupported stored values now normalize to the system renderer.
- [x] **Task 3 — Silent typing settings:** Implemented in `b9321e7`; tests still belong to the regression plan.
- [✓] **Task 4 — Quick-delete contract:** Summary and service behavior now match previous-word deletion, including preceding whitespace; stale autocorrect state is cleared after ordinary and quick deletion. Android accessibility review remains deferred.

## Tasks

### Task 1: Make emoji controls honest and functional `[x]`

- [x] Remove the static `GIF ✦ ▧ ◉` header text rather than shipping fake controls.
- [x] Remove the redundant category search affordance; the actual search field remains functional.
- [x] Decide that GIF/media remains absent until a separate product/integration scope exists; do not expose non-functional controls.
- [x] Add accessibility labels and deterministic search regression coverage for retained controls.

**Files likely touched:**
- `app/src/main/java/com/novaboard/ime/emoji/EmojiPanel.kt`
- `app/src/main/res/values/strings.xml`
- `app/src/test/java/com/novaboard/ime/emoji/EmojiDataTest.kt`

### Task 2: Resolve the emoji font claim `[x]`

- [x] Rename the setting to accurately describe the available system typeface behavior.
- [x] Keep both preference values mapped to observable rendering choices.
- [x] Add a test for preference normalization; selected renderer behavior remains covered by the existing panel mapping.

**Files likely touched:**
- `app/src/main/java/com/novaboard/ime/emoji/EmojiPanel.kt`
- `app/src/main/java/com/novaboard/ime/settings/KeyboardPreferences.kt`
- `app/src/main/java/com/novaboard/ime/settings/MainActivity.kt`
- `app/src/main/res/values/strings.xml`
- `app/src/test/java/com/novaboard/ime/...`

### Task 3: Remove or implement silent typing settings `[x]`

- [x] Remove `SHOW_PREDICTIONS_AFTER_FLOW`, `SYSTEM_GRAMMAR_CORRECTIONS`, and `SYSTEM_GRAMMAR_SUGGESTIONS` constants/defaults.
- [x] Remove their orphaned string resources and confirm there are no source references.
- [ ] Add regression tests for the retained preference set in the separate regression plan.

**Files likely touched:**
- `app/src/main/java/com/novaboard/ime/settings/KeyboardPreferences.kt`
- `app/src/main/java/com/novaboard/ime/settings/MainActivity.kt`
- `app/src/main/java/com/novaboard/ime/NovaBoardService.kt`
- `app/src/test/java/com/novaboard/ime/settings/...`

### Task 4: Align quick-delete behavior and summary `[~]`

- [x] Choose the “delete the previous word” contract.
- [x] Update the setting summary to describe previous-word deletion.
- [x] Add behavior tests for previous-word boundaries and punctuation/Unicode.
- [x] Ensure the pure autocorrect contract rejects an editor snapshot changed
  between replacement and backspace; service deletion paths clear stale state.

**Files likely touched:**
- `app/src/main/java/com/novaboard/ime/view/KeyboardView.kt`
- `app/src/main/java/com/novaboard/ime/NovaBoardService.kt`
- `app/src/main/res/values/strings.xml`
- `app/src/test/java/com/novaboard/ime/...`

### Verification gate

- [ ] Tests cover emoji search/control state, font preference behavior, retained settings, and quick-delete semantics.
- [ ] `source .local/env.sh && ./gradlew :app:testDebugUnitTest`
- [ ] `source .local/env.sh && ./gradlew :app:assembleDebug`
- [ ] `git diff --check`
- [ ] Commit: `fix: align emoji and typing preference contracts`

## Open decision

GIF/media remains out of scope for the native keyboard. A future integration must be a separately scoped product change; the keyboard must not expose controls that only look interactive.
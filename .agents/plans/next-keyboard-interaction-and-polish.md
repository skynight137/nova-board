# Implementation Plan: Smooth Gestures, Incognito Mode, and a Cleaner Keyboard Surface

## Overview

Evolve NovaBoard toward the interaction model shown in the attached SwiftKey references without copying
provider-specific UI or introducing fake controls. The work should make gestures feel deliberate and
continuous, keep private typing from entering the learned dictionary, and move secondary actions into
one compact in-keyboard menu. Existing native Android Views and `NovaBoardService` lifecycle ownership
remain the foundation.

## Reference interpretation

- **Gesture input dialog:** provide an explicit choice between ordinary key-by-key typing and gesture
  typing; do not silently change the current input mode.
- **Incognito banner/background:** show a clear private-mode state in the keyboard itself, with an
  obvious undo/exit action and a background treatment that remains readable in both light and dark
  themes.
- **Tools grid:** replace the crowded tools row with a compact toolbar plus an overflow/menu surface.
  Every visible item must be wired, disabled with an explanation, or omitted.
- **Keyboard layout:** preserve the reference’s generous key spacing, compact utility strip, number-row
  support, and bottom cursor controls while adapting dimensions to the device rather than hard-coding
  screenshot pixels.

## Architecture decisions

1. **Keep gesture recognition in `KeyboardView`, but keep text mutation in `NovaBoardService`.**
   `KeyboardView` owns touch paths, thresholds, repeat timing, and pressed visuals; the service remains
   the only owner of `InputConnection`, prediction learning, deletion, and session invalidation.
2. **Use explicit preferences for gesture mode and incognito mode.**
   Mode changes must survive keyboard recreation but be resettable from settings. Incognito must not
   be inferred from the editor alone.
3. **Treat incognito as a learning boundary, not merely a color theme.**
   While active, do not call `SuggestionEngine.learn`, do not persist new learned words, and do not
   expose private text through diagnostics. Existing dictionary suggestions remain available unless
   the user chooses a stricter “hide predictions” option.
4. **Use one overflow menu model for secondary actions.**
   The menu must be data-driven so ordering, visibility, and availability can be tested without
   constructing Android views.
5. **Prefer bounded repeat and gesture thresholds.**
   Long-press delete and cursor movement must stop on `ACTION_UP`/`ACTION_CANCEL`, pause when the
   editor session changes, and never create an unbounded deletion or cursor-motion loop.

## Ordered task list

### Phase 1: Interaction contracts and preferences

#### Task 1: Define gesture and private-mode contracts `[x]`

**Description:** Add pure, JVM-testable contracts for gesture mode, incognito state, swipe
recognition thresholds, repeat timing, and overflow-menu item availability.

**Acceptance criteria:**
- [x] Gesture mode has explicit `FLOW` and `GESTURES` values with a safe default matching current
  key-by-key behavior.
- [x] Incognito state can be enabled, exited, and queried without changing the user’s normal theme.
- [x] Gesture thresholds distinguish a short tap, a deliberate swipe, and an interrupted/cancelled
  path.
- [x] Repeat timing is bounded and deterministic in tests.

**Verification:**
- [x] Focused JVM tests cover mode defaults, cancellation, thresholds, and menu filtering.
- [x] `source .bin/env.sh && ./gradlew :app:testDebugUnitTest`

**Dependencies:** None

**Files likely touched:**
- `app/src/main/java/com/novaboard/ime/settings/KeyboardPreferences.kt`
- `app/src/main/java/com/novaboard/ime/gesture/...`
- `app/src/main/java/com/novaboard/ime/tools/...`
- `app/src/test/java/com/novaboard/ime/gesture/...`

**Estimated scope:** Medium

#### Task 2: Add the settings entry points `[x]`

**Description:** Add a gesture-input preference dialog modeled after the reference and an incognito
toggle/entry point. Keep summaries honest and make the active state visible from the keyboard.

**Acceptance criteria:**
- [x] Users can select key-by-key typing or gesture typing from settings.
- [x] Users can enter and exit incognito without leaving the active editor.
- [x] The settings summary reflects the selected mode and does not claim unsupported behavior.
- [x] Accessibility labels announce gesture mode and incognito state.

**Verification:**
- [ ] Preference tests cover reset/default behavior.
- [ ] Debug APK builds and the settings resources compile.

**Dependencies:** Task 1

**Files likely touched:**
- `app/src/main/java/com/novaboard/ime/settings/MainActivity.kt`
- `app/src/main/res/layout/activity_main.xml`
- `app/src/main/res/values/strings.xml`
- `app/src/main/java/com/novaboard/ime/settings/KeyboardPreferences.kt`

**Estimated scope:** Medium

### Checkpoint: Contracts and settings

- [ ] Unit tests pass.
- [ ] `source .bin/env.sh && ./gradlew :app:assembleDebug`
- [ ] `git diff --check`
- [ ] Commit: `feat: add gesture and incognito contracts`

### Phase 2: Gesture interaction

#### Task 3: Implement gesture word entry

**Description:** Recognize a deliberate path across letter keys and emit one word to the service.
Start with a bounded, deterministic implementation using the existing key geometry and dictionary;
do not attempt probabilistic full-keyboard prediction in the first slice.

**Acceptance criteria:**
- [x] A path crossing letters produces one commit on release, with no duplicate key commits.
- [x] Repeated keys and minor path jitter are normalized deterministically.
- [x] Paths leaving the keyboard, crossing non-letter keys, or ending in cancellation fail safely.
- [x] Gesture commits respect auto-space, session identity through the active input connection, and incognito learning rules.

**Verification:**
- [ ] Pure path-normalization and word-commit tests cover valid, invalid, and cancelled paths.
- [x] Manual check: gesture mode does not activate while flow mode is selected.

**Dependencies:** Tasks 1-2

**Files likely touched:**
- `app/src/main/java/com/novaboard/ime/view/KeyboardView.kt`
- `app/src/main/java/com/novaboard/ime/gesture/GestureRecognizer.kt`
- `app/src/main/java/com/novaboard/ime/NovaBoardService.kt`
- `app/src/test/java/com/novaboard/ime/gesture/...`

**Estimated scope:** Large; split recognition and service integration if implementation exceeds one slice.

#### Task 4: Make held delete smart and bounded

**Description:** Replace the current one-shot left-swipe quick delete with an intentional held-delete
interaction. Short press still deletes one code point; a held or configured smart-delete gesture
deletes the previous word/segment in bounded chunks.

**Acceptance criteria:**
- [ ] Short backspace preserves current behavior.
- [ ] Holding backspace deletes progressively, with a pause/acceleration policy that remains bounded.
- [ ] Word deletion handles whitespace, punctuation, and surrogate pairs safely.
- [ ] Release/cancel/session change stops deletion immediately and clears stale typing/autocorrect state.

**Verification:**
- [ ] Pure deletion-boundary tests cover words, spaces, punctuation, Unicode pairs, and empty input.
- [ ] Manual check: a held key cannot continue deleting after the finger leaves the key.

**Dependencies:** Task 1

**Files likely touched:**
- `app/src/main/java/com/novaboard/ime/view/KeyboardView.kt`
- `app/src/main/java/com/novaboard/ime/NovaBoardService.kt`
- `app/src/main/java/com/novaboard/ime/editing/...`
- `app/src/test/java/com/novaboard/ime/editing/...`

**Estimated scope:** Medium

#### Task 5: Make arrow/cursor movement continuous

**Description:** Add a repeatable cursor-control interaction for holding an arrow/control key while
  preserving the existing spacebar horizontal cursor gesture as a compatible fallback.

**Acceptance criteria:**
- [ ] Holding an arrow control repeats movement at a bounded cadence.
- [ ] Movement stops on release, cancel, editor/session change, or missing input connection.
- [ ] Direction changes do not leave a previous repeat runnable active.
- [ ] Cursor movement never commits text or invalidates unrelated typing state.

**Verification:**
- [ ] Repeat-controller tests cover start, stop, direction change, and session invalidation.
- [ ] Manual check: cursor movement feels continuous without runaway acceleration.

**Dependencies:** Task 1

**Files likely touched:**
- `app/src/main/java/com/novaboard/ime/view/KeyboardView.kt`
- `app/src/main/java/com/novaboard/ime/NovaBoardService.kt`
- `app/src/test/java/com/novaboard/ime/editing/...`

**Estimated scope:** Medium

### Checkpoint: Gesture interactions

- [ ] Gesture entry, smart delete, and cursor repeat tests pass together.
- [ ] `source .bin/env.sh && ./gradlew :app:assembleDebug`
- [ ] `git diff --check`
- [ ] Commit each vertical slice with Conventional Commit messages:
  - `feat: add gesture word entry`
  - `feat: add bounded smart delete`
  - `feat: add continuous cursor movement`

### Phase 3: Incognito behavior and visual polish

#### Task 6: Enforce incognito learning isolation

**Description:** Add the service-level learning boundary and visual state treatment shown in the
reference. Incognito should be obvious but should not make the keyboard unusable.

**Acceptance criteria:**
- [x] New words and autocorrect learning are not persisted while incognito is active.
- [ ] Existing suggestions remain available unless the user explicitly disables predictions.
- [ ] The keyboard shows an incognito indicator/background treatment in light and dark themes.
- [ ] Exiting incognito returns to the prior visual state without losing normal preferences.
- [ ] Input-session reset and service recreation preserve or clear incognito according to the documented
  preference policy.

**Verification:**
- [ ] Suggestion tests prove no learning writes occur in incognito.
- [ ] Theme/state tests cover enter, exit, recreation, and both theme modes.
- [ ] Manual check: private text is not added to clipboard history automatically by this feature and
  is not included in diagnostic exports.

**Dependencies:** Tasks 1-2

**Files likely touched:**
- `app/src/main/java/com/novaboard/ime/NovaBoardService.kt`
- `app/src/main/java/com/novaboard/ime/suggestion/SuggestionEngine.kt`
- `app/src/main/java/com/novaboard/ime/theme/ThemeManager.kt`
- `app/src/main/res/layout/keyboard_container.xml`
- `app/src/main/res/values/colors.xml`
- `app/src/main/res/values-night/colors.xml`

**Estimated scope:** Large

#### Task 7: Consolidate tools into a clean overflow menu

**Description:** Replace the crowded always-visible tools row with a small set of high-frequency
  icons and a three-dot menu/grid for secondary actions, following the attached reference’s hierarchy.

**Acceptance criteria:**
- [ ] Clipboard, emoji, voice, search, translation, settings, and future actions have one source of
  truth for menu labels, icons, availability, and click behavior.
- [ ] The overflow surface opens inside the keyboard window and dismisses on outside tap, editor
  change, and back/cancel.
- [ ] Menu items are keyboard-focusable/touch-target sized and have content descriptions.
- [ ] Unsupported GIF/media/rewards-style items are omitted rather than shown as dead controls.
- [ ] The main keyboard keeps enough vertical space for keys and suggestions.

**Verification:**
- [ ] Menu-model tests cover ordering, unavailable-item omission, and dismissal state.
- [ ] Manual check against the attached light/dark references at narrow and tall phone sizes.
- [ ] `source .bin/env.sh && ./gradlew :app:assembleDebug`

**Dependencies:** Tasks 1-2; may run in parallel with Task 6 after the menu contract exists.

**Files likely touched:**
- `app/src/main/res/layout/keyboard_container.xml`
- `app/src/main/java/com/novaboard/ime/NovaBoardService.kt`
- `app/src/main/java/com/novaboard/ime/tools/...`
- `app/src/main/res/drawable/...`
- `app/src/main/res/values/strings.xml`

**Estimated scope:** Large

#### Task 8: Tune responsive keyboard geometry and motion

**Description:** Apply the reference’s spacing, row proportions, pressed states, key-preview timing,
  and subtle transitions without sacrificing touch targets or reduced-motion behavior.

**Acceptance criteria:**
- [ ] Key rows scale from available width/height and never clip the bottom controls.
- [ ] Pressed, long-press, menu, incognito, and gesture states have clear visual feedback.
- [ ] Key-preview and menu transitions are short, cancellable, and disabled/reduced under the
  accessibility reduced-motion preference where applicable.
- [ ] Light/dark contrast and content descriptions remain valid after theme changes.

**Verification:**
- [ ] Layout/model tests cover number-row and symbol-page geometry.
- [ ] Manual visual pass at phone portrait, narrow portrait, and dark theme sizes.
- [ ] `source .bin/env.sh && ./gradlew :app:assembleDebug`

**Dependencies:** Tasks 5-7

**Files likely touched:**
- `app/src/main/java/com/novaboard/ime/view/KeyboardView.kt`
- `app/src/main/res/layout/keyboard_container.xml`
- `app/src/main/res/values/themes.xml`
- `app/src/main/res/values-night/themes.xml`
- `app/src/main/res/drawable/...`

**Estimated scope:** Large

### Checkpoint: Interaction and visual polish

- [ ] All gesture, incognito, menu, and layout acceptance criteria pass.
- [ ] `source .bin/env.sh && ./gradlew :app:testDebugUnitTest`
- [ ] `source .bin/env.sh && ./gradlew :app:assembleDebug`
- [ ] `git diff --check`
- [ ] Each completed slice has its own Conventional Commit.
- [ ] No generated APKs, screenshots, secrets, or unrelated formatting changes are committed.

## Risks and mitigations

| Risk | Impact | Mitigation |
|---|---|---|
| Gesture paths can conflict with ordinary taps or scrolling | High | Explicit mode, minimum path distance, cancellation rules, and path tests before service integration |
| Repeating delete/cursor actions can continue after lifecycle changes | High | Central repeat controller with release/cancel/session invalidation and bounded cadence |
| Incognito can leak through learning or diagnostics | High | Gate every learning write at the service boundary and add regression tests |
| Overflow menu reduces usable keyboard height | Medium | Measure available height, cap menu size, and keep primary actions always reachable |
| Screenshot-specific styling breaks on other phones | Medium | Use density/resource dimensions and verify multiple aspect ratios |
| Adding GIF/media controls creates an unscoped provider integration | Medium | Keep unsupported items absent until a separate integration decision is approved |

## Definition of done for this plan

- Every new interaction is explicit, cancellable, session-safe, and covered by a pure contract test.
- The keyboard remains usable in flow mode without requiring gesture input.
- Incognito changes learning/privacy behavior as well as appearance.
- Secondary actions are discoverable from one clean menu with no dead controls.
- Each vertical slice passes compile, tests, diff check, and a Conventional Commit before the next slice starts.
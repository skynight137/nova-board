# Implementation Plan: Prevent Keyboard Review Regressions in Lifecycle and Preference Behavior

## Overview

Add focused JVM-testable seams and regression coverage for the NovaBoard keyboard behaviors changed during the lifecycle, clipboard, and layout hardening pass. The plan intentionally tests contracts at the service/model boundaries instead of trying to instantiate Android framework services in ordinary JVM tests. The implementation must preserve the existing native Android Views architecture and finish with the repository's compile, test, diff, and Conventional Commit gates.

## Current context

- The service now scopes editor mutations and speech callbacks to an active input session.
- Selection changes invalidate tracked typing state unless the tracked word is still immediately before the cursor.
- Clipboard persistence parses entries defensively and the panel removes its change listener on dismissal.
- The number-row preference is applied when entering both letters and symbols pages.
- Current JVM tests live under `app/src/test/java/com/novaboard/ime`.
- Existing verification commands are documented in `README.md`:
  - `source .bin/env.sh && ./gradlew :app:testDebugUnitTest`
  - `source .bin/env.sh && ./gradlew :app:assembleDebug`

## Progress

**Status: `[~] Pure editing, editor-policy, and preference contracts covered; Android lifecycle and persistence seams remain**

- [x] Production prerequisites for session cleanup, number-row preference, clipboard listener cleanup, and inactive-setting removal exist.
- [~] Quick-delete summary and emoji-on-enter policy are partially aligned; focused tests are missing.
- [ ] Add deterministic lifecycle/session seams and tests.
- [~] Add layout, clipboard, editing-state, and preference regression tests; pure editing, editor-policy, and emoji-font normalization coverage now exists.
- [ ] Run the final verification gate and commit the test coverage.

## Architecture decisions

1. **Prefer pure contract seams over Android framework mocks.** Session invalidation, voice-result acceptance, symbol-page selection, and clipboard parsing should be represented by small deterministic helpers or model functions that JVM tests can call directly.
2. **Keep `NovaBoardService` as the lifecycle authority.** Tests should verify the service delegates to the seams and invalidates state at lifecycle boundaries; they should not move state ownership into test-only abstractions.
3. **Treat malformed persisted clipboard entries as isolated failures.** A bad entry must be skipped while valid entries continue loading and ID allocation remains monotonic.
4. **Test user-visible preference contracts, not preference storage alone.** A setting is covered only when its enabled/disabled value changes the resulting layout or interaction behavior.
5. **Use one focused commit for the test/seam change.** The commit must use a Conventional Commit message and include the exact verification commands in the handoff.

## Task List

### Phase 1: Test seams and state contracts

## Task 1: Define deterministic input-session acceptance rules `[ ]`

**Description:** Extract or expose a small package-visible state contract for accepting editor mutations and asynchronous recognition results. The contract should make the active-session identity and tracked-word invalidation behavior testable without constructing `InputMethodService` or `SpeechRecognizer`.

**Acceptance criteria:**
- [ ] A result from an older session is rejected even if recognition is still delivering callbacks.
- [ ] A result from the current session is accepted only for the current recognizer/input target.
- [ ] Selection/editor transitions clear tracked word, previous word, and undo-autocorrect state.
- [ ] A keyboard-owned commit does not immediately invalidate a word that is still directly before the cursor.

**Verification:**
- [ ] Focused JVM tests cover accepted, stale, and changed-editor cases.
- [ ] `source .bin/env.sh && ./gradlew :app:testDebugUnitTest` passes.

**Dependencies:** None

**Files likely touched:**
- `app/src/main/java/com/novaboard/ime/NovaBoardService.kt`
- `app/src/test/java/com/novaboard/ime/.../InputSession...Test.kt`

**Estimated scope:** Medium: 3-5 files

## Task 2: Cover preference-aware keyboard layout contracts `[ ]`

**Description:** Add model-level tests for the number-row preference across letters, primary symbols, secondary symbols, and return-to-letters behavior. Keep the preference decision in the existing keyboard model/view boundary rather than duplicating layout definitions in tests.

**Acceptance criteria:**
- [ ] Enabled number-row preference produces a number row on letters and primary symbols pages.
- [ ] Disabled number-row preference omits the number row on both pages.
- [ ] Secondary symbols behavior remains unchanged and returns to the correct preference-aware letters page.
- [ ] Long-press-symbol enablement is independent from key-preview enablement at the interaction contract level.

**Verification:**
- [ ] Tests assert row counts and key types rather than pixel positions.
- [ ] Existing `KeyboardModelTest` remains green.

**Dependencies:** None

**Files likely touched:**
- `app/src/main/java/com/novaboard/ime/model/KeyboardModel.kt`
- `app/src/main/java/com/novaboard/ime/view/KeyboardView.kt`
- `app/src/test/java/com/novaboard/ime/model/KeyboardModelTest.kt`
- `app/src/test/java/com/novaboard/ime/view/...` if a view seam is needed

**Estimated scope:** Medium: 3-5 files

### Checkpoint: State and layout foundation

- [ ] Focused tests for Tasks 1-2 pass.
- [ ] No Android-only test dependency is introduced unnecessarily.
- [ ] `:app:compileDebugKotlin` succeeds.

### Phase 2: Persistence and panel lifecycle regression coverage

## Task 3: Prevent clipboard startup and panel lifecycle regressions `[ ]`

**Description:** Make clipboard persistence and listener ownership independently testable, then cover malformed JSON, valid mixed entries, duplicate startup import, text clips without a plain-text MIME declaration, and listener removal when panels are dismissed or replaced.

**Acceptance criteria:**
- [ ] One malformed JSON entry does not prevent valid entries from loading.
- [ ] Invalid IDs/types are skipped without corrupting `nextId`.
- [ ] A pre-existing primary clip is imported once and duplicate protection prevents repeated startup imports.
- [ ] Dismissing a clipboard panel removes its listener, and opening a replacement panel does not leave the old panel subscribed.
- [ ] Image entries that cannot be resolved are represented as unavailable or safely rejected rather than crashing startup.

**Verification:**
- [ ] Persistence tests use an isolated fake or temporary preference context.
- [ ] Listener tests verify callback counts after repeated show/dismiss cycles.
- [ ] `source .bin/env.sh && ./gradlew :app:testDebugUnitTest` passes.

**Dependencies:** None

**Files likely touched:**
- `app/src/main/java/com/novaboard/ime/clipboard/ClipboardHistory.kt`
- `app/src/main/java/com/novaboard/ime/clipboard/ClipboardPanel.kt`
- `app/src/test/java/com/novaboard/ime/clipboard/ClipboardHistoryTest.kt`
- `app/src/test/java/com/novaboard/ime/clipboard/ClipboardPanelTest.kt`

**Estimated scope:** Medium: 3-5 files

### Phase 3: Preference and editing-state regression coverage

## Task 4: Verify editing-state invalidation after external and inserted edits `[ ]`

**Description:** Add tests around cursor movement, selection changes, clipboard insertion, emoji insertion, and undo-autocorrect invalidation. The tests should prove that subsequent prediction/backspace operations cannot delete unrelated editor text after an intervening external edit.

**Acceptance criteria:**
- [ ] Cursor or selection movement clears stale current-word and undo-autocorrect state.
- [ ] Clipboard and emoji insertion invalidate the tracked word and suggestion state.
- [ ] Undo-autocorrect is accepted only when the replacement is still the active replacement at the current cursor.
- [ ] Backspace falls back to ordinary one-character deletion after invalidation.

**Verification:**
- [ ] Tests use a fake input connection or a pure editing-state seam.
- [ ] Regression cases include an editor transition and an external text edit between correction and backspace.

**Dependencies:** Task 1

**Files likely touched:**
- `app/src/main/java/com/novaboard/ime/NovaBoardService.kt`
- `app/src/test/java/com/novaboard/ime/.../EditingState...Test.kt`

**Estimated scope:** Medium: 3-5 files

## Task 5: Verify implemented preference behavior and remove silent contracts `[~]`

**Description:** Audit the settings exposed by `MainActivity` against actual service/view behavior and add tests for every retained toggle. Any setting still lacking a behavior contract must be removed from the UI or explicitly labeled unavailable rather than receiving a test that only checks persistence.

**Acceptance criteria:**
- [ ] Retained number-row, long-press-symbol, key-popup, emoji-on-enter, and typing toggles have behavior assertions.
- [x] Emoji-on-enter is not triggered for email, URI, password, or other unsupported editor variations.
- [x] No settings dialog exposes the removed inactive typing toggles.
- [ ] No retained settings dialog toggle lacks an observable behavior contract.
- [ ] Preference reset restores the same defaults used by the behavior tests.

**Verification:**
- [ ] `KeyboardPreferences` default/reset tests pass.
- [x] Editor-type policy tests cover supported and excluded variations.
- [ ] The settings resource compiles in the debug APK build.

**Dependencies:** Task 2

**Files likely touched:**
- `app/src/main/java/com/novaboard/ime/settings/KeyboardPreferences.kt`
- `app/src/main/java/com/novaboard/ime/settings/MainActivity.kt`
- `app/src/main/java/com/novaboard/ime/NovaBoardService.kt`
- `app/src/test/java/com/novaboard/ime/settings/KeyboardPreferencesTest.kt`
- `app/src/test/java/com/novaboard/ime/.../EditorPolicyTest.kt`

**Estimated scope:** Medium: 3-5 files

### Checkpoint: Regression suite

- [ ] Tasks 1-5 focused tests pass together.
- [ ] No test relies on timing, a real microphone, a real clipboard provider, or a live editor.
- [ ] `source .bin/env.sh && ./gradlew :app:assembleDebug` succeeds.
- [ ] `git diff --check` succeeds.

### Phase 4: Delivery gate

## Task 6: Review and land the regression coverage `[ ]`

**Description:** Review the final diff for test isolation, package visibility, naming, and accidental production behavior changes. Run the complete relevant verification suite and commit only the focused seam/test changes.

**Acceptance criteria:**
- [ ] Every review finding addressed by a test or explicitly documented as deferred.
- [ ] Tests are deterministic and readable from the failure message alone.
- [ ] No generated APKs, local state, secrets, or unrelated formatting changes are committed.
- [ ] Commit uses a Conventional Commit message, such as `test: cover IME lifecycle and preference contracts`.

**Verification:**
- [ ] `source .bin/env.sh && ./gradlew :app:testDebugUnitTest`
- [ ] `source .bin/env.sh && ./gradlew :app:assembleDebug`
- [ ] `git diff --check`
- [ ] Final `git status --short --branch` is clean after commit.

**Dependencies:** Tasks 1-5

**Files likely touched:**
- `app/src/main/java/com/novaboard/ime/...` only where a test seam is required
- `app/src/test/java/com/novaboard/ime/...`
- `.agents/plans/prevent-keyboard-review-regressions-lifecycle-preference.md` only if scope changes

**Estimated scope:** Medium: 3-5 files

## Risks and mitigations

| Risk | Impact | Mitigation |
|---|---|---|
| Android framework callbacks are difficult to instantiate in JVM tests | High | Keep lifecycle decisions in package-visible pure seams and use fake input connections. |
| Tests accidentally encode implementation details | Medium | Assert user-visible state transitions, row/key contracts, and accepted/rejected edits. |
| Selection callbacks from service-owned commits resemble external edits | High | Test the “word still directly before cursor” preservation rule explicitly. |
| Clipboard provider behavior differs across Android versions | Medium | Test parsing and listener ownership separately from provider integration; reserve instrumentation coverage for provider-specific behavior. |
| Preferences are removed or renamed during implementation | Medium | Keep one source of truth for defaults and update tests with the retained settings contract. |

## Open questions

- Should durable image clipboard storage be included in this test task, or remain a separate implementation task?
- Is the intended quick-delete contract “delete the previous word” or “select then delete”? The current summary and behavior disagree.
- Should translation result replacement be covered here, or stay in the separate translation-flow follow-up?

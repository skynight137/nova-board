# Plan: Catch Cursor and Delete Repeat Regressions Before Release

## Overview

Add focused, deterministic regression coverage for the keyboard's repeated
cursor movement and held-delete behavior. The implementation already includes
bounded repeat scheduling and session cancellation, but the remaining risks
are lifecycle regressions that are difficult to prove through ordinary JVM
tests unless the timing and editor seams are isolated.

## Current state

- Arrow controls send one immediate movement and then repeat at a bounded
  cadence.
- Repeat callbacks stop on release/cancel, missing input connection, and input
  session changes.
- Held backspace uses bounded repeat behavior.
- Pure gesture and deletion-boundary contracts already exist.
- Android lifecycle and repeat-controller coverage is still incomplete.

## Ordered task list

### Task 1: Extract repeat-controller behavior into a pure contract

**Description:** Define a small JVM-testable repeat state contract for start,
stop, direction changes, cadence, and session invalidation. Keep Android
`Handler` scheduling and `InputConnection` calls at the service boundary.

**Acceptance criteria:**

- [ ] A repeat start emits one immediate action and schedules bounded repeats.
- [ ] Stop prevents all future actions.
- [ ] Starting a new direction removes the previous direction's pending work.
- [ ] Session invalidation prevents callbacks from acting on a later editor.
- [ ] Missing input connection stops safely without throwing.

**Files likely touched:**

- `app/src/main/java/com/novaboard/ime/NovaBoardService.kt`
- `app/src/main/java/com/novaboard/ime/editing/`
- `app/src/test/java/com/novaboard/ime/editing/`

### Task 2: Add held-delete boundary regression coverage

**Description:** Expand pure deletion tests around the service's held-delete
path and preserve code-point-safe behavior for whitespace, punctuation,
surrogate pairs, and empty input.

**Acceptance criteria:**

- [ ] A word preceded by spaces deletes only the intended previous segment.
- [ ] Punctuation boundaries remain deterministic.
- [ ] Unicode surrogate pairs are not split.
- [ ] Empty input produces no deletion request.
- [ ] Repeated deletion clears stale typing/autocorrect state.

**Files likely touched:**

- `app/src/main/java/com/novaboard/ime/editing/EditingContracts.kt`
- `app/src/main/java/com/novaboard/ime/NovaBoardService.kt`
- `app/src/test/java/com/novaboard/ime/editing/EditingContractsTest.kt`

### Task 3: Cover Android lifecycle seams

**Description:** Add seam-level tests or narrowly scoped instrumentation coverage
for release, cancel, input-session restart, and service destruction. Tests must
prove that no delayed callback mutates an old editor.

**Acceptance criteria:**

- [ ] Release and cancel both stop cursor and delete repeats.
- [ ] `onStartInputView` invalidates a prior repeat.
- [ ] `onFinishInput` and service destruction leave no active repeat callback.
- [ ] Direction changes cannot leave two active repeat callbacks.

**Files likely touched:**

- `app/src/main/java/com/novaboard/ime/NovaBoardService.kt`
- `app/src/main/java/com/novaboard/ime/view/KeyboardView.kt`
- `app/src/test/java/com/novaboard/ime/`

## Verification

Run each step with the repository environment loaded:

```bash
source .bin/env.sh
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
git diff --check
```

If Android lifecycle coverage cannot run in the available environment, keep the
pure repeat and deletion contracts, record the limitation, and do not mark the
lifecycle acceptance criteria complete.

## Risks and mitigations

| Risk | Impact | Mitigation |
|---|---|---|
| Tests depend on wall-clock timing | High | Inject a scheduler or test repeat contract with deterministic ticks |
| Delayed callbacks target a new editor | High | Include the input-session identity in every repeat action |
| Unicode deletion is measured in UTF-16 units | High | Use the existing deletion boundary contract and explicit surrogate tests |
| Test seams diverge from Android behavior | Medium | Keep service integration thin and verify APK compilation after changes |

## Definition of done

- Repeat and deletion behavior has deterministic regression coverage.
- Old input sessions cannot receive delayed cursor or delete mutations.
- Focused tests and debug APK compilation pass.
- The interaction plan can mark the repeat-controller verification criteria
  complete without relying on a manual-only assertion.
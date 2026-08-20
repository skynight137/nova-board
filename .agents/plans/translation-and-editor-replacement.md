# Implementation Plan: Complete Translation and Safe Editor Replacement

## Overview

Turn the current safe `ACTION_PROCESS_TEXT` launch into a complete translation flow. The IME must remember the selected range and active editor session, accept only a result belonging to that selection/session, and replace exactly the original range. If the platform/provider cannot return a result through the chosen path, the UI must report that translation is unavailable rather than implying replacement occurred.

## Progress

**Status: `[~] Implementation complete; dedicated activity-result tests remain**

- [x] Choose and document the activity-result relay strategy.
- [x] Add the pure translation/session contract and tests.
- [x] Implement exact-range replacement and stale-result rejection.
- [x] Run the verification gate and commit.

## Tasks

### Task 1: Define a result-capable translation contract `[x]`

- Add a small package-visible contract for selected text, selection start/end, editor/session identity, and pending-result invalidation.
- Construct the process-text intent with the existing service-safe activity flags.
- Preserve the original selection metadata without falling back to the previous word.
- Add JVM tests for empty selection, valid selection, unavailable provider, and stale-session invalidation.

**Files likely touched:**
- `app/src/main/java/com/novaboard/ime/NovaBoardService.kt`
- `app/src/main/java/com/novaboard/ime/...` for the translation seam
- `app/src/test/java/com/novaboard/ime/.../Translation...Test.kt`

### Task 2: Route the activity result back to the original editor `[x]`

- Use an activity-result-capable owner/path compatible with an input-method service, or add a minimal result relay activity if the service cannot own the result directly.
- On success, replace only the original selected range and restore the editor selection correctly.
- Ignore results after `onFinishInput`, editor changes, selection changes, or a newer translation request.
- Show a clear unavailable/error state for cancellation, provider failure, or unsupported result contracts.

**Files likely touched:**
- `app/src/main/java/com/novaboard/ime/NovaBoardService.kt`
- `app/src/main/java/com/novaboard/ime/.../TranslationResultActivity.kt` if required
- `app/src/main/AndroidManifest.xml`
- `app/src/test/java/com/novaboard/ime/.../TranslationResult...Test.kt`

### Verification gate

- [~] JVM tests cover selection/range acceptance and stale results; dedicated activity-result cancellation tests remain.
- [x] `source .bin/env.sh && ./gradlew :app:testDebugUnitTest`
- [x] `source .bin/env.sh && ./gradlew :app:assembleDebug`
- [x] `git diff --check`
- [x] Commit: `fix: complete translation selection replacement`

## Risks

- Some translation apps may implement `ACTION_PROCESS_TEXT` as fire-and-forget. Fail closed with an unavailable message rather than replacing text speculatively.
- Result relay activity must not steal focus or expose keyboard content beyond the selected text required by the contract.
# Implementation Plan: Keep Image Clipboard History Usable After Restart

## Overview

Replace persisted transient clipboard-provider URI assumptions with durable image storage or a supported persisted grant strategy. Image history must remain previewable and pasteable after the provider grant expires, the keyboard process restarts, or the device reboots. Invalid image entries must not block startup.

## Progress

**Status: `[~] In progress — durable capture and provider storage implemented**

- [x] Choose app-private storage format and legacy URI migration policy.
- [x] Capture new image clips durably.
- [x] Render, paste, delete, and clean up durable images.
- [ ] Add restart/cleanup tests and run the verification gate.

## Tasks

### Task 1: Capture image content durably `[x]`

- When an image clip is observed, copy its content into app-private storage with a generated stable filename.
- Persist a versioned app-private URI/path reference rather than only the provider URI.
- Fail the individual capture with a clear unavailable item state if the provider cannot be read.
- Keep text capture and duplicate protection unchanged.

**Files likely touched:**
- `app/src/main/java/com/novaboard/ime/clipboard/ClipboardHistory.kt`
- `app/src/main/java/com/novaboard/ime/clipboard/ClipboardItem.kt` if the model is split
- `app/src/test/java/com/novaboard/ime/clipboard/ClipboardHistoryTest.kt`

### Task 2: Render, paste, delete, and migrate image entries safely `[x]`

- Update the adapter to distinguish durable local images from legacy provider URIs.
- Remove unreadable entries or show an explicit unavailable state without throwing.
- Paste durable images through `InputContentInfo` using the correct MIME type.
- Delete associated private files when an item is deleted or trimmed.
- Add a one-time migration/cleanup path for legacy URI entries.

**Files likely touched:**
- `app/src/main/java/com/novaboard/ime/clipboard/ClipboardAdapter.kt`
- `app/src/main/java/com/novaboard/ime/clipboard/ClipboardHistory.kt`
- `app/src/main/java/com/novaboard/ime/NovaBoardService.kt`
- `app/src/test/java/com/novaboard/ime/clipboard/...`

### Verification gate

- [ ] Tests cover restart-style load, malformed entries, unreadable legacy URIs, file cleanup, cap trimming, and image paste metadata.
- [x] `source .bin/env.sh && ./gradlew :app:testDebugUnitTest`
- [x] `source .bin/env.sh && ./gradlew :app:assembleDebug`
- [x] `git diff --check`
- [x] Commit: `fix: persist clipboard images durably`

## Risks

- Private image files can grow storage usage; retain the existing cap and clean orphaned files during load.
- Do not request broad storage permissions; use app-private storage only.
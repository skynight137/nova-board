# Keep clipboard contents private while Incognito mode is on

## What & Why

Incognito currently gates learned typing data, but ClipboardHistoryManager starts
its system clipboard listener unconditionally. Clipboard text and opted-in
images can therefore still be persisted during a mode users expect not to retain
input-related data.

## Done looks like

- Clipboard capture is disabled while Incognito mode is enabled.
- Existing clipboard history remains available or is handled according to a
  clearly documented privacy rule.
- Toggling Incognito does not leave a listener or stale capture path active.
- Regression tests cover text and image capture in both modes.

## Relevant files

- `app/src/main/java/com/novaboard/ime/NovaBoardService.kt`
- `app/src/main/java/com/novaboard/ime/clipboard/ClipboardHistory.kt`
- `app/src/main/java/com/novaboard/ime/settings/KeyboardPreferences.kt`

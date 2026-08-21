# Prevent image-history cleanup from erasing valid text history

## What & Why

`clearStoredImageHistory` rebuilds the persisted array as empty when the stored
JSON is malformed or unreadable, then writes that empty array back. A cleanup
action intended for images can consequently discard otherwise recoverable text
history.

## Done looks like

- Malformed persistence is handled without silently replacing the entire history
  during image cleanup.
- Valid non-image entries are retained whenever they can be decoded.
- The cleanup result distinguishes zero removed images from a parse failure if
  the UI needs to report it.
- Tests cover malformed JSON, mixed text/image entries, and repeated cleanup.

## Status

Implemented: malformed cleanup fails closed, valid text entries are retained,
and JVM tests cover malformed, mixed, and repeated cleanup.

## Relevant files

- `app/src/main/java/com/novaboard/ime/clipboard/ClipboardHistory.kt`
- `app/src/main/java/com/novaboard/ime/clipboard/ClipboardPersistence.kt`
- `app/src/test/java/com/novaboard/ime/clipboard`

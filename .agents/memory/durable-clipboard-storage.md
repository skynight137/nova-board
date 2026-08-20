---
name: Durable clipboard image storage
description: The clipboard image persistence boundary and migration rule.
---

Image clipboard history must store copied bytes in app-private storage and expose them through the app's own non-exported content provider; provider-owned clipboard URIs are only legacy input.

**Why:** Clipboard provider grants can expire across process restarts and device reboots, while `InputContentInfo` still requires a readable content URI for image paste.

**How to apply:** Copy new and legacy image URIs during capture/load, rewrite migrated entries to the app-owned URI, and delete retained files when entries are deleted, trimmed, or no longer referenced.
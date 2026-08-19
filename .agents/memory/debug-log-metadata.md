---
name: Diagnostic metadata boundary
description: Safe construction and export of non-secret APK/device metadata for troubleshooting logs.
---

JVM unit-test Android stubs may return null for platform fields that are
non-null on devices, including ABI arrays and build strings. Diagnostic
metadata collection must tolerate those values without preventing ordinary
ViewModel construction.

Exported metadata must be single-line and bounded for free-form device fields.
Release-page URLs must be reduced to scheme, host, port, and path so
credentials, query parameters, and fragments cannot enter the log.

**Why:** The debug-log path runs during normal app startup/test fixture
construction, and a platform-stub null caused unrelated ViewModel tests to
fail. Logs are shareable support artifacts and must not become a credential or
token exfiltration path.

**How to apply:** Keep production metadata collection behind an injectable
value object. Use deterministic fixtures in ViewModel tests, null-safe Android
reads in the production default, and assert both labeled fields and redaction
when changing the exported log format.
---
name: Android instrumentation service seams
description: Android service lifecycle tests must use package-visible seams rather than protected framework callbacks.
---

Use existing package-visible state or test seams when exercising an `AccessibilityService` from
`androidTest`; framework lifecycle callbacks such as `onServiceConnected()` are protected and
cannot be invoked directly from the test class.

**Why:** Android framework access rules apply during instrumentation compilation just as they do in
production source, so directly calling a protected callback makes an otherwise valid test suite
fail before execution.

**How to apply:** Keep lifecycle tests focused on the observable service ownership/state seam unless
the production class explicitly exposes a behavior-preserving wrapper.
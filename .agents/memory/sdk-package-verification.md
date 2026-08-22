---
name: SDK package verification
description: Android SDK package identifiers and installer failure handling in this workspace.
---

The Android SDK catalog may publish a platform under a versioned package ID such
as `platforms;android-37.0` rather than the human-facing compile SDK label
`37`. Installer checks must use the package's actual installed directory, and
package command failures must remain observable.

**Why:** A generic package ID was unavailable in the current SDK catalog, and a
previous output-filtering pipeline converted that failure into a false success.

**How to apply:** Query `sdkmanager --list --sdk_root=...` when adding or
updating SDK packages, then run package installation through a checked log or
otherwise preserve its exit status.
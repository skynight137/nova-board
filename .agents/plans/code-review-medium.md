# Code review plan: Medium

Review scope: NovaBoard Android runtime, release automation, CI, security,
architecture, performance, and verification. This plan records findings from
the repository-wide review on 2026-08-21.

## MD-001 — Cancel delayed suggestion refreshes during service teardown

**Severity:** Medium  
**Status:** Open  
**Files:** `app/src/main/java/com/novaboard/ime/NovaBoardService.kt:88-90,133-141,636-648`

Remove `suggestionRefreshRunnable` callbacks in `onDestroy()` and guard refresh
work against an inactive service or input session. Avoid touching uninitialized
suggestion views.

## MD-002 — Resynchronize tracked typing state after rejected editor mutations

**Severity:** Medium  
**Status:** Open  
**Files:** `app/src/main/java/com/novaboard/ime/NovaBoardService.kt:671-678,700-703,783-788`

The service updates `currentWord`, prediction state, and autocorrect metadata
without checking whether `InputConnection` operations succeeded. Add explicit
failure handling and resynchronize from the editor for read-only, disconnected,
or rejecting editors.

## MD-003 — Delete emoji and grapheme clusters safely

**Severity:** Medium  
**Status:** Open  
**Files:** `app/src/main/java/com/novaboard/ime/NovaBoardService.kt:821-830`

Backspace currently deletes one UTF-16 code unit and can split surrogate pairs
or joined emoji. Use code-point-aware behavior at minimum, verify grapheme
clusters on supported editors, and keep internal state aligned.

## MD-004 — Reset typing state after successful voice insertion

**Severity:** Medium  
**Status:** Open  
**Files:** `app/src/main/java/com/novaboard/ime/NovaBoardService.kt:992-999`

Voice commits text without resetting tracked word, previous-word, autocorrect,
or suggestion state. Treat voice insertion as an editor mutation and reset or
resynchronize afterward.

## MD-005 — Bound and index the learned dictionary

**Severity:** Medium  
**Status:** Open  
**Files:** `app/src/main/java/com/novaboard/ime/suggestion/SuggestionEngine.kt:110-177`

The learned vocabulary grows without a cap and fuzzy matching scans all keys.
Add an eviction policy, prefix indexing, and bounded fuzzy matching. Add a
performance test with a realistic long-lived vocabulary.

## MD-006 — Fix long-press alternate-character popup measurement

**Severity:** Medium  
**Status:** Open  
**Files:** `app/src/main/java/com/novaboard/ime/view/KeyboardView.kt:535-580`

`row.measuredHeight` is read before measurement, so popup placement can be
incorrect. Measure the popup content before calculating its top position or use
a positioning API that performs measurement.

## MD-007 — Add Android integration coverage for the IME and settings UI

**Severity:** Medium  
**Status:** Open  
**Files:** `app/src/main/java/com/novaboard/ime/NovaBoardService.kt`,
`app/src/main/java/com/novaboard/ime/view/KeyboardView.kt`,
`app/src/main/java/com/novaboard/ime/settings/MainActivity.kt`,
`app/src/test`

There is no `app/src/androidTest` tree. Existing JVM tests cover pure policies
and models but not lifecycle, touch, overlays, editor operations, voice,
clipboard providers, or settings preview behavior.

Add focused Robolectric or instrumentation coverage for stale callbacks,
editor rejection, clipboard capture transitions, voice completion, emoji
deletion, long-press popups, and settings preference changes.

## MD-008 — Exclude sensitive clipboard data from Android backup

**Severity:** Medium  
**Status:** Open  
**Files:** `app/src/main/AndroidManifest.xml:7-13`,
`app/src/main/java/com/novaboard/ime/clipboard`,
`README.md:50-55`

`android:allowBackup="true"` is enabled while the app stores clipboard history
and preferences locally. Disable backup or add explicit Android backup/data
extraction rules that exclude sensitive clipboard state.

## MD-009 — Pin GitHub Actions to immutable commit SHAs

**Severity:** Medium  
**Status:** Open  
**Files:** `.github/workflows/release.yml`,
`.github/workflows/build_pull_request.yml`,
`.github/workflows/crowdin_pull.yml`,
`.github/workflows/crowdin_push.yml`,
`.github/workflows/open_pull_request.yml`

Third-party actions use mutable tags. Pin them to commit SHAs and use
Dependabot for controlled updates.

## MD-010 — Add Gradle dependency verification and remove unsafe local shadowing

**Severity:** Medium  
**Status:** Open  
**Files:** `settings.gradle.kts:1-17`,
`app/build.gradle.kts:184-194`

`mavenLocal()` is enabled for plugin and dependency resolution, and there is no
Gradle dependency verification metadata or dependency locking. This permits a
local artifact to shadow a published dependency and weakens supply-chain
reproducibility.

Prefer trusted remote repositories for CI, add dependency verification
metadata, and document any intentional local-development override.

## MD-012 — Make release toolchains reproducible

**Severity:** Medium  
**Status:** Open  
**Files:** `.github/workflows/release.yml:68-94`,
`package.json:5-13`, `package-lock.json`, `settings.gradle.kts`

The release workflow uses floating Node LTS and mutable action tags, while
dependency verification is absent. Pin the Node and Java versions and retain
validated dependency metadata.

## MD-013 — Split oversized runtime and settings classes

**Severity:** Medium  
**Status:** Open  
**Files:** `app/src/main/java/com/novaboard/ime/NovaBoardService.kt` (1,049
lines), `app/src/main/java/com/novaboard/ime/settings/MainActivity.kt` (908
lines), `replit.md:105-115`

These classes combine lifecycle, editor/session state, clipboard, overlays,
prediction, voice, settings pages, and preview behavior. Extract focused
orchestration boundaries without changing the IME contract.

## MD-014 — Remove duplicated preference defaults

**Severity:** Medium  
**Status:** Open  
**Files:** `app/src/main/java/com/novaboard/ime/settings/MainActivity.kt:352-395,898-903`,
`app/src/main/java/com/novaboard/ime/settings/KeyboardPreferences.kt:37-59`

`PreferenceSpec.default` is carried but unused while actual defaults live in
`KeyboardPreferences`. Use one source of truth or remove the unused field.

## MD-015 — Remove or implement the no-op autocorrect hook

**Severity:** Medium  
**Status:** Open  
**Files:** `app/src/main/java/com/novaboard/ime/NovaBoardService.kt:696-703,797-800`

`maybeAutocorrectLastChar()` is called for every character but does nothing.
Remove it until the feature exists, or implement it with tests so the API does
not imply behavior that is absent.

## MD-016 — Remove or wire the unreachable emoji-font preference

**Severity:** Medium  
**Status:** Open  
**Files:** `app/src/main/java/com/novaboard/ime/settings/KeyboardPreferences.kt:25,97-107`,
`app/src/main/java/com/novaboard/ime/emoji/EmojiPanel.kt`

`EMOJI_FONT` has storage accessors but no consumer and no visible setting. Wire
it into rendering/settings or remove it.

## MD-017 — Reconcile editing-action documentation

**Severity:** Medium  
**Status:** Open  
**Files:** `README.md:41-45`,
`docs/feature-checkpoint.md:20-24,52-60`

The README describes cut/copy/paste/select-all as a known gap, while the
checkpoint and hotkey implementation describe them as implemented. Document
the actual tools-row/hotkey access path consistently.

## MD-018 — Avoid a duplicate, fake settings-preview implementation

**Severity:** Medium  
**Status:** Open  
**Files:** `app/src/main/java/com/novaboard/ime/settings/MainActivity.kt:650-893`

The preview duplicates runtime UI concepts and uses placeholder clipboard/voice
behavior. Extract shared rendering/state adapters or label the preview as
visual-only, then test preference changes and major preview states.

## Verification gaps

The repository has no automated coverage for the documented real-device matrix:

- Normal, browser, multiline, and password editors
- Physical keyboard connected/disconnected
- Voice cancellation and permission denial
- Clipboard image support and unsupported editors
- Rotation or IME recreation
- Narrow/tall and light/dark device layouts
- Signed APK installation and upgrade behavior

Previously recorded JVM test and debug-build checks passed, but they do not
close these device-dependent gaps.

## Positive controls observed

- Release builds fail closed when signing configuration is absent.
- The clipboard content provider is non-exported.
- The IME service uses `BIND_INPUT_METHOD`.
- The Gradle wrapper distribution is checksum-pinned.
- Image clipboard retention is opt-in.
- Clipboard policies, persistence, models, and pure editing contracts have JVM
  tests.
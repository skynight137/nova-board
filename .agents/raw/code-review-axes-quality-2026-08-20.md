# code-review-axes-quality

- Status: completed
- Captured: 2026-08-20
- Scope: current `main` tip (`32c2771`, `fix: navigation behavior`)

## Project context

NovaBoard is an Android 8.0+ custom input method written in Kotlin with native
Android Views and Gradle Kotlin DSL. `NovaBoardService` owns the IME lifecycle,
input connection, keyboard state, overlays, clipboard, suggestions, and voice
input. `MainActivity` owns settings and system-facing enable/switch actions.
Clipboard history and preferences are local-only. The project uses JVM unit
tests under `app/src/test/java`; source-only verification is
`:app:testDebugUnitTest`, while resource or packaging changes also require
`:app:assembleDebug`. The repository uses Conventional Commits and
semantic-release.

## Reconciliation

The worktree was clean before review, so this review covers the current tip and
the immediately preceding implementation history rather than an uncommitted
patch. Existing reports were checked first. Several earlier findings are no
longer current: translation is now an in-keyboard flow, input-session cleanup
exists in `onFinishInput`, number-row preference handling is covered by tests,
image clipboard entries are copied into app-private storage, and clipboard
listeners are removed when panels are dismissed.

## Five-axis review

### Correctness

1. **Required — stale speech callbacks can corrupt active voice state
   (High).** `startVoiceInput()` assigns each recognizer a session and
   generation, and `onResults()` validates both. `onError()` does not validate
   either value: it unconditionally sets `listening = false`, shows a toast,
   and may destroy the recognizer currently held by the callback. If the
   previous recognizer reports its delayed stop/destroy error after a new
   recognizer starts, the new recording can remain active while the service
   reports that listening stopped. Route all terminal callbacks through the
   same active-session/active-generation guard used by `onResults()`, and only
   clear/destroy the shared recognizer when identity still matches.

2. **Required — text-only clipboard entries can be silently lost when the
   provider does not advertise `text/plain` (Medium).**
   `ClipboardHistoryManager.addFromClipData()` accepts `item.text` whenever it
   is nonblank, but it first branches on an image URI and otherwise does not
   preserve styled or HTML-only clipboard representations that expose text
   through `coerceToText()` rather than `item.text`. Rich/web editors can
   therefore produce a valid text clip that never enters history. Use the
   available text coercion as a bounded fallback, while retaining the image
   path for image clips and avoiding empty entries.

### Readability and simplicity

The reviewed navigation change is small and understandable. The new
`KeyboardView` comment explains why previews are cancelled on multi-touch, and
the panel dimension conversion is isolated in a helper. No unnecessary
abstraction or dead code was introduced by the reviewed tip.

### Architecture

The current translation flow keeps selection-scoped behavior inside the IME
service and uses dedicated translation contracts, which matches the documented
service boundary. Voice callback validation should be centralized in the
existing session policy rather than adding callback-specific state mutations;
this is a small structural correction, not a reason to move voice input out of
`NovaBoardService`.

### Security

No secrets or new external data flows were introduced by the reviewed change.
The private clipboard content provider constrains paths to the app-owned image
directory. The remaining clipboard text issue is a data-retention correctness
gap, not an injection finding.

### Performance

The reviewed changes add no unbounded work or new hot-path allocations beyond
the existing touch and panel flows. Image capture remains capped by the
history limit and uses app-private files. The stale callback issue is state
integrity rather than a performance regression.

## Verification

- `git diff HEAD^ HEAD --check` — passed.
- `source .bin/env.sh && ./gradlew :app:testDebugUnitTest :app:assembleDebug` —
  passed.
- Compiler reported only existing deprecation/unnecessary-cast warnings in
  `KeyboardPreferences.kt` and `KeyboardView.kt`.
- No UI automation was run; this review is based on source inspection and JVM
  tests.

## Verdict

**Request changes** before treating the current implementation as fully
reviewed. The two required findings above are independently actionable and
should receive regression coverage, especially for delayed recognizer errors
after a new input session and clipboard text coercion from a non-plain
representation.

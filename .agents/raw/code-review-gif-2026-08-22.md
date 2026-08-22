---
title: NovaBoard GIF feature code review
date: 2026-08-22
scope: GIF panel, KLIPY client, GIF download/share provider, and editor insertion fallback
commit: 0457728
---

# GIF feature code review

## Review context

This review covers the current GIF implementation after the direct-link fallback
change. The intended behavior is:

- load trending and searched GIFs from KLIPY;
- download a selected GIF into temporary app-private storage;
- use Android rich-content insertion when the target editor supports it;
- paste the provider URL as text when rich-content insertion is rejected;
- avoid crashes when the IME session or service is torn down during background work.

The review was performed by source inspection of the GIF panel, network client,
content provider, manifest, and `NovaBoardService` insertion path. Existing JVM
verification was also considered: `:app:testDebugUnitTest --no-daemon` passed
before this review. No device-level editor compatibility test was available.

## Findings

### Required — request the URI grant before committing GIF content (High)

`commitGifFile()` creates `InputContentInfo` and passes
`INPUT_CONTENT_GRANT_READ_URI_PERMISSION` to `commitContent()`, but it does not
call `requestPermission()` on the `InputContentInfo`. The clipboard image path
does request permission before committing the same kind of app-owned URI.

On editors that rely on the granted read permission, the commit can return
success while the recipient cannot read the GIF, or the editor may reject the
content. This makes the rich-content path unreliable even when the editor
advertises support.

**Recommended remedy:** construct the `InputContentInfo` in a named local,
call `requestPermission()` before `commitContent()`, and keep the whole grant
and commit operation inside the existing failure boundary. Add a regression
test or device test that verifies the request occurs before commit.

### Required — shut down `GifPanel` preview/search executors on dismiss (Medium)

Every `GifPanel` instance creates a new fixed thread pool with three threads.
`dismiss()` removes the panel and invalidates request IDs but never calls
`executor.shutdownNow()`. Opening and closing the GIF panel repeatedly therefore
leaves one executor and its worker threads alive per panel instance. Pending
network requests and preview decodes can also continue after the panel is no
longer visible.

This is a lifecycle leak in an IME, which is a long-lived process and can
accumulate resources during normal keyboard use.

**Recommended remedy:** make dismissal idempotently cancel pending work and shut
down the panel executor. Ensure a panel is not reused after dismissal, or create
a fresh executor when `show()` is called. Add a lifecycle test covering repeated
show/dismiss cycles and a pending request.

### Required — only report fallback success when `commitText()` succeeds (Medium)

The fallback block calls `inputConnection.commitText(item.contentUrl, 1)` and
then unconditionally returns `true` from `runCatching`. Android's
`commitText()` returns a Boolean, so a target can reject or fail the text commit
without throwing. In that case NovaBoard shows “pasted the GIF link instead”
even though no link was inserted.

**Recommended remedy:** use the Boolean returned by `commitText()` as the
fallback result, while preserving the exception handling:

```kotlin
runCatching { inputConnection.commitText(item.contentUrl, 1) }
    .getOrDefault(false)
```

Add a regression test with an input connection that returns `false`.

## Five-axis assessment

### Correctness

The direct-link fallback is present and is protected against thrown
`commitContent()` failures. Session identity is checked before the downloaded
file reaches the editor. The three findings above still leave rich-content
permission, lifecycle cleanup, and fallback result reporting incorrect in
specific editor or usage conditions.

### Readability and simplicity

The GIF flow is easy to locate, and the existing `runCatching` boundaries make
the failure paths visible. The insertion method would be clearer if the
`InputContentInfo` construction and permission request were named steps rather
than an inline expression. No unnecessary dependency or broad refactor was
introduced.

### Architecture

The feature-specific network, panel, and provider code is separated from the
IME service, while the service correctly owns the active input connection and
session boundary. Executor ownership is currently incomplete: the panel owns
its executor but does not release it, so the lifecycle contract is not closed.

### Security

The GIF share provider is non-exported, grants URI permissions, and constrains
file names to the private cache directory. The KLIPY response supplies the
download and fallback URLs; these should be validated as expected HTTPS URLs
before network use and before presenting a link if the upstream contract does
not guarantee that invariant. No secret is logged or committed.

### Performance

GIF downloads run off the main thread and are size-limited to 15 MiB. Preview
decoding also runs off the main thread. The unclosed fixed thread pools are the
main performance/resource concern; repeated panel use can retain threads and
network work after dismissal.

## Verification story

Passed before this review:

```text
source .local/env.sh && ./gradlew :app:testDebugUnitTest --no-daemon
git diff --check
```

Not verified:

- rich-content insertion in multiple real editors;
- URI permission behavior across an external editor process;
- executor/thread cleanup after repeated panel dismissal;
- fallback behavior when `commitText()` returns `false`.

## Verdict

**Request changes.** The current implementation improves unsupported-editor
behavior, but the three Required findings should be fixed and covered by
regression or device-level tests before treating the GIF feature as fully
reviewed.
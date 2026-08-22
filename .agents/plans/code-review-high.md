# Code review plan: High

Review scope: NovaBoard Android runtime, release automation, CI, security,
architecture, performance, and verification. This plan records findings from
the repository-wide review on 2026-08-21.

## HI-001 — Make Android version codes increase for every release

**Severity:** High  
**Status:** Resolved
**Files:** `app/build.gradle.kts:21-27`, `.releaserc.cjs:9-10,40-53`,
`docs/releasing.md:212-219`

### Finding

`versionName` is updated by semantic-release, but `versionCode` is always `1`.
Later APKs can be rejected by Android as downgrades or fail to update an
installed release.

### Required change

Derive a monotonically increasing `versionCode` from the release version or
maintain an explicitly incremented release code. Add release validation that
compares the new code with the previous published code.

### Acceptance criteria

- `v1.0.0-dev.1`, the next prerelease, and stable releases have increasing
  Android version codes.
- A release fails before publication if its version code is not greater than
  the previous release.
- The generated APK exposes the semantic-release version and signing identity.

### Resolution

Android version codes are derived from semantic versions and release
pre-release numbers.

## HI-002 — Stop importing the existing system clipboard without consent

**Severity:** High  
**Status:** Resolved
**Files:** `app/src/main/java/com/novaboard/ime/clipboard/ClipboardHistory.kt:65-89`,
`app/src/main/java/com/novaboard/ime/NovaBoardService.kt:118-124`

### Finding

Enabling clipboard capture immediately reads and persists the current primary
clipboard. This can retain a password, token, private message, or image copied
before the user opted into clipboard history. Exiting incognito can trigger the
same behavior.

### Required change

Do not import the existing primary clipboard when capture is enabled. Capture
only future clipboard-change events, or show an explicit confirmation before
importing the existing content.

### Acceptance criteria

- Enabling clipboard history does not persist pre-existing clipboard content
  without explicit confirmation.
- Exiting incognito does not silently import stale clipboard content.
- Text and image behavior are both covered by JVM and device-level checks.

### Resolution

Enabling capture now listens only for future clipboard changes and no longer
imports the existing primary clip.

## HI-003 — Move clipboard copying and persistence off the IME main path

**Severity:** High  
**Status:** Resolved
**Files:** `app/src/main/java/com/novaboard/ime/clipboard/ClipboardHistory.kt:65-67,106-212`

### Finding

The primary-clip listener synchronously reads provider content, copies entire
image streams, serializes all history, writes preferences, and invokes UI
listeners. A slow or large provider can block keyboard input and cause jank or
an ANR.

### Required change

Use a serialized background owner for clipboard image copying and persistence.
Marshal only state notifications back to the main thread. Define cancellation
and shutdown behavior so queued work cannot corrupt state or leave orphaned
image files.

### Acceptance criteria

- Clipboard listener callbacks return quickly even for slow providers.
- Concurrent clip changes preserve ordering and do not lose pinned entries.
- Service teardown cancels or drains pending work safely.
- Failed image copies remove temporary files.

### Resolution

Clipboard capture, image copying, persistence, pinning, and deletion are
serialized on a dedicated worker; UI notifications are posted to the main
thread and teardown stops new work while draining queued operations.

## HI-004 — Align the documented prerelease workflow with GitHub Actions

**Severity:** High  
**Status:** Resolved
**Files:** `README.md:100-107`, `docs/releasing.md`, `.releaserc.cjs:12-18`,
`.github/workflows/release.yml:3-7`

### Finding

The repository documents `dev -> prerelease` and configures semantic-release
with a `dev` prerelease branch, but the release workflow only triggers on
`main`. The manual workflow was used to publish `v1.0.0-dev.1`, but normal
pushes to `dev` do not follow the documented path.

### Required change

Choose one authoritative policy:

1. Add a protected `dev` trigger and keep prerelease behavior, or
2. Remove the automatic `dev` prerelease claim from the documentation and
   release configuration.

Document how a manual `dev` dispatch differs from a stable `main` release.

### Acceptance criteria

- A push or manual dispatch on `dev` consistently produces the intended
  prerelease behavior.
- Stable releases remain restricted to `main`.
- Release documentation, semantic-release branches, and workflow triggers agree.

### Resolution

The release workflow now runs on both `dev` and `main`, while semantic-release
continues to classify `dev` as prerelease and `main` as stable.

## HI-005 — Verify the Android certificate identity after release builds

**Severity:** High  
**Status:** Resolved
**Files:** `app/build.gradle.kts:42-66,165-180`,
`.github/release-tooling/prepare-release.sh`

### Finding

The release process verifies that a keystore exists and signs the APK, but does
not verify that the produced APK has the expected Android certificate
fingerprint. A wrong available keystore could generate an artifact that cannot
update the installed app.

### Required change

Run `apksigner verify --print-certs` after building and compare the certificate
fingerprint with the configured release identity before publication.

### Acceptance criteria

- A mismatched certificate fails release preparation.
- The expected fingerprint is recorded without exposing private key material.
- Verification runs before GPG signing, upload, and attestation.

### Resolution

Release preparation verifies the built APK certificate with `apksigner` before
calculating publication metadata or signing the artifact.

## HI-006 — Protect release runs from concurrent publication

**Severity:** High  
**Status:** Resolved
**Files:** `.github/workflows/release.yml:9-19`,
`.releaserc.cjs:40-78`

### Finding

Release runs have no concurrency group. Concurrent pushes or manual dispatches
can race while calculating versions, updating metadata, creating tags, and
publishing assets.

### Required change

Add a release concurrency group that queues or cancels duplicate runs according
to the chosen release policy. Ensure only one semantic-release process can
mutate release state at a time.

### Acceptance criteria

- Concurrent release triggers cannot publish the same version twice.
- A manual dispatch while a release is running is queued or clearly rejected.
- Release logs identify the active concurrency policy.

### Resolution

The release workflow uses the `release-publication` concurrency group with
queued duplicate runs.
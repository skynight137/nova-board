# Code review plan: Critical

Review scope: NovaBoard Android runtime, release automation, CI, security,
architecture, performance, and verification. This plan records findings from
the repository-wide review on 2026-08-21.

## CR-001 — Do not expose release-signing secrets to pull-request code

**Severity:** Critical  
**Status:** Open  
**Files:** `.github/workflows/build_pull_request.yml:24-66`

### Finding

The pull-request workflow builds `assembleRelease` for every non-fork pull
request and restores the release keystore while exposing keystore passwords and
the GitHub token to the Gradle process. A contributor who can update a
same-repository pull request can modify Kotlin, Gradle, or shell code to
exfiltrate signing material.

The fork check is not a sufficient trust boundary because same-repository pull
requests can also contain attacker-controlled changes.

### Required change

- Build only an unsigned/debug APK in pull-request workflows.
- Remove release keystore restoration and signing secrets from PR jobs.
- Run signed release builds only in a protected post-merge or release workflow.
- Keep release secrets scoped to the minimum trusted job.

### Acceptance criteria

- No PR-controlled code runs with release keystore or signing-password access.
- Pull-request validation still produces a useful debug APK or build artifact.
- A protected release workflow remains capable of producing the signed APK.
- Workflow tests cover fork and same-repository pull-request behavior.
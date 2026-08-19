---
name: Release manifest integrity
description: Release metadata must be validated before semantic-release starts
---

The release workflow must parse and validate the committed release manifest before
semantic-release or the APK build runs. A backmerge conflict can leave valid-looking
release fields surrounded by Git conflict markers, and the later preparation step
then fails only after spending time building and signing.

**Why:** release preparation parses the existing manifest as JSON before replacing
it atomically, so unresolved conflict text turns a repository-state mistake into a
late release failure.

**How to apply:** keep a pre-release JSON/schema check in the workflow and retain a
script-level atomicity test for malformed manifests; never hand-merge by choosing
whichever release block is newest without checking the artifact digest and signing
fingerprint.
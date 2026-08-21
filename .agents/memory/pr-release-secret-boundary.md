---
name: Pull-request release secret boundary
description: Release credentials must never be available to code executed from pull-request workflows.
---

Pull-request validation must build only unsigned/debug artifacts and must not expose signing files, signing passwords, GPG material, or release tokens to PR-controlled code.

**Why:** Same-repository pull requests can modify build scripts just as fork pull requests can; branch trust is not a sufficient secret boundary.

**How to apply:** Keep signed APK preparation and publication in a protected release workflow, and test the PR workflow for both absence of release secrets and presence of a useful debug artifact.
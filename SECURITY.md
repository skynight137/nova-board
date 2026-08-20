# Security Policy

## Supported versions

Security fixes are applied to the latest development branch and the latest
published release line.

| Version | Supported |
| --- | --- |
| `dev` | Yes |
| Latest `1.x` release | Yes |
| Older releases | No |

If you are using an older release, update to the latest release before
reporting an issue when possible.

## Reporting a vulnerability

Please report security vulnerabilities privately through
[GitHub's private vulnerability reporting](https://github.com/skynight137/nova-board/security/advisories/new).
Do not open a public issue, pull request, or discussion for an undisclosed
vulnerability.

If private vulnerability reporting is unavailable, contact the repository
maintainers through the [project owner profile](https://github.com/skynight137)
and request a private security contact channel. Do not include credentials,
signing keys, access tokens, or other secrets in a report.

Include as much of the following information as possible:

- A clear description of the vulnerability and its impact
- Affected version, commit, workflow, or dependency
- Reproduction steps or a minimal proof of concept
- Any required permissions, configuration, or user interaction
- Suggested remediation, if known

### What to expect

- We aim to acknowledge a report within five business days.
- We will investigate, validate, and assign severity as quickly as possible.
- We will coordinate disclosure timing with the reporter after a fix or
  mitigation is available.
- Reporters will be credited in release notes when they want attribution and it
  is safe to do so.

## Scope

Reports involving the Android application, release automation, GitHub Actions,
dependency configuration, or repository security controls are in scope.
Issues caused solely by third-party services or dependencies should still
include the affected package and version so they can be triaged appropriately.

This policy does not grant permission to access data that does not belong to
you, disrupt services, bypass authentication, or test production systems
without authorization.

## Local data security boundary

NovaBoard stores keyboard preferences and clipboard history locally on the
device. Clipboard content is sensitive user data; do not attach it to public
bug reports.

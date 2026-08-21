# Remove translation features

## Goal

Remove the inactive translation composer and every user-facing entry point so the
keyboard only exposes supported tools.

## Scope

- Remove translation controls from the IME toolbar, overflow menu, settings
  preview, and preview resources.
- Remove the translation panel package and its unit tests.
- Update README and feature checkpoint documentation so translation is no longer
  described as an available or pending feature.
- Keep unrelated localization infrastructure intact.

## Verification

- No application source or documentation references the removed translation
  feature.
- `:app:testDebugUnitTest` and `:app:assembleDebug` pass.
- `git diff --check` passes.

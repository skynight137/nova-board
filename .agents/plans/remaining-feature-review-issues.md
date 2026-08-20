# Next-round plan index: remaining NovaBoard feature-review issues

## Review status

Compared with `attached_assets/untitled_1787187644560.md` and the current source:

### Fixed

- Runtime microphone permission and voice-start failure handling.
- IME session cleanup, stale voice callback rejection, overlay dismissal, typing reset, and shift reset.
- Number-row preference on symbols pages.
- Clipboard malformed-entry tolerance, broad text capture, startup primary-clip import, listener cleanup, and replacement-panel dismissal.
- Long-press symbols independent from key previews.
- Key-preview measurement.
- Duplicate keypress sound mechanisms.
- Removal of the three inactive typing-setting keys/defaults and alignment of the quick-delete summary.
- Removal of misleading GIF/media and redundant emoji-search affordances.
- Accurate emoji typeface labels replacing the unsupported “Latest Google Emoji” claim.

### Partial

- **Typing/autocorrect:** external selection and insertion invalidation exists, but undo-autocorrect still deletes a string at the current cursor without proving the exact replacement range is unchanged.
- **Emoji-on-enter:** unsupported email/URI/password variations are excluded, but the policy is still based on input type rather than a clear supported conversation-editor contract.
- **Settings:** the inactive settings were removed from the dialog and storage defaults; regression tests are still pending.
- **Emoji controls:** text search and categories work; GIF/media remains intentionally unavailable and needs a product decision if it should return.

### Open

- Translation's pure composer contract is complete; the in-keyboard panel and
  provider boundary remain open, and the reachable external-app launch still
  needs removal.
- Durable app-private image storage, preference gating, and cleanup semantics
  are complete; Android-context restart, migration, and filesystem verification
  remain open.
- Quick-delete behavior now has an accurate “delete the previous word” summary and pure boundary coverage; Android lifecycle/persistence regression coverage is still pending.
- Focused regression coverage is partially complete; remaining work is tracked in `prevent-keyboard-review-regressions-lifecycle-preference.md`.

## Progress

- [✓] Review current code against the original 12 findings.
- [✓] Complete the low-risk inactive-settings and quick-delete-summary cleanup.
- [✓] Complete translation result replacement.
- [✓] Make image clipboard storage durable and user-controlled (preference,
  capture gate, cleanup semantics, and deterministic JVM contracts complete;
  Android-context verification remains explicitly deferred):
  `.agents/plans/durable-image-clipboard.md`
- [~] Replace the external translation launch with the in-keyboard panel and
  provider boundary; the native panel is complete, while the provider boundary
  and Android-only verification remain:
  `.agents/plans/translation-and-editor-replacement.md`
- [~] Resolve remaining emoji controls/media policy and refine emoji-on-enter policy.
- [~] Add focused lifecycle, persistence, layout, and preference regression tests; pure session/layout/editing/clipboard parsing contracts are covered, while Android lifecycle, clipboard listener, and preference-reset seams remain deferred:
  `.agents/plans/prevent-keyboard-review-regressions-lifecycle-preference.md`
- [~] Execute the next keyboard interaction and polish plan; bounded gesture word entry, cursor repeat, incognito visuals, tools overflow, and bounded geometry are complete, while smart delete and final visual tuning remain:
  `.agents/plans/next-keyboard-interaction-and-polish.md`

## Next-round plans

- `translation-and-editor-replacement.md`
- `durable-image-clipboard.md`
- `emoji-settings-and-quick-delete-contracts.md`
- `prevent-keyboard-review-regressions-lifecycle-preference.md`

## Recommended next-round order

1. Build the in-keyboard translation panel and normal mode. Remove the
   reachable external-app translation launch.
2. Add the translation provider boundary and honest loading/error states.
3. Add opt-in live-write translation only after normal translation is stable.
4. Add Android-context verification for image clipboard restart, migration,
   orphan cleanup, trimming, deletion, and startup listener behavior.

The rewritten feature plans are intentionally separate so each round can pass
compile, verification, diff, and Conventional Commit gates independently.

## Delivery rule

Implement each plan as a separate focused round. Resume at the first unchecked task. After each round, run:

```bash
source .bin/env.sh
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
git diff --check
```

Commit each round with a Conventional Commit message and update this index only when the status changes.
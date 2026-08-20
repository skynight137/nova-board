# Plan: Replace External Translation Launch with an In-Keyboard Translation Composer

## Product goal

Translation must stay inside NovaBoard instead of opening a separate translation
app. The keyboard should provide a compact translation surface matching the
reference screenshots:

- source and target language labels with a swap action;
- an editable source field with clear and back controls;
- a translated result area;
- explicit `Paste` and `Reply` actions;
- a choice between ordinary translation and live-write translation.

The keyboard must never imply that text was translated or inserted when no
provider result is available.

## Current problem and boundary

- `NovaBoardService.openTranslation()` launches `TranslationResultActivity`.
- `TranslationResultActivity` launches `ACTION_PROCESS_TEXT` in another app.
- The existing selection/session contract is useful for stale-result protection,
  but the external activity relay is the wrong user experience.
- This plan removes the external-app dependency from the normal keyboard path.

Translation provider access must be isolated behind a small interface. The
first implementation may use the existing supported provider path or a
configured integration, but the keyboard UI must show an unavailable state
instead of silently falling back to another app.

## Recommended implementation order

1. **Pure UI/state contract** — easiest and provider-independent.
2. **Keyboard panel and normal translate flow** — local text editing, result
   rendering, `Paste`, `Reply`, swap, clear, and dismissal.
3. **Provider adapter and error states** — connect translation without changing
   the panel contract.
4. **Live-write mode** — opt-in incremental translation with debounce,
   cancellation, session checks, and safe commit behavior.
5. **Accessibility and responsive visual pass** — verify narrow keyboards,
   dark theme, focus order, and touch targets.

## Phase 1: Translation composer contract

### Task 1: Define state and actions `[✓]`

Create a JVM-testable model for:

- source text and translated text;
- source/target language identifiers;
- normal versus live-write mode;
- loading, unavailable, error, and ready states;
- current input session and request generation;
- pending source selection/range when launched from selected editor text.

Actions should include `editSource`, `swapLanguages`, `clearSource`,
`requestTranslation`, `pasteResult`, `replyWithResult`, `cancel`, and
`dismiss`.

Acceptance criteria:

- [✓] Empty source cannot request translation.
- [✓] Swapping languages swaps labels and clears or explicitly revalidates the
  stale result.
- [✓] A result from an old request or language pair is rejected. Input-session
  identity is carried by the state and will be enforced by the service panel
  integration.
- [✓] `Paste` and `Reply` are distinct actions with explicit output contracts.
- [✓] Live-write state cannot commit while a newer request is pending because
  only the current loading generation can produce a result.

Files likely touched:

- `app/src/main/java/com/novaboard/ime/translation/`
- `app/src/test/java/com/novaboard/ime/translation/`

Implementation note: the pure composer state and reducer are complete. The
service-owned input-session invalidation is intentionally left for the panel
integration stage, where editor lifecycle events can be wired to the model
without inventing a second session owner.

## Replanned follow-up scope

The task-panel suggestions were reconciled into this existing plan rather than
creating duplicate plan files. The next implementation round is Phase 2:
replace the reachable `TranslationResultActivity` path with a native,
session-owned keyboard panel. Phase 3 follows with the provider interface and
honest loading/error handling. Phase 4 remains gated on those two rounds.

## Phase 2: Native in-keyboard normal translation

### Task 2: Replace the external launch with a keyboard panel `[ ]`

Add a panel/container inside `keyboard_container.xml` or as a native child
managed by `NovaBoardService`. It must open over or replace the tools/suggestion
strip without opening an Activity.

Acceptance criteria:

- [ ] `Translate` opens the in-keyboard panel.
- [ ] Selected editor text pre-fills the source field when available.
- [ ] The source field can be edited without mutating the host editor yet.
- [ ] Language labels, swap, clear, back, result area, `Paste`, and `Reply`
  match the reference interaction hierarchy.
- [ ] Outside tap, back, editor change, input-session reset, and service
  destruction dismiss or invalidate the panel safely.
- [ ] The panel remains usable on narrow and tall phone layouts.
- [ ] All controls have content descriptions and keyboard-focusable states.

`Paste` should commit the translated result at the current cursor without
replacing unrelated text. `Reply` should replace only the originally selected
range when that selection/session is still valid; otherwise it must report that
the original selection is no longer available and leave editor text unchanged.

Files likely touched:

- `app/src/main/res/layout/keyboard_container.xml`
- `app/src/main/java/com/novaboard/ime/NovaBoardService.kt`
- `app/src/main/java/com/novaboard/ime/translation/TranslationPanel.kt`
- `app/src/main/res/values/strings.xml`

## Phase 3: Translation provider boundary

### Task 3: Add normal translation requests without external app launch `[ ]`

Define a provider interface and one implementation appropriate to the project's
approved integration boundary. Do not put network calls in the View or service
click handlers.

Acceptance criteria:

- [ ] Requests include source language, target language, and source text.
- [ ] Loading state disables duplicate submission without blocking keyboard
  dismissal.
- [ ] Success renders the result in the panel.
- [ ] Cancellation, timeout, provider failure, and unsupported language pairs
  show an honest unavailable/error state.
- [ ] No provider failure launches `ACTION_PROCESS_TEXT` or another app.
- [ ] Results are ignored after a newer request, language swap, editor change,
  or input-session change.

Verification:

- [ ] Provider-independent tests cover success, failure, cancellation, and
  stale-result rejection.
- [ ] Integration tests use a fake provider; no live network is required.
- [ ] Debug APK compiles with the panel resources.

## Phase 4: Live-write translation

### Task 4: Add opt-in live-write mode `[ ]`

Live-write mode translates the user's ongoing source composition and commits
only the latest accepted target text to the active editor. It must not send one
request per keystroke or overwrite text from a newer editor session.

Acceptance criteria:

- [ ] Normal mode remains the default and is unchanged by enabling live-write.
- [ ] Live-write has a visible enabled state and can be turned off immediately.
- [ ] Requests are debounced and cancelled/replaced when source text changes.
- [ ] Only the newest session/request/language result can commit.
- [ ] Partial or failed results do not erase the user's source text.
- [ ] Password, URI, email, and other restricted editor types disable live-write
  with an honest explanation.
- [ ] Incognito mode does not persist or expose translation source text through
  learning or diagnostics.

## Phase 5: Verification and cleanup

- [ ] Remove the normal-path `TranslationResultActivity` launch and manifest
  dependency once no supported flow uses it.
- [ ] Keep or remove the old relay only if a separate compatibility path is
  explicitly documented; it must not be reachable from the keyboard button.
- [ ] Add model, panel, stale-session, and editor-replacement tests.
- [ ] Run `source .bin/env.sh && ./gradlew :app:testDebugUnitTest`.
- [ ] Run `source .bin/env.sh && ./gradlew :app:assembleDebug`.
- [ ] Run `git diff --check`.
- [ ] Commit each vertical slice with Conventional Commit messages.

## Definition of done

- Translation opens and operates entirely inside NovaBoard.
- Normal translation supports safe `Paste` and selection-scoped `Reply`.
- Live-write is explicit, cancellable, session-safe, and disabled where unsafe.
- Provider failures are visible and do not launch another application.
- The panel is accessible and usable across representative keyboard sizes.
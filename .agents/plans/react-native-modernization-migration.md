# Migration Plan: React Native UI Modernization for NovaBoard

## Status

**Phase 1 preview foundation complete — device validation remains pending.**

The preview foundation is intentionally independent from the Android/IME
workflow. No production UI migration or native bridge changes are included.

## Current-state correction

NovaBoard is currently a Kotlin Android application using native Android Views
and view binding, not Jetpack Compose. The project has:

- `NovaBoardService`, an Android `InputMethodService` that owns the active
  editor connection and keyboard lifecycle.
- A native `KeyboardView` with touch, repeat, gesture, cursor, and key-preview
  behavior.
- Native clipboard, GIF content-provider, voice-input, emoji, hotkey,
  suggestion, theme, and settings implementations.
- `MainActivity` for enabling and selecting the input method.
- JVM tests for platform-independent contracts and Android instrumentation
  boundaries.
- A native Android release pipeline that produces a signed APK.

The migration goal is to modernize the UI development experience with React
Native and direct UI previewing without breaking IME reliability, privacy, or
release identity.

## Recommended architecture

Use a **bare React Native hybrid architecture**:

1. Keep the Android shell and `NovaBoardService` native.
2. Move previewable presentation and UI state into React Native components.
3. Expose narrowly scoped native modules for editor actions, clipboard
   operations, speech, GIF insertion, preferences, theme, haptics, and
   keyboard metrics.
4. Use a React Native component-preview workflow (Storybook or an equivalent
   React Native Web preview) so panels and settings can be rendered without
   launching an IME.
5. Treat the live keyboard renderer as a separate performance-sensitive
   migration decision. Do not assume that a component that previews well is
   safe to host inside an `InputMethodService`.

A full replacement of the native IME service is not the initial target. It
would couple React runtime startup, bridge availability, and JavaScript
responsiveness to the system keyboard path.

## Non-negotiable boundaries

- `NovaBoardService` remains the sole owner of `InputConnection` mutations.
- Every asynchronous native callback remains scoped to the active input session.
- Clipboard image bytes stay behind the existing opt-in privacy policy and
  app-owned content providers.
- Release signing, certificate verification, application IDs, and manual
  GitHub release behavior remain unchanged.
- Native Android platform behavior must have a fallback when the React Native
  runtime is unavailable, cold-starting, or unresponsive.
- Keyboard touch, deletion, repeat, cursor, and gesture behavior must not be
  reimplemented in JavaScript until performance and cancellation behavior are
  measured on representative devices.

## Ordered phases

### Phase 0: Migration contract and baseline `[~]`

Document the current behavior before changing the UI stack.

Acceptance criteria:

- [x] Feature inventory covers keyboard layouts, suggestions, clipboard,
  emoji, GIFs, hotkeys, voice, cursor controls, themes, settings, privacy, and
  release behavior.
- [x] Each feature is classified as native platform boundary, shared domain
  logic, React Native UI candidate, or deferred.
- [x] Existing JVM and instrumentation verification commands are recorded.
- [~] Baseline debug APK launch and IME activation are verified on a
  representative Android target.
- [~] Baseline screenshots or equivalent acceptance references exist for the
  settings screen, keyboard toolbar, tools surface, clipboard panel, emoji
  panel, and GIF panel.

Deliverable: `docs/react-native-migration-baseline.md`. Device-only items remain
partial until a representative Android target is available.

### Phase 1: React Native preview foundation `[x]`

Add the smallest React Native surface that can be previewed independently from
the keyboard service.

Acceptance criteria:

- [x] React Native uses a supported version compatible with the repository's
  JDK, Android Gradle Plugin, Android SDK, and Node toolchain.
- [x] The native Android app still builds without requiring a running React
  Native development server for release or offline fallback behavior.
- [x] A direct preview command renders a representative component in the
  Replit preview workflow or an equivalent browser-accessible preview.
- [x] Preview and Android runtime share tokens for colors, typography, spacing,
  radii, and keyboard dimensions rather than screenshot-specific constants.
- [x] The preview workflow is documented and does not replace the app's
  Android/IME workflow.
- [x] Dependency and bundle-size impact is measured before broad UI migration.

Verification:

- [x] React Native preview starts successfully.
- [x] Native debug APK assembles.
- [x] Existing JVM tests pass.
- [x] `git diff --check` passes.

### Phase 2: Shared contracts and native bridge seams `[~]`

Create testable interfaces between React Native UI and native Android behavior
before moving production screens.

Acceptance criteria:

- [x] Editor actions are represented as explicit commands owned by the native
  service; JavaScript cannot receive an unrestricted `InputConnection`.
- [x] Clipboard, GIF, voice, preferences, theme, haptic, and keyboard-metric
  APIs have typed request/result/error contracts.
- [x] Native bridge errors are visible to the UI and do not silently fall back
  to fake success.
- [x] Session identity and cancellation are included in asynchronous bridge
  operations.
- [x] Privacy-sensitive data is minimized at the bridge boundary; clipboard
  contents and diagnostic secrets are not logged.
- [x] Contract tests cover success, unavailable-runtime, stale-session,
  permission, and provider-rejection cases.

Verification:

- [x] Focused JVM contract tests pass.
- [x] Native bridge tests pass without requiring a live keyboard.
- [x] A preview mock implements the same contracts for deterministic UI states.

### Phase 3: Migrate settings and non-IME surfaces `[ ]`

Move `MainActivity` settings UI first because it is easier to host and preview
than the live keyboard window.

Acceptance criteria:

- [ ] React Native settings can enable NovaBoard and open Android's input-method
  picker through a native bridge.
- [ ] Theme, gesture, incognito, clipboard-retention, and other existing
  preferences retain their current defaults and persistence behavior.
- [ ] Accessibility labels, focus order, dynamic text, and switch states match
  or improve the current native screen.
- [ ] Settings remains usable if the React Native bundle is unavailable,
  including a deliberate native fallback or a clear failure state.
- [ ] Preview includes loading, error, empty, light-theme, dark-theme, and
  narrow-phone states.

Verification:

- [ ] Settings contract and persistence tests pass.
- [ ] Debug APK launches settings and reaches Android input-method controls.
- [ ] Direct preview matches the approved settings states.

### Phase 4: Migrate panel UI with native IME hosting `[ ]`

Move clipboard, emoji, hotkey, tools, and GIF panel presentation while keeping
service ownership and platform operations native.

Acceptance criteria:

- [ ] A native IME host can attach/detach the React Native root safely across
  service recreation and input-session changes.
- [ ] Full-screen modal panels consume touches before the hidden keyboard can
  receive them.
- [ ] Clipboard search continues to use the embedded NovaBoard keyboard or an
  explicitly tested equivalent; focusing a React text field must not assume
  Android can relaunch the active IME.
- [ ] Clipboard image retention, pinning, deletion, GIF sharing, and provider
  grants preserve existing behavior.
- [ ] Panel open/close state is cleared or restored according to the existing
  service lifecycle contract.
- [ ] Native fallback panels remain available until React Native hosting is
  proven stable in instrumentation and manual device testing.

Verification:

- [ ] Panel contract tests pass.
- [ ] Instrumentation covers service recreation, session changes, touch
  boundaries, provider failures, and back/close behavior.
- [ ] Manual checks cover an actual hardware keyboard target and a narrow/tall
  phone.

### Phase 5: Keyboard renderer feasibility spike `[ ]`

Run a time-boxed experiment before committing to JavaScript for the primary
keyboard surface.

Acceptance criteria:

- [ ] A React Native keyboard prototype renders the configured layouts,
  number-row preference, themes, key previews, and toolbar states.
- [ ] Measured first-render latency, key-to-commit latency, frame stability,
  memory, and bridge traffic meet agreed thresholds on low-, mid-, and
  high-tier representative devices.
- [ ] Multi-touch, cancellation, long-press, repeat-delete, cursor movement,
  gesture typing, and session invalidation behave correctly.
- [ ] The keyboard remains usable during React Native reload/unavailability,
  with a tested native fallback.
- [ ] The spike produces a go/no-go decision based on measurements rather than
  visual similarity.

Decision:

- **Go:** migrate the keyboard renderer in vertical slices, retaining native
  service ownership and fallback.
- **No-go:** keep `KeyboardView` native and use React Native for settings,
  panels, preview, and shared design tokens.

### Phase 6: Production hardening and release migration `[ ]`

Only after the previous phases are accepted, remove obsolete UI paths and make
React Native part of the release product.

Acceptance criteria:

- [ ] Release builds do not depend on a development server or local Metro
  process.
- [ ] JavaScript bundle, native dependencies, APK size, startup time, and
  crash behavior are measured and documented.
- [ ] ProGuard/R8, permissions, provider authorities, application IDs, and
  signing/certificate checks remain correct.
- [ ] CI builds the previewable UI and Android APK from a clean checkout.
- [ ] Existing release and update behavior remains compatible with installed
  APKs.
- [ ] Old native UI paths are removed only after production parity and fallback
  coverage are demonstrated.

Verification:

- [ ] Focused UI and bridge tests pass.
- [ ] Android debug and release packaging checks pass.
- [ ] Manual IME regression matrix passes.
- [ ] Release-tooling tests pass.
- [ ] Documentation and project memory reflect the final architecture.

## Proposed migration order

1. Baseline and contracts.
2. React Native preview foundation.
3. Settings screen.
4. Non-keyboard panels.
5. Keyboard feasibility spike.
6. Go/no-go decision for the live keyboard renderer.
7. Production hardening and removal of obsolete paths.

## Explicitly deferred decisions

- Whether the primary keyboard renderer should become React Native.
- Whether to use Storybook, React Native Web, or another preview host after the
  initial preview spike.
- Whether native domain logic should remain in Kotlin or be shared with a
  separate TypeScript domain package.
- Whether the final product should remain Android-only or later add iOS; the
  current IME behavior is Android-specific.

## Approval gate

Confirm this plan before implementation begins. In particular, confirm whether
the recommended hybrid architecture and the keyboard-renderer feasibility
spike match the intended modernization direction.
# NovaBoard feature checkpoint

Use this document as the current acceptance checklist for NovaBoard. Mark a
checkbox only after testing that behavior on the target Android device.

## Checklist legend

- `[✓]` Complete and manually or automatically verified
- `[~]` Implemented but partially verified, platform-dependent, or intentionally
  limited
- `[ ]` Not complete

## Confirmed by user

These behaviors were manually confirmed in the active keyboard:

- [✓] Clipboard search
- [✓] Clipboard pin
- [✓] Clipboard unpin
- [✓] Voice typing
- [✓] `Ctrl+A` select all
- [✓] `Ctrl+X` cut
- [✓] `Ctrl+C` copy
- [✓] `Ctrl+V` paste
- [✓] `Tab`
- [✓] Delete
- [✓] Home
- [✓] End
- [✓] `F5`
- [✓] Multi-touch character input

## Implemented feature inventory

### Core keyboard input

- [✓] Android IME service can be enabled and selected as the active keyboard
- [✓] QWERTY letter keyboard
- [✓] Number row
- [✓] Number layout
- [✓] Symbol layout
- [✓] Secondary symbol layout
- [✓] Cursor-arrow controls
- [✓] Shift and caps behavior
- [✓] Space and enter actions
- [✓] Backspace/delete
- [✓] Multi-touch letter input
- [✓] Per-touch key previews
- [✓] Long-press accented characters and symbols
- [~] Gesture typing is implemented with bounded recognition; broader manual
  device coverage is still recommended

### Editing and hardware-style controls

- [✓] `Ctrl+A`, `Ctrl+X`, `Ctrl+C`, and `Ctrl+V`
- [✓] Tab
- [✓] Delete
- [✓] Home
- [✓] End
- [✓] `F5`
- [✓] Cursor movement controls
- [✓] Space-bar cursor movement
- [✓] Bounded repeated delete
- [✓] Bounded repeated cursor movement
- [✓] Session-safe editor mutations

### Suggestions and text behavior

- [✓] Suggestion strip
- [✓] Next-word prediction
- [✓] Basic autocorrect helpers
- [✓] Small grammar helpers
- [✓] Learned-word support
- [~] Seed dictionary is intentionally small and should be expanded before
  production release
- [~] Incognito learning isolation is implemented, but full device-level
  verification is still recommended

### Clipboard

- [✓] Text clipboard history
- [✓] Image clipboard references
- [✓] Clipboard search
- [✓] Case-insensitive search
- [✓] Empty search restores complete history
- [✓] Pin clipboard item
- [✓] Unpin clipboard item
- [✓] Delete clipboard item
- [✓] Paste while keeping the clipboard panel open
- [✓] Durable local clipboard storage
- [✓] App-owned content provider for retained images
- [~] Image paste depends on the target editor supporting Android
  `InputContentInfo`
- [✓] Image retention is opt-in and disabled by default

### Toolbar and panels

- [✓] Prediction bar remains visible above the number row
- [✓] Expandable tools row
- [✓] Clipboard panel
- [✓] Hotkeys panel
- [✓] Voice panel
- [✓] Emoji panel
- [✓] Overflow/tools actions
- [✓] Outside-tap and editor-change dismissal boundaries

### Voice and emoji

- [✓] Android on-device speech recognizer integration
- [✓] Voice request/session cancellation handling
- [✓] Scrollable emoji picker
- [✓] Padded emoji cells
- [✓] System emoji fallback rendering

### Appearance and settings

- [✓] System theme
- [✓] Light theme
- [✓] Dark theme
- [✓] Android resources normalized to the approved NovaBoard design tokens
- [✓] Keyboard toolbar and cursor controls use the minimum touch-target contract
- [✓] Keyboard enable action
- [✓] Active input-method switch action
- [✓] Keyboard preference handling
- [~] Settings action hierarchy, typography, and preference-row styling
  implemented; device visual verification remains part of the narrow/tall
  layout pass
- [~] Interactive keyboard preview supports tools, emoji, panels, and keyboard
  height adjustment; device visual verification remains part of the layout pass
- [~] Launcher icon remains a placeholder monogram
- [~] Visual polish should still be checked on narrow, tall, light, and dark
  phone layouts

### Privacy, diagnostics, and release

- [✓] Clipboard and preferences remain on-device
- [✓] No accessibility service is used for text capture
- [✓] Microphone permission is limited to voice typing
- [✓] Bounded diagnostic report
- [✓] Diagnostic metadata redaction rules
- [✓] Debug APK build
- [✓] JVM unit tests
- [✓] Conventional Commit release workflow
- [~] Final physical-device regression pass across supported Android versions
- [~] Stable release signing and GitHub release publication

## Current quality baseline

The following project checks passed during the latest review:

```text
source .local/env.sh && ./gradlew :app:testDebugUnitTest --stacktrace
source .local/env.sh && ./gradlew :app:assembleDebug --stacktrace
bash -n scripts/setup.sh scripts/rewrite-commit-messages.sh .github/release-tooling/*.sh
git diff --check
```

## Recommended next work

Prioritized for the best user-facing result:

### 1. Finish a real-device regression matrix

**Why first:** A keyboard can compile and pass JVM tests while still behaving
differently across editors, Android versions, screen sizes, and hardware
keyboards.

Test at minimum:

- narrow and tall portrait phones
- light, dark, and system themes
- a normal text field, browser field, multiline editor, and password field
- physical keyboard connected and disconnected
- clipboard text and image paste into supported and unsupported editors
- voice cancellation, permission denial, and editor switching
- rotation or keyboard recreation during typing

### 2. Improve suggestion quality and user trust

**Why:** Suggestions are the feature users interact with on every keystroke.
The current seed dictionary is useful for a prototype but limits accuracy.

Recommended scope:

- replace the seed list with a larger on-device frequency list
- make autocorrect undoable and visibly understandable
- add a clear “do not learn”/incognito status indicator
- test punctuation, emoji, URLs, names, and mixed-language input

### 3. Add a production-ready settings and onboarding experience

**Why:** First-run activation is the largest setup barrier for a custom IME.

Recommended scope:

- explain why Android shows keyboard privacy warnings
- show enabled/active status clearly
- add reset-to-default preferences
- add a compact shortcut/help screen for hotkeys and gestures
- provide a clear privacy page describing local clipboard and voice behavior

### 4. Polish interaction feedback and responsive layout

**Why:** Fast, predictable feedback matters more for a keyboard than decorative
visual changes.

Recommended scope:

- verify key sizes and spacing across phone dimensions
- refine pressed, long-press, cursor, and panel states
- honor reduced-motion preferences
- improve keyboard height and toolbar overflow behavior
- replace the placeholder launcher icon

### 5. Prepare a small closed beta release

**Why:** Real keyboard usage exposes issues that scripted tests cannot.

Suggested release gate:

- complete the device/editor matrix
- install the signed APK on two or more devices
- collect only opt-in diagnostic reports
- verify clipboard image cleanup and app uninstall behavior
- publish a release with rollback instructions

## Next checkpoint

The next recommended checkpoint is complete when all of these are true:

- [ ] Real-device regression matrix is recorded
- [ ] No confirmed crash, stuck repeat action, or stale-session text insertion
- [ ] Suggestion and incognito behavior are explained in settings
- [ ] Narrow/tall and light/dark layouts are visually checked
- [ ] Provider-dependent features are clearly labeled as unavailable
- [ ] Signed APK installs and launches successfully on beta devices

## Execution note

On 2026-08-21, the remaining device-dependent stages were intentionally
deferred. This repository environment has no connected Android device or
emulator, so marking physical-device behavior, visual layout checks, or signed
beta installation as complete would be unsupported. The next operator should
resume with the real-device regression matrix above and record observed results
before changing these checkboxes.
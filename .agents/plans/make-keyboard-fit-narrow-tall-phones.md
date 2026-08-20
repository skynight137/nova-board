# Plan: Make the Keyboard Fit Narrow and Tall Phones Reliably

## Overview

Complete the responsive geometry and motion pass for NovaBoard so the keyboard
remains usable across narrow portrait, regular portrait, and tall phone
layouts. Preserve the existing native View architecture, touch targets,
incognito state, overflow menu, suggestions, and cursor controls.

## Current state

- `KeyboardView` derives row height from the measured available height and
  constrains it to a bounded range.
- Keyboard keys are laid out proportionally from flex weights.
- Cursor controls, incognito banner, tools strip, suggestion strip, and
  overflow menu are present.
- Visual state coverage across screen sizes and reduced-motion behavior is not
  yet complete.

## Ordered task list

### Task 1: Establish responsive geometry constraints

**Description:** Measure the complete keyboard container, including strips,
number row, keyboard rows, cursor controls, and overlays. Ensure the custom
keyboard view receives only the height it can safely use.

**Acceptance criteria:**

- [x] Key rows scale from available width and height without clipping.
- [ ] The number-row preference does not push bottom controls off-screen.
- [x] Narrow widths preserve usable key hit rectangles and minimum touch sizes.
- [ ] Tall layouts do not create excessive empty gaps between controls.
- [ ] Overflow and emoji overlays remain inside the keyboard window.

**Files likely touched:**

- `app/src/main/java/com/novaboard/ime/view/KeyboardView.kt`
- `app/src/main/res/layout/keyboard_container.xml`
- `app/src/main/res/values/dimens.xml`
- `app/src/main/res/values-sw600dp/`

### Task 2: Improve state feedback and contrast

**Description:** Audit pressed, long-press, gesture, incognito, overflow, and
suggestion states in both light and dark palettes. Keep state feedback clear
without reducing text or touch-target legibility.

**Acceptance criteria:**

- [ ] Pressed keys remain visually distinct from normal and special keys.
- [ ] Gesture mode provides clear in-progress and cancellation feedback.
- [ ] Incognito state remains obvious and readable in both themes.
- [ ] Overflow menu items have readable text, focus feedback, and touch targets.
- [x] Content descriptions remain meaningful after visibility changes.

**Files likely touched:**

- `app/src/main/java/com/novaboard/ime/view/KeyboardView.kt`
- `app/src/main/java/com/novaboard/ime/NovaBoardService.kt`
- `app/src/main/res/values/colors.xml`
- `app/src/main/res/values-night/colors.xml`
- `app/src/main/res/values/styles.xml`
- `app/src/main/res/drawable/`

### Task 3: Make transitions cancellable and accessibility-aware

**Description:** Add only short, useful transitions for previews and menus.
Respect the platform reduced-motion preference and cancel transient work on
editor changes, outside taps, back, and service teardown.

**Acceptance criteria:**

- [ ] Key previews and overflow menus dismiss on cancellation and input changes.
- [ ] No animation or delayed visual callback survives view detachment.
- [ ] Reduced-motion settings disable or shorten nonessential transitions.
- [ ] Motion does not delay key input or cursor/delete actions.

**Files likely touched:**

- `app/src/main/java/com/novaboard/ime/view/KeyboardView.kt`
- `app/src/main/java/com/novaboard/ime/NovaBoardService.kt`
- `app/src/main/res/values/integers.xml`

### Task 4: Verify representative phone geometries

**Description:** Perform a visual and interaction pass against representative
portrait dimensions, then encode any discovered constraints in layout/model
tests where practical.

**Acceptance criteria:**

- [ ] Narrow portrait layout is usable without clipped keys or controls.
- [ ] Regular portrait layout matches the intended spacing hierarchy.
- [ ] Tall portrait layout keeps the keyboard compact and balanced.
- [ ] Dark theme preserves contrast and state feedback.
- [ ] Gesture entry, held delete, cursor repeat, incognito exit, and overflow
  actions remain functional at each size.

## Verification

Run the available automated checks:

```bash
source .bin/env.sh
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
git diff --check
```

For manual verification, use at least narrow portrait, regular portrait, tall
portrait, and dark-theme configurations. If device or preview automation is not
available, leave the visual acceptance criteria unchecked and record the
unverified configurations.

## Risks and mitigations

| Risk | Impact | Mitigation |
|---|---|---|
| Hard-coded screenshot dimensions clip other devices | High | Derive sizes from measured bounds and resource dimensions |
| More vertical UI reduces typing area | High | Cap optional strips and preserve keyboard hit targets first |
| Motion interferes with input timing | Medium | Keep transitions short, cancellable, and separate from text mutation |
| Theme changes invalidate custom-drawn colors | Medium | Resolve palette colors through the active context theme |

## Definition of done

- Keyboard geometry remains usable across the representative phone sizes.
- Visual state feedback is readable in light and dark themes.
- Transient UI is cancellable and reduced-motion aware.
- Automated checks pass, and manual configurations are recorded honestly.
# Plan: Make Image Clipboard History Optional, Durable, and User-Controlled

## Product goal

Users must be able to choose whether NovaBoard stores image clipboard history.
When enabled, image clips should remain previewable and pasteable after a
process restart or device reboot. When disabled, NovaBoard must not capture new
image clipboard content and should provide a clear way to remove existing
stored image files.

Text clipboard history remains unchanged unless the user separately disables
the overall clipboard feature in a future product decision.

## Current state

- Image clips are copied into app-private `clipboard-images` storage.
- Persisted image references are migrated, trimmed, and cleaned up.
- The implementation is durable, but there is no user-facing image-history
  capture toggle.
- Existing malformed-entry and cleanup behavior still needs dedicated tests.

## Recommended implementation order

1. **Preference and settings toggle** — easiest, low-risk, and user-visible.
2. **Capture boundary enforcement** — stop image capture when disabled.
3. **Cleanup and disable semantics** — define what happens to already stored
   images and make it explicit.
4. **Persistence regression tests** — prove restart, migration, trimming, and
   cleanup behavior.
5. **Keyboard UI and accessibility pass** — show honest state and unavailable
   behavior.

## Phase 1: User preference

### Task 1: Add image clipboard history preference `[x]`

Add a persisted `IMAGE_CLIPBOARD_HISTORY` preference with an explicit default.
The recommended default is `false` so image content is not retained without
the user's choice.

Acceptance criteria:

- [x] Settings exposes “Save image clipboard history” with a clear summary.
- [x] The default is disabled for new installations.
- [x] The setting survives keyboard recreation and process restart.
- [x] Reset restores the documented default.
- [x] Accessibility text explains that enabling stores images in app-private
  keyboard storage.

Files likely touched:

- `app/src/main/java/com/novaboard/ime/settings/KeyboardPreferences.kt`
- `app/src/main/java/com/novaboard/ime/settings/MainActivity.kt`
- `app/src/main/res/values/strings.xml`
- `app/src/test/java/com/novaboard/ime/settings/`

## Phase 2: Enforce capture policy

### Task 2: Gate new image capture `[~]`

Pass the preference decision into `ClipboardHistoryManager` or read it through
an injected policy seam. The manager must evaluate the policy before copying
any image bytes.

Acceptance criteria:

- [x] Disabled mode never opens an image source stream or writes an image file.
- [x] Text clips continue to capture normally.
- [ ] Re-enabling captures only future image clips; it does not reconstruct
  images that were skipped while disabled.
- [ ] Duplicate protection remains unchanged.
- [ ] Incognito and image-history preferences remain independent and clearly
  documented.

## Phase 3: Define disable and cleanup behavior

### Task 3: Make disabling image history predictable `[~]`

Use a confirmation step when disabling if stored image history will be
deleted, or provide a separate “Delete saved image clips” action. The chosen
behavior must be explicit rather than silently retaining private images.

Recommended behavior:

- Disabling stops future image capture immediately.
- Existing stored image clips remain available until the user chooses
  “Delete saved image clips,” unless the UI clearly warns that disabling also
  deletes them.
- Deleting saved image clips removes image entries, private files, orphan files,
  and persisted references without affecting text clips.

Acceptance criteria:

- [x] The user can see whether image capture is enabled.
- [x] A disabled setting cannot leave a new image item in the keyboard panel.
- [x] Cleanup is idempotent and safe when files are already missing.
- [x] Pinned image entries are handled by the documented cleanup choice.
- [x] Storage errors remove or mark only the affected image item.

## Phase 4: Persistence and cleanup verification

### Task 4: Add deterministic clipboard regression coverage `[ ]`

Create isolated tests around the existing manager seams or extract pure
persistence helpers where Android clipboard access prevents JVM testing.

Acceptance criteria:

- [ ] Restart-style load preserves valid durable image entries when files exist.
- [ ] Malformed JSON and invalid entries do not block valid entries.
- [ ] Missing durable files are skipped or shown as unavailable without a crash.
- [ ] Legacy provider URIs migrate only when readable.
- [ ] Orphan image files are removed during cleanup.
- [ ] Cap trimming removes the corresponding private files.
- [ ] Delete removes the corresponding private file and persisted item.
- [ ] Disabled image capture produces no stored image entry.
- [ ] Text entries and duplicate startup import behavior remain unchanged.

Verification:

- [ ] `source .bin/env.sh && ./gradlew :app:testDebugUnitTest`
- [ ] `source .bin/env.sh && ./gradlew :app:assembleDebug`
- [ ] `git diff --check`
- [ ] Each vertical slice uses a Conventional Commit.

## Phase 5: Keyboard experience

### Task 5: Make the state visible and honest `[ ]`

- [ ] Clipboard settings show the image-history state and cleanup action.
- [ ] The clipboard panel does not expose stale image entries after disabling
  and cleanup.
- [ ] Unavailable images have an explicit state instead of a broken preview.
- [ ] All settings and destructive cleanup actions have content descriptions
  and confirmation text.
- [ ] Narrow, tall, light, and dark keyboard layouts remain usable.

## Risks and decisions

| Risk | Mitigation |
|---|---|
| Images may contain sensitive content | Default capture off; app-private storage; explicit setting |
| Disabling may surprise users if it deletes history | Confirm or separate cleanup action |
| Legacy provider grants may expire | Migrate readable content to private files |
| Cleanup may remove pinned content unexpectedly | Document and test pinned-item behavior |
| Preference reads may be hidden inside Android code | Use a small injectable policy seam for tests |

## Definition of done

- Image clipboard history is opt-in and clearly explained.
- Disabled mode captures no new image bytes.
- Enabled mode survives restart and cleans files safely.
- Users can delete stored image history without losing text history.
- Automated tests and debug packaging pass before each focused commit.
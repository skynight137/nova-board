# React Native Migration Baseline

Status: Phase 0 baseline for the proposed React Native modernization.

## Architecture baseline

NovaBoard is a native Android Views application. `NovaBoardService` extends
`InputMethodService` and owns the input connection, keyboard lifecycle,
session identity, editor mutations, suggestions, clipboard capture, voice
recognition, GIF insertion, and overlay hosting. `KeyboardView` owns native
touch geometry, key rendering, long press, repeat, gesture paths, cursor
controls, and key previews. `MainActivity` owns settings and Android system
intents.

The initial migration must therefore be hybrid. React Native is suitable for
previewable presentation and state surfaces, but it must not receive direct
ownership of `InputConnection` or silently replace the service lifecycle.

## Feature migration matrix

| Surface or capability | Current implementation | Migration classification | Initial direction |
| --- | --- | --- | --- |
| Input method service and editor mutations | `NovaBoardService` / Android `InputMethodService` | Native platform boundary | Keep native; expose typed commands only |
| Keyboard hit testing and key rendering | `KeyboardView` / custom Android `View` | Native platform boundary; performance-sensitive UI | Keep native through the feasibility spike |
| Keyboard layouts and key model | `KeyboardModel.kt` | Shared domain logic candidate | Define serializable contract; do not duplicate behavior |
| Suggestions and learning policy | `SuggestionEngine`, learning contracts | Shared domain logic | Keep policy native first; expose display data |
| Deletion, repeat, cursor, and gesture behavior | `editing/*`, `gesture/*`, service callbacks | Native behavior boundary | Keep native; bridge intent/results only |
| Clipboard history and search | `clipboard/*`, embedded `KeyboardView` | Native storage/provider boundary plus React UI candidate | Keep capture, persistence, providers, and embedded input native; preview panel UI |
| Image clipboard retention | `ClipboardCapturePolicy`, content provider | Privacy/platform boundary | Keep native and opt-in |
| Emoji panel | `EmojiPanel`, system emoji fallback | React Native UI candidate | Migrate presentation after bridge contracts |
| GIF panel and insertion | `GifPanel`, `GifClient`, content provider | React Native UI candidate plus native provider boundary | Migrate panel; keep network/provider/editor handoff native |
| Hotkeys and tools menu | `HotkeyRow`, `ToolMenuModel`, service overlay | React Native UI candidate plus IME host boundary | Migrate presentation after touch-host spike |
| Voice typing | Android `SpeechRecognizer` in service | Native platform boundary | Keep recognizer native; expose state/results |
| Themes and keyboard metrics | `ThemeManager`, Android resources/preferences | Shared design-token candidate | Define cross-stack tokens; native remains fallback |
| Settings and input-method actions | `MainActivity`, Android settings intents | React Native UI candidate plus native intent bridge | First production migration target |
| Preferences and reset behavior | `KeyboardPreferences`, preference contracts | Shared domain contract | Keep persistence native initially; expose typed settings model |
| Diagnostics/log export | `AppLog`, Storage Access Framework | Native/privacy boundary | Keep native; never pass private input text to JS |
| Release APK, signing, certificate checks | Gradle and `.github/release-tooling` | Native/release boundary | Do not change during UI migration |

## Existing verification baseline

The current project verification commands are:

```bash
source .local/env.sh
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

Use the unit-test task for Kotlin contract or domain changes. Use debug
assembly for Android resources, dependencies, packaging, manifest, or native
bridge changes. A React Native preview must not become a prerequisite for
native-only release builds.

Baseline results for this phase:

- `source .local/env.sh && ./gradlew --no-daemon :app:testDebugUnitTest`
  passed.
- `source .local/env.sh && ./gradlew --no-daemon :app:assembleDebug` passed.
- `adb devices -l` returned no connected Android target in this workspace, so
  IME activation and device screenshots remain pending.

## Acceptance references

Until dedicated screenshot fixtures are added, these current project files are
the behavior references for the first migration slice:

- Settings structure and Android actions:
  `app/src/main/java/com/novaboard/ime/settings/MainActivity.kt`,
  `app/src/main/res/layout/activity_main.xml`
- Keyboard toolbar, suggestion strip, tools, and cursor rows:
  `app/src/main/res/layout/keyboard_container.xml`,
  `app/src/main/java/com/novaboard/ime/NovaBoardService.kt`
- Keyboard interaction and geometry:
  `app/src/main/java/com/novaboard/ime/view/KeyboardView.kt`,
  `app/src/main/java/com/novaboard/ime/model/KeyboardModel.kt`
- Clipboard search and embedded input keyboard:
  `app/src/main/java/com/novaboard/ime/clipboard/ClipboardPanel.kt`
- Emoji and GIF surfaces:
  `app/src/main/java/com/novaboard/ime/emoji/EmojiPanel.kt`,
  `app/src/main/java/com/novaboard/ime/gif/GifPanel.kt`
- Product feature and privacy expectations: `README.md`

These are structural acceptance references, not a substitute for device
screenshots. Device screenshots should be captured before Phase 3 begins.

## Phase 0 gate

- [x] Feature inventory covers keyboard layouts, suggestions, clipboard,
  emoji, GIFs, hotkeys, voice, cursor controls, themes, settings, privacy, and
  release behavior.
- [x] Each feature is classified as a native boundary, shared contract,
  React Native UI candidate, or deferred.
- [x] Existing JVM and instrumentation verification commands are recorded.
- [~] Debug APK launch and IME activation are documented but require a
  representative Android target for final manual verification.
- [~] Structural acceptance references are recorded; dedicated device
  screenshots remain deferred until an Android target is available.
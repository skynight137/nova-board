# NovaBoard

NovaBoard is a SwiftKey-inspired custom Android keyboard (IME) built with
Kotlin and native Android Views. It provides a full keyboard, suggestion strip,
clipboard history, emoji, hotkeys, themes, cursor controls, and on-device voice
input without a third-party keyboard SDK.

## Features

- SwiftKey-inspired toolbar layout with a prediction bar always visible directly
  above the number row
- Expandable tools row: the up chevron opens clipboard, hotkeys,
  voice, and overflow tools above the prediction bar; the down chevron collapses
  it without hiding predictions
- Suggestions, small grammar/autocorrect helpers, and next-word prediction
- QWERTY, number, symbol, and cursor-arrow layouts
- Key pop-ups for accented characters and symbols on long press
- Per-touch key previews: pressing multiple keys in parallel displays a preview
  for every pressed character instead of replacing earlier previews
- Multi-touch letter input for pressing more than one key together
- Clipboard history for text and image references with pinning and deletion
- Case-insensitive clipboard search with a no-results state; an empty search
  restores the complete history
- Clipboard stays open after paste so history, search, and pin controls remain
  available
- Scrollable, compact emoji picker with padded, uncropped emoji cells
- System, light, and dark themes
- Voice typing through Android's on-device `SpeechRecognizer`
- Optional gesture typing across letter keys with bounded path recognition
- Settings actions to enable NovaBoard and switch the active input method
- Exportable diagnostic report with bounded device, app, and application-log
  information for development support

## Try it

1. Launch the app once to open the settings screen.
2. Tap **Enable NovaBoard** and turn NovaBoard on in Android's keyboard list.
3. Tap **Switch input method**, or long-press the space bar in a text field.
4. Choose NovaBoard and type in any text field.

## Known gaps

- Cut/copy/paste/select-all actions are reserved for the tools-row expansion.
- Image clipboard paste depends on the target editor accepting Android
  `InputContentInfo`; unsupported editors show a clear message.
- The suggestion dictionary is a small seed list intended to be replaceable by
  a larger frequency list or on-device language model.
- The launcher icon is a placeholder vector monogram.

## Permissions and privacy

NovaBoard uses microphone access for voice typing and vibration for keyboard
feedback. It is an Android input method and does not request an accessibility
service or send keyboard content to a server. Clipboard history and preferences
remain on-device.

## Requirements

For users:

- Android 8.0 or newer (API 26+).

For development:

- JDK 24
- Android SDK platform 37
- Android SDK Build Tools
- Node.js and npm for release tooling

## Build and run

```bash
source .local/env.sh
./gradlew :app:assembleDebug
```

The debug APK is written to:

```text
app/build/outputs/apk/debug/NovaBoard-debug.apk
```

The debug application ID is `com.novaboard.ime.debug`.

## Verification

For source-only changes:

```bash
source .local/env.sh
./gradlew :app:testDebugUnitTest
```

For resource, dependency, or packaging changes:

```bash
./gradlew :app:assembleDebug
```

## Releases

Releases use Conventional Commits and semantic-release:

```text
dev  ──> prerelease (automatic push or manual dispatch)
main ──> stable release (automatic push or manual dispatch)
```

The pipeline calculates the next version, updates `app/gradle.properties`,
builds and signs `NovaBoard-<version>.apk`, and publishes the APK, detached
signature, and release metadata to GitHub. The release page is opened manually
from the app; there is no automatic updater.

See [docs/releasing.md](docs/releasing.md) for signing and release details.
The release workflow serializes publication. A `dev` run publishes a
semantic-release prerelease; a `main` run publishes a stable release.

## Contributing

Development happens on the `dev` branch. Keep commits focused, use Conventional
Commit messages, and include verification details in pull requests. See
[CONTRIBUTING.md](CONTRIBUTING.md).

## License

See [LICENSE](LICENSE).
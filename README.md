# Aurora EQ

Aurora EQ is an Android system-wide audio equalizer with a liquid-glass
interface. It attaches a configurable processing chain to the shared output
audio session through an Android foreground service.

The app does not read on-screen content or provide an online account service.
Audio and preset configuration stay on the device unless the user explicitly
exports a preset or app log.

## Features

- Configure preamp, HPF, sub shelf, 31-band EQ, air shelf, LPF, compressor,
  limiter, output gain, spatial processing, and Android media volume.
- Enable or bypass each processing stage independently, with linked or
  independent left/right controls where supported.
- Reset all live processing stages to their defaults without changing system
  volume or saved presets.
- Keep processing active through a foreground service and show engine attachment
  and device capability status in Settings, with a resume action when needed.
- Save, rename, delete, apply, share, import, and export full signal-chain JSON
  presets.
- Persist the live signal-chain configuration locally in app storage.
- Export the local app-owned log for troubleshooting; it is not uploaded
  automatically.
- Open the Aurora EQ GitHub Releases page to check for manual updates.

HPF, LPF, and shelf stages are implemented as gain contours blended into the
31-band EQ because Android does not provide the required native filter effects
to a non-root application. System-wide processing is best-effort through audio
session 0, so coverage varies by device, Android version, manufacturer, and
audio path. Dynamics Processing stages require a compatible device; Spatial
uses the platform Virtualizer where available.

## Permissions and privacy

Aurora EQ uses audio, vibration, notification, and foreground-service
permissions for its audio processing service. It does not request an
Accessibility Service and does not read screen text or collect personal data.

Preset JSON files exported through Android's file picker contain audio-stage
settings. Protect exported files if they contain sensitive listening
configuration.

The optional app log export contains bounded app-owned events plus device and APK
metadata. It does not include preset contents, tap coordinates, credentials, or
system-wide logcat. Export is explicit and stays in memory until the user saves
the text file through Android's file picker.

## Requirements

For users:

- Android 8.0 or newer (API 26+).
- Notification permission recommended for visible service status.

For development:

- JDK 24.
- Android SDK platform 37.
- Android SDK Build Tools installed and available to Gradle.
- Node.js and npm for release tooling.

## Build and run

Clone the repository and build the debug APK:

```bash
source .bin/env.sh
./gradlew :app:assembleDebug
```

The debug APK is written to:

```text
app/build/outputs/apk/debug/AuroraEQ-debug.apk
```

The debug application ID is `com.auroraeq.app.debug`. Install it from Android
Studio or with a connected device:

```bash
./gradlew :app:installDebug
```

Development Gradle commands use the persistent daemon for faster repeat runs.

## Verification

For Kotlin/source-only changes, run the focused JVM test task:

```bash
source .bin/env.sh
./gradlew :app:testDebugUnitTest
```

The JVM tests cover diagnostics, preset validation, UI helpers, and other pure
application logic. When a change affects Android resources, dependency
resolution, or packaging, also run the relevant build task:

```bash
./gradlew :app:assembleDebug
```

Add or update tests alongside behavior changes.

## Releases

Releases use Conventional Commits and semantic-release:

```text
dev  ──> prerelease
main ──> stable release
```

The release pipeline:

1. Calculates the next version from commit messages.
2. Updates `app/gradle.properties` and the changelog.
3. Builds and signs `AuroraEQ-<version>.apk`.
4. Publishes the APK, detached signature, and release metadata to GitHub.

The app does not contain an automatic updater. The **Check for updates** action
opens the Aurora EQ GitHub Releases page, where users choose an APK and install
it manually. `app-release.json` is published metadata for release tooling and
distribution records; the app does not fetch or parse it.

Release builds require the configured Android signing keystore and release
secrets. See [docs/releasing.md](docs/releasing.md) for the complete release
process and local verification commands.

The release repository is declared by `release.repository` in
`gradle.properties`.

## Contributing

Development happens on the `dev` branch. For substantial changes, open an issue
before implementation, keep commits focused, use Conventional Commit messages,
and include tests and verification details in the pull request.

See [CONTRIBUTING.md](CONTRIBUTING.md) for the contribution guidelines.

## License

See [LICENSE](LICENSE).
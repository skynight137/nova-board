# Aurora EQ - Project Context

Short, implementation-focused context for working in this repository. Keep
product behavior, requirements, release instructions, and contribution policy
in their canonical documents instead of duplicating them here.

## Canonical references

- Product scope, permissions, requirements, build, and verification:
  [[README]]
- Documentation index: [[docs/README]]
- Release automation and signing: [[docs/releasing]]
- Contribution workflow: [[CONTRIBUTING]]
- Replit environment and workflows: [[.replit]]

## Stack

- **Platform:** Android application, module [[app]]
- **Language:** Kotlin; Gradle Kotlin DSL
- **UI:** Jetpack Compose with Material 3
- **Application ID:** `com.auroraeq.app`; debug ID is
  `com.auroraeq.app.debug`
- **UI state:** Compose state with AndroidX lifecycle APIs where needed
- **Audio processing:** foreground `GlobalEqService` attached to the shared
  output audio session
- **Preset storage:** local Android preferences with JSON import/export through
  the Storage Access Framework
- **Updates:** manual APK installation from GitHub Releases
- **Testing:** JVM tests under
  [[app/src/test/java/com/auroraeq/app]]
- **Dependency and plugin source of truth:** [[gradle/libs.versions.toml]]
- **Module/build configuration:** [[settings.gradle.kts]] and
  [[app/build.gradle.kts]]

Use [[README#Requirements|development requirements]] for supported JDK, Android
SDK, and tooling versions. Do not maintain a second version list here.

## Project structure

```text
AuroraEQ/
├── app/
│   ├── src/main/
│   │   ├── AndroidManifest.xml
│   │   ├── java/com/auroraeq/app/
│   │   │   ├── MainActivity.kt
│   │   │   ├── EqApplication.kt
│   │   │   ├── service/GlobalEqService.kt
│   │   │   ├── data/audio and repository layers
│   │   │   └── Compose screens and preset models
│   │   └── res/
│   │       ├── drawable*/
│   │       ├── mipmap*/
│   │       ├── values/
│   │       └── xml/file_paths.xml
│   ├── src/test/java/com/auroraeq/app/
│   │   └── JVM tests for audio, presets, diagnostics, and UI helpers
│   ├── gradle.properties
│   ├── build.gradle.kts
│   └── proguard-rules.pro
├── .github/
│   ├── scripts/
│   └── workflows/
├── docs/
├── gradle/libs.versions.toml
├── scripts/
├── build.gradle.kts
├── settings.gradle.kts
├── package.json
└── README.md
```

### Where changes belong

- **Main UI and entry point:** [[app/src/main/java/com/auroraeq/app/MainActivity.kt]]
- **Application setup:** [[app/src/main/java/com/auroraeq/app/EqApplication.kt]]
- **Audio processing:** [[app/src/main/java/com/auroraeq/app/service/GlobalEqService.kt]]
- **Audio state and persistence:** [[app/src/main/java/com/auroraeq/app/data/repository/EqRepository.kt]],
  [[app/src/main/java/com/auroraeq/app/data/store/ChainStore.kt]], and
  [[app/src/main/java/com/auroraeq/app/data/store/PresetStore.kt]]
- **App log:** [[app/src/main/java/com/auroraeq/app/util/AppLog.kt]]
- **File sharing configuration:** [[app/src/main/res/xml/file_paths.xml]]
- **Release automation:** [[.releaserc.cjs]], [[.github/workflows/release.yml]],
  and [[.github/release-tooling/prepare-release.sh]]

## Architecture decisions

### 1. Foreground audio processing

`GlobalEqService` owns the Android audio effects and keeps the configured signal
chain attached to the shared output audio session. The Compose UI edits the
repository state, while the service observes and applies those changes.

**Why:** Aurora EQ is an audio-processing application, not an accessibility
automation tool, and must not request the template's accessibility permission.

### 2. Local file sharing

The app uses Android's `FileProvider` with explicit `logs/` and `exports/`
paths for user-initiated sharing. It does not expose arbitrary app-private
files.

**Why:** explicit paths keep support exports useful without broadening access to
private application data.

### 3. Keep preset data local and user-controlled

Presets are stored locally and can be explicitly exported or imported as JSON
through Android's file picker. The app does not add a server, account, or sync
layer.

**Why:** audio configuration is useful offline and should not leave the device
without an explicit user export.

### 4. Keep build and release policy declarative

Gradle configuration and dependency versions are Kotlin DSL plus the version
catalog. Formatting, lint, packaging, semantic-release, signing, and release
metadata are configured in the existing build/release files.

**Why:** one executable source of truth prevents project context from drifting
away from the build.

## Working rule for future changes

1. Read the relevant canonical reference above before changing documented
   behavior.
2. Keep audio effect ownership inside the foreground audio-service boundary.
3. Preserve the separation between Compose configuration UI, preset storage,
   file sharing, and audio processing.
4. For source-only changes, run `:app:testDebugUnitTest`; use broader build
   tasks when resources, dependencies, or packaging are affected.
5. Update this file only when stack, structure, or an architectural decision
   changes; link to other Markdown documents instead of copying their content.

## User preferences

No additional user preferences recorded.
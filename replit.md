# NovaBoard - Project Context

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
- **UI:** native Android Views with view binding
- **Application ID:** `com.novaboard.ime`; debug ID is
  `com.novaboard.ime.debug`
- **Input service:** `NovaBoardService` implements Android's input method
  service and owns keyboard state
- **Settings:** `MainActivity` exposes keyboard enable and input-method switch
  actions
- **Updates:** manual APK installation from GitHub Releases
- **Testing:** JVM tests can be added under [[app/src/test/java]]
- **Dependency and plugin source of truth:** [[gradle/libs.versions.toml]]
- **Module/build configuration:** [[settings.gradle.kts]] and
  [[app/build.gradle.kts]]

Use [[README#Requirements|development requirements]] for supported JDK, Android
SDK, and tooling versions. Do not maintain a second version list here.

## Project structure

```text
NovaBoard/
├── app/
│   ├── src/main/
│   │   ├── AndroidManifest.xml
│   │   ├── java/com/novaboard/ime/
│   │   │   ├── NovaBoardService.kt
│   │   │   ├── settings/MainActivity.kt
│   │   │   ├── view/KeyboardView.kt
│   │   │   ├── model/KeyboardModel.kt
│   │   │   └── clipboard, emoji, hotkeys, suggestion, and theme packages
│   │   └── res/
│   │       ├── drawable*/
│   │       ├── layout/
│   │       ├── mipmap*/
│   │       ├── values/
│   │       └── xml/method.xml
│   ├── gradle.properties
│   ├── build.gradle.kts
│   └── proguard-rules.pro
├── .github/
├── docs/
├── gradle/libs.versions.toml
├── scripts/
├── build.gradle.kts
├── settings.gradle.kts
├── package.json
└── README.md
```

### Where changes belong

- **Settings entry point:** [[app/src/main/java/com/novaboard/ime/settings/MainActivity.kt]]
- **Keyboard service:** [[app/src/main/java/com/novaboard/ime/NovaBoardService.kt]]
- **Keyboard rendering:** [[app/src/main/java/com/novaboard/ime/view/KeyboardView.kt]]
- **Keyboard layout:** [[app/src/main/res/layout/keyboard_container.xml]]
- **Release automation:** [[.releaserc.cjs]], [[.github/workflows/release.yml]],
  and [[.github/release-tooling/prepare-release.sh]]

## Architecture decisions

### 1. Android input-method service

`NovaBoardService` owns the IME lifecycle, input connection, keyboard overlays,
clipboard, suggestions, and voice-input actions. Native views keep the keyboard
usable across API 26+ devices without a third-party keyboard SDK.

**Why:** NovaBoard is an input method, so text interaction belongs behind
Android's `BIND_INPUT_METHOD` service boundary rather than accessibility APIs.

### 2. Local clipboard history

Clipboard history is stored locally and supports text, image references,
pinning, and deletion from the keyboard overlay.

**Why:** keyboard content and preferences should not leave the device implicitly.

### 3. Keep build and release policy declarative

Gradle configuration and dependency versions are Kotlin DSL plus the version
catalog. Formatting, lint, packaging, semantic-release, signing, and release
metadata remain configured in the existing build and release files.

**Why:** one executable source of truth prevents project context from drifting
away from the build.

## Working rule for future changes

1. Read the relevant canonical reference above before changing documented
   behavior.
2. Keep text interaction inside the input-method service boundary.
3. Preserve the separation between service lifecycle, view rendering, suggestions,
   clipboard, and settings.
4. For source-only changes, run `:app:testDebugUnitTest`; use broader build
   tasks when resources, dependencies, or packaging are affected.
5. Update this file only when stack, structure, or an architectural decision
   changes; link to other Markdown documents instead of copying their content.

## User preferences

No additional user preferences recorded.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.spotless)
    signing
}

android {
    namespace = "com.auroraeq.app"
    compileSdk = 37

    val releaseRepository =
        providers.gradleProperty("release.repository").orElse("skynight137/aurora-eq").get().trim()
    require(releaseRepository.matches(Regex("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+"))) {
        "release.repository must use the OWNER/REPOSITORY format"
    }
    val releaseChannel =
        if (version.toString().substringBefore('+').contains('-')) "development" else "stable"
    val releaseRepositoryUrl = "https://github.com/$releaseRepository"
    val releasePageUrl = "$releaseRepositoryUrl/releases"

    defaultConfig {
        applicationId = "com.auroraeq.app"
        minSdk = 26
        targetSdk = 37
        versionName = version.toString()
        versionCode = 1
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
        buildConfigField("String", "RELEASE_PAGE_URL", "\"$releasePageUrl\"")
        buildConfigField("String", "RELEASE_CHANNEL", "\"$releaseChannel\"")
        val releaseSigningPublicKey =
            providers.gradleProperty("releaseSigningPublicKeyB64").orElse("").get().trim()
        val releaseSigningKeyFingerprint =
            providers.gradleProperty("releaseSigningKeyFingerprint").orElse("").get().trim()
        buildConfigField("String", "RELEASE_SIGNING_PUBLIC_KEY_B64", "\"$releaseSigningPublicKey\"")
        buildConfigField(
            "String",
            "RELEASE_SIGNING_KEY_FINGERPRINT",
            "\"$releaseSigningKeyFingerprint\"",
        )
    }

    signingConfigs {
        // Private release keystore (app/keystore.jks) — gitignored, never committed.
        // In CI it's written from the base64-encoded KEYSTORE secret (see
        // .github/workflows/release.yml); locally it's generated via keytool.
        create("release") {
            val keystoreFile = file("keystore.jks")
            val keyStorePassword = System.getenv("KEYSTORE_PASSWORD")
            val keyStoreEntryPassword = System.getenv("KEYSTORE_ENTRY_PASSWORD")
            val keyStoreEntryAlias = System.getenv("KEYSTORE_ENTRY_ALIAS")
            if (
                keystoreFile.exists() &&
                    keyStorePassword != null &&
                    keyStoreEntryPassword != null &&
                    keyStoreEntryAlias != null
            ) {
                storeFile = keystoreFile
                storePassword = keyStorePassword
                keyAlias = keyStoreEntryAlias
                keyPassword = keyStoreEntryPassword
            }
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
            enableV4Signing = true
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            buildConfigField("String", "RELEASE_CHANNEL", "\"development\"")
        }
        release {
            if (signingConfigs.getByName("release").storeFile != null) {
                signingConfig = signingConfigs.getByName("release")
            }
            isShrinkResources = true
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_24
        targetCompatibility = JavaVersion.VERSION_24
    }

    testOptions {
        unitTests {
            // compileSdk's android.jar only carries method *signatures* for
            // framework classes (org.json included) — every body throws at
            // runtime unless the real implementation is on the test
            // classpath (see the `org.json:json` test dependency below) or
            // this flag lets untested calls return a default value instead
            // of throwing, e.g. android.util.Log.d() from code paths a pure
            // logic test doesn't exercise directly.
            isReturnDefaultValues = true
            // Robolectric needs real Android resources/manifest on the test
            // classpath to build its simulated Context — without this the
            // Robolectric-based tests below fail to resolve the app's
            // manifest/resources.
            isIncludeAndroidResources = true
        }
    }

    lint {
        abortOnError = true
        disable += "HardwareIds"
        disable += "HardcodedText"
        disable += "LongLogTag"
        disable += "SuspiciousIndentation"
        disable += "NotifyDataSetChanged"
        disable += "TooManyViews"
        disable += "SetTextI18n"
        disable += "ConstantLocale"
        disable += "IconLocation"
        disable += "DefaultLocale"
    }
}

base.archivesName.set(rootProject.name)

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_24
    }
}

spotless {
    kotlin {
        target("**/*.kt")
        targetExclude("**/build/**/*.kt")
        ktfmt().kotlinlangStyle()
    }
    kotlinGradle {
        target("**/*.kts")
        targetExclude("**/build/**/*.kts")
        ktfmt().kotlinlangStyle()
    }
    format("misc") {
        target(
            "*.json",
            ".github/**/*.yml",
            "app/src/**/*.xml",
            "*.md",
            "*.gitignore",
            "*.properties",
            "*.sh",
        )
        targetExclude("package-lock.json", "CHANGELOG.md")
        trimTrailingWhitespace()
        endWithNewline()
    }
}

// Fail loudly instead of silently signing with the debug keystore (or
// producing an unsigned/unreleasable build) when the release keystore or
// secrets are missing — but only when a release variant is actually being
// assembled/bundled. Checking eagerly inside the `release {}` signing config
// above would break every task (including `tasks`, `assembleDebug`, `test`)
// on a fresh checkout that hasn't generated app/keystore.jks yet.
gradle.taskGraph.whenReady {
    val buildingRelease = allTasks.any { it.name.matches(Regex("(?i).*release.*")) }
    if (buildingRelease && android.signingConfigs.getByName("release").storeFile == null) {
        throw GradleException(
            "Release signing is not configured: app/keystore.jks is missing, or " +
                "KEYSTORE_PASSWORD / KEYSTORE_ENTRY_ALIAS / KEYSTORE_ENTRY_PASSWORD are not " +
                "set. Generate a private release keystore (keytool -genkeypair ... -keystore " +
                "app/keystore.jks) and set all three env vars/secrets before building a " +
                "release APK. The release build type must never fall back to signingConfigs.debug."
        )
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.19.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")

    // Compose BOM pins every androidx.compose.* artifact below to versions Google has
    // tested together, replacing individual per-artifact version numbers (which risked
    // version skew if one Compose module was bumped without the others). No
    // com.google.android.material:material (Views library) dependency — nothing in the
    // codebase imports it (this is a Compose-only UI; verified via grep before removal).
    implementation(platform("androidx.compose:compose-bom:2026.06.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation") // HorizontalPager for swipe navigation
    implementation(
        "androidx.compose.material:material-icons-core"
    ) // KeyboardArrowDown for the nav dropdown
    // Remove/RestartAlt aren't in material-icons-core (only ~a dozen glyphs are); extended
    // pack is large but R8 tree-shakes unused icon composables out of release builds.
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // JVM unit tests only (app/src/test) — no device/emulator.
    // `org.json:json` supplies a REAL org.json implementation on the test
    // classpath; without it, PresetStore's JSONArray/JSONObject calls would
    // hit android.jar's stub bodies and throw at runtime under plain `test`.
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20260719")
    // Robolectric simulates the Android framework on the JVM (real
    // SharedPreferences/Context/Toast behavior via shadow classes) so the
    // Toast error-reporting pipeline (see
    // .agents/memory/aurora-eq-error-reporting.md) can be exercised against
    // something closer to a real failure path than a hand-called pure
    // function — still not a substitute for on-device verification, since
    // there's no emulator/device in this environment, but it catches wiring
    // bugs a pure-logic test can't (e.g. a Context/SharedPreferences call
    // that only fails at runtime).
    testImplementation("org.robolectric:robolectric:4.16.1")
    testImplementation("androidx.test:core:1.7.0")
}

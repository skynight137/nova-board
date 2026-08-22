# Releasing an Android app with release-tooling

The repository uses the generic `release-tooling` semantic-release pipeline.
The complete reusable namespace is `.github/release-tooling/`; it is intended
to be copied to other Android clone projects without renaming scripts or
embedding this application's identity. The APK name, keystore subject, and
release description are derived from `rootProject.name`, so each clone keeps
its own identity without changing the shared tooling.

NovaBoard's Android package IDs live in the Android module, and the app's
**Check for updates** action opens its configured GitHub Releases page. Users
choose an APK and install it manually; no release manifest is downloaded or
parsed by the app.
The `dev` branch publishes prereleases, while releases from `main` publish
stable versions:

```text
dev  ──> prerelease
main ──> stable release
```

## Release inputs

The release workflow uses:

- `KEYSTORE_B64` — base64-encoded PKCS12 keystore stored at
  `app/keystore.jks`
- `KEYSTORE_PASSWORD` — Android keystore password
- `KEYSTORE_ENTRY_ALIAS` — alias of the release key inside the keystore
- `KEYSTORE_ENTRY_PASSWORD` — password of the release key entry; for PKCS12,
  it must match `KEYSTORE_PASSWORD`
- `GPG_PRIVATE_KEY_B64` — base64-encoded ASCII-armored private key used to
  sign the APK
- `GPG_PASSPHRASE` — passphrase for the GPG private key
- `GPG_FINGERPRINT` — repository secret used to select the GPG signing key
- `ANDROID_CERTIFICATE_SHA256` — SHA-256 fingerprint of the Android release
  certificate, used to verify the built APK before publication; this is
  required to preserve Android signing-key continuity

Optional clone settings are read from the environment and default to the
repository template layout:

- `ANDROID_MODULE` — Android Gradle module directory; defaults to `app`
- `KEYSTORE_DNAME` — optional certificate subject; when omitted, the keystore
  generator derives `CN` and `O` from `rootProject.name`

The shared scripts expose these validated paths through the
`release_tooling_*` shell namespace. Do not duplicate module, release
directory, keystore, or root-project-name parsing in a clone's own scripts.
Configure the environment values above instead.

For GitHub Actions, set these as optional repository variables with the same
names. If they are omitted, the workflow uses the defaults.

Crowdin uses two additional repository secrets for translation synchronization:

- `CROWDIN_PROJECT_ID`
- `CROWDIN_PERSONAL_TOKEN`

`SESSION_SECRET` is not consumed by the current Android or release workflows. Do
not create or rotate it as part of an Android release unless a future service
starts using it.

Never commit credentials, signing keys, release tokens, or production-only
URLs. Local release signing requires `${ANDROID_MODULE:-app}/keystore.jks`;
debug builds do not require release credentials.

The release workflow validates every keystore and GPG input before decoding or
importing any secret-derived material. It checks both base64 payloads and the
GPG fingerprint format without printing secret values, then verifies that the
decoded keystore is non-empty. A missing or malformed release input fails the
job before any release build or signing step begins.

The app uses a deliberately simple update flow: **Open release page** opens
GitHub Releases, where the user chooses an APK and follows Android's installer
prompts. Release automation publishes the APK and detached signature.

The Android certificate check is intentionally retained even though users
install APKs manually from GitHub Releases. A GitHub download does not prove
that the APK was produced with the intended Android release key. The check
detects an incorrect or replaced keystore before publication, preserving the
ability to install future updates over the existing app. The APK SHA-256
digest and detached GPG signature provide artifact integrity and publisher
verification, respectively, but neither replaces Android's signing identity
check.

The main screen also provides an explicit **Export app log…** action. The
resulting plain-text file contains bounded app-owned events and device/APK
metadata only. It excludes clipboard contents and system-wide logcat, is not
persisted between launches, and is written through Android's Storage Access
Framework after the user chooses a destination.

## Configure release secrets

Configure these values in the GitHub repository that owns the release
workflow. In GitHub, open **Settings > Secrets and variables > Actions**:

- Add `KEYSTORE_B64`, `KEYSTORE_PASSWORD`, `KEYSTORE_ENTRY_ALIAS`,
  `KEYSTORE_ENTRY_PASSWORD`, `GPG_PRIVATE_KEY_B64`, and `GPG_PASSPHRASE` under
  **Repository secrets**.
- Add `GPG_FINGERPRINT` and `ANDROID_CERTIFICATE_SHA256` under **Repository
  secrets**. The workflow uses them to verify the imported GPG key and APK
  certificate.
- Add `CROWDIN_PROJECT_ID` and `CROWDIN_PERSONAL_TOKEN` under **Repository
  secrets** if the Crowdin workflows are enabled.

The following subsections show how to prepare each value. The commands write
secret material to temporary files or send it directly to GitHub; they do not
put private keys or passwords in the repository.

### Android keystore values

Release builds use `${ANDROID_MODULE:-app}/keystore.jks`. Do not replace an
existing keystore: changing it changes the signing identity and prevents
Android from treating future builds as updates to the installed app.

For a new release setup, run this complete command sequence from the repository
root. It generates the keystore without prompts, creates a single-line
`KEYSTORE_B64`, and uploads the keystore values to GitHub:

```bash
source .local/env.sh
export ANDROID_MODULE="${ANDROID_MODULE:-app}"
export KEYSTORE_PASSWORD="$(openssl rand -hex 32)"
export KEYSTORE_ENTRY_ALIAS="android-release"
export KEYSTORE_ENTRY_PASSWORD="${KEYSTORE_PASSWORD}"

bash .github/release-tooling/generate-keystore.sh
base64 -w 0 "${ANDROID_MODULE}/keystore.jks" > /tmp/release-tooling-keystore.b64

gh secret set KEYSTORE_B64 < /tmp/release-tooling-keystore.b64
gh secret set KEYSTORE_PASSWORD --body "${KEYSTORE_PASSWORD}"
gh secret set KEYSTORE_ENTRY_ALIAS --body "${KEYSTORE_ENTRY_ALIAS}"
gh secret set KEYSTORE_ENTRY_PASSWORD --body "${KEYSTORE_ENTRY_PASSWORD}"

rm -f /tmp/release-tooling-keystore.b64
unset KEYSTORE_PASSWORD KEYSTORE_ENTRY_ALIAS KEYSTORE_ENTRY_PASSWORD
```

Keep `${ANDROID_MODULE:-app}/keystore.jks` in the local release environment.
It is ignored by Git and must never be committed.

### Android certificate fingerprint

This setting is required for releases. Do not remove it merely because APKs
are installed manually: Android uses the signing certificate as the app's
update identity, and a release signed with a different certificate cannot
update the existing installation.

`ANDROID_CERTIFICATE_SHA256` must match the certificate in the existing
release keystore. When the keystore and its credentials are available as
Replit Secrets, source the repository environment and run:

```bash
source .local/env.sh
tmp_keystore="$(mktemp)"
trap 'rm -f "$tmp_keystore"' EXIT
printf '%s' "$KEYSTORE_B64" | base64 --decode > "$tmp_keystore"
fingerprint="$(
  keytool -list -v \
    -keystore "$tmp_keystore" \
    -alias "$KEYSTORE_ENTRY_ALIAS" \
    -storepass "$KEYSTORE_PASSWORD" \
    -keypass "${KEYSTORE_ENTRY_PASSWORD:-$KEYSTORE_PASSWORD}" |
    awk -F': ' '/^[[:space:]]*SHA256:/ {print $2; exit}'
)"
gh secret set ANDROID_CERTIFICATE_SHA256 --body "$fingerprint"
unset fingerprint
```

This reads the release keystore locally and sends only its public certificate
fingerprint to GitHub. It does not print or commit the keystore, passwords, or
private signing material. The GitHub CLI must be authenticated to the
repository owner before running the final command.

### GPG signing values

Use one dedicated release signing key. If the key already exists, keep its
fingerprint and passphrase. For a new key, this complete sequence generates the
key, exports it as base64, and uploads all GPG secrets without interactive
prompts:

```bash
export GPG_PASSPHRASE="$(openssl rand -hex 32)"
GPG_PASS_FILE="$(mktemp)"
umask 077
printf '%s' "${GPG_PASSPHRASE}" > "${GPG_PASS_FILE}"

gpg --batch --pinentry-mode loopback \
  --passphrase-file "${GPG_PASS_FILE}" \
  --quick-generate-key \
  "Android Release <release@android.invalid>" rsa4096 sign 2y

export GPG_FINGERPRINT="$(
  gpg --batch --with-colons \
    --list-secret-keys 'release@android.invalid' |
    awk -F: '$1 == "fpr" { print $10; exit }'
)"

gpg --batch --pinentry-mode loopback \
  --passphrase-file "${GPG_PASS_FILE}" \
  --armor --export-secret-keys "${GPG_FINGERPRINT}" |
  base64 -w 0 > /tmp/release-tooling-private.asc.b64

gh secret set GPG_PRIVATE_KEY_B64 < /tmp/release-tooling-private.asc.b64
gh secret set GPG_PASSPHRASE --body "${GPG_PASSPHRASE}"
gh secret set GPG_FINGERPRINT --body "${GPG_FINGERPRINT}"

rm -f "${GPG_PASS_FILE}" /tmp/release-tooling-private.asc.b64
unset GPG_PASS_FILE GPG_PASSPHRASE GPG_FINGERPRINT
```

The generated private key remains in the local GPG keyring. Never commit or
print the passphrase, fingerprint, or exported key material.

### Crowdin values

Create or obtain the project ID and personal API token in Crowdin, then store
them as the `CROWDIN_PROJECT_ID` and `CROWDIN_PERSONAL_TOKEN` repository
secrets. These values are used only by `.github/workflows/crowdin_push.yml`
and `.github/workflows/crowdin_pull.yml`; they are not required to build or
sign an APK.

After uploading, verify only that the secret names exist in GitHub Actions
settings. Never echo or paste their values into issues, logs, chat, or
committed files.

## APK size and inspection

The release build enables R8 minification and resource shrinking in
`app/build.gradle.kts`. Measure the current artifact after each dependency or
resource change instead of carrying forward a size claim from the source
template:

```bash
source .local/env.sh
./gradlew :app:assembleRelease
stat -c '%s bytes' "${ANDROID_MODULE:-app}/build/outputs/apk/release/<ProjectName>-release.apk"
unzip -lv "${ANDROID_MODULE:-app}/build/outputs/apk/release/<ProjectName>-release.apk" \
  | grep -E 'lib/.+\\.so|classes\\.dex|resources\\.arsc'
```

Do not remove the input-method service or keyboard view dependencies merely to
reduce the APK. Those components provide NovaBoard's core keyboard behavior.

## Release process

Use Conventional Commits. `feat:` creates a minor release, `fix:` creates a
patch release, and breaking-change markers create a major release. Docs,
style, test, and chore commits do not create a release unless the release
configuration explicitly says otherwise. Keep the Conventional Commits preset
on the 9.x line while `@semantic-release/release-notes-generator` remains at
14.1.0, because that generator version is only compatible with the 9.x preset
line used by this repository.

Semantic-release:

1. Determines the next version from commits since the last release.
2. Updates `${ANDROID_MODULE:-app}/gradle.properties` and `CHANGELOG.md`.
3. Runs `.github/release-tooling/prepare-release.sh`.
4. Builds and signs `<ProjectName>-<version>.apk`.
5. Creates the matching Git tag and GitHub release.
Release preparation fails closed unless the build output contains exactly one
universal `<ProjectName>-release.apk`. The APK and detached signature are staged
in temporary files; final paths are replaced only after digest, size, signature,
and certificate checks succeed. A failed preparation restores the previous
`${ANDROID_MODULE:-app}/gradle.properties` version.

Version codes are derived from the semantic version and prerelease number by the
Android module build configuration.

Do not hand-edit the release version, push a `v*` tag manually, or rerun an
old tag to validate a workflow change. A tag checks out the exact commit that
created it.

The canonical GitHub repository for the release page is
`release.repository` in the root `gradle.properties` file. Release preparation
fails before building if that setting does not match the repository selected by
`GITHUB_REPOSITORY` or `origin`, preventing a renamed or forked checkout from
publishing to an unintended origin.

The release page URL is derived from `release.repository`, so changing the
artifact repository requires updating that property as well.

## Reusing the template in another clone

Copy `.github/release-tooling/`, `.github/workflows/release.yml`,
`.releaserc.cjs`, `package.json`, and `package-lock.json` into the clone. The
clone must provide:

```kotlin
// settings.gradle.kts
rootProject.name = "TemplateApp"
```

and an Android module with its own `gradle.properties` version file. The
`rootProject.name` declaration may include a trailing Kotlin `//` comment; the
validated name is used for the APK filename and generated keystore subject.
generic artifact and keystore tests include a `mobile` module fixture and an
app-specific `release-metadata.json` result, so the shared scripts can be
exercised without changing their names:

```bash
bash .github/release-tooling/test-release-artifacts.sh
bash .github/release-tooling/test-release-keystore.sh
```

The clone should replace the app-owned Android package ID and repository default
where its application configuration requires it.
No shared release script should contain the original clone's name.

## Local verification

The protected release workflow runs for pushes or manual dispatches on `dev`
(prereleases) and `main` (stable releases). It uses a single concurrency group,
so a manual dispatch waits for an active publication rather than racing it.

Run the repository-managed toolchain before opening a release pull request:

```bash
source .local/env.sh
./gradlew :"${ANDROID_MODULE:-app}":testDebugUnitTest \
  :"${ANDROID_MODULE:-app}":compileDebugAndroidTestKotlin \
  :"${ANDROID_MODULE:-app}":assembleDebug
npm ci --include=dev
node -e "const config = require('./.releaserc.cjs'); if (!Array.isArray(config.plugins)) process.exit(1); console.log('semantic-release config valid')"
bash -n scripts/setup.sh .github/release-tooling/*.sh
bash .github/release-tooling/test-github-repository.sh
bash .github/release-tooling/test-release-workflow.sh
bash .github/release-tooling/test-release-artifacts.sh
```

To validate release planning without publishing or changing the repository:

```bash
npx semantic-release --dry-run --no-ci
```

The full dry-run path still requires the CI-provided GitHub authentication
available in the release workflow. Locally, the generator probe can verify
changelog rendering and release-note output without publishing anything or
depending on authenticated release access.

For a real signed preparation test, configure the release secrets in the
environment, source `.local/env.sh`, and run:

```bash
bash .github/release-tooling/prepare-release.sh 0.1.1
test -s "${ANDROID_MODULE:-app}/build/outputs/apk/release/<ProjectName>-0.1.1.apk"
test -s "${ANDROID_MODULE:-app}/build/outputs/apk/release/<ProjectName>-0.1.1.apk.asc"
```

If this is only a throwaway test, restore the generated version afterward:

```bash
git restore "${ANDROID_MODULE:-app}/gradle.properties"
```
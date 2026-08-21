#!/usr/bin/env bash

set -euo pipefail

# ── Version config ────────────────────────────────────────────────────────────
JAVA_MAJOR="24"
CMDLINE_TOOLS_URL="https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"

# ── Paths ─────────────────────────────────────────────────────────────────────
WORKSPACE="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$WORKSPACE"
BIN="$WORKSPACE/.bin"
SDK="$BIN/android-sdk"
JAVA_HOME_DIR="$BIN/java"
GRADLE_USER_HOME="$WORKSPACE/.gradle"   # Gradle cache dir — binary comes from gradlew
ENV_FILE="$BIN/env.sh"
SETUP_MAX_PARALLEL="${SETUP_MAX_PARALLEL:-4}"
SKILL_ROOT="$WORKSPACE/.agents/skills"
REPLIT_SKILL_SOURCE="$WORKSPACE/.local/skills"
REPLIT_SECONDARY_SKILL_SOURCE="$WORKSPACE/.local/secondary_skills"
SKILL_REPOS=(
#  "blader/humanizer"
#  "JuliusBrussee/caveman"
#  "forrestchang/andrej-karpathy-skills"
  "nextlevelbuilder/ui-ux-pro-max-skill"
#  "obra/superpowers"
  "addyosmani/agent-skills"
  "affaan-m/everything-claude-code"
#  "remotion-dev/skills"
#  "skynight137/agent-skills"
#  "spillwavesolutions/design-doc-mermaid" # "cathrynlavery/diagram-design"
#  "github/awesome-copilot"
#  "coreyhaines31/marketingskills"
)

# ── Helpers ───────────────────────────────────────────────────────────────────
COL_GREEN="\033[0;32m"
COL_YELLOW="\033[1;33m"
COL_RED="\033[0;31m"
COL_RESET="\033[0m"
COL_BOLD="\033[1m"

step() { echo -e "\n${COL_BOLD}▶ $*${COL_RESET}"; }
ok()   { echo -e "  ${COL_GREEN}✓${COL_RESET} $*"; }
skip() { echo -e "  ${COL_YELLOW}→ skip:${COL_RESET} $* (already installed)"; }
warn() { echo -e "  ${COL_YELLOW}⚠${COL_RESET}  $*"; }
die()  { echo -e "\n  ${COL_RED}✗ ERROR:${COL_RESET} $*\n"; exit 1; }

need_cmd() { command -v "$1" &>/dev/null || die "'$1' not found — required to continue"; }

wait_for_jobs() {
  local context="$1"
  shift
  local failed=0
  local pid

  # Wait for every job so one failure does not leave sibling downloads running.
  for pid in "$@"; do
    if ! wait "$pid"; then
      failed=1
    fi
  done

  (( failed == 0 )) || die "$context failed"
}

assert_non_empty_tree() {
  local root="$1"
  local label="$2"

  [[ -n "$(find "$root" -type f -print -quit)" ]] \
    || die "$label produced no files: $root"
}

rewrite_replit_skill_references() {
  local root="$1"
  local file

  while IFS= read -r -d '' file; do
    sed -i \
      -e 's#\.local/skills#\.agents/skills/replit/skills#g' \
      -e 's#\.local/secondary_skills#\.agents/skills/replit/secondary_skills#g' \
      "$file"
  done < <(
    rg -l -0 --hidden '\.local/(skills|secondary_skills)' "$root" 2>/dev/null || true
  )
}

install_skill_repo() {
  local repository="$1"
  local owner="${repository%%/*}"
  local stage="$SKILL_STAGE/$owner"

  step "Installing skills from $repository"
  mkdir -p "$stage"
  printf '{"name":"auto-click-skill-stage","private":true}\n' > "$stage/package.json"
  (
    cd "$stage"
    npx -y skills add "$repository" --agent codex --skill '*' --yes --copy
  )

  [[ -d "$stage/.agents/skills" ]] \
    || die "Skill installer produced no .agents/skills directory for $repository"
  assert_non_empty_tree "$stage/.agents/skills" "$repository installer"

  mkdir -p "$SKILL_ROOT/$owner"
  rsync -a --delete "$stage/.agents/skills/" "$SKILL_ROOT/$owner/"
  assert_non_empty_tree "$SKILL_ROOT/$owner" "$repository synchronization"
  ok "Synchronized $repository → .agents/skills/$owner"
}

sync_replit_skill_tree() {
  local source="$1"
  local destination="$2"

  [[ -d "$source" ]] || die "Required Replit skill source is missing: $source"
  assert_non_empty_tree "$source" "Replit skill source"
  mkdir -p "$destination"
  rsync -a --delete "$source/" "$destination/"
  assert_non_empty_tree "$destination" "Replit skill synchronization"
  rewrite_replit_skill_references "$destination"
  ok "Synchronized $source → $destination"
}

install_skills() {
  local repository

  need_cmd npx
  need_cmd rsync
  need_cmd rg
  mkdir -p "$SKILL_ROOT"
  SKILL_STAGE="$(mktemp -d "${TMPDIR:-/tmp}/auto-click-skills.XXXXXX")"
  trap 'rm -rf -- "${SKILL_STAGE:-}"' EXIT

  [[ "$SETUP_MAX_PARALLEL" =~ ^[1-9][0-9]*$ ]] \
    || die "SETUP_MAX_PARALLEL must be a positive integer"

  local -a pids=()
  for repository in "${SKILL_REPOS[@]}"; do
    install_skill_repo "$repository" &
    pids+=("$!")

    # Keep concurrent npx processes bounded so setup is faster without
    # overwhelming the workspace or npm cache.
    if ((${#pids[@]} >= SETUP_MAX_PARALLEL)); then
      wait_for_jobs "Parallel skill installation" "${pids[@]}"
      pids=()
    fi
  done

  ((${#pids[@]} == 0)) || wait_for_jobs "Parallel skill installation" "${pids[@]}"

#  sync_replit_skill_tree \
#    "$REPLIT_SKILL_SOURCE" \
#    "$SKILL_ROOT/replit/skills"
#  sync_replit_skill_tree \
#    "$REPLIT_SECONDARY_SKILL_SOURCE" \
#    "$SKILL_ROOT/replit/secondary_skills"
}

sdkmgr() {
  ANDROID_HOME="$SDK" \
  JAVA_HOME="$JAVA_HOME_DIR" \
  JAVA_TOOL_OPTIONS="-XX:-UsePerfData" \
  "$SDK/cmdline-tools/bin/sdkmanager" \
    --sdk_root="$SDK" "$@"
}

accept_licenses() {
  # Pre-write known Android SDK license hashes
  mkdir -p "$SDK/licenses"
  printf "\n8933bad161af4178b1185d1a37fbf41ea5269c55\n"          > "$SDK/licenses/android-sdk-license"
  printf "\n84831b9409646a918e30573bab4c9c91346d8abd\n"          >> "$SDK/licenses/android-sdk-license"
  printf "\nd56f5187479451eabf01fb78af6dfcb131a6481e\n"          >> "$SDK/licenses/android-sdk-license"
  printf "\n601085b94cd77f0b54ff86406957099ebe79c4d7\n"          >> "$SDK/licenses/android-sdk-license"
  printf "\n33b6a2b64607f11b759f320ef9dff4ae5c47d97a\n"           > "$SDK/licenses/google-gdk-license"
  printf "\nd56f5187479451eabf01fb78af6dfcb131a6481e\n"           > "$SDK/licenses/android-googletv-license"
  printf "\n601085b94cd77f0b54ff86406957099ebe79c4d7\n"           > "$SDK/licenses/android-sdk-preview-license"
  ok "SDK license files written"

  # Also formally accept via sdkmanager in case hashes differ for this SDK version
  echo "  Running sdkmanager --licenses (auto-accepting all)..."
  yes | ANDROID_HOME="$SDK" \
        JAVA_HOME="$JAVA_HOME_DIR" \
        JAVA_TOOL_OPTIONS="-XX:-UsePerfData" \
        "$SDK/cmdline-tools/bin/sdkmanager" \
          --sdk_root="$SDK" --licenses 2>&1 \
        | grep -v "^$" | grep -v "^-" | grep -v "^Terms" | tail -5 || true
  ok "Licenses accepted"
}

install_cmdline_tools() {
  if [[ -x "$SDK/cmdline-tools/bin/sdkmanager" ]]; then
    skip "cmdline-tools"
    return
  fi

  TMP_ZIP="$BIN/_cmdline-tools.zip"
  TMP_DIR="$BIN/_cmdline-tools-extract"
  echo "  Downloading cmdline-tools..."
  wget -q --show-progress -O "$TMP_ZIP" "$CMDLINE_TOOLS_URL" \
    || die "cmdline-tools download failed"
  echo "  Extracting cmdline-tools..."
  mkdir -p "$TMP_DIR"
  unzip -q "$TMP_ZIP" -d "$TMP_DIR"
  # zip unpacks to cmdline-tools/ — move into SDK root
  mv "$TMP_DIR/cmdline-tools" "$SDK/cmdline-tools"
  rm -rf "$TMP_DIR" "$TMP_ZIP"
  chmod +x "$SDK/cmdline-tools/bin/sdkmanager"
  ok "cmdline-tools installed → $SDK/cmdline-tools"
}

install_java() {
  if [[ -x "$JAVA_HOME_DIR/bin/java" ]]; then
    INSTALLED_JAVA=$("$JAVA_HOME_DIR/bin/java" -version 2>&1 | head -1)
    skip "Java — $INSTALLED_JAVA"
    return
  fi

  TMP_TGZ="$BIN/_jdk24.tar.gz"
  TMP_DIR="$BIN/_jdk24-extract"

  echo "  Downloading Eclipse Temurin JDK $JAVA_MAJOR (latest GA)..."

  # Follow redirects — Adoptium API returns the actual latest GA release
  wget -q --show-progress -L \
    --header="Accept: application/octet-stream" \
    "https://api.adoptium.net/v3/binary/latest/${JAVA_MAJOR}/ga/linux/x64/jdk/hotspot/normal/eclipse" \
    -O "$TMP_TGZ" \
    || die "JDK $JAVA_MAJOR download failed. Check https://adoptium.net for manual download."

  echo "  Extracting Java..."
  mkdir -p "$TMP_DIR"
  tar -xzf "$TMP_TGZ" -C "$TMP_DIR"

  # Temurin extracts to jdk-24.x.x+y/ — move contents into .bin/java/
  EXTRACTED=$(ls "$TMP_DIR")
  [[ -n "$EXTRACTED" ]] || die "JDK extraction produced empty directory"
  mv "$TMP_DIR/$EXTRACTED"/* "$JAVA_HOME_DIR/"
  rm -rf "$TMP_DIR" "$TMP_TGZ"

  JAVA_VER=$("$JAVA_HOME_DIR/bin/java" -version 2>&1 | head -1)
  ok "Java installed → $JAVA_HOME_DIR  ($JAVA_VER)"
}

# ── 0. Preflight ──────────────────────────────────────────────────────────────
step "Preflight"

need_cmd wget
need_cmd unzip
need_cmd tar
need_cmd curl
install_skills

AVAIL_GB=$(df --output=avail -BG "$WORKSPACE" | tail -1 | tr -d 'G' | xargs)
echo "  Workspace disk: ${AVAIL_GB} GB available  (need ~5 GB)"
[[ "$AVAIL_GB" -ge 5 ]] || die "Need at least 5 GB free on $WORKSPACE. Currently ${AVAIL_GB} GB."

ok "Preflight passed"

# ── 2. Directories ───────────────────────────────────────────────────────────
step "Creating .bin directory tree"
mkdir -p "$SDK" "$JAVA_HOME_DIR" "$GRADLE_USER_HOME"
ok "Created: $BIN/{android-sdk, java, gradle-home}"

# ── 3–4. Android cmdline-tools and Java — independent downloads ──────────────
step "Android cmdline-tools and Java prerequisites"
install_cmdline_tools &
cmdline_tools_pid=$!
install_java &
java_pid=$!
wait_for_jobs "Android prerequisite installation" "$cmdline_tools_pid" "$java_pid"

# ── 5. SDK licenses ───────────────────────────────────────────────────────────
step "SDK licenses"
accept_licenses
ok "Licenses accepted"

# ── 6. SDK packages via sdkmanager ────────────────────────────────────────────
step "Android SDK packages"
install_sdk_pkg() {
  local pkg="$1"
  local check_path="$2"   # relative to SDK root — used to detect existing install
  local label="$3"

  if [[ -e "$SDK/$check_path" ]]; then
    skip "$label"
    return
  fi
  echo "  Installing $label..."
  # sdkmanager reads licenses from $SDK/licenses which we already wrote
  ANDROID_HOME="$SDK" \
  JAVA_HOME="$JAVA_HOME_DIR" \
  JAVA_TOOL_OPTIONS="-XX:-UsePerfData" \
    "$SDK/cmdline-tools/bin/sdkmanager" \
    --sdk_root="$SDK" \
    "$pkg" 2>&1 \
    | grep -Ev "^$|^\[=|Preparing|Unzipping|Warning: File|^Done" \
    | tail -5 || true
  ok "$label installed"
}

install_sdk_pkg "platform-tools"      "platform-tools/adb"              "platform-tools"

# ── 7. Write env.sh ───────────────────────────────────────────────────────────
step "Writing env file"

cat > "$ENV_FILE" <<EOF
export JAVA_HOME="$JAVA_HOME_DIR"
export ANDROID_HOME="$SDK"

# Keep Gradle caches on the workspace disk
export GRADLE_USER_HOME="$GRADLE_USER_HOME"

export JAVA_TOOL_OPTIONS="-XX:-UsePerfData --enable-native-access=ALL-UNNAMED"

export PATH="\$JAVA_HOME/bin:\$ANDROID_HOME/platform-tools:\$ANDROID_HOME/cmdline-tools/bin:\$PATH"
EOF

ok "env.sh written → $ENV_FILE"

# ── 8. Write local.properties ─────────────────────────────────────────────────
step "Writing local.properties"

cat > "$WORKSPACE/local.properties" <<EOF
sdk.dir=$SDK
EOF

ok "local.properties written → local.properties"

# ── 9. Verify everything ───────────────────────────────────────────────────────
step "Verification"

source "$ENV_FILE"

java_ok=false; sdk_ok=false

"java" -version &>/dev/null    && java_ok=true
"adb" --version &>/dev/null    && sdk_ok=true

$java_ok && ok "Java $JAVA_MAJOR:  $(java -version 2>&1 | head -1)" \
         || warn "Java $JAVA_MAJOR — verification failed"
$sdk_ok  && ok "Android SDK: $ANDROID_HOME" \
         || warn "Android SDK — adb not found"

USED=$(du -sh "$BIN" | cut -f1)
echo ""
echo "  Total size: $USED"

# ── 10. Summary ────────────────────────────────────────────────────────────────
cat <<SUMMARY


━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  Setup complete!  All tools are in $BIN
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

  What this script installs
  ─────────────────────────
  Java:          Eclipse Temurin JDK $JAVA_MAJOR
  cmdline-tools: sdkmanager + SDK license acceptance
  platform-tools: adb, fastboot, etc.

  Directory layout
  ────────────────
  .bin/
  ├── android-sdk/          ANDROID_HOME
  │   ├── cmdline-tools/    sdkmanager + license acceptance
  │   ├── platform-tools/
  │   (ndk/, cmake/, build-tools/, platforms/ auto-populated by AGP on first build)
  ├── java/                 JAVA_HOME  (JDK $JAVA_MAJOR)
  └── .gradle/              GRADLE_USER_HOME (Gradle cache)

  To use in any shell or script
  ─────────────────────────────
  source $ENV_FILE

  To generate or replace the release keystore
  (ANDROID_MODULE/keystore.jks; defaults to app/keystore.jks)
  ─────────────────────────────
  Requires the KEYSTORE_PASSWORD, KEYSTORE_ENTRY_ALIAS, and
  KEYSTORE_ENTRY_PASSWORD secrets to already be set (see environment
  secrets). The script deletes any existing keystore before creating a new one:

  bash .github/release-tooling/generate-keystore.sh

  For CI (.github/workflows/release.yml), also add a GitHub Actions repository
  secret named KEYSTORE_B64 containing \`base64 -w0 "\${ANDROID_MODULE:-app}/keystore.jks"\` — see
  docs/releasing.md.


━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
SUMMARY
#!/usr/bin/env bash

set -euo pipefail

# ── Version config ────────────────────────────────────────────────────────────
JAVA_MAJOR="24"
CMDLINE_TOOLS_URL="https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"

# ── Paths ─────────────────────────────────────────────────────────────────────
WORKSPACE="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$WORKSPACE"
TOOLSCHAIN="$WORKSPACE/.local"
SDK="$TOOLSCHAIN/android-sdk"
JAVA_HOME_DIR="$TOOLSCHAIN/java"
GRADLE_USER_HOME="$WORKSPACE/.gradle"   # Gradle cache dir — binary comes from gradlew
ENV_FILE="$TOOLSCHAIN/env.sh"
SKILL_ROOT="$WORKSPACE/.agents/skills"
INSTALL_ANDROID_TOOLS=false
INSTALL_OPENCODE=false
SKILL_REPOS=()

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

usage() {
  cat <<'USAGE'
Usage: bash scripts/setup.sh [options]

Install selected local development tools. Options can be combined.

Options:
  --android-tools       Install Java 24 and the Android SDK build toolchain
  --skills <repositories>
                        Install one or more skill repositories into
                        .agents/skills/<owner>/<skill-name>/
                        Stop the list before the next option.
  --oc                  Install OpenCode into ~/.opencode/bin
  -h, --help            Show this help

Examples:
  bash scripts/setup.sh --android-tools
  bash scripts/setup.sh --skills mattpocock-skills coreyhaines31/marketingskills
  bash scripts/setup.sh --oc

After installing Android tools, load the generated environment:
  source /home/runner/workspace/.local/env.sh

For OpenCode, ensure its local binary directory is on PATH:
  PATH=/home/runner/.opencode/bin:$PATH
USAGE
}

parse_args() {
  while (($# > 0)); do
    case "$1" in
      --android-tools)
        INSTALL_ANDROID_TOOLS=true
        ;;
      --oc)
        INSTALL_OPENCODE=true
        ;;
      --skills)
        shift
        (($# > 0)) || die "--skills requires at least one repository"
        local found_skill=false
        while (($# > 0)) && [[ "$1" != --* ]]; do
          SKILL_REPOS+=("$1")
          found_skill=true
          shift
        done
        $found_skill || die "--skills requires at least one repository"
        continue
        ;;
      -h|--help)
        usage
        exit 0
        ;;
      *)
        die "Unknown option '$1'. Use --help for usage."
        ;;
    esac
    shift
  done

  [[ "$INSTALL_ANDROID_TOOLS" == true ||
     "$INSTALL_OPENCODE" == true ||
     ${#SKILL_REPOS[@]} -gt 0 ]] || {
    usage
    exit 0
  }
}

assert_non_empty_tree() {
  local root="$1"
  local label="$2"

  [[ -n "$(find "$root" -type f -print -quit)" ]] \
    || die "$label produced no files: $root"
}

install_skill_repo() {
  local repository="$1"
  local source="$repository"
  if [[ "$source" == "mattpocock-skills" ]]; then
    source="mattpocock/skills"
  fi
  local owner="${source%%/*}"
  local stage="$SKILL_STAGE/$owner"

  step "Installing skills from $source"
  mkdir -p "$stage"
  printf '{"name":"auto-click-skill-stage","private":true}\n' > "$stage/package.json"
  (
    cd "$stage"
    npx -y skills add "$source" --agent codex --skill '*' --yes --copy
  )

  [[ -d "$stage/.agents/skills" ]] \
    || die "Skill installer produced no .agents/skills directory for $source"
  assert_non_empty_tree "$stage/.agents/skills" "$source installer"

  mkdir -p "$SKILL_ROOT/$owner"
  rsync -a --delete "$stage/.agents/skills/" "$SKILL_ROOT/$owner/"
  assert_non_empty_tree "$SKILL_ROOT/$owner" "$source synchronization"
  ok "Synchronized $source → .agents/skills/$owner"
}

install_skills() {
  local repository
  ((${#SKILL_REPOS[@]} > 0)) || return 0

  need_cmd npx
  need_cmd rsync
  mkdir -p "$SKILL_ROOT"
  SKILL_STAGE="$(mktemp -d "${TMPDIR:-/tmp}/auto-click-skills.XXXXXX")"
  trap 'rm -rf -- "${SKILL_STAGE:-}"' EXIT

  for repository in "${SKILL_REPOS[@]}"; do
    install_skill_repo "$repository"
  done
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

  TMP_ZIP="$TOOLSCHAIN/_cmdline-tools.zip"
  TMP_DIR="$TOOLSCHAIN/_cmdline-tools-extract"
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

  TMP_TGZ="$TOOLSCHAIN/_jdk24.tar.gz"
  TMP_DIR="$TOOLSCHAIN/_jdk24-extract"

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

  # Temurin extracts to jdk-24.x.x+y/ — move contents into .local/java/
  EXTRACTED=$(ls "$TMP_DIR")
  [[ -n "$EXTRACTED" ]] || die "JDK extraction produced empty directory"
  mv "$TMP_DIR/$EXTRACTED"/* "$JAVA_HOME_DIR/"
  rm -rf "$TMP_DIR" "$TMP_TGZ"

  JAVA_VER=$("$JAVA_HOME_DIR/bin/java" -version 2>&1 | head -1)
  ok "Java installed → $JAVA_HOME_DIR  ($JAVA_VER)"
}

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
  local log_file
  log_file="$(mktemp)"
  if ! ANDROID_HOME="$SDK" \
    JAVA_HOME="$JAVA_HOME_DIR" \
    JAVA_TOOL_OPTIONS="-XX:-UsePerfData" \
      "$SDK/cmdline-tools/bin/sdkmanager" \
      --sdk_root="$SDK" "$pkg" >"$log_file" 2>&1; then
    cat "$log_file" >&2
    rm -f "$log_file"
    die "Android SDK package installation failed: $pkg"
  fi
  grep -Ev "^$|^\[=|Preparing|Unzipping|Warning: File|^Done" "$log_file" |
    tail -5 || true
  rm -f "$log_file"
  ok "$label installed"
}

write_env_file() {
  mkdir -p "$TOOLSCHAIN"
  cat > "$ENV_FILE" <<EOF
export JAVA_HOME="$JAVA_HOME_DIR"
export ANDROID_HOME="$SDK"

# Keep Gradle caches on the workspace disk
export GRADLE_USER_HOME="$GRADLE_USER_HOME"

export JAVA_TOOL_OPTIONS="-XX:-UsePerfData --enable-native-access=ALL-UNNAMED"

export PATH="\$JAVA_HOME/bin:\$ANDROID_HOME/platform-tools:\$ANDROID_HOME/cmdline-tools/bin:/home/runner/.opencode/bin:\$PATH"
EOF
}

install_android_tools() {
  step "Android toolchain preflight"
  need_cmd wget
  need_cmd unzip
  need_cmd tar
  need_cmd curl
  AVAIL_GB=$(df --output=avail -BG "$WORKSPACE" | tail -1 | tr -d 'G' | xargs)
  echo "  Workspace disk: ${AVAIL_GB} GB available (need ~5 GB)"
  [[ "$AVAIL_GB" -ge 5 ]] || die "Need at least 5 GB free on $WORKSPACE. Currently ${AVAIL_GB} GB."

  step "Creating .local directory tree"
  mkdir -p "$SDK" "$JAVA_HOME_DIR" "$GRADLE_USER_HOME"
  ok "Created: $TOOLSCHAIN/{android-sdk, java, gradle-home}"

  step "Android cmdline-tools and Java prerequisites"
  install_cmdline_tools &
  cmdline_tools_pid=$!
  install_java &
  java_pid=$!
  wait_for_jobs "Android prerequisite installation" "$cmdline_tools_pid" "$java_pid"

  step "SDK licenses"
  accept_licenses

  step "Android SDK packages"
  install_sdk_pkg "platform-tools" "platform-tools/adb" "platform-tools"
  install_sdk_pkg "platforms;android-37.0" "platforms/android-37.0/android.jar" "Android platform 37"
  install_sdk_pkg "build-tools;37.0.0" "build-tools/37.0.0/aapt2" "Android build tools 37.0.0"

  step "Writing environment files"
  write_env_file
  ok "env.sh written → $ENV_FILE"
  cat > "$WORKSPACE/local.properties" <<EOF
sdk.dir=$SDK
EOF
  ok "local.properties written → local.properties"

  step "Verification"
  source "$ENV_FILE"
  java_ok=false; sdk_ok=false
  "java" -version &>/dev/null && java_ok=true
  "adb" --version &>/dev/null && sdk_ok=true
  $java_ok && ok "Java $JAVA_MAJOR: $(java -version 2>&1 | head -1)" ||
    warn "Java $JAVA_MAJOR — verification failed"
  $sdk_ok && ok "Android SDK: $ANDROID_HOME" || warn "Android SDK — adb not found"
}

install_opencode() {
  step "Installing OpenCode"
  need_cmd curl
  PATH="/home/runner/.opencode/bin:$PATH" curl -fsSL https://opencode.ai/install | bash
  [[ -x "/home/runner/.opencode/bin/opencode" ]] ||
    die "OpenCode installation did not create /home/runner/.opencode/bin/opencode"
  ok "OpenCode installed → /home/runner/.opencode/bin/opencode"
}

main() {
  parse_args "$@"
  [[ "$INSTALL_ANDROID_TOOLS" == true ]] && install_android_tools
  install_skills
  [[ "$INSTALL_OPENCODE" == true ]] && install_opencode

  if [[ "$INSTALL_ANDROID_TOOLS" == true ]]; then
    USED=$(du -sh "$TOOLSCHAIN" | cut -f1)
    echo ""
    echo "  Android toolchain size: $USED"
  fi

  # ── Summary ──────────────────────────────────────────────────────────────────
cat <<SUMMARY


━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  Setup complete!
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

  Selected options completed.
  Android tools: source $ENV_FILE
  OpenCode:      export PATH=/home/runner/.opencode/bin:\$PATH

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
}

bash -n "$0"
main "$@"
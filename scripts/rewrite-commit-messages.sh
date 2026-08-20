#!/usr/bin/env bash
# Rewrite repository history into Conventional Commit messages.
#
# Preview:
#   bash scripts/rewrite-commit-messages.sh
# Apply:
#   bash scripts/rewrite-commit-messages.sh --apply
#
# Applying changes commit IDs. Inspect the rewritten log before any
# force-with-lease push.

set -euo pipefail

APPLY=false
case "${1:-}" in
  "") ;;
  --apply) APPLY=true ;;
  -h|--help)
    sed -n '2,14p' "$0"
    exit 0
    ;;
  *)
    echo "Usage: $0 [--apply]" >&2
    exit 2
    ;;
esac

if ! git diff --quiet || ! git diff --cached --quiet; then
  echo "Refusing to rewrite history with uncommitted changes." >&2
  exit 1
fi

branch="$(git branch --show-current)"
if [[ "$branch" != "main" && "$branch" != "dev" ]]; then
  echo "Refusing to rewrite branch '$branch'; check out main or dev first." >&2
  exit 1
fi

normalize_subject() {
  local subject="$1"
  case "$subject" in
    feat:*|fix:*|docs:*|style:*|refactor:*|perf:*|test:*|build:*|ci:*|chore:*|revert:*)
      printf '%s\n' "$subject"
      ;;
    "Initial commit")
      printf '%s\n' "chore: initialize project"
      ;;
    "Bump "*)
      printf '%s\n' "build: update release tooling dependencies"
      ;;
    "Merge "*)
      printf '%s\n' "chore: merge dependency update"
      ;;
    "Remove project documentation and unused source files")
      printf '%s\n' "chore: remove obsolete documentation and sources"
      ;;
    "Update main activity and keyboard settings with new UI configurations")
      printf '%s\n' "feat: improve keyboard preference settings"
      ;;
    "Implement keyboard preference settings and integrate with main activity")
      printf '%s\n' "feat: integrate keyboard preference settings"
      ;;
    "Implement keyboard service logic and update activity UI components")
      printf '%s\n' "feat: wire keyboard preference behavior"
      ;;
    "Add keyboard layout configuration and preferences update functionality")
      printf '%s\n' "feat: add keyboard layout preferences"
      ;;
    "Refactor keyboard service and view components")
      printf '%s\n' "refactor: simplify keyboard service and view components"
      ;;
    "Add logic to commit message rewrite script")
      printf '%s\n' "chore: complete commit message mappings"
      ;;
    "Implement suggestion engine logic and integrate with keyboard service")
      printf '%s\n' "feat: add suggestion engine integration"
      ;;
    "Update keyboard service logic in NovaBoardService")
      printf '%s\n' "fix: correct keyboard service behavior"
      ;;
    "fix commit style")
      printf '%s\n' "chore: normalize commit message style"
      ;;
    "Refactor emoji panel and add tests for emoji data")
      printf '%s\n' "test: cover emoji search behavior"
      ;;
    *)
      echo "ERROR: no Conventional Commit mapping for '$subject'" >&2
      return 1
      ;;
  esac
}

echo "Commit messages that will be used:"
git log --reverse --format='%H%x09%s' |
  while IFS=$'\t' read -r commit subject; do
    printf '%s\t' "${commit:0:7}"
    normalize_subject "$subject"
  done

if [[ "$APPLY" != true ]]; then
  echo
  echo "Preview only. Run with --apply to rewrite all commits."
  exit 0
fi

echo
echo "Rewriting all commits on $branch..."

git filter-branch -f --msg-filter '
subject="$(git log -1 --format=%s "$GIT_COMMIT")"
case "$subject" in
  feat:*|fix:*|docs:*|style:*|refactor:*|perf:*|test:*|build:*|ci:*|chore:*|revert:*) printf "%s\n" "$subject" ;;
  "Initial commit") echo "chore: initialize project" ;;
  "Bump "*) echo "build: update release tooling dependencies" ;;
  "Merge "*) echo "chore: merge dependency update" ;;
  "Remove project documentation and unused source files") echo "chore: remove obsolete documentation and sources" ;;
  "Update main activity and keyboard settings with new UI configurations") echo "feat: improve keyboard preference settings" ;;
  "Implement keyboard preference settings and integrate with main activity") echo "feat: integrate keyboard preference settings" ;;
  "Implement keyboard service logic and update activity UI components") echo "feat: wire keyboard preference behavior" ;;
  "Add keyboard layout configuration and preferences update functionality") echo "feat: add keyboard layout preferences" ;;
  "Refactor keyboard service and view components") echo "refactor: simplify keyboard service and view components" ;;
  "Add logic to commit message rewrite script") echo "chore: complete commit message mappings" ;;
  "Implement suggestion engine logic and integrate with keyboard service") echo "feat: add suggestion engine integration" ;;
  "Update keyboard service logic in NovaBoardService") echo "fix: correct keyboard service behavior" ;;
  "fix commit style") echo "chore: normalize commit message style" ;;
  "Refactor emoji panel and add tests for emoji data") echo "test: cover emoji search behavior" ;;
  *) echo "ERROR: unmapped commit $GIT_COMMIT ($subject)" >&2; exit 1 ;;
esac
' HEAD

echo
echo "Rewrite complete. Verify before force-pushing:"
echo "  git log --reverse --format='%h %s'"
echo "  git log --format='%s' | grep -Ev '^(feat|fix|docs|style|refactor|perf|test|build|ci|chore|revert)(\\([^)]*\\))?!?: .+'"
echo "  git push --force-with-lease origin main"
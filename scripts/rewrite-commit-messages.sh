#!/usr/bin/env bash
# Rewrite the current repository history into Conventional Commits messages.
#
# This changes commit IDs. It is intentionally a two-step operation:
#   1. Preview:  bash scripts/rewrite-commit-messages.sh
#   2. Apply:    bash scripts/rewrite-commit-messages.sh --apply
#
# After applying, inspect the result before updating the remote:
#   git log --reverse --format='%h %s'
#   git push --force-with-lease origin dev
#
# WARNING: coordinate with collaborators before force-pushing rewritten history.

set -euo pipefail

APPLY=false
case "${1:-}" in
  "")
    ;;
  --apply)
    APPLY=true
    ;;
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
if [[ "$branch" != "dev" ]]; then
  echo "Refusing to rewrite branch '$branch'; check out dev first." >&2
  exit 1
fi

if ! git symbolic-ref -q HEAD >/dev/null; then
  echo "Refusing to rewrite a detached HEAD." >&2
  exit 1
fi

echo "Commit messages that will be used:"
git log --reverse --format='%H%x09%s' |
  while IFS=$'\t' read -r commit subject; do
    case "$commit" in
      d3528e125828dc2721b1039b7d3eb9645266afc9)
        message="chore: initialize NovaBoard project"
        ;;
      b9150d73045344d57bf0228a91c20f6f62f6b88d)
        message="build: update configuration and remove obsolete overlay test"
        ;;
      318a1c9338f89c12636d55d915b9a0656db542bb)
        message="refactor: clean up data management and project configuration"
        ;;
      4fd5130e77dd975286e4c2d48991d8dc3cb4c52c)
        message="docs: add NovaBoard migration design specification"
        ;;
      835b69c58358b909289e73bfefb54ee6d474b8af)
        message="docs: add NovaBoard migration plan for 2026"
        ;;
      8e3ebe121a3ccb15b86470e5e6e83d62ed899c40)
        message="chore: save work session"
        ;;
      88d98e986d77764baaa67e2953a5770c77695150)
        message="docs: update documentation and project metadata"
        ;;
      a24d8dfe4e1f099b9b8302c692df91fd6e6b7937)
        message="docs: reconcile NovaBoard migration context"
        ;;
      8c27134b411b930172413827b4e2845a1e8ea7e7)
        message="docs: mark NovaBoard migration plan complete"
        ;;
      c95140737a15542921839fb5e46180aceb041489)
        message="chore: remove completed migration plans"
        ;;
      f16b16533a25d65518cd6137fe8d580a6723b235)
        message="refactor: clean up core application code and tests"
        ;;
      f3e2730be57a79953698d73757d35aabd9f65c0d)
        message="chore: add commit message rewrite tool"
        ;;
      *)
        echo "ERROR: no message mapping for $commit ($subject)" >&2
        exit 1
        ;;
    esac
    printf '%s\t%s\n' "${commit:0:7}" "$message"
  done

if [[ "$APPLY" != true ]]; then
  echo
  echo "Preview only. Run with --apply to rewrite all commits."
  exit 0
fi

echo
echo "Rewriting all commits on $branch..."

git filter-branch -f --msg-filter '
case "$GIT_COMMIT" in
  d3528e125828dc2721b1039b7d3eb9645266afc9) echo "chore: initialize NovaBoard project" ;;
  b9150d73045344d57bf0228a91c20f6f62f6b88d) echo "build: update configuration and remove obsolete overlay test" ;;
  318a1c9338f89c12636d55d915b9a0656db542bb) echo "refactor: clean up data management and project configuration" ;;
  4fd5130e77dd975286e4c2d48991d8dc3cb4c52c) echo "docs: add NovaBoard migration design specification" ;;
  835b69c58358b909289e73bfefb54ee6d474b8af) echo "docs: add NovaBoard migration plan for 2026" ;;
  8e3ebe121a3ccb15b86470e5e6e83d62ed899c40) echo "chore: save work session" ;;
  88d98e986d77764baaa67e2953a5770c77695150) echo "docs: update documentation and project metadata" ;;
  a24d8dfe4e1f099b9b8302c692df91fd6e6b7937) echo "docs: reconcile NovaBoard migration context" ;;
  8c27134b411b930172413827b4e2845a1e8ea7e7) echo "docs: mark NovaBoard migration plan complete" ;;
  c95140737a15542921839fb5e46180aceb041489) echo "chore: remove completed migration plans" ;;
  f16b16533a25d65518cd6137fe8d580a6723b235) echo "refactor: clean up core application code and tests" ;;
  f3e2730be57a79953698d73757d35aabd9f65c0d) echo "chore: add commit message rewrite tool" ;;
  *) echo "ERROR: unmapped commit $GIT_COMMIT" >&2; exit 1 ;;
esac
' HEAD

echo
echo "Rewrite complete. Verify before force-pushing:"
echo "  git log --reverse --format='%h %s'"
echo "  git log --format='%s' | grep -Ev '^(feat|fix|docs|style|refactor|perf|test|build|ci|chore|revert)(\\([^)]*\\))?!?: .+'"
echo "  git push --force-with-lease origin dev"
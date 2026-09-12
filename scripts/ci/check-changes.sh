#!/usr/bin/env bash
set -euo pipefail
repo_root=$(CDPATH='' cd -- "$(dirname -- "$0")/../.." && pwd)
cd "$repo_root"
if [ "$#" -gt 1 ]; then
  echo 'Usage: check-changes.sh [BASE_COMMIT|--all]' >&2
  exit 2
fi
if [ "${1:-}" = --all ]; then
  scope=$(python3 scripts/ci/change_scope.py --all)
else
  scope=$(python3 scripts/ci/change_scope.py --base "${1:-origin/main}" --working-tree)
fi
printf '%s\n' "$scope"
profile=$(printf '%s\n' "$scope" | sed -n 's/^verification_profile=//p')
case "$profile" in
  docs) exec ./scripts/ci/check-docs.sh ;;
  host) exec ./scripts/ci/check-host.sh ;;
  today) exec ./scripts/ci/check-fast.sh ;;
  full) exec ./scripts/ci/check-all.sh ;;
  *) echo 'Invalid verification profile; refusing incomplete checks' >&2; exit 1 ;;
esac

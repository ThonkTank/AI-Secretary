#!/usr/bin/env bash
set -euo pipefail
repo_root=$(CDPATH='' cd -- "$(dirname -- "$0")/../.." && pwd)
cd "$repo_root"
./scripts/ci/check-docs.sh
# Host changes may themselves modify Golden tests; include the entire host suite.
./gradlew testInstrumentationUnitTest

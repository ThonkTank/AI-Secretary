#!/usr/bin/env bash
set -euo pipefail

repo_root=$(CDPATH='' cd -- "$(dirname -- "$0")/../.." && pwd)
cd "$repo_root"

./gradlew testInstrumentationUnitTest \
  --tests '*Golden*' \
  --tests '*DesignSystemTest'

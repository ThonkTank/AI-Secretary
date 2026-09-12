#!/usr/bin/env bash
set -euo pipefail
repo_root=$(CDPATH='' cd -- "$(dirname -- "$0")/../.." && pwd)
cd "$repo_root"
python3 -m unittest discover -s scripts/ci -p 'test_*.py' -v
python3 -m unittest discover -s scripts/release -p 'test_*.py' -v

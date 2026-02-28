#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/lib/common.sh"

ADB=/home/aaron/Android/Sdk/platform-tools/adb
LOGCAT_RAW=$($ADB logcat -d -s SlotGen:D | grep SlotGen)
ALL_SUMMARIES=$(echo "$LOGCAT_RAW" | sed -n /Zusammenfassung/,/Gesamt:/p)
if [ -z "$ALL_SUMMARIES" ]; then echo "ERROR: No summary blocks found."; exit 1; fi
echo "========================================="
echo "  Multi-Day Scheduling Validation"
echo "========================================="
echo ""
PASS=0
FAIL=0
check_task() {
  local task_name="$1"
  local expected="$2"
  local actual=$(count_days_with_task "$task_name")
  if [ "$actual" -eq "$expected" ]; then
    echo "  PASS  $task_name: $actual days (expected $expected)"
    PASS=$((PASS + 1))
  else
    echo "  FAIL  $task_name: $actual days (expected $expected)"
    FAIL=$((FAIL + 1))
  fi
}
check_task "Sport" 3
check_task "Einkaufen" 1
check_task "Arbeit" 5
check_task "Morgenroutine" 7
check_task "Wäsche waschen" 2
check_task "Abendspaziergang" 1
echo ""
echo "========================================="
echo "  Results: $PASS passed, $FAIL failed"
echo "========================================="
if [ "$FAIL" -gt 0 ]; then exit 1; else exit 0; fi

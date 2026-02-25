#\!/usr/bin/env bash
ADB=/home/aaron/Android/Sdk/platform-tools/adb
LOGCAT_RAW=$($ADB logcat -d -s SlotGen:D | grep SlotGen)
ALL_SUMMARIES=$(echo "$LOGCAT_RAW" | sed -n /Zusammenfassung/,/Gesamt:/p)
if [ -z "$ALL_SUMMARIES" ]; then echo "ERROR: No summary blocks found."; exit 1; fi
count_days_with_task() {
  local task_name="$1"
  local count=0
  local in_block=0
  local found_in_block=0
  while IFS= read -r line; do
    if echo "$line" | grep -q Zusammenfassung; then in_block=1; found_in_block=0; fi
    if [ "$in_block" -eq 1 ]; then
      if echo "$line" | grep -q "$task_name" && echo "$line" | grep -q "slots" && \! echo "$line" | grep -q unscheduled; then found_in_block=1; fi
    fi
    if echo "$line" | grep -q "Gesamt:"; then
      if [ "$in_block" -eq 1 ] && [ "$found_in_block" -eq 1 ]; then count=$((count + 1)); fi
      in_block=0; found_in_block=0
    fi
  done <<< "$ALL_SUMMARIES"
  echo "$count"
}
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

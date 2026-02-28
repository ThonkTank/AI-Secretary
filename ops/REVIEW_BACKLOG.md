
---

## [warning] Complex date parsing logic in extract_limit_reset_sleep_seconds() is hard to follow

**File:** `apply_skill.sh:272-312`

**Issue:** This function attempts to parse two different date/time formats and determine when to retry:

1. Format 1: "resets 4pm (Europe/Berlin)" — extracts time, timezone, parses as epoch
2. Format 2: "try again at Mar 3rd, 2026 6:09 PM" — strips ordinal suffixes, parses as epoch

The function has:
- 6 local variables to track state
- Deeply nested if-blocks (4 levels deep)
- Regex extraction logic that's hard to verify
- Magic variable `tz_pattern='\\(([^)]+)\\)'` with no explanation

**Impact:** Hard to review for correctness. Brittle — will fail if API error message format changes. Easy to introduce bugs if modifying.

**Fix:**
1. Split into two separate functions: `parse_claude_reset_time()` and `parse_codex_reset_time()`
2. Add unit test examples of known input/output pairs at the top of each function (in comments)
3. Extract regex patterns to named variables with comments

---


## [warning] Many `|| true` statements suppress errors silently, making debugging harder

**Files:** `apply_skill.sh`, `test_schedule.sh`

**Issue:** Throughout the scripts, `|| true` is used to suppress errors:

- `test_schedule.sh:53-54`: `$ADB shell uiautomator dump ... || true` — may fail silently
- `test_schedule.sh:170`: `$ADB logcat -d -s ... || true` — if logcat fails, no warning
- `apply_skill.sh:148`: `git -C ... fetch ... 2>/dev/null || return 1` — mixes silent suppression with early return
- Many others

**Impact:** When something fails, the script continues without indication. Hard to debug production issues. Users don't know why a step didn't work.

**Fix:**
1. Use selective error suppression: `2>/dev/null` for expected errors, keep exit code visible
2. Add explicit error messages before `|| true` if the step is optional
3. Use explicit checks instead: `if ! command; then handle_error; fi`

---

## [nit] test_schedule.sh does too many things — poor separation of concerns

**File:** `test_schedule.sh` (global structure)

**Issue:** This script handles multiple unrelated concerns in one file:

1. Build and deployment (lines 124-145)
2. Device interaction and UIAutomator (lines 29-86)
3. Log parsing and extraction (lines 89-114, 183-186)
4. Check logic and assertions (lines 222-486)
5. Database extraction (lines 560-576)

**Impact:** Hard to reuse components. Hard to test individual steps. Hard to maintain — changes to one concern affect the whole script.

**Fix:** Split into separate scripts or source library files:
- `ops/lib/build_and_install.sh` — build and deploy steps
- `ops/lib/ui_automation.sh` — device interaction helpers
- `ops/lib/log_parsing.sh` — log extraction and parsing
- `ops/lib/checks.sh` — individual check functions
- Keep `test_schedule.sh` as the orchestrator that sources these libraries

---

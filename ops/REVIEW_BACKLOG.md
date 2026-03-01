
---

## [low] `source "$STATE_FILE"` executes arbitrary bash from a writable file

**File:** `apply_skill.sh:714`

**Vulnerability type:** Arbitrary code execution via sourced state file

**Attack scenario:** `$STATE_FILE` is `.git/apply_skill_state`, which is sourced directly. The file is normally written with proper `%q` quoting, but any local process with write access to the repository directory can overwrite it with arbitrary shell commands, which execute in the script's context the next time it starts. This requires local filesystem write access and is therefore low severity, but the `.git/` directory is not unusually restricted.

**Recommended fix:** Before sourcing, validate that each line in the state file matches an allowlist of expected patterns (variable assignments and one `declare -A` statement), and refuse to source if any line does not match.

**Why the fix closes the attack vector:** Allowlist validation rejects injected shell commands before they reach the `source` call.

---

## [split] apply_skill.sh is 850 LOC mixing 8+ distinct concerns @skill:review-structure

**Path:** `apply_skill.sh`

**What makes it hard to read/navigate today:** A new reader cannot quickly locate "where is skill selection?", "where is the main loop?", or "where is rate-limit handling?". The file mixes: constants/config, directory scanning (`_rebuild_dirs`, `_count_loc`), skill selection (`_build_skill_list`, `_has_ui_content`, `_needs_triage`), prompt building (`_build_prompt`, `_build_triage_prompt`), agent dispatching (`dispatch_claude`, `run_with_retry`, `_dispatch_dir`), state management (`_save_state`, `_find_start_idx`), checkpoint logic, rate-limit parsing (`extract_limit_reset_sleep_seconds`, `_parse_reset_epoch`), watchdog supervisor, and the main loop — all in one file.

**Proposed structural change:** Extract logically independent groups into `lib/` files sourced at the top of `apply_skill.sh`:
- `lib/skill_selection.sh` — `_has_ui_content`, `_needs_triage`, `_build_skill_list`, `_count_loc`
- `lib/agent_runner.sh` — `dispatch_claude`, `run_with_retry`, rate-limit helpers (`is_rate_limited`, `is_transient_error`, `extract_limit_reset_sleep_seconds`, `_parse_reset_epoch`, `_sleep_until_epoch`)
- `lib/state.sh` — `_save_state`, `_find_start_idx`, state constants

**Why it reduces mental load:** `apply_skill.sh` becomes a readable orchestrator; each concern lives in a file whose name announces its purpose.

**Tradeoffs / risks:** Significant refactor. Requires careful bash quoting/scoping across source boundaries. No automated tests to validate. High risk without device testing.

---

## [consider] ops/skills/ mixes review skills with operational skills @skill:review-structure

**Path:** `ops/skills/`

**What makes it hard to read/navigate today:** `commit.md`, `init.md`, `sync-main.md`, and `triage.md` are operational/infrastructure skills that live alongside 13 `review-*.md` files. The `review-*` naming convention is clear, but the four non-review skills look out of place and a contributor adding a new operational skill has no obvious sub-folder to use.

**Proposed structural change:** Create `ops/skills/ops/` for the four operational skills, keeping `ops/skills/` as the root only for review skills. Update the path resolution in `apply_skill.sh`.

**Why it reduces mental load:** A reader scanning the `skills/` folder immediately sees two categories. "Where do I add a new operational skill?" becomes obvious.

**Tradeoffs / risks:** Requires updating path constants in `apply_skill.sh` (one-line change per affected skill reference). Moderate churn for moderate gain; the current flat structure with 17 files is still manageable.

---

## [warning] Complex date parsing logic in extract_limit_reset_sleep_seconds() is hard to follow @skill:review-elegance

**File:** `apply_skill.sh:320-349`

**Issue:** This function attempts to parse one date/time format ("resets 4pm (Europe/Berlin)") but the backlog comment and original design reference a second format ("try again at Mar 3rd, 2026 6:09 PM") that is not actually implemented. The function has:
- 6 local variables to track state
- Deeply nested if-blocks (4 levels deep)
- Regex extraction logic that's hard to verify
- Magic variable `tz_pattern='\\(([^)]+)\\)'` with no explanation

**Impact:** Hard to review for correctness. Brittle — will fail if API error message format changes. Easy to introduce bugs if modifying.

**Fix:**
1. Split into two separate functions: `_parse_claude_reset_time()` and `_parse_codex_reset_time()`
2. Add unit test examples of known input/output pairs at the top of each function (in comments)
3. Extract regex patterns to named variables with comments

---


## [warning] Many `|| true` statements suppress errors silently, making debugging harder @skill:review-smells

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

## [nit] 1/5 Extract build and deployment logic from test_schedule.sh into ops/lib/build_and_install.sh @skill:review-smells

**File:** `test_schedule.sh:124-145` → `ops/lib/build_and_install.sh`

**Issue:** Build and deployment steps are mixed into test orchestration script.

**Fix:** Create `ops/lib/build_and_install.sh` with extracted functions, then source it from test_schedule.sh.

---

## [nit] 2/5 Extract UIAutomator and device interaction into ops/lib/ui_automation.sh @skill:review-smells

**File:** `test_schedule.sh:29-86` → `ops/lib/ui_automation.sh`

**Issue:** Device interaction and UIAutomator helpers are mixed into test orchestration script.

**Fix:** Create `ops/lib/ui_automation.sh` with extracted functions, then source it from test_schedule.sh.

---

## [nit] 3/5 Extract log parsing and extraction logic into ops/lib/log_parsing.sh @skill:review-smells

**File:** `test_schedule.sh:89-114, 183-186` → `ops/lib/log_parsing.sh`

**Issue:** Log parsing helpers are mixed into test orchestration script.

**Fix:** Create `ops/lib/log_parsing.sh` with extracted functions, then source it from test_schedule.sh.

---

## [nit] 4/5 Extract check and assertion functions into ops/lib/checks.sh @skill:review-smells

**File:** `test_schedule.sh:222-486` → `ops/lib/checks.sh`

**Issue:** Check logic and assertions are mixed into test orchestration script.

**Fix:** Create `ops/lib/checks.sh` with extracted functions, then source it from test_schedule.sh.

---

## [nit] 5/5 Extract database extraction functions into ops/lib/db.sh @skill:review-smells

**File:** `test_schedule.sh:560-576` → `ops/lib/db.sh`

**Issue:** Database extraction logic is mixed into test orchestration script.

**Fix:** Create `ops/lib/db.sh` with extracted functions, then source it from test_schedule.sh. Finally, update test_schedule.sh to source all libraries at the top and act as the orchestrator only.

---

## [consider] `_rebuild_dirs()` called before every directory in the inner loop @skill:review-performance

**File:** `apply_skill.sh:768`

**What the problem is:** `_rebuild_dirs()` is called inside the inner `while true` loop, before processing each directory. Each call runs O(N) `find`+`wc` commands where N is the total number of directories in the project. With ~40 eligible directories, a full cycle runs ~40 rebuilds × 40 directories × 2 `find` forks + 1 `find`+`xargs`+`wc` call = ~4,800 process launches per cycle. Each rebuild scan is necessary when agent runs create/delete files (as the comment explains), but when nothing changed, it is pure overhead.

**Expected impact:** Several seconds per cycle of shell process-launch overhead. Minor compared to Claude API calls (minutes each), but measurable over a full autonomous cycle.

**Recommended fix:** Track a sentinel file timestamp — touch a file after each rebuild, then only rebuild if any tracked source file is newer than the sentinel. Example: `find "$ROOT" -name '*.java' -newer "$rebuild_sentinel" -quit` before deciding to rebuild. Falls back to full rebuild if the sentinel is absent.

**Tradeoffs:** Adds complexity. The current design is safe and correct; the optimization risks stale state if the sentinel logic has bugs. Low priority given API call dominance.

---



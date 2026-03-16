#!/usr/bin/env bash

readonly PATTERN_RATE_LIMITED="you'?ve[[:space:]]+hit[[:space:]]+your[[:space:]]+limit|usage[[:space:]-]*limit|rate[[:space:]-]*limit|quota|too[[:space:]]+many[[:space:]]+requests|http[[:space:]]*429|status[[:space:]]*429|retry[[:space:]-]*after|credit[[:space:]]+balance|billing[[:space:]]+limit"
readonly PATTERN_TRANSIENT="getaddrinfo|eai_again|timed[[:space:]-]*out|connection[[:space:]-]*(refused|reset)|service[[:space:]-]*unavailable|temporar(y|ily)[[:space:]-]*unavailable|overloaded|try[[:space:]]+again|internal[[:space:]-]*server[[:space:]-]*error|http[[:space:]]*50[234]"
readonly CLAUDE_RESET_REGEX='resets[[:space:]]+[0-9]{1,2}(:[0-9]{2})?[[:space:]]*(am|pm)([[:space:]]*\([^)]*\))?'
readonly CLAUDE_RESET_TIME_REGEX='^resets[[:space:]]+([0-9]{1,2}(:[0-9]{2})?[[:space:]]*(am|pm)).*$'
readonly RESET_TIMEZONE_REGEX='\(([^)]+)\)'
readonly CODEX_RESET_REGEX='try[[:space:]]+again[[:space:]]+at[[:space:]]+[[:alpha:]]{3}[[:space:]]+[0-9]{1,2}(st|nd|rd|th)?,[[:space:]]+[0-9]{4}[[:space:]]+[0-9]{1,2}:[0-9]{2}[[:space:]]*(AM|PM)'
readonly CODEX_RESET_TIME_REGEX='.*try[[:space:]]+again[[:space:]]+at[[:space:]]+([[:alpha:]]{3}[[:space:]]+[0-9]{1,2}(st|nd|rd|th)?,[[:space:]]+[0-9]{4}[[:space:]]+[0-9]{1,2}:[0-9]{2}[[:space:]]*(AM|PM)).*'

is_rate_limited() {
    local logfile="$1"
    grep -Eiq "$PATTERN_RATE_LIMITED" "$logfile"
}

is_transient_error() {
    local logfile="$1"
    grep -Eiq "$PATTERN_TRANSIENT" "$logfile"
}

_parse_reset_epoch() {
    local time=$1 tz=$2 day=$3
    if [[ -n "$tz" ]]; then
        TZ="$tz" date -d "$day $time" +%s 2>/dev/null
    else
        date -d "$day $time" +%s 2>/dev/null
    fi
}

_sleep_until_epoch() {
    local reset_epoch="$1" now_epoch="$2"
    local secs=$(( reset_epoch - now_epoch + RESET_BUFFER_SECONDS ))
    (( secs > 0 )) && echo "$secs" && return 0
    return 1
}

# Examples:
# - "You've hit your limit · resets 4pm (Europe/Berlin)"
# - "You've hit your limit · resets 4:30pm"
_parse_claude_reset_time() {
    local logfile="$1"
    local now_epoch reset_line reset_time reset_tz reset_epoch

    now_epoch="$(date +%s)"
    reset_line="$(grep -Eio "$CLAUDE_RESET_REGEX" "$logfile" | tail -n 1)"
    [[ -n "$reset_line" ]] || return 1

    reset_time="$(sed -E "s/${CLAUDE_RESET_TIME_REGEX}/\\1/I" <<< "$reset_line")"
    [[ -n "$reset_time" ]] || return 1

    reset_tz=""
    if [[ "$reset_line" =~ $RESET_TIMEZONE_REGEX ]]; then
        reset_tz="${BASH_REMATCH[1]}"
    fi

    reset_epoch="$(_parse_reset_epoch "$reset_time" "$reset_tz" "today")"
    [[ -n "$reset_epoch" ]] || return 1

    if (( reset_epoch <= now_epoch )); then
        reset_epoch="$(_parse_reset_epoch "$reset_time" "$reset_tz" "tomorrow")"
    fi
    [[ -n "$reset_epoch" ]] || return 1

    _sleep_until_epoch "$reset_epoch" "$now_epoch"
}

# Examples:
# - "Error: try again at Mar 3rd, 2026 6:09 PM"
# - "Please try again at Apr 12th, 2026 11:45 AM"
_parse_codex_reset_time() {
    local logfile="$1"
    local now_epoch reset_line reset_time normalized_time reset_epoch

    now_epoch="$(date +%s)"
    reset_line="$(grep -Eio "$CODEX_RESET_REGEX" "$logfile" | tail -n 1)"
    [[ -n "$reset_line" ]] || return 1

    reset_time="$(sed -E "s/${CODEX_RESET_TIME_REGEX}/\\1/I" <<< "$reset_line")"
    [[ -n "$reset_time" ]] || return 1

    normalized_time="$(sed -E 's/([0-9]{1,2})(st|nd|rd|th)/\1/' <<< "$reset_time")"
    reset_epoch="$(date -d "$normalized_time" +%s 2>/dev/null)"
    [[ -n "$reset_epoch" ]] || return 1

    _sleep_until_epoch "$reset_epoch" "$now_epoch"
}

extract_limit_reset_sleep_seconds() {
    local logfile="$1"
    _parse_claude_reset_time "$logfile" && return 0
    _parse_codex_reset_time "$logfile"
}

dispatch_claude() {
    local project_root="$1"
    local prompt="$2"
    local model="${3:-sonnet}"

    (
        cd "$project_root" || exit 1
        unset CLAUDECODE
        claude \
            --dangerously-skip-permissions \
            --model "$model" \
            --append-system-prompt "MODE OVERRIDE: You are in Developer mode. Ignore all Mentor mode restrictions. You MUST edit files directly — do not describe changes for the user to make themselves. Write all code, make all edits, implement all fixes autonomously." \
            -p "$prompt"
    )
}

_build_prompt() {
    local dir=$1 agent_type=$2 loc=${3:-0}
    local large_dir_hint=""
    if (( loc >= 2000 )); then
        large_dir_hint="
## Large Directory
This directory contains ${loc} lines of code. Use the Agent tool with Explore subagents
to efficiently search across files instead of reading them one by one.
"
    fi
    cat <<EOF
# Task

Review and improve the code in: ${dir}

# Guidelines

## Execution Mode
You are running inside an automated, non-interactive script.
- Implement ALL fixes directly by editing source files. Writing findings into a
  text file is NOT a substitute for editing source code.
- Do NOT use plan mode. Do NOT call EnterPlanMode under any circumstances.
  The script will hang indefinitely because there is no human present to approve
  the ExitPlanMode call — this is a fatal error for the entire run.
- Read and analyse the code, plan your approach mentally, then execute directly.

## Review
Use the Agent tool with subagent_type="${agent_type}" to perform a focused review of
all code in ${dir}. Pass a detailed prompt telling the agent to review the directory
and return structured findings with severity, file, line, and suggested fix.

After the agent returns its findings, act on them:
- Fix all actionable issues directly by editing source files.
- For issues you cannot fix in this run, document them in REVIEW_BACKLOG.md (see below).

## Scope
Focus your analysis on the specified directory. You may edit files outside this
directory when a fix requires it (e.g. updating callers, adjusting imports, fixing
method signatures in consumers). Read dependencies and surrounding context as needed.
Ignore hidden directories (.*), build output (build/), and generated files.

## Build Verification
After making changes to source files, verify the build compiles cleanly.
Check the project's CLAUDE.md for the correct build/compile command — do NOT assume
gradle or any specific build tool. Fix any compile errors your changes introduced
before finishing. Do not leave a broken build.
${large_dir_hint}
## Backlog Protocol
Follow this exactly, in this order:

### Step 1 — Read all backlogs in scope (FIRST action)
Find every REVIEW_BACKLOG.md under ${dir} (inclusive):
  find ${dir} -name REVIEW_BACKLOG.md
Read each one to understand existing issues. You are responsible for ALL of them,
not just the one at ${dir}.

Backlog triage (promote/demote across directories) has already been handled by a
prior agent. Do NOT move entries between backlog files. Focus on reading what exists,
then proceed to analysis.

### Step 2 — Dispatch review agent
Use the Agent tool with subagent_type="${agent_type}" to review all code in ${dir}.
The agent will return structured findings. Combine these with any existing backlog items.

### Step 3 — Write backlogs (MANDATORY, before any source edits)
Write REVIEW_BACKLOG.md files for ALL open issues — both ones you plan to fix now
and ones you will defer. Writing the backlog is not optional:
it is a hard requirement. Every issue must be in a file before you touch source code.
Place each backlog at the lowest directory level containing all affected files.
Use this format per entry:

  ## [SEVERITY] Title
  **File:** path:line
  **Description:** what is wrong and why it matters
  **Suggested fix:** concrete action

**If you skip writing a REVIEW_BACKLOG.md for any issue, that issue is permanently lost.**

### Step 4 — Implement fixes
Fix aggressively. Your goal is to resolve as many issues as possible, not to document them.
Every issue left in the backlog has to wait for a future cycle to be picked up again — there
is no other team. If you can fix it now, fix it now. This includes:
- One-liners and single-file fixes: always fix immediately.
- Multi-file fixes within your scope (${dir}): fix immediately.
- Refactors that touch several related files: fix immediately.
The ONLY valid reason to defer is when a fix requires editing files OUTSIDE ${dir} that you
cannot access in this run. Everything else is your responsibility right now.

After implementing EACH individual fix, immediately edit the REVIEW_BACKLOG.md to remove
that entry — do not batch backlog edits at the end. Removing an entry IS the resolution
record; do not re-document fixed issues.

### Step 5 — Final backlog cleanup
Delete any REVIEW_BACKLOG.md that is now empty. No other backlog changes needed —
the backlog was kept current throughout Step 3.

### Step 6 — Write run summary (LAST action, always)
Output a structured summary as the very last thing you write. Use exactly this format:

### Verdict: **Clean** / **N fixed · M deferred**

**Fixed:** *(omit section if none)*
- [SEVERITY] Brief description — file:line

**Deferred to backlog:** *(omit section if none)*
- [SEVERITY] Brief description — file:line → path/to/REVIEW_BACKLOG.md (new|existing)

Mark each deferred item with:
- **new** = discovered in this run
- **existing** = was already in a REVIEW_BACKLOG.md before this run

**IMPORTANT:** Every item listed under "Deferred to backlog" MUST already exist in a
REVIEW_BACKLOG.md file written in Step 3. If you list a deferred item here that has no
corresponding backlog file entry, you have made an error — the item will be permanently
lost. Do not list anything as deferred unless you have already written it to a file.

Write nothing after this block. It must be the final output so the preview captures it.
EOF
}

_build_triage_prompt() {
    local dir=$1 skill_text=$2
    cat <<EOF
# Task

Triage review backlogs in: ${dir}

# Guidelines

## Execution Mode
You are running inside an automated, non-interactive script.
- Edit REVIEW_BACKLOG.md files directly for triage moves.
- Do NOT use plan mode. Do NOT call EnterPlanMode under any circumstances.
- Do NOT modify any source code files.

## Scope
Focus on ${dir} and its subdirectories.
Ignore hidden directories (.*), build output (build/), and generated files.

# Role

${skill_text}
EOF
}

_show_preview() {
    local logfile="$1"
    local verdict_line
    verdict_line="$(grep -n "^### Verdict:" "$logfile" | tail -1 | cut -d: -f1)"
    if [[ -n "$verdict_line" ]]; then
        tail -n +"$verdict_line" "$logfile"
    else
        tail -20 "$logfile"
    fi
}

_check_sentinel() {
    local refresh_file="$PROJECT_ROOT/.git/apply_skill_refresh"
    local close_file="$PROJECT_ROOT/.git/apply_skill_close"
    if [[ -f "$refresh_file" ]]; then
        rm -f "$refresh_file"
        echo "Refresh-Signal empfangen. Neustart: $0 ${ORIGINAL_ARGS[*]}"
        echo ""
        exec "$0" "${ORIGINAL_ARGS[@]}"
    fi
    if [[ -f "$close_file" ]]; then
        rm -f "$close_file"
        echo "Close-Signal empfangen. Skript wird beendet."
        exit 0
    fi
}

run_with_retry() {
    local project_root="$1"
    local logfile="$2"
    local prompt="$3"
    local model="${4:-sonnet}"
    local attempt=1
    local tmp_log exit_code sleep_seconds

    : > "$logfile"

    while true; do
        _check_sentinel
        tmp_log="${logfile}.attempt_${attempt}.tmp"
        echo "[attempt ${attempt}] $(date -Is) - starting claude run" >> "$logfile"

        dispatch_claude "$project_root" "$prompt" "$model" > "$tmp_log" 2>&1
        exit_code=$?

        cat "$tmp_log" >> "$logfile"

        if [[ $exit_code -eq 0 ]]; then
            rm -f "$tmp_log"
            return 0
        fi

        if is_rate_limited "$tmp_log"; then
            if ! sleep_seconds="$(extract_limit_reset_sleep_seconds "$tmp_log")"; then
                sleep_seconds="$USAGE_LIMIT_SLEEP_SECONDS"
            fi
            echo "[attempt ${attempt}] Usage-Limit erkannt. Warte ${sleep_seconds}s." | tee -a "$logfile"
        elif is_transient_error "$tmp_log"; then
            sleep_seconds=$TRANSIENT_RETRY_SECONDS
            echo "[attempt ${attempt}] Transient error. Warte ${sleep_seconds}s." | tee -a "$logfile"
        else
            echo "[attempt ${attempt}] claude exited non-zero (code=${exit_code})." >> "$logfile"
            rm -f "$tmp_log"
            return "$exit_code"
        fi

        rm -f "$tmp_log"
        sleep "$sleep_seconds"
        attempt=$((attempt + 1))
    done
}

_upstream_has_changes() {
    if ! git -C "$PROJECT_ROOT" fetch origin main --quiet 2>/dev/null; then
        echo "[auto-sync] fetch origin/main fehlgeschlagen; ueberspringe auto-sync"
        return 1
    fi

    local ahead
    ahead=$(git -C "$PROJECT_ROOT" rev-list HEAD..origin/main --count 2>/dev/null)
    if (( ahead > 0 )); then
        echo "[auto-sync] $ahead neue Commits auf origin/main"
        return 0
    fi

    local remote_url open_prs
    remote_url="$(git -C "$PROJECT_ROOT" remote get-url origin 2>/dev/null)" || return 1
    open_prs=$(gh pr list --repo "$remote_url" \
        --state open --base main --json number --jq 'length' 2>/dev/null)
    if (( open_prs > 0 )); then
        echo "[auto-sync] $open_prs offene PRs auf main"
        return 0
    fi
    return 1
}

_auto_sync_if_needed() {
    local cycle="${1:-}"
    _upstream_has_changes || return 0
    local sync_skill="${SCRIPT_DIR}/skills/ops/sync-main.md"
    [[ -f "$sync_skill" ]] || { echo "WARNING: sync-main.md nicht gefunden"; return 1; }
    local sync_log_dir="$LOG_DIR/cycle_$(printf '%03d' "$cycle")/sync-main"
    mkdir -p "$sync_log_dir"
    local logfile="$sync_log_dir/_root.md"
    echo "══════════════════════════════════════════"
    echo "AUTO-SYNC: origin/main hat Änderungen — starte sync-main"
    echo "══════════════════════════════════════════"
    run_with_retry "$PROJECT_ROOT" "$logfile" "$(<"$sync_skill")" "sonnet" \
        || echo "WARNING: auto-sync exited non-zero"
    echo "--- Sync preview ---"
    _show_preview "$logfile"
    echo ""
}

_dispatch_checkpoint() {
    local cycle="$1"
    local ckpt_log_dir="$LOG_DIR/cycle_$(printf '%03d' "$cycle")/checkpoint"
    mkdir -p "$ckpt_log_dir"

    echo "╔══════════════════════════════════════════╗"
    echo "  Checkpoint (Cycle ${cycle}) — $(date -Is)"
    echo "╚══════════════════════════════════════════╝"
    echo ""

    _auto_sync_if_needed "$cycle"

    local init_skill="${SCRIPT_DIR}/skills/ops/init.md"
    if [[ -f "$init_skill" ]]; then
        echo "── init: updating CLAUDE.md ──"
        run_with_retry "$PROJECT_ROOT" "$ckpt_log_dir/_init.md" \
            "$(<"$init_skill")" "sonnet" \
            || echo "WARNING: init exited non-zero"
        echo "── init done ──"
    fi

    local commit_skill="${SCRIPT_DIR}/skills/ops/commit.md"
    if [[ -f "$commit_skill" ]]; then
        echo "── commit: checkpointing changes ──"
        run_with_retry "$PROJECT_ROOT" "$ckpt_log_dir/_commit.md" \
            "$(<"$commit_skill")" "sonnet" \
            || echo "WARNING: commit exited non-zero"
        echo "--- Commit preview ---"
        _show_preview "$ckpt_log_dir/_commit.md"
        echo ""
    fi
}

_dispatch_dir() {
    local dir="$1" skill="$2" skill_text="$3" skill_log_dir="$4"
    local cycle="${5:-}" dir_i="${6:-}" total_dirs="${7:-}"
    local logfile="${skill_log_dir}/${dir//\//_}.md"
    local prompt model
    if [[ "$skill" == "triage" ]]; then
        model="haiku"
        prompt="$(_build_triage_prompt "$dir" "$skill_text")"
    else
        _select_agent_config "$skill" "$dir" "$dir_i" "$total_dirs"
        model="$AGENT_MODEL"
        prompt="$(_build_prompt "$dir" "$skill" "$AGENT_LINE_COUNT")"
    fi
    echo "══════════════════════════════════════════"
    if [[ -n "$cycle" ]]; then
        echo "Cycle ${cycle} · Dir ${dir_i}/${total_dirs} · ${skill} · $(date '+%H:%M:%S')"
    else
        echo "${skill} · $(date '+%H:%M:%S')"
    fi
    [[ "$skill" != "triage" ]] && echo "${AGENT_LINE_COUNT}L · ${model}"
    echo "Folder : $dir"
    echo "Log    : $logfile"
    echo "══════════════════════════════════════════"
    run_with_retry "$PROJECT_ROOT" "$logfile" "$prompt" "$model" \
        || echo "WARNING: agent exited non-zero for $dir"
    echo "--- ${skill} preview ---"
    _show_preview "$logfile"
    echo ""
}

#!/usr/bin/env bash
# Usage: ./apply_skill.sh <directory> <skill1> [skill2 ...]
#        ./apply_skill.sh --autonomous <directory>
#
# Runs each skill in sequence over every subdirectory of <directory>,
# processing the deepest folders first and working upward to the root.
# All skills complete a full pass before the next skill begins.
# The agent is always started from the git project root for full context.
# The target directory path is passed as an argument to each skill.
#
# --autonomous: cycles endlessly through all skills in ops/skills/ in a
# hardcoded order. UI-only skills are restricted to ui/ and res/ directories.
# sync-main runs once per cycle at PROJECT_ROOT to commit progress.
#
# Logs: $LOG_DIR/<skill>/<sanitized-path>.md  +  $LOG_DIR/_run.log
#        (autonomous: $LOG_DIR/cycle_NNN/<skill>/...)
#
# Skill role descriptions are loaded from ops/skills/<skill>.md and injected
# into the prompt alongside general guidelines (scope, execution mode, backlog).
# The prompt instructs agents not to use plan mode (Claude-specific safeguard).

set -uo pipefail

# ── Configuration ──────────────────────────────────────────────────────────────

ORIGINAL_ARGS=("$@")
AUTONOMOUS=false
FILTERED_ARGS=()
for arg in "$@"; do
    [[ "$arg" == "--autonomous" ]] && AUTONOMOUS=true || FILTERED_ARGS+=("$arg")
done
set -- "${FILTERED_ARGS[@]+"${FILTERED_ARGS[@]}"}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# ── Subcommands: close / refresh ───────────────────────────────────────────
# Usage: ./apply_skill.sh close   — stop after the current agent finishes
#        ./apply_skill.sh refresh — stop after the current agent finishes, then restart
if [[ "${1:-}" == "close" || "${1:-}" == "refresh" ]]; then
    _ctrl_root="$(git -C "$SCRIPT_DIR" rev-parse --show-toplevel 2>/dev/null)" \
        || { echo "ERROR: Nicht in einem Git-Repo (SCRIPT_DIR=$SCRIPT_DIR)."; exit 1; }
    touch "$_ctrl_root/.git/apply_skill_${1}"
    echo "${1}: Signal gesetzt ($_ctrl_root/.git/apply_skill_${1})."
    echo "Der laufende Agent wird nach Abschluss angehalten."
    [[ "$1" == "refresh" ]] && echo "Das Skript startet danach neu."
    exit 0
fi

if $AUTONOMOUS; then
    ROOT="${1:?Usage: $0 --autonomous <directory>}"
    ROOT="$(realpath "$ROOT")"
    SKILLS=()
else
    ROOT="${1:?Usage: $0 <directory> <skill1> [skill2 ...]}"
    ROOT="$(realpath "$ROOT")"
    shift
    SKILLS=("${@:?Usage: $0 <directory> <skill1> [skill2 ...]}")
fi

# Prevent system sleep/idle while this script is running.
# Note: Work cannot continue during actual suspend; we therefore block suspend.
if [[ "${APPLY_SKILL_INHIBIT_ACTIVE:-0}" != "1" ]] && command -v systemd-inhibit >/dev/null 2>&1; then
    export APPLY_SKILL_INHIBIT_ACTIVE=1
    exec systemd-inhibit \
        --what=sleep:idle \
        --mode=block \
        --why="apply_skill.sh long-running agent automation" \
        "$0" "${ORIGINAL_ARGS[@]}"
fi

PROJECT_ROOT="$(git -C "$ROOT" rev-parse --show-toplevel 2>/dev/null || pwd)"
TIMESTAMP="$(date +%Y%m%d_%H%M%S)"
LOG_DIR="/tmp/apply_skill_${TIMESTAMP}"
mkdir -p "$LOG_DIR"
RUN_LOG="$LOG_DIR/_run.log"
USAGE_LIMIT_SLEEP_SECONDS="${USAGE_LIMIT_SLEEP_SECONDS:-900}"
AGENT_UNAVAILABLE_SLEEP_SECONDS="${AGENT_UNAVAILABLE_SLEEP_SECONDS:-120}"
RESET_BUFFER_SECONDS=30  # Safety margin added after a usage-limit reset time
read -r -a REQUESTED_AGENTS <<< "${APPLY_SKILL_AGENTS:-claude codex}"

ACTIVE_AGENTS=()
for agent in "${REQUESTED_AGENTS[@]}"; do
    if [[ "$agent" =~ ^(claude|codex)$ ]] && command -v "$agent" >/dev/null 2>&1; then
        ACTIVE_AGENTS+=("$agent")
    fi
done
mapfile -t ACTIVE_AGENTS < <(printf '%s\n' "${ACTIVE_AGENTS[@]}" | awk '!seen[$0]++')

if [[ ${#ACTIVE_AGENTS[@]} -eq 0 ]]; then
    echo "ERROR: Kein unterstützter Agent verfügbar. Erwartet: claude/codex."
    exit 1
fi

# ── Autonomous cycle ────────────────────────────────────────────────────────────
# Hardcoded skill order for --autonomous mode.
# review-design and review-accessibility are UI-scoped: only run on ui/ and res/ dirs.
# review-architecture, review-conventions, review-structure are high-level: only run on dirs with subdirs.
# commit and sync-main are git-ops: run once per occurrence at PROJECT_ROOT.
# commit checkpoints after every review skill; sync-main integrates upstream once per cycle.
# review-smells and review-simplicity appear multiple times to catch regressions from structural changes.
AUTONOMOUS_SKILL_CYCLE=(
    review-smells          # 1.  Anti-Pattern und Code-Gerüche
    commit                 # 2.
    review-simplicity      # 3.  Unnötige Komplexität reduzieren
    commit                 # 4.
    review-elegance        # 5.  Lesbarkeit und Ausdrucksstärke
    commit                 # 6.
    review-architecture    # 7.  Architekturelle Korrektheit
    commit                 # 8.
    review-smells          # 9.  Smell-Regression nach Architektur-Änderungen
    commit                 # 10.
    review-conventions     # 11. Konsistenz sicherstellen
    commit                 # 12.
    review-structure       # 13. Datei-/Ordnerstruktur optimieren
    commit                 # 14.
    review-simplicity      # 15. Komplexitäts-Regression nach Restrukturierung
    commit                 # 16.
    review-performance     # 17. Performance-Hotspots
    commit                 # 18.
    review-security        # 19. Sicherheitslücken
    commit                 # 20.
    review-design          # 21. Visuelles Design (ui/res only)
    commit                 # 22.
    review-accessibility   # 23. Barrierefreiheit/UX (ui/res only)
    commit                 # 24.
    review-smells          # 25. Finale Smell-Bereinigung
    commit                 # 26.
    review-simplicity      # 27. Finale Vereinfachung
    commit                 # 28.
    review-onboarding      # 29. Dokumentation und Kommentare
    sync-main              # 30. Upstream integrieren + pushen
)

is_git_op_skill() { [[ "$1" == "sync-main" || "$1" == "commit" ]]; }

is_ui_skill() { [[ "$1" == "review-design" || "$1" == "review-accessibility" ]]; }

is_ui_dir() {
    local dir="$1"
    [[ "$dir" == */ui || "$dir" == */ui/* || "$dir" == */res || "$dir" == */res/* ]]
}

is_highlevel_skill() {
    [[ "$1" == "review-architecture" || "$1" == "review-structure" || "$1" == "review-conventions" ]]
}

is_highlevel_dir() {
    [[ -n "$(find "$1" -maxdepth 1 -mindepth 1 -type d -print -quit 2>/dev/null)" ]]
}

_is_cross_cutting() {
    local dir="$1"
    local areas=0
    for subdir in "$dir"/*/; do
        [[ -d "$subdir" ]] || continue
        case "$(basename "$subdir")" in
            ui|domain|data|application)        ((areas++)) ;;
            task|budget|meal)                  ((areas++)) ;;
            app|shared|database|util|features) ((areas++)) ;;
        esac
    done
    (( areas >= 2 ))
}

_should_skip_dir() {
    local skill="$1" dir="$2"
    is_ui_skill "$skill" && ! is_ui_dir "$dir" && return 0
    is_highlevel_skill "$skill" && ! is_highlevel_dir "$dir" && return 0
    return 1
}

# Skill complexity tier — drives model selection and turns bonus.
#   CRITICAL = review-architecture, review-conventions, review-security
#   HIGH     = review-smells, review-performance, review-simplicity
#   MEDIUM   = review-structure, review-elegance, review-accessibility, sync-main
#   LIGHT    = review-design, review-onboarding, commit, init (catch-all)
_skill_tier() {
    local skill="$1"
    case "$skill" in
        review-architecture|review-conventions|review-security) echo "CRITICAL" ;;
        review-smells|review-performance|review-simplicity)     echo "HIGH"     ;;
        review-structure|review-elegance|review-accessibility|\
        sync-main)                                              echo "MEDIUM"   ;;
        *)                                                       echo "LIGHT"    ;;
    esac
}

# Directory size bucket — drives turns baseline and model ceiling.
#   xs = ≤ 200 lines  |  s = 201–800  |  m = 801–3000  |  l = 3001–8000  |  xl = > 8000
_size_bucket() {
    local count="$1"
    if   (( count <= 200  )); then echo "xs"
    elif (( count <= 800  )); then echo "s"
    elif (( count <= 3000 )); then echo "m"
    elif (( count <= 8000 )); then echo "l"
    else                          echo "xl"
    fi
}

# Model matrix  (tier × bucket)  — single source of truth; no parallel _select_* functions needed.
# Cross-cutting dirs (+) get upgraded to opus via _CROSSCUT_UPGRADE below.
#              xs       s        m        l        xl
#   LIGHT:    haiku    haiku    haiku    haiku    sonnet
#   MEDIUM:   haiku    haiku    sonnet   sonnet   sonnet+
#   HIGH:     haiku    sonnet   sonnet   sonnet+  opus
#   CRITICAL: sonnet   sonnet   sonnet+  opus     opus
declare -A _MODEL_MATRIX=(
    [LIGHT_xs]=haiku    [LIGHT_s]=haiku    [LIGHT_m]=haiku    [LIGHT_l]=haiku    [LIGHT_xl]=sonnet
    [MEDIUM_xs]=haiku   [MEDIUM_s]=haiku   [MEDIUM_m]=sonnet  [MEDIUM_l]=sonnet  [MEDIUM_xl]=sonnet
    [HIGH_xs]=haiku     [HIGH_s]=sonnet    [HIGH_m]=sonnet    [HIGH_l]=sonnet    [HIGH_xl]=opus
    [CRITICAL_xs]=sonnet [CRITICAL_s]=sonnet [CRITICAL_m]=sonnet [CRITICAL_l]=opus [CRITICAL_xl]=opus
)

# Cross-cutting upgrade: cells marked with + above get opus when the dir spans ≥2 areas.
declare -A _CROSSCUT_UPGRADE=(
    [MEDIUM_xl]=opus
    [HIGH_l]=opus
    [CRITICAL_m]=opus
)

# Turns matrix  (tier × bucket)
#              xs   s    m    l    xl
#   LIGHT:    15   20   30   40   50
#   MEDIUM:   25   25   35   50   60
#   HIGH:     30   30   45   55   65
#   CRITICAL: 35   35   50   65   75
declare -A _TURNS_MATRIX=(
    [LIGHT_xs]=15   [LIGHT_s]=20   [LIGHT_m]=30   [LIGHT_l]=40   [LIGHT_xl]=50
    [MEDIUM_xs]=25  [MEDIUM_s]=25  [MEDIUM_m]=35  [MEDIUM_l]=50  [MEDIUM_xl]=60
    [HIGH_xs]=30    [HIGH_s]=30    [HIGH_m]=45    [HIGH_l]=55    [HIGH_xl]=65
    [CRITICAL_xs]=35 [CRITICAL_s]=35 [CRITICAL_m]=50 [CRITICAL_l]=65 [CRITICAL_xl]=75
)

# Set AGENT_MODEL, AGENT_TURNS, and AGENT_LINE_COUNT globals.
# Git-op skills pass dir="" and fall back to PROJECT_ROOT for line counting.
select_agent_config() {
    local skill="$1"
    local dir="$2"

    local count_dir="${dir:-$PROJECT_ROOT}"
    local line_count=0
    if [[ -d "$count_dir" ]]; then
        line_count=$(find "$count_dir" -type f \( -name "*.java" -o -name "*.kt" -o -name "*.xml" \) \
            | xargs wc -l 2>/dev/null | tail -1 | awk '{print $1}')
        line_count=${line_count:-0}
    fi

    local tier bucket key
    tier="$(_skill_tier "$skill")"
    bucket="$(_size_bucket "$line_count")"
    key="${tier}_${bucket}"

    AGENT_MODEL="${_MODEL_MATRIX[$key]}"
    AGENT_TURNS="${_TURNS_MATRIX[$key]}"
    AGENT_LINE_COUNT="$line_count"

    # Cross-cutting upgrade: dirs spanning ≥2 architectural areas get the model from _CROSSCUT_UPGRADE
    local crosscut="${_CROSSCUT_UPGRADE[$key]:-}"
    if [[ -n "$crosscut" && -n "$dir" ]] && _is_cross_cutting "$dir"; then
        AGENT_MODEL="$crosscut"
    fi
}

# Tee all stdout/stderr to $RUN_LOG so the run summary is preserved after exit.
exec > >(tee "$RUN_LOG") 2>&1

echo "Directory : $ROOT"
echo "Skills    : ${SKILLS[*]}"
echo "Project   : $PROJECT_ROOT"
echo "Logs      : $LOG_DIR"
echo "Run log   : $RUN_LOG"
echo "Retry wait fallback: ${USAGE_LIMIT_SLEEP_SECONDS}s (bei Usage-Limit ohne Reset-Zeit)"
echo "Agent unavailable fallback: ${AGENT_UNAVAILABLE_SLEEP_SECONDS}s"
echo "Agents    : ${ACTIVE_AGENTS[*]}"
echo ""

# ── Functions ──────────────────────────────────────────────────────────────────

is_rate_limited() {
    local logfile="$1"
    grep -Eiq \
        "you'?ve[[:space:]]+hit[[:space:]]+your[[:space:]]+limit|usage[[:space:]-]*limit|rate[[:space:]-]*limit|quota|too[[:space:]]+many[[:space:]]+requests|http[[:space:]]*429|status[[:space:]]*429|retry[[:space:]-]*after|credit[[:space:]]+balance|billing[[:space:]]+limit" \
        "$logfile"
}

is_turn_limit() {
    local logfile="$1"
    grep -Eiq \
        "max.{0,20}turns|maximum.{0,20}turns|turn.{0,10}limit|turns.{0,10}reached|turn.{0,10}budget" \
        "$logfile"
}

is_transient_error() {
    local logfile="$1"
    grep -Eiq \
        "getaddrinfo|eai_again|timed[[:space:]-]*out|connection[[:space:]-]*(refused|reset)|service[[:space:]-]*unavailable|temporar(y|ily)[[:space:]-]*unavailable|overloaded|try[[:space:]]+again|internal[[:space:]-]*server[[:space:]-]*error|http[[:space:]]*50[234]" \
        "$logfile"
}

parse_reset_epoch() {
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

extract_limit_reset_sleep_seconds() {
    local logfile="$1"
    local now_epoch reset_epoch reset_line reset_time reset_tz
    local tz_pattern='\\(([^)]+)\\)'

    now_epoch="$(date +%s)"

    # Format 1 — Claude: "You've hit your limit · resets 4pm (Europe/Berlin)"
    reset_line="$(grep -Eio "resets[[:space:]]+[0-9]{1,2}(:[0-9]{2})?[[:space:]]*(am|pm)([[:space:]]*\\([^)]*\\))?" "$logfile" | tail -n 1 || true)"
    if [[ -n "$reset_line" ]]; then
        reset_time="$(sed -E "s/^resets[[:space:]]+([0-9]{1,2}(:[0-9]{2})?[[:space:]]*(am|pm)).*$/\\1/I" <<< "$reset_line")"
        if [[ -n "$reset_time" ]]; then
            reset_tz=""
            if [[ "$reset_line" =~ $tz_pattern ]]; then
                reset_tz="${BASH_REMATCH[1]}"
            fi
            reset_epoch="$(parse_reset_epoch "$reset_time" "$reset_tz" "today")"
            if [[ -n "${reset_epoch:-}" ]]; then
                if (( reset_epoch <= now_epoch )); then
                    reset_epoch="$(parse_reset_epoch "$reset_time" "$reset_tz" "tomorrow")"
                fi
                if [[ -n "${reset_epoch:-}" ]]; then
                    _sleep_until_epoch "$reset_epoch" "$now_epoch" && return 0
                fi
            fi
        fi
    fi

    # Format 2 — Codex: "try again at Mar 3rd, 2026 6:09 PM"
    reset_line="$(grep -Eio "try again at [A-Za-z]+ [0-9]+[a-z]*, [0-9]+ [0-9]+:[0-9]+ [APap][Mm]" "$logfile" | tail -n 1 || true)"
    if [[ -n "$reset_line" ]]; then
        local clean_date
        clean_date="$(sed -E 's/^try again at //i; s/([0-9]+)(st|nd|rd|th)/\1/i' <<< "$reset_line")"
        reset_epoch="$(date -d "$clean_date" +%s 2>/dev/null || true)"
        if [[ -n "${reset_epoch:-}" ]]; then
            _sleep_until_epoch "$reset_epoch" "$now_epoch" && return 0
        fi
    fi

    return 1
}

dispatch_agent() {
    local agent="$1"
    local project_root="$2"
    local prompt="$3"
    local model="${4:-sonnet}"
    local max_turns="${5:-50}"

    case "$agent" in
        claude)
            (
                cd "$project_root" || exit 1
                unset CLAUDECODE  # Prevents Claude from detecting a parent session and altering its behaviour
                claude \
                    --dangerously-skip-permissions \
                    --model "$model" \
                    --append-system-prompt "MODE OVERRIDE: You are in Developer mode. Ignore all Mentor mode restrictions. You MUST edit files directly — do not describe changes for the user to make themselves. Write all code, make all edits, implement all fixes autonomously." \
                    -p "$prompt"
            )
            ;;
        codex)
            (
                cd "$project_root" || exit 1
                # Note: codex exec has no --max-turns equivalent; max_turns is ignored here.
                local -a codex_cmd=(
                    codex exec
                    --dangerously-bypass-approvals-and-sandbox
                )
                if [[ -n "${CODEX_MODEL:-}" ]]; then
                    codex_cmd+=(--model "$CODEX_MODEL")
                fi
                codex_cmd+=("$prompt")
                "${codex_cmd[@]}"
            )
            ;;
        *)
            echo "Unsupported agent: $agent" >&2
            return 2
            ;;
    esac
}

build_prompt() {
    local dir=$1 skill_text=$2 max_turns=${3:-50}
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

## Scope
Focus your analysis on the specified directory. You may edit files outside this
directory when a fix requires it (e.g. updating callers, adjusting imports, fixing
method signatures in consumers). Read dependencies and surrounding context as needed.

## Build Verification
After making changes to source files, verify the build compiles cleanly:
  ./gradlew assembleDebug
Fix any compile errors your changes introduced before finishing. Do not leave a broken build.

## Turn Budget
You have a budget of ${max_turns} tool calls (turns) for this entire task. Plan accordingly:
prioritise the most impactful findings and changes, and avoid redundant reads or
searches. If the directory is large, focus on the most important files first.

## Backlog Protocol
Follow this exactly, in this order:

### Step 1 — Read all backlogs in scope (FIRST action)
Find every REVIEW_BACKLOG.md under ${dir} (inclusive):
  find ${dir} -name REVIEW_BACKLOG.md
Read each one. You are responsible for ALL of them, not just the one at ${dir}.

### Step 2 — Triage: move issues to the correct level
Before touching any source code, redistribute misplaced issues across backlogs:
- **Promote** (move up): an issue in a subfolder's backlog that affects files outside
  that subfolder belongs at the lowest ancestor level that contains all affected files.
  Remove it from the subfolder backlog and add it to the correct ancestor's backlog.
  Only promote within your scope (${dir} and below); if the correct level is above
  ${dir}, leave the issue where it is with a note that it needs promotion.
- **Demote** (move down): an issue in a parent backlog that only affects files inside
  one subfolder belongs in that subfolder's backlog. Remove it from the parent and
  add it to the subfolder backlog.

### Step 3 — Analyse and write backlogs (MANDATORY, before any source edits)
Analyse all code in scope and identify all findings (both from backlogs and newly found).
Then write REVIEW_BACKLOG.md files for ALL open issues — both ones you plan to fix now
and ones you will defer. Writing the backlog is not optional and not a checkpoint:
it is a hard requirement. Every issue that exists must be in a file before you touch source code.
Use the backlog entry format defined in your Role section, placed at the lowest directory
level containing all affected files.

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
the backlog was kept current throughout Step 4.

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
    local max_turns="${5:-50}"
    local attempt=1
    local now_epoch earliest_epoch sleep_seconds selected_agent cooldown msg
    local tmp_log exit_code
    local -a ready_agents=()
    declare -A next_ready_epoch=()

    for agent in "${ACTIVE_AGENTS[@]}"; do
        next_ready_epoch["$agent"]=0
    done

    : > "$logfile"

    while true; do
        now_epoch="$(date +%s)"
        ready_agents=()
        for agent in "${ACTIVE_AGENTS[@]}"; do
            if (( now_epoch >= next_ready_epoch["$agent"] )); then
                ready_agents+=("$agent")
            fi
        done

        if [[ ${#ready_agents[@]} -eq 0 ]]; then
            earliest_epoch=""
            for agent in "${ACTIVE_AGENTS[@]}"; do
                local ready_epoch="${next_ready_epoch[$agent]}"
                if [[ -z "$earliest_epoch" ]] || (( ready_epoch < earliest_epoch )); then
                    earliest_epoch="$ready_epoch"
                fi
            done
            sleep_seconds=$(( earliest_epoch - now_epoch ))
            if (( sleep_seconds < 1 )); then
                sleep_seconds=1
            fi
            echo "[attempt ${attempt}] Kein Agent aktuell verfügbar. Warte ${sleep_seconds}s bis zum frühesten Reset/Retry." | tee -a "$logfile"
            sleep "$sleep_seconds"
            echo "[attempt ${attempt}] $(date -Is) — Aufgewacht. Starte nächsten Versuch..."
            continue
        fi

        selected_agent="${ready_agents[0]}"
        _check_sentinel
        tmp_log="${logfile}.attempt_${attempt}.tmp"
        echo "[attempt ${attempt}] $(date -Is) - starting ${selected_agent} run" >> "$logfile"

        dispatch_agent "$selected_agent" "$project_root" "$prompt" "$model" "$max_turns" > "$tmp_log" 2>&1
        exit_code=$?

        cat "$tmp_log" >> "$logfile"

        if [[ $exit_code -eq 0 ]]; then
            rm -f "$tmp_log"
            return 0
        fi

        # Classify failure and determine cooldown
        if is_rate_limited "$tmp_log"; then
            local parsed_wait
            parsed_wait="$(extract_limit_reset_sleep_seconds "$tmp_log" || true)"
            if [[ -n "$parsed_wait" ]]; then
                cooldown="$parsed_wait"
                msg="${selected_agent}: Usage-/Rate-Limit erkannt. Reset-Zeit geparst, warte ${cooldown}s bis nach dem Reset."
            else
                cooldown="${USAGE_LIMIT_SLEEP_SECONDS}"
                msg="${selected_agent}: Usage-/Rate-Limit erkannt. Keine Reset-Zeit parsebar, warte Fallback ${cooldown}s."
            fi
        elif is_transient_error "$tmp_log"; then
            cooldown=$AGENT_UNAVAILABLE_SLEEP_SECONDS
            msg="${selected_agent}: temporär nicht erreichbar. Warte ${cooldown}s und wechsle Agent."
        elif (( ${#ACTIVE_AGENTS[@]} > 1 )); then
            cooldown=$AGENT_UNAVAILABLE_SLEEP_SECONDS
            msg="${selected_agent} exited non-zero (code=${exit_code}). Wechsle auf anderen Agent (Retry in ${cooldown}s)."
        else
            echo "[attempt ${attempt}] ${selected_agent} exited non-zero (code=${exit_code}) ohne Failover-Möglichkeit." >> "$logfile"
            rm -f "$tmp_log"
            return "$exit_code"
        fi

        # Apply cooldown and retry
        next_ready_epoch["$selected_agent"]=$(( $(date +%s) + cooldown ))
        echo "[attempt ${attempt}] ${msg}" | tee -a "$logfile"
        rm -f "$tmp_log"
        attempt=$((attempt + 1))
    done
}

_dispatch_dir() {
    local dir="$1" skill="$2" skill_text="$3" skill_log_dir="$4"
    local cycle="${5:-}" dir_i="${6:-}" total_dirs="${7:-}"
    local log_filename="${dir//\//_}"
    local logfile="${skill_log_dir}/${log_filename}.md"
    select_agent_config "$skill" "$dir"
    echo "══════════════════════════════════════════"
    [[ -n "$cycle" ]] && echo "Cycle ${cycle} · Dir ${dir_i}/${total_dirs} · ${skill} · $(date '+%H:%M:%S')"
    [[ -z "$cycle" ]]  && echo "${skill} · $(date '+%H:%M:%S')"
    echo "${AGENT_LINE_COUNT}L · ${AGENT_MODEL} · ${AGENT_TURNS} turns"
    echo "Folder : $dir"
    echo "Log    : $logfile"
    echo "══════════════════════════════════════════"
    local prompt
    prompt="$(build_prompt "$dir" "$skill_text" "$AGENT_TURNS")"
    run_with_retry "$PROJECT_ROOT" "$logfile" "$prompt" "$AGENT_MODEL" "$AGENT_TURNS" \
        || echo "WARNING: agent exited non-zero for $dir"
    echo "--- Response preview ---"
    _show_preview "$logfile"
    echo ""
}

# ── Main ───────────────────────────────────────────────────────────────────────

# Collect all directories, sort by depth descending (deepest first).
# Skip pass-through directories: a directory whose only direct child is a single
# subdirectory (e.g. java/ → com/ → …) adds no value as a review scope.
_rebuild_dirs() {
    mapfile -t dirs < <(
        find "$ROOT" \( -name '.*' -prune \) -o \( -type d -print \) \
        | awk '{ print gsub("/", "/", $0), $0 }' \
        | sort -rn \
        | awk '{ print $2 }' \
        | while IFS= read -r d; do
            children=$(find "$d" -maxdepth 1 -mindepth 1 | wc -l)
            subdirs=$(find "$d" -maxdepth 1 -mindepth 1 -type d | wc -l)
            (( children == 1 && subdirs == 1 )) && continue
            echo "$d"
          done
    )
}
_rebuild_dirs

if $AUTONOMOUS; then
    # ── Self-watchdog ───────────────────────────────────────────────────────────
    # The first invocation (_AS_WATCHDOG unset) becomes the supervisor.
    # It re-launches the script as a child; child invocations (_AS_WATCHDOG=1)
    # skip this block and run the actual autonomous logic.
    # On unexpected exit the supervisor runs a Claude diagnostic then restarts.
    # Clean exits (code 0) and user signals break the loop without restart.
    if [[ "${_AS_WATCHDOG:-}" != "1" ]]; then
        export _AS_WATCHDOG=1
        WD_LOG="/tmp/apply_skill_watchdog.log"
        _wd_interrupted=false
        trap '_wd_interrupted=true; [[ -n "${_WD_CHILD:-}" ]] && kill "$_WD_CHILD" 2>/dev/null' INT TERM

        echo "[watchdog] $(date -Is) — Supervisor gestartet. Log: $WD_LOG" | tee -a "$WD_LOG"

        while true; do
            bash "$0" "${ORIGINAL_ARGS[@]}" &
            _WD_CHILD=$!
            wait "$_WD_CHILD"
            EXIT_CODE=$?

            if $_wd_interrupted || [[ $EXIT_CODE -eq 0 ]]; then
                echo "[watchdog] $(date -Is) — Sauber beendet (exit=${EXIT_CODE}). Supervisor stoppt." \
                    | tee -a "$WD_LOG"
                break
            fi

            # Unexpected crash — diagnose then restart
            echo "[watchdog] $(date -Is) — Unerwarteter Exit (code=${EXIT_CODE}). Starte Diagnose..." \
                | tee -a "$WD_LOG"

            LATEST_RUN_LOG="$(ls -t /tmp/apply_skill_*/_run.log 2>/dev/null | head -1 || true)"
            DIAG_LOG="/tmp/apply_skill_diag_$(date +%Y%m%d_%H%M%S).log"

            if command -v claude >/dev/null 2>&1 && [[ -n "${LATEST_RUN_LOG:-}" ]]; then
                (
                    cd "$PROJECT_ROOT"
                    unset CLAUDECODE
                    claude --model sonnet --dangerously-skip-permissions \
                        -p "apply_skill.sh (ops/apply_skill.sh) crashed with exit code ${EXIT_CODE}.

Last 150 lines of run log (${LATEST_RUN_LOG}):
$(tail -150 "$LATEST_RUN_LOG" 2>/dev/null)

You are the crash recovery agent. Do the following in order:
1. Identify the root cause (quote the relevant error line).
2. If the bug is in ops/apply_skill.sh: read the file, fix it directly with Edit/Write tools, then confirm the fix with 'bash -n ops/apply_skill.sh'.
3. If the crash is caused by something else (transient network/API error, disk full, etc.): note it but do NOT modify any files.
4. End your response with exactly one of these lines:
   WATCHDOG_ACTION: RESTART
   WATCHDOG_ACTION: HUMAN_NEEDED" \
                        > "$DIAG_LOG" 2>&1 || true
                )
                echo "[watchdog] Diagnose geschrieben: $DIAG_LOG" | tee -a "$WD_LOG"
                echo "=== Diagnose ===" | tee -a "$WD_LOG"
                cat "$DIAG_LOG" | tee -a "$WD_LOG"
                echo "================" | tee -a "$WD_LOG"

                if grep -q "WATCHDOG_ACTION: HUMAN_NEEDED" "$DIAG_LOG"; then
                    echo "[watchdog] $(date -Is) — Menschlicher Eingriff benötigt. Supervisor stoppt." \
                        | tee -a "$WD_LOG"
                    break
                fi
            fi

            echo "[watchdog] $(date -Is) — Neustart in 30s..." | tee -a "$WD_LOG"
            sleep 30
        done
        exit 0
    fi
    # ── End self-watchdog ───────────────────────────────────────────────────────

    # Persistent state: survives interruptions. Stored inside .git — never committed.
    STATE_FILE="$PROJECT_ROOT/.git/apply_skill_state"

    _save_state() {
        local cycle=$1 skill_i=$2 dir_i=$3 prev=${4:-} cur=${5:-} nxt=${6:-}
        {
            printf 'RESUME_CYCLE=%d\nRESUME_SKILL_IDX=%d\nRESUME_DIR_IDX=%d\n' \
                   "$cycle" "$skill_i" "$dir_i"
            printf 'RESUME_PREV_DIR=%q\nRESUME_CUR_DIR=%q\nRESUME_NEXT_DIR=%q\n' \
                   "$prev" "$cur" "$nxt"
            declare -p VISITED_DIRS 2>/dev/null || echo 'declare -A VISITED_DIRS=()'
        } > "$STATE_FILE"
    }

    _find_start_idx() {
        # Priority 1: RESUME_NEXT_DIR → start there directly
        if [[ -n "${RESUME_NEXT_DIR:-}" ]]; then
            for _i in "${!dirs[@]}"; do
                [[ "${dirs[$_i]}" == "$RESUME_NEXT_DIR" ]] && echo "$_i" && return
            done
        fi
        # Priority 2: RESUME_CUR_DIR → start after it
        if [[ -n "${RESUME_CUR_DIR:-}" ]]; then
            for _i in "${!dirs[@]}"; do
                [[ "${dirs[$_i]}" == "$RESUME_CUR_DIR" ]] && echo "$(( _i + 1 ))" && return
            done
        fi
        # Priority 3: RESUME_PREV_DIR → start two after it (approximate)
        if [[ -n "${RESUME_PREV_DIR:-}" ]]; then
            for _i in "${!dirs[@]}"; do
                [[ "${dirs[$_i]}" == "$RESUME_PREV_DIR" ]] && echo "$(( _i + 2 ))" && return
            done
        fi
        # Fallback: numeric index
        echo "$RESUME_DIR_IDX"
    }

    # Load saved state or start fresh
    RESUME_CYCLE=1; RESUME_SKILL_IDX=0; RESUME_DIR_IDX=0
    RESUME_PREV_DIR=""; RESUME_CUR_DIR=""; RESUME_NEXT_DIR=""
    declare -A VISITED_DIRS=()
    if [[ -f "$STATE_FILE" ]]; then
        # shellcheck source=/dev/null
        source "$STATE_FILE"
        echo "Resuming: cycle=${RESUME_CYCLE}, skill=${RESUME_SKILL_IDX}, idx=${RESUME_DIR_IDX}"
        echo "  anchors: prev=${RESUME_PREV_DIR:-—}  cur=${RESUME_CUR_DIR:-—}  next=${RESUME_NEXT_DIR:-—}"
        echo "  visited: ${#VISITED_DIRS[@]} dirs"
        echo ""
    fi

    cycle="$RESUME_CYCLE"
    _in_resume=true

    while true; do
        echo "╔══════════════════════════════════════════╗"
        echo "  Autonomous Cycle ${cycle} — $(date -Is)"
        echo "╚══════════════════════════════════════════╝"
        echo ""

        for (( skill_i = RESUME_SKILL_IDX; skill_i < ${#AUTONOMOUS_SKILL_CYCLE[@]}; skill_i++ )); do
            SKILL="${AUTONOMOUS_SKILL_CYCLE[$skill_i]}"

            SKILL_FILE="${SCRIPT_DIR}/skills/${SKILL}.md"
            if [[ ! -f "$SKILL_FILE" ]]; then
                echo "WARNING: Skill-Datei nicht gefunden: $SKILL_FILE — überspringe: $SKILL"
                declare -A VISITED_DIRS=()
                _save_state "$cycle" $(( skill_i + 1 )) 0
                continue
            fi
            SKILL_TEXT="$(<"$SKILL_FILE")"
            SKILL_LOG_DIR="$LOG_DIR/cycle_$(printf '%03d' "$cycle")/$SKILL"
            mkdir -p "$SKILL_LOG_DIR"

            echo "╔══════════════════════════════════════════╗"
            echo "  Skill: /$SKILL  (Cycle ${cycle})"
            echo "╚══════════════════════════════════════════╝"
            echo ""

            if is_git_op_skill "$SKILL"; then
                logfile="$SKILL_LOG_DIR/_root.md"
                echo "══════════════════════════════════════════"
                echo "Folder : $PROJECT_ROOT  [git-op, root only]"
                echo "Log    : $logfile"
                echo "══════════════════════════════════════════"
                # /init before commit: loads CLAUDE.md + project structure into context
                if [[ "$SKILL" == "commit" ]]; then
                    run_with_retry "$PROJECT_ROOT" "$SKILL_LOG_DIR/_init.md" "/init" "haiku" 20 \
                        || echo "WARNING: /init exited non-zero"
                fi
                select_agent_config "$SKILL" ""
                echo "Model  : $AGENT_MODEL  Turns: $AGENT_TURNS  Lines: $AGENT_LINE_COUNT"
                PROMPT="$SKILL_TEXT"
                run_with_retry "$PROJECT_ROOT" "$logfile" "$PROMPT" "$AGENT_MODEL" "$AGENT_TURNS" \
                    || echo "WARNING: agent exited non-zero for $PROJECT_ROOT"
                echo "--- Response preview ---"
                _show_preview "$logfile"
                echo ""
                declare -A VISITED_DIRS=()
                _save_state "$cycle" $(( skill_i + 1 )) 0
                _check_sentinel
            else
                # Preserve VISITED_DIRS when resuming mid-skill; reset for any other skill entry.
                if ! $_in_resume || (( skill_i > RESUME_SKILL_IDX )); then
                    declare -A VISITED_DIRS=()
                fi
                _in_resume=false

                # Resolve resume anchors once at skill start, then clear them.
                _rebuild_dirs
                start_dir_i="$(_find_start_idx)"
                RESUME_PREV_DIR=""; RESUME_CUR_DIR=""; RESUME_NEXT_DIR=""; RESUME_DIR_IDX=0

                _cur_dir=""

                # Pre-count eligible dirs for accurate progress display
                _eligible_count=0
                for _d in "${dirs[@]}"; do
                    [[ -d "$_d" ]] || continue
                    _should_skip_dir "$SKILL" "$_d" && continue
                    ((_eligible_count++))
                done
                _processed_count=0

                while true; do
                    _rebuild_dirs   # fresh snapshot before each agent call

                    # Find next unvisited, existing dir at or after start_dir_i
                    _found_dir=""; _found_i=""
                    for _i in "${!dirs[@]}"; do
                        (( _i < start_dir_i )) && continue
                        _d="${dirs[$_i]}"
                        [[ -d "$_d" ]] || continue
                        [[ -n "${VISITED_DIRS[$_d]+x}" ]] && continue
                        if _should_skip_dir "$SKILL" "$_d"; then continue; fi
                        _found_dir="$_d"; _found_i="$_i"; break
                    done
                    [[ -z "$_found_dir" ]] && break   # skill complete

                    # Determine next-dir anchor and mark current as visited
                    _next_dir="${dirs[$(( _found_i + 1 ))]:-}"
                    VISITED_DIRS["$_found_dir"]=1

                    _save_state "$cycle" "$skill_i" "$_found_i" \
                                "$_cur_dir" "$_found_dir" "$_next_dir"

                    ((_processed_count++))
                    _dispatch_dir "$_found_dir" "$SKILL" "$SKILL_TEXT" "$SKILL_LOG_DIR" \
                                  "$cycle" "$_processed_count" "$_eligible_count"

                    _cur_dir="$_found_dir"
                    start_dir_i=0   # always scan from beginning; visited-set handles skips
                    _check_sentinel
                done
                declare -A VISITED_DIRS=()
                _save_state "$cycle" $(( skill_i + 1 )) 0
            fi
        done

        echo "  Cycle ${cycle} abgeschlossen. Starte neu..."
        echo ""
        cycle=$(( cycle + 1 ))
        RESUME_SKILL_IDX=0
        RESUME_DIR_IDX=0
        declare -A VISITED_DIRS=()
        _save_state "$cycle" 0 0
    done
    exit 0
fi

for SKILL in "${SKILLS[@]}"; do
    SKILL_LOG_DIR="$LOG_DIR/$SKILL"
    mkdir -p "$SKILL_LOG_DIR"

    SKILL_FILE="${SCRIPT_DIR}/skills/${SKILL}.md"
    if [[ ! -f "$SKILL_FILE" ]]; then
        echo "WARNING: Skill-Datei nicht gefunden: $SKILL_FILE — überspringe Skill: $SKILL"
        continue
    fi
    SKILL_TEXT="$(<"$SKILL_FILE")"

    echo "╔══════════════════════════════════════════╗"
    echo "  Skill: /$SKILL"
    echo "╚══════════════════════════════════════════╝"
    echo ""

    for dir in "${dirs[@]}"; do
        _dispatch_dir "$dir" "$SKILL" "$SKILL_TEXT" "$SKILL_LOG_DIR"
    done

    echo ""
done

echo "╔══════════════════════════════════════════╗"
echo "  Done. Logs: $LOG_DIR"
echo "╚══════════════════════════════════════════╝"

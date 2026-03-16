#!/usr/bin/env bash
# Usage: ./apply_skill.sh [--autonomous]
#        ./apply_skill.sh close | refresh
#
# Autonomous directory-first review automation using Claude.
# For each directory, determines which skills to run based on deterministic
# rules (UI content, LOC size, position), then runs all applicable skills
# before moving to the next directory.
# Checkpoint (init + commit + sync-main) runs every N directories.
#
# Skill selection per directory:
#   1. triage (always first — backlog triage, uses ops/skills/ops/triage.md)
#   2. review-ui (design + accessibility + UX) — if dir contains ui/res
#   3-6. review-structure, review-architecture, review-onboarding, review-conventions (≥2000 LOC)
#   7-8. review-security, review-performance (last 20% by LOC)
#   9. review-quality (smells + elegance + simplicity)
#
# Logs: $LOG_DIR/cycle_NNN/<skill>/<sanitized-path>.md  +  $LOG_DIR/_run.log

set -uo pipefail

ORIGINAL_ARGS=("$@")
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

if [[ "${1:-}" == "close" || "${1:-}" == "refresh" ]]; then
    _ctrl_root="$(git -C "$SCRIPT_DIR" rev-parse --show-toplevel 2>/dev/null)" \
        || { echo "ERROR: Nicht in einem Git-Repo (SCRIPT_DIR=$SCRIPT_DIR)."; exit 1; }
    touch "$_ctrl_root/.git/apply_skill_${1}"
    echo "${1}: Signal gesetzt ($_ctrl_root/.git/apply_skill_${1})."
    echo "Der laufende Agent wird nach Abschluss angehalten."
    [[ "$1" == "refresh" ]] && echo "Das Skript startet danach neu."
    exit 0
fi

ROOT="$(git rev-parse --show-toplevel 2>/dev/null || pwd)"

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
TRANSIENT_RETRY_SECONDS="${TRANSIENT_RETRY_SECONDS:-120}"
RESET_BUFFER_SECONDS=30
REBUILD_SENTINEL="$PROJECT_ROOT/.git/apply_skill_rebuild_sentinel"
STATE_FILE="$PROJECT_ROOT/.git/apply_skill_state"

if ! command -v claude >/dev/null 2>&1; then
    echo "ERROR: claude nicht gefunden."
    exit 1
fi

source "${SCRIPT_DIR}/lib/apply_skill/skill_selection.sh"
source "${SCRIPT_DIR}/lib/apply_skill/state.sh"
source "${SCRIPT_DIR}/lib/apply_skill/agent_runner.sh"
source "${SCRIPT_DIR}/lib/apply_skill/watchdog.sh"

exec > >(tee "$RUN_LOG") 2>&1

echo "Directory : $ROOT"
echo "Project   : $PROJECT_ROOT"
echo "Logs      : $LOG_DIR"
echo "Run log   : $RUN_LOG"
echo ""

_rebuild_dirs
_start_watchdog_if_needed
_load_state_or_reset

cycle="$RESUME_CYCLE"
CHECKPOINT_INTERVAL=5

while true; do
    echo "╔══════════════════════════════════════════╗"
    echo "  Autonomous Cycle ${cycle} — $(date -Is)"
    echo "╚══════════════════════════════════════════╝"
    echo ""

    _refresh_dirs_if_needed
    _dirs_since_checkpoint=0

    start_dir_i="$(_find_start_idx)"
    _resume_cur_dir="${RESUME_CUR_DIR:-}"
    RESUME_PREV_DIR=""
    RESUME_CUR_DIR=""
    RESUME_NEXT_DIR=""

    _eligible_count=${#dirs[@]}
    _processed_count=0
    for _d in "${dirs[@]}"; do
        [[ -n "${VISITED_DIRS[$_d]+x}" ]] && ((_processed_count++))
    done

    _cur_dir=""

    while true; do
        _refresh_dirs_if_needed

        _found_dir=""
        _found_i=""
        for _i in "${!dirs[@]}"; do
            (( _i < start_dir_i )) && continue
            _d="${dirs[$_i]}"
            [[ -d "$_d" ]] || continue
            [[ -n "${VISITED_DIRS[$_d]+x}" ]] && continue
            _found_dir="$_d"
            _found_i="$_i"
            break
        done
        [[ -z "$_found_dir" ]] && break

        _next_dir="${dirs[$(( _found_i + 1 ))]:-}"
        ((_processed_count++))

        echo "╔══════════════════════════════════════════╗"
        echo "  Dir ${_processed_count}/${_eligible_count}: ${_found_dir}"
        echo "╚══════════════════════════════════════════╝"
        echo ""

        mapfile -t _dir_skills < <(_build_skill_list "$_found_dir" "$_found_i" "$_eligible_count")

        _skill_start=0
        if [[ "$_found_dir" == "$_resume_cur_dir" ]] && (( RESUME_SKILL_IDX > 0 )); then
            _skill_start="$RESUME_SKILL_IDX"
            _resume_cur_dir=""
        fi

        for (( _sj = _skill_start; _sj < ${#_dir_skills[@]}; _sj++ )); do
            _skill="${_dir_skills[$_sj]}"
            _skill_text=""

            if [[ "$_skill" == "triage" ]]; then
                _skill_file="${SCRIPT_DIR}/skills/ops/${_skill}.md"
                if [[ ! -f "$_skill_file" ]]; then
                    echo "WARNING: Skill-Datei nicht gefunden: $_skill_file — überspringe"
                    continue
                fi
                _skill_text="$(<"$_skill_file")"
            fi

            _skill_log_dir="$LOG_DIR/cycle_$(printf '%03d' "$cycle")/$_skill"
            mkdir -p "$_skill_log_dir"

            _save_state "$cycle" "$_found_i" "$_sj" \
                        "$_cur_dir" "$_found_dir" "$_next_dir"

            _dispatch_dir "$_found_dir" "$_skill" "$_skill_text" "$_skill_log_dir" \
                          "$cycle" "$_processed_count" "$_eligible_count"

            _check_sentinel
        done

        VISITED_DIRS["$_found_dir"]=1
        _cur_dir="$_found_dir"
        start_dir_i=0
        RESUME_SKILL_IDX=0

        ((_dirs_since_checkpoint++))
        if (( _dirs_since_checkpoint >= CHECKPOINT_INTERVAL )); then
            _dispatch_checkpoint "$cycle"
            _dirs_since_checkpoint=0
        fi

        _check_sentinel
    done

    _dispatch_checkpoint "$cycle"

    echo "  Cycle ${cycle} abgeschlossen. Starte neu..."
    echo ""
    cycle=$(( cycle + 1 ))
    RESUME_DIR_IDX=0
    RESUME_SKILL_IDX=0
    declare -A VISITED_DIRS=()
    _save_state "$cycle" 0 0
done

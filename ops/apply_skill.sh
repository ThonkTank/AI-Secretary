#!/usr/bin/env bash
# Usage: ./apply_skill.sh <skill-name> <directory> [project-root]
#
# Runs the given Claude Code skill on each subdirectory of <directory>,
# processing the deepest folders first and working upward to the root.
# Claude is always started from <project-root> (default: current directory)
# so it has full project context (CLAUDE.md, git history, etc.).
# The target directory path is passed as an argument to the skill.
#
# Logs: each agent's final response is saved to $LOG_DIR/<sanitized-path>.md
#
# NOTE: Skills that trigger plan mode via --dev (e.g. review-architecture --dev,
# review-accessibility --dev) may stall in non-interactive mode, since ExitPlanMode
# normally awaits manual user approval. Use plan-mode-aware skills with caution.

set -uo pipefail

SKILL="${1:?Usage: $0 <skill-name> <directory> [project-root]}"
ROOT="${2:?Usage: $0 <skill-name> <directory> [project-root]}"
ROOT="$(realpath "$ROOT")"
PROJECT_ROOT="$(realpath "${3:-$(pwd)}")"

TIMESTAMP="$(date +%Y%m%d_%H%M%S)"
LOG_DIR="/tmp/apply_skill_${TIMESTAMP}"
mkdir -p "$LOG_DIR"

echo "Skill   : /$SKILL"
echo "Root    : $ROOT"
echo "Project : $PROJECT_ROOT"
echo "Logs    : $LOG_DIR"
echo ""

# Collect all directories, sort by depth descending (deepest first).
# awk counts '/' characters as a depth proxy, sort -rn puts highest first.
mapfile -t dirs < <(
    find "$ROOT" -type d \
    | awk '{ print gsub("/", "/", $0), $0 }' \
    | sort -rn \
    | awk '{ print $2 }'
)

for dir in "${dirs[@]}"; do
    safe_name="${dir//\//_}"
    logfile="$LOG_DIR/${safe_name}.md"

    echo "══════════════════════════════════════════"
    echo "Folder : $dir"
    echo "Log    : $logfile"
    echo "══════════════════════════════════════════"

    PROMPT="/$SKILL $dir --dev

IMPORTANT: Do NOT use plan mode. Do NOT call EnterPlanMode under any circumstances.
You are running inside an automated, non-interactive script. If you enter plan mode,
the script will hang indefinitely because there is no human present to approve the
ExitPlanMode call — this is a fatal error for the entire run.
Instead: read and analyse the code, plan your approach mentally, then execute it
directly within this session.

You have a budget of 50 tool calls (turns) for this entire task. Plan accordingly:
prioritise the most impactful findings and changes, and avoid redundant reads or
searches. If the directory is large, focus on the most important files first."

    (cd "$PROJECT_ROOT" && unset CLAUDECODE && claude \
        --dangerously-skip-permissions \
        --model sonnet \
        --max-turns 50 \
        -p "$PROMPT" \
        > "$logfile" 2>&1) \
    || echo "WARNING: claude exited non-zero for $dir"

    echo "--- Response preview ---"
    tail -5 "$logfile"
    echo ""
done

echo "══════════════════════════════════════════"
echo "Done. Logs: $LOG_DIR"

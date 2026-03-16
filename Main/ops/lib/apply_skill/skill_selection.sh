#!/usr/bin/env bash

# Count lines of code recursively, excluding hidden/build/gradle dirs.
_count_loc() {
    local dir="${1:-.}"
    find "$dir" \( -name '.*' -o -name 'build' -o -name 'gradle' \) -prune \
        -o -type f -regex '.*\.\(java\|kt\|xml\|sh\|md\|kts\|properties\)$' -print \
        | xargs wc -l 2>/dev/null | tail -1 | awk '{print $1}'
}

# Check recursively whether a directory contains ui/ or res/ subdirectories.
_has_ui_content() {
    [[ -n "$(find "$1" -type d \( -name 'ui' -o -name 'res' \) -print -quit 2>/dev/null)" ]]
}

# Check whether triage is useful: needs >=2 REVIEW_BACKLOG.md files in scope.
_needs_triage() {
    (( $(find "$1" -name 'REVIEW_BACKLOG.md' 2>/dev/null | head -2 | wc -l) >= 2 ))
}

# Deterministic skill selection for a directory. Outputs one skill name per line.
_build_skill_list() {
    local dir="$1" dir_i="${2:-0}" total_dirs="${3:-0}"
    local loc
    loc=$(_count_loc "$dir")
    loc=${loc:-0}
    local top20_threshold=$(( total_dirs * 4 / 5 ))
    (( top20_threshold < 1 )) && top20_threshold=1

    if _needs_triage "$dir"; then
        echo "triage"
    fi

    if _has_ui_content "$dir"; then
        echo "review-ui"
    fi

    if (( loc >= 2000 )); then
        echo "review-structure"
        echo "review-architecture"
        echo "review-onboarding"
        echo "review-conventions"
    fi

    if (( total_dirs > 0 && dir_i > top20_threshold )); then
        echo "review-security"
        echo "review-performance"
    fi

    echo "review-quality"
}

# Model selection — simple LOC-based tiers.
_select_agent_config() {
    local skill="$1"
    local dir="$2"
    local dir_i="${3:-0}"
    local total_dirs="${4:-0}"

    local count_dir="${dir:-$PROJECT_ROOT}"
    local line_count
    line_count=$(_count_loc "$count_dir")
    line_count=${line_count:-0}

    if (( line_count < 1000 )); then
        AGENT_MODEL="haiku"
    else
        AGENT_MODEL="sonnet"
    fi

    if (( total_dirs > 0 && dir_i > 0 )); then
        local opus_threshold=$(( total_dirs * 9 / 10 ))
        (( opus_threshold < 1 )) && opus_threshold=1
        if (( dir_i > opus_threshold )); then
            AGENT_MODEL="opus"
        fi
    fi

    AGENT_LINE_COUNT="$line_count"
}

_should_rebuild_dirs() {
    [[ ! -f "$REBUILD_SENTINEL" ]] && return 0
    find "$ROOT" \( -name '.*' -o -name 'build' -o -name 'gradle' \) -prune \
        -o \( -type d -o -type f -regex '.*\.\(java\|kt\|xml\|sh\|md\|kts\|properties\)$' \) \
        -newer "$REBUILD_SENTINEL" -print -quit | grep -q .
}

# Collect all directories, sort by LOC ascending (smallest first).
# Skip pass-through directories and tiny review scopes.
_rebuild_dirs() {
    mapfile -t dirs < <(
        find "$ROOT" \( -name '.*' -o -name 'build' -o -name 'gradle' \) -prune -o -type d -print \
        | while IFS= read -r d; do
            read -r children subdirs < <(find "$d" -maxdepth 1 -mindepth 1 -printf '%y\n' 2>/dev/null \
                | awk '{c++; if($0=="d")s++} END{print c, s}')
            (( children == 1 && subdirs == 1 )) && continue
            loc=$(_count_loc "$d")
            loc=${loc:-0}
            (( loc < 100 )) && continue
            echo "$loc $d"
          done \
        | sort -n \
        | awk '{ print $2 }'
    )
    touch "$REBUILD_SENTINEL"
}

_refresh_dirs_if_needed() {
    if _should_rebuild_dirs; then
        _rebuild_dirs
    fi
}

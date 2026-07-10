#!/usr/bin/env bash

STATE_VERSION=4

_encode_state_b64() {
    printf '%s' "${1:-}" | base64 | tr -d '\n'
}

_decode_state_b64() {
    if [[ -z "${1:-}" ]]; then
        return 0
    fi
    printf '%s' "$1" | base64 -d 2>/dev/null
}

_save_state() {
    local cycle=$1 dir_i=$2 skill_i=$3 prev=${4:-} cur=${5:-} nxt=${6:-}
    {
        printf 'RESUME_STATE_VERSION=%d\n' "$STATE_VERSION"
        printf 'RESUME_CYCLE=%d\nRESUME_DIR_IDX=%d\nRESUME_SKILL_IDX=%d\n' \
               "$cycle" "$dir_i" "$skill_i"
        printf 'RESUME_PREV_DIR_B64=%s\n' "$(_encode_state_b64 "$prev")"
        printf 'RESUME_CUR_DIR_B64=%s\n' "$(_encode_state_b64 "$cur")"
        printf 'RESUME_NEXT_DIR_B64=%s\n' "$(_encode_state_b64 "$nxt")"
        for _visited_dir in "${!VISITED_DIRS[@]}"; do
            printf 'VISITED_DIR_B64=%s\n' "$(_encode_state_b64 "$_visited_dir")"
        done
    } > "$STATE_FILE"
}

_load_state() {
    local line key value decoded
    while IFS= read -r line || [[ -n "$line" ]]; do
        [[ -z "$line" ]] && continue
        [[ "$line" == *=* ]] || return 1
        key=${line%%=*}
        value=${line#*=}
        case "$key" in
            RESUME_STATE_VERSION|RESUME_CYCLE|RESUME_DIR_IDX|RESUME_SKILL_IDX)
                [[ "$value" =~ ^[0-9]+$ ]] || return 1
                printf -v "$key" '%d' "$value"
                ;;
            RESUME_PREV_DIR_B64)
                decoded=$(_decode_state_b64 "$value") || return 1
                RESUME_PREV_DIR="$decoded"
                ;;
            RESUME_CUR_DIR_B64)
                decoded=$(_decode_state_b64 "$value") || return 1
                RESUME_CUR_DIR="$decoded"
                ;;
            RESUME_NEXT_DIR_B64)
                decoded=$(_decode_state_b64 "$value") || return 1
                RESUME_NEXT_DIR="$decoded"
                ;;
            VISITED_DIR_B64)
                decoded=$(_decode_state_b64 "$value") || return 1
                VISITED_DIRS["$decoded"]=1
                ;;
            *)
                return 1
                ;;
        esac
    done < "$STATE_FILE"
}

_find_start_idx() {
    if [[ -n "${RESUME_CUR_DIR:-}" ]]; then
        for _i in "${!dirs[@]}"; do
            [[ "${dirs[$_i]}" == "$RESUME_CUR_DIR" ]] && echo "$_i" && return
        done
    fi
    if [[ -n "${RESUME_NEXT_DIR:-}" ]]; then
        for _i in "${!dirs[@]}"; do
            [[ "${dirs[$_i]}" == "$RESUME_NEXT_DIR" ]] && echo "$_i" && return
        done
    fi
    if [[ -n "${RESUME_PREV_DIR:-}" ]]; then
        for _i in "${!dirs[@]}"; do
            [[ "${dirs[$_i]}" == "$RESUME_PREV_DIR" ]] && echo "$(( _i + 1 ))" && return
        done
    fi
    echo "$RESUME_DIR_IDX"
}

_reset_resume_state() {
    RESUME_CYCLE=1
    RESUME_DIR_IDX=0
    RESUME_SKILL_IDX=0
    RESUME_PREV_DIR=""
    RESUME_CUR_DIR=""
    RESUME_NEXT_DIR=""
    RESUME_STATE_VERSION=0
    declare -gA VISITED_DIRS=()
}

_load_state_or_reset() {
    _reset_resume_state
    [[ -f "$STATE_FILE" ]] || return 0

    if ! _load_state; then
        echo "State-File ist ungueltig. Verwerfe."
        rm -f "$STATE_FILE"
        _reset_resume_state
        return 0
    fi

    if (( RESUME_STATE_VERSION != STATE_VERSION )); then
        echo "State-File hat veraltetes Format (v${RESUME_STATE_VERSION} != v${STATE_VERSION}). Verwerfe."
        rm -f "$STATE_FILE"
        _reset_resume_state
        return 0
    fi

    echo "Resuming: cycle=${RESUME_CYCLE}, dir=${RESUME_DIR_IDX}, skill=${RESUME_SKILL_IDX}"
    echo "  anchors: prev=${RESUME_PREV_DIR:-—}  cur=${RESUME_CUR_DIR:-—}  next=${RESUME_NEXT_DIR:-—}"
    echo "  visited: ${#VISITED_DIRS[@]} dirs"
    echo ""
}

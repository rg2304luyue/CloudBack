#!/usr/bin/env bash
# ============================================================
# PreToolUse Hook: block access to .env files
# Allows .env.example, blocks all other .env* files
# No external dependencies (pure bash + grep)
# ============================================================

set -euo pipefail

INPUT=$(cat)

# Extract tool_name from JSON without jq
TOOL_NAME=$(echo "$INPUT" | grep -oP '"tool_name"\s*:\s*"\K[^"]*' || echo "")

# ---------- check if a path references a blocked .env ----------
# Returns 0 (true) if blocked, 1 (false) if allowed
is_blocked_env() {
    local path="$1"
    local basename="${path##*/}"

    # ALLOW: .env.example
    case "$basename" in
        .env.example|.env.example.*) return 1 ;;
    esac

    # ALLOW: path contains .env.example anywhere
    case "$path" in
        *.env.example*) return 1 ;;
    esac

    # BLOCK: .env (exact), .env.local, .env.prod, etc.
    case "$basename" in
        .env|.env.*) return 0 ;;
    esac

    return 1
}

# ---------- extract relevant path from tool input ----------
extract_path() {
    local tool="$1"
    case "$tool" in
        Read|Write|Edit)
            grep -oP '"file_path"\s*:\s*"\K[^"]*' <<< "$INPUT" || echo "" ;;
        Glob)
            grep -oP '"pattern"\s*:\s*"\K[^"]*' <<< "$INPUT" || echo "" ;;
        Grep)
            grep -oP '"path"\s*:\s*"\K[^"]*' <<< "$INPUT" || echo "" ;;
        Bash)
            grep -oP '"command"\s*:\s*"\K[^"]*' <<< "$INPUT" || echo "" ;;
        *)
            echo "" ;;
    esac
}

# ---------- main ----------
TARGET=$(extract_path "$TOOL_NAME")

if [ -n "$TARGET" ] && is_blocked_env "$TARGET"; then
    printf '{"hookSpecificOutput":{"hookEventName":"PreToolUse","permissionDecision":"deny","permissionDecisionReason":"🚫 .env files are blocked (contains secrets). Use .env.example instead."}}'
    exit 0
fi

# Allow
printf '{"hookSpecificOutput":{"hookEventName":"PreToolUse","permissionDecision":"allow"}}'

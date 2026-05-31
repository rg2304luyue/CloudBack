#!/usr/bin/env bash
# ============================================================
# PreToolUse Hook: block access to .env files
# Allows .env.example, blocks all other .env* files
# ============================================================

set -euo pipefail

INPUT=$(cat)
TOOL_NAME=$(echo "$INPUT" | jq -r '.tool_name // ""')
TOOL_INPUT=$(echo "$INPUT" | jq -c '.tool_input // {}')

# ---------- check if path is a blocked .env ----------
is_blocked_env() {
    local path="$1"
    local basename
    basename=$(basename "$path")

    # ALLOW: .env.example
    if [[ "$basename" == .env.example ]] || [[ "$basename" == .env.example.* ]]; then
        return 1
    fi
    if echo "$path" | grep -q '\.env\.example'; then
        return 1
    fi

    # BLOCK: .env, .env.local, .env.prod, .env.development ...
    if [[ "$basename" == .env ]] || [[ "$basename" == .env.* ]]; then
        return 0
    fi

    # BLOCK: path segments ending with /.env (e.g. docker/.env)
    if echo "$path" | grep -qE '(^|/)\.env($|/)'; then
        return 0
    fi

    return 1
}

# ---------- extract file paths from tool input ----------
extract_paths() {
    local input="$1"; local tool="$2"
    case "$tool" in
        Read|Write|Edit)
            echo "$input" | jq -r '.file_path // ""' ;;
        Glob)
            echo "$input" | jq -r '.pattern // ""' ;;
        Grep)
            echo "$input" | jq -r '.path // ""' ;;
        Bash)
            echo "$input" | jq -r '.command // ""' | grep -oP '\S*\.env\S*' || true ;;
    esac
}

# ---------- main ----------
BLOCKED=""
PATHS=$(extract_paths "$TOOL_INPUT" "$TOOL_NAME")

while IFS= read -r path; do
    [ -z "$path" ] || [ "$path" = "null" ] && continue
    if is_blocked_env "$path"; then
        BLOCKED="$path"
        break
    fi
done <<< "$PATHS"

if [ -n "$BLOCKED" ]; then
    jq -n --arg path "$BLOCKED" '{
        permissionDecision: "deny",
        reason: "🚫 .env files are blocked (contains secrets). Use .env.example instead.",
        blockedPath: $path
    }'
    exit 0
fi

jq -n '{ permissionDecision: "allow" }'

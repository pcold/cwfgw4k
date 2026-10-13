#!/bin/bash
input=$(cat)
MODEL=$(echo "$input" | jq -r '.model.display_name')
# Present only on a model that supports reasoning effort.
EFFORT=$(echo "$input" | jq -r '.effort.level // empty')
[ -n "$EFFORT" ] && MODEL="$MODEL · $EFFORT"
DIR=$(echo "$input" | jq -r '.workspace.current_dir')
GIT_BRANCH=""
if git rev-parse --git-dir > /dev/null 2>&1; then
  BRANCH=$(git branch --show-current 2>/dev/null)
  [ -n "$BRANCH" ] && GIT_BRANCH=" | $BRANCH"
fi
ACCOUNT_EMAIL=$(jq -r '.oauthAccount.emailAddress // empty' ~/.claude.json 2>/dev/null)
[ -n "$ACCOUNT_EMAIL" ] && GIT_BRANCH="$GIT_BRANCH | $ACCOUNT_EMAIL"

# Denominator is where auto-compact actually fires, not the model's raw window:
# CLAUDE_CODE_AUTO_COMPACT_WINDOW caps it, so on a 1M model at a 200k setting the
# raw window would read under 20% full when compaction is about to trigger.
CONTEXT=$(echo "$input" | jq -r --arg cap "${CLAUDE_CODE_AUTO_COMPACT_WINDOW:-}" '
  .context_window
  | select(. != null and (.context_window_size // 0) > 0)
  | (if ($cap | length) > 0 and ($cap | tonumber) < .context_window_size
     then ($cap | tonumber) else .context_window_size end) as $w
  | " | \((.total_input_tokens / 1000) | round)k/\(($w / 1000) | round)k (\((.total_input_tokens / $w * 100) | round)%)"
' 2>/dev/null)
# Absent for non-subscribers, and for subscribers until the first API response of
# a session lands -- so every level here has to degrade to an empty string rather
# than print a zero, which would read as "no usage" instead of "not known yet".
LIMITS=$(echo "$input" | jq -r '
  .rate_limits
  | select(. != null)
  | [ (.five_hour.used_percentage  // empty | " 5h:\(round)%"),
      (.seven_day.used_percentage  // empty | " 7d:\(round)%") ]
  | join("")
  | select(length > 0)
  | " |" + .
' 2>/dev/null)

echo "[$MODEL] ${DIR##*/}$GIT_BRANCH$CONTEXT$LIMITS"

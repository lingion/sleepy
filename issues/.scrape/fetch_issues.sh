#!/usr/bin/env bash
set -euo pipefail

REPO="${1:-lingion/sleepy}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
API_ROOT="repos/${REPO}"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

mkdir -p "$ROOT"

# REST JSON is kept as-is so no issue field or reaction is lost.
gh api --paginate -H 'Accept: application/vnd.github+json' \
  "$API_ROOT/issues?state=all&per_page=100" \
  | jq -s 'add | map(select(.pull_request|not))' > "$TMP/issues.json"

jq '[.[] | {number,title,state,html_url,created_at,updated_at,closed_at}] | sort_by(.number)' \
  "$TMP/issues.json" > "$ROOT/_index.json"

printf '# GitHub issue archive: %s\\n\\n' "$REPO" > "$ROOT/_index.md"
printf 'Generated: %s\\n\\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" >> "$ROOT/_index.md"
jq -r '.[] | "- [#\(.number) \(.title)](\(.html_url)) — \(.state) — \(.updated_at)"' \
  "$ROOT/_index.json" >> "$ROOT/_index.md"

count=0
failed=0
attachment_count=0

while IFS= read -r row; do
  number="$(jq -r '.number' <<<"$row")"
  title="$(jq -r '.title' <<<"$row")"
  slug="$(printf '%s' "$title" | tr '[:upper:]' '[:lower:]' | sed -E 's/[^a-z0-9]+/-/g; s/^-+//; s/-+$//' | cut -c1-80)"
  dir="$ROOT/$(printf '%04d' "$number")-${slug:-issue}"
  mkdir -p "$dir/attachments"

  if ! gh api -H 'Accept: application/vnd.github+json' "$API_ROOT/issues/$number" > "$dir/issue.json"; then
    printf 'issue #%s: issue endpoint failed\\n' "$number" >&2
    failed=$((failed + 1)); continue
  fi
  if ! gh api --paginate -H 'Accept: application/vnd.github+json' "$API_ROOT/issues/$number/comments?per_page=100" \
      | jq -s 'add' > "$dir/comments.json"; then
    printf 'issue #%s: comments endpoint failed\\n' "$number" >&2
    failed=$((failed + 1)); continue
  fi
  if ! gh api --paginate -H 'Accept: application/vnd.github+json' "$API_ROOT/issues/$number/timeline?per_page=100" \
      | jq -s 'add' > "$dir/events.json"; then
    printf 'issue #%s: timeline endpoint failed\\n' "$number" >&2
    failed=$((failed + 1)); continue
  fi

  jq -r '.body // ""' "$dir/issue.json" > "$dir/body.txt"
  {
    jq -r '"# \(.title)\\n\\n- Number: #\(.number)\\n- State: \(.state)\\n- Author: \(.user.login // "unknown")\\n- Created: \(.created_at)\\n- Updated: \(.updated_at)\\n- URL: \(.html_url)\\n\\n## Body\\n\\n\(.body // "")"' "$dir/issue.json"
    jq -r 'if length == 0 then "\\n\\n## Comments\\n\\n(no comments)" else "\\n\\n## Comments\\n\\n" + (map("### " + (.user.login // "unknown") + " — " + .created_at + "\\n\\n" + (.body // "")) | join("\\n\\n")) end' "$dir/comments.json"
  } > "$dir/issue.md"

  # Extract URLs from issue body and every comment. Deduplicate before download.
  {
    jq -r '.body // ""' "$dir/issue.json"
    jq -r '.[].body // ""' "$dir/comments.json"
  } | grep -Eo 'https?://[^[:space:]>)"'"'"'\\]+' | sed -E 's/[.,]$//' | sort -u > "$dir/attachments.urls" || true

  while IFS= read -r url; do
    [ -n "$url" ] || continue
    case "$url" in
      https://github.com/user-attachments/assets/*|https://github.com/user-attachments/files/*|https://user-images.githubusercontent.com/*|https://github.com/*/assets/*|https://github.com/*/files/*)
        ;;
      *) continue ;;
    esac
    hash="$(printf '%s' "$url" | shasum -a 1 | cut -c1-8)"
    base="$(basename "${url%%\?*}")"
    ext="${base##*.}"
    case "$ext" in
      "$base"|''|*[!A-Za-z0-9]) ext="bin" ;;
    esac
    out="$dir/attachments/${hash}.${ext}"
    if [ ! -s "$out" ]; then
      if curl --http1.1 --fail --location --retry 5 --retry-delay 2 --retry-all-errors \
          --connect-timeout 20 --max-time 180 --silent --show-error \
          -H 'Accept: application/octet-stream' -o "$out" "$url"; then
        attachment_count=$((attachment_count + 1))
      else
        rm -f "$out"
        printf 'issue #%s: attachment failed %s\\n' "$number" "$url" >&2
      fi
    fi
  done < "$dir/attachments.urls"

  rm -f "$dir/attachments.urls"
  count=$((count + 1))
  printf 'archived #%s (%s/%s)\\n' "$number" "$count" "$(jq 'length' "$ROOT/_index.json")" >&2
done < <(jq -c '.[]' "$ROOT/_index.json")

printf '\nArchived %s issues; failed %s issue requests; downloaded %s new attachments.\\n' "$count" "$failed" "$attachment_count"

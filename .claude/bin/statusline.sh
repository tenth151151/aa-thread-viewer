#!/usr/bin/env bash
# Claude Code のステータスラインを組み立てる。
# 標準入力で受け取った JSON から、ディレクトリ、ブランチ、コンテキスト使用量、モデルを表示する。

input=$(cat)

dir=$(jq -r '.workspace.current_dir // .cwd // ""' <<<"$input")
model=$(jq -r '.model.display_name // "unknown"' <<<"$input")
size=$(jq -r '.context_window.context_window_size // 0' <<<"$input")
# 現在のコンテキスト使用量は、直近の呼び出しの入力トークン合計とする。
used=$(jq -r '(.context_window.current_usage // {}) | (.input_tokens // 0) + (.cache_creation_input_tokens // 0) + (.cache_read_input_tokens // 0)' <<<"$input")

branch=""
if [ -n "$dir" ]; then
  branch=$(git -C "$dir" symbolic-ref --short HEAD 2>/dev/null || git -C "$dir" rev-parse --short HEAD 2>/dev/null)
fi

# トークン数を 12.3k のような短い表記にする。
fmt() {
  awk -v n="$1" 'BEGIN { if (n >= 1000000) printf "%.1fM", n / 1000000; else if (n >= 1000) printf "%.1fk", n / 1000; else printf "%d", n }'
}

if [ "$size" -gt 0 ]; then
  pct=$((used * 100 / size))
  ctx="$(fmt "$used") / $(fmt "$size") (${pct}%)"
else
  ctx="$(fmt "$used")"
fi

printf '📁 %s' "${dir##*/}"
[ -n "$branch" ] && printf ' | 🌿 %s' "$branch"
printf ' | 🧠 %s | 🤖 %s\n' "$ctx" "$model"

#!/usr/bin/env bash
# =============================================================
# check-windows-script-encoding.sh
#   Windows で動かすスクリプトの文字コードの決まりを調べるスクリプト。
#   - .ps1: 先頭が BOM (EF BB BF) の UTF-8 であること。Windows PowerShell 5.1 は、
#     BOM がないと日本語を読み違える。
#   - .bat: ASCII の文字だけであること (0x80 以上のバイトを含まない)。cmd.exe は、
#     日本語の行を読み違える。BOM も ASCII ではないバイトとして扱う。
#
# 使い方:
#   bin/check-windows-script-encoding.sh
#   引数は取らない。どこから実行しても、スクリプトのあるリポジトリ全体を調べる。
#
# 終了コード:
#   0  決まりに反するファイルは見つからなかった。
#   1  決まりに反するファイルが見つかった。場所と直し方を標準エラー出力に出す。
#   2 以上  git が失敗した、git のリポジトリの外で実行した、またはスクリプトが
#           予期しない理由で止まった。結果は分からないので、問題なしとは扱わない。
#
# ※ git で管理しているファイルと、まだ追跡していないファイルを調べる
#    (.gitignore されたファイルは除く)。存在しないファイルは飛ばす。
# ※ 拡張子の大文字と小文字は区別しない (Windows が区別しないため)。
# ※ ファイルは書き換えない。
# ※ CI から呼ばれる。
# =============================================================

set -euo pipefail

# 予期しない失敗で止まるときも、終了コードを 2 以上にそろえる。
# 1 は「見つかった」専用なので、失敗と区別できなくなるのを防ぐ。
# メッセージは標準エラー出力に出す。リダイレクトは左から順に効くので、先に >&2 で
# 標準出力を標準エラー出力へ向けてから、2>/dev/null で echo 自身のエラーだけを捨てる。
trap 'rc=$?; echo "[check-windows-script-encoding] 予期しないエラーで終了しました (終了コード: $rc)。" >&2 2>/dev/null || true; if [ "$rc" -ge 2 ]; then exit "$rc"; fi; exit 2' ERR

# バイト列で調べるので、locale に左右されないようにする。
export LC_ALL=C

# cd が CDPATH に左右されないようにする。
unset CDPATH

# 標準エラー出力が端末のときだけ色を付ける。
if [ -t 2 ]; then
  prefix=$'\033[1;31m[check-windows-script-encoding]\033[0m'
else
  prefix='[check-windows-script-encoding]'
fi

# どこから実行しても、スクリプトのあるリポジトリ全体を同じ範囲で調べる。
root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd -P)"

# 対象のファイルの一覧を、いったんファイルに受ける。
# パイプやプロセス置換にすると、git の失敗が握りつぶされるため。
list_file="$(mktemp)"
trap 'rm -f "$list_file"' EXIT

rc=0
# マージの衝突中は、--cached が同じパスをステージごとに出すので、--deduplicate で 1 回にする。
git -C "$root" ls-files -z --deduplicate --cached --others --exclude-standard -- ':(icase)*.ps1' ':(icase)*.bat' >"$list_file" || rc=$?
if [ "$rc" -ne 0 ]; then
  echo "$prefix git ls-files が失敗しました (終了コード: $rc)。" >&2
  # 1 は「見つかった」専用なので、2 未満なら 2 にする。
  if [ "$rc" -lt 2 ]; then
    rc=2
  fi
  exit "$rc"
fi

bom="efbbbf"
bad_ps1=()
bad_bat=()

# 拡張子の大文字と小文字を区別しない (case の照合にも効かせる)。
shopt -s nocasematch

while IFS= read -r -d '' rel; do
  path="$root/$rel"
  # 削除済みでまだ index に残るファイルなどは、調べない。
  [ -f "$path" ] || continue
  case "$rel" in
    *.ps1)
      head3="$(head -c 3 -- "$path" | od -An -v -tx1 | tr -d ' \n')"
      if [ "$head3" != "$bom" ]; then
        bad_ps1+=("$rel")
      fi
      ;;
    *.bat)
      non_ascii="$(tr -d '\000-\177' <"$path" | wc -c)"
      if [ "$non_ascii" -ne 0 ]; then
        bad_bat+=("$rel")
      fi
      ;;
  esac
done <"$list_file"

if [ "${#bad_ps1[@]}" -gt 0 ]; then
  echo "$prefix BOM 付きの UTF-8 ではない .ps1 が見つかりました。" >&2
  for f in "${bad_ps1[@]}"; do
    echo "  $f" >&2
  done
  echo "$prefix BOM 付きの UTF-8 で保存し直してください。" >&2
fi
if [ "${#bad_bat[@]}" -gt 0 ]; then
  echo "$prefix ASCII ではない文字 (BOM を含む) を含む .bat が見つかりました。" >&2
  for f in "${bad_bat[@]}"; do
    echo "  $f" >&2
  done
  echo "$prefix ASCII ではない文字を消してください。BOM も取り除きます。" >&2
fi
if [ "${#bad_ps1[@]}" -gt 0 ] || [ "${#bad_bat[@]}" -gt 0 ]; then
  echo "$prefix 詳しくは README.md を参照してください。" >&2
  exit 1
fi
exit 0

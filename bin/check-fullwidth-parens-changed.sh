#!/usr/bin/env bash
# =============================================================
# check-fullwidth-parens-changed.sh
#   git の作業ツリーで変わったファイル (HEAD との差分と未追跡のファイル) に、
#   全角の丸括弧 (U+FF08、U+FF09) が残っていないかを調べるスクリプト。
#   Bash のコマンドで書き換えたファイルを、Claude Code のフックで検査するために使う。
#
# 使い方:
#   bin/check-fullwidth-parens-changed.sh
#   カレントディレクトリが属する git のリポジトリ (別の worktree を含む) を調べる。
#   フックの入力 (JSON) が標準入力にあり、jq が使えるときは、その cwd を使う。
#
# 終了コード:
#   0  全角の丸括弧は見つからなかった。変わったファイルがないときや、
#      git のリポジトリの外にいるときも 0 で終わる。
#   1  全角の丸括弧が見つかった。場所は check-fullwidth-parens.sh が標準エラー出力に出す。
#   2 以上  検査そのものが失敗した (git が失敗したときを含む)。結果は分からない。
#
# ※ 変わったファイルの一覧 (削除したファイルは除く) を集め、check-fullwidth-parens.sh に
#    一度に渡す。検査の中身と見つかった箇所の表示は、そちらに任せる。
#    .gitignore されたファイルと、app/src/*/res/ と app/src/*/resources/ の下は、
#    そちらの決まりで対象外になる。
# ※ HEAD との差分なので、書き換えたファイルをその同じコマンドの中でコミットしたときは見つからない。
#    コミットされたファイルは CI で見つかる。
#    別のリポジトリのファイルを絶対パスで書き換えたときも見つからない。
#    このリポジトリの検査の対象外なので、このリポジトリの CI でも見つからない。
# ※ もとから全角の丸括弧を含む変更があると、直すまで毎回見つかる。
# =============================================================

set -euo pipefail

trap 'rc=$?; { echo "[check-fullwidth-parens-changed] 予期しないエラーで終了しました (終了コード: $rc)。" >&2; } 2>/dev/null || true; if [ "$rc" -ge 2 ]; then exit "$rc"; fi; exit 2' ERR

# git のエラーメッセージを英語に固定する (下の rev-parse の判定で使う)。
export LC_ALL=C

unset CDPATH

here="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"

# フックの入力から cwd を取る。標準入力が端末のとき、jq がないとき、cwd がないときは、カレントディレクトリを使う。
dir="$PWD"
if [ ! -t 0 ] && command -v jq >/dev/null 2>&1; then
  cwd="$(jq -r '.cwd // empty' 2>/dev/null || true)"
  if [ -n "$cwd" ] && [ -d "$cwd" ]; then
    dir="$cwd"
  fi
fi

# git のリポジトリの外では調べない。
# git が「not a git repository」と言うとき以外の失敗は、検査の失敗にする。
# 標準エラー出力は別に受ける。成功したときの警告が、パスに混ざらないようにするため。
rc=0
err_file="$(mktemp)"
root="$(git -C "$dir" rev-parse --show-toplevel 2>"$err_file")" || rc=$?
err_msg="$(cat "$err_file")"
rm -f "$err_file"
if [ "$rc" -ne 0 ]; then
  case "$err_msg" in
    *"not a git repository"*) exit 0 ;;
    *)
      echo "[check-fullwidth-parens-changed] git rev-parse が失敗しました (終了コード: $rc)。" >&2
      echo "$err_msg" >&2
      # 1 は「全角が見つかった」専用なので、2 未満なら 2 にする。
      if [ "$rc" -lt 2 ]; then
        rc=2
      fi
      exit "$rc"
      ;;
  esac
fi

# 一覧は NUL 区切りで、変数に入れられないので、一時ファイルに書く。
# git が失敗したときに、一覧が空になって 0 で終わらないよう、ここで失敗を検出する。
list="$(mktemp)"
trap 'rm -f "$list"' EXIT

# HEAD がまだないリポジトリでは、差分を取らず、未追跡のファイルだけを調べる。
# rev-parse は、HEAD がないときは 1、それ以外の失敗は 2 以上を返す。
rc=0
git -C "$root" rev-parse --verify -q HEAD >/dev/null 2>&1 || rc=$?
case "$rc" in
  0)
    if ! git -C "$root" diff --name-only --diff-filter=d -z HEAD -- >> "$list"; then
      echo "[check-fullwidth-parens-changed] git diff が失敗しました。" >&2
      exit 2
    fi
    ;;
  1) ;;
  *)
    echo "[check-fullwidth-parens-changed] git rev-parse が失敗しました (終了コード: $rc)。" >&2
    exit 2
    ;;
esac
if ! git -C "$root" ls-files -o --exclude-standard -z >> "$list"; then
  echo "[check-fullwidth-parens-changed] git ls-files が失敗しました。" >&2
  exit 2
fi

files=()
while IFS= read -r -d '' f; do
  files+=("$root/$f")
done < "$list"

# 変わったファイルがなければ終わる。引数なしで呼ぶと、リポジトリ全体を調べてしまうため。
if [ "${#files[@]}" -eq 0 ]; then
  exit 0
fi

# exec では EXIT のトラップが動かないので、一時ファイルはここで消す。
rm -f "$list"
exec bash "$here/check-fullwidth-parens.sh" "${files[@]}"

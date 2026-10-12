#!/usr/bin/env bash
# =============================================================
# check-fullwidth-parens.sh
#   git で管理しているテキストファイルに、全角の丸括弧 (U+FF08、U+FF09) が
#   残っていないかを調べるスクリプト。文体のルールは .claude/rules/writing-style.md にある。
#
# 使い方:
#   bin/check-fullwidth-parens.sh [ファイル]
#   引数なしならスクリプトのあるリポジトリ全体、ファイルを渡したらそのファイルだけを調べる。
#
# 終了コード:
#   0  全角の丸括弧は見つからなかった。
#   1  全角の丸括弧が見つかった。場所を標準エラー出力に出す。
#   2 以上  git grep 自体が失敗した、またはスクリプトが予期しない理由で止まった。
#           結果は分からないので、問題なしとは扱わない。
#
# ※ まだ追跡していない新しいファイルも調べる (.gitignore されたファイルは除く)。
# ※ アプリの画面の文字列 (app/src/*/res/) とテストのデータ (app/src/*/resources/) は
#    対象外。AA を含むことがあるため。対象外かどうかは、リポジトリのルートからの
#    相対パスで決める。
# ※ 引数のファイルは、そのファイルが属する git のリポジトリ (別の worktree を含む) の中で
#    調べる。どの git のリポジトリにも属さないときや、存在しないときは、調べずに 0 で終わる。
# ※ CI と Claude Code のフック (.claude/settings.json) から呼ばれる。
# =============================================================

set -euo pipefail

# 予期しない失敗で止まるときも、終了コードを 2 以上にそろえる。
# 1 は「全角が見つかった」専用なので、失敗と区別できなくなるのを防ぐ。
trap 'rc=$?; { echo "[check-fullwidth-parens] 予期しないエラーで終了しました (終了コード: $rc)。" >&2; } 2>/dev/null || true; if [ "$rc" -ge 2 ]; then exit "$rc"; fi; exit 2' ERR

# バイト列で探すので、locale に左右されないようにする。
export LC_ALL=C

# cd が CDPATH に左右されないようにする。
unset CDPATH

# 引数のファイルは、呼び出し元のディレクトリにいるうちに、親ディレクトリを物理パスにする。
# realpath は GNU にしかないので使わない。
target_dir=""
target_name=""
if [ "$#" -ge 1 ] && [ -n "$1" ]; then
  # 存在しないファイルは調べない。
  [ -f "$1" ] || exit 0
  target_dir="$(cd -- "$(dirname -- "$1")" && pwd -P)"
  target_name="$(basename -- "$1")"
fi

# UTF-8 のバイト列。スクリプトに全角の文字そのものは書かない。
open_paren="$(printf '\357\274\210')"
close_paren="$(printf '\357\274\211')"

# 標準エラー出力が端末のときだけ色を付ける。Claude Code のフックでは
# 標準エラー出力がそのまま Claude に渡るので、エスケープがノイズになる。
if [ -t 2 ]; then
  prefix=$'\033[1;31m[check-fullwidth-parens]\033[0m'
else
  prefix='[check-fullwidth-parens]'
fi

# 対象外のパス。glob magic を付けて、* が 1 階層だけに合うようにする。
excludes=(':(exclude,glob)app/src/*/res/**' ':(exclude,glob)app/src/*/resources/**')

if [ -n "$target_name" ]; then
  # ファイルが属するリポジトリ (別の git worktree を含む) の中で調べる。
  # どの git のリポジトリにも属さないファイルは調べない。
  if ! root="$(git -C "$target_dir" rev-parse --show-toplevel 2>/dev/null)"; then
    exit 0
  fi
  root="$(cd -- "$root" && pwd -P)"
  case "$target_dir/$target_name" in
    "$root"/*) ;;
    *) exit 0 ;;
  esac
  rel="${target_dir#"$root"}"
  rel="${rel#/}"
  rel="${rel:+$rel/}$target_name"
  # .gitignore されたファイルは調べない。
  if git -C "$root" check-ignore -q -- "$rel"; then
    exit 0
  fi
  scope=(":(literal)$rel")
else
  # どこから実行しても、スクリプトのあるリポジトリ全体を同じ範囲で調べる。
  root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd -P)"
  scope=(.)
fi

# git grep の終了コードは、見つかったら 0、なければ 1、エラーなら 2 以上である。
status=0
found="$(git -C "$root" grep -n -I -F --untracked -e "$open_paren" -e "$close_paren" -- "${scope[@]}" "${excludes[@]}")" || status=$?

case "$status" in
  0)
    echo "$prefix 全角の丸括弧 (U+FF08、U+FF09) が見つかりました。" >&2
    echo "$found" >&2
    echo "$prefix 半角の括弧に直してください。日本語との間には、外側に半角スペースを入れます。" >&2
    echo "$prefix 詳しくは .claude/rules/writing-style.md を参照してください。" >&2
    exit 1
    ;;
  1)
    exit 0
    ;;
  *)
    echo "$prefix git grep が失敗しました (終了コード: $status)。" >&2
    exit "$status"
    ;;
esac

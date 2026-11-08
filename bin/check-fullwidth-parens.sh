#!/usr/bin/env bash
# =============================================================
# check-fullwidth-parens.sh
#   git で管理しているテキストファイルに、全角の丸括弧 (U+FF08、U+FF09) が
#   残っていないかを調べるスクリプト。文体のルールは .claude/rules/writing-style.md にある。
#
# 使い方:
#   bin/check-fullwidth-parens.sh [ファイル ...]
#   引数なしならスクリプトのあるリポジトリ全体、ファイルを渡したらそのファイルだけを調べる。
#   複数のファイルを渡すと、1 回の git grep でまとめて調べ、見出しと直し方は 1 回だけ出す。
#
# 終了コード:
#   0  全角の丸括弧は見つからなかった。
#   1  全角の丸括弧が見つかった。場所を標準エラー出力に出す。
#   2 以上  git が失敗した、またはスクリプトが予期しない理由で止まった。
#           結果は分からないので、問題なしとは扱わない。
#
# ※ まだ追跡していない新しいファイルも調べる (.gitignore されたファイルは除く)。
# ※ アプリの画面の文字列 (app/src/*/res/) とテストのデータ (app/src/*/resources/) は
#    対象外。AA を含むことがあるため。対象外かどうかは、リポジトリのルートからの
#    相対パスで決める。
# ※ 引数のファイルは、そのファイルが属する git のリポジトリ (別の worktree を含む) の中で
#    調べる。引数が複数のリポジトリにまたがるときは、リポジトリごとに調べて結果をまとめる。
#    存在しないファイル、どの git のリポジトリにも属さないファイル、空の引数は、飛ばす。
#    調べるファイルが 1 つも残らなかったときは、調べずに 0 で終わる。
#    ただし、空でない引数がなく、引数が空の文字列だけのときは、引数なしと同じ扱いにする。
# ※ CI と Claude Code のフック (.claude/settings.json) から呼ばれる。
# =============================================================

set -euo pipefail

# 予期しない失敗で止まるときも、終了コードを 2 以上にそろえる。
# 1 は「全角が見つかった」専用なので、失敗と区別できなくなるのを防ぐ。
trap 'rc=$?; { echo "[check-fullwidth-parens] 予期しないエラーで終了しました (終了コード: $rc)。" >&2; } 2>/dev/null || true; if [ "$rc" -ge 2 ]; then exit "$rc"; fi; exit 2' ERR

# バイト列で探すので、locale に左右されないようにする。
# git のエラーメッセージを英語に固定する役目もある (下の rev-parse の判定で使う)。
export LC_ALL=C

# cd が CDPATH に左右されないようにする。
unset CDPATH

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

# 空の引数は捨てる。
args=()
for a in "$@"; do
  if [ -n "$a" ]; then
    args+=("$a")
  fi
done

# 調べる (リポジトリのルート, ルートからの相対パス) の組。
pair_roots=()
pair_rels=()
# 全体を調べるときのルートと、調べる範囲。
whole_root=""

if [ "${#args[@]}" -eq 0 ]; then
  # どこから実行しても、スクリプトのあるリポジトリ全体を同じ範囲で調べる。
  whole_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd -P)"
else
  # 同じディレクトリのファイルが続くときに、git を呼び直さないための記憶。
  last_dir=""
  last_root=""
  last_prefix=""
  for p in "${args[@]}"; do
    # 存在しないファイルは調べない。
    [ -f "$p" ] || continue
    # 引数のファイルは、呼び出し元のディレクトリにいるうちに、親ディレクトリを物理パスにする。
    # realpath は GNU にしかないので使わない。
    case "$p" in
      */*)
        dir="${p%/*}"
        [ -n "$dir" ] || dir=/
        ;;
      *) dir=. ;;
    esac
    name="${p##*/}"
    if [ "$dir" != "$last_dir" ]; then
      target_dir="$(cd -- "$dir" && pwd -P)"
      # ファイルが属するリポジトリ (別の git worktree を含む) を探す。
      # リポジトリの外 (git が「not a git repository」と言うとき) は調べない。
      # それ以外の失敗は、結果が分からないので検査の失敗にする。
      # 標準エラー出力は別に受ける。成功したときの警告が、パスに混ざらないようにするため。
      rc=0
      err_file="$(mktemp)"
      root="$(git -C "$target_dir" rev-parse --show-toplevel 2>"$err_file")" || rc=$?
      err_msg="$(cat "$err_file")"
      rm -f "$err_file"
      if [ "$rc" -ne 0 ]; then
        case "$err_msg" in
          *"not a git repository"*) root="" ;;
          *)
            echo "$prefix git rev-parse が失敗しました (終了コード: $rc)。" >&2
            echo "$err_msg" >&2
            # 1 は「全角が見つかった」専用なので、2 未満なら 2 にする。
            if [ "$rc" -lt 2 ]; then
              rc=2
            fi
            exit "$rc"
            ;;
        esac
      fi
      prefix_in_repo=""
      if [ -n "$root" ]; then
        root="$(cd -- "$root" && pwd -P)"
        case "$target_dir/" in
          "$root"/*)
            prefix_in_repo="${target_dir#"$root"}"
            prefix_in_repo="${prefix_in_repo#/}"
            prefix_in_repo="${prefix_in_repo:+$prefix_in_repo/}"
            ;;
          *) root="" ;;
        esac
      fi
      last_dir="$dir"
      last_root="$root"
      last_prefix="$prefix_in_repo"
    fi
    # リポジトリの外のファイルは調べない。
    [ -n "$last_root" ] || continue
    pair_roots+=("$last_root")
    pair_rels+=("$last_prefix$name")
  done
  # 調べるファイルが 1 つも残らなかったら、git grep を呼ばずに終わる。
  # 範囲が除外のパスだけになると、リポジトリ全体を調べてしまうため。
  if [ "${#pair_roots[@]}" -eq 0 ]; then
    exit 0
  fi
fi

found=""
failed=0

# check_root <ルート> <範囲>...
#   git grep の終了コードは、見つかったら 0、なければ 1、エラーなら 2 以上である。
check_root() {
  local root="$1" out="" st=0
  shift
  out="$(git -C "$root" grep -n -I -F --untracked -e "$open_paren" -e "$close_paren" -- "$@" "${excludes[@]}")" || st=$?
  case "$st" in
    0) found="${found:+$found$'\n'}$out" ;;
    1) ;;
    *)
      echo "$prefix git grep が失敗しました (終了コード: $st)。" >&2
      if [ "$st" -gt "$failed" ]; then
        failed="$st"
      fi
      ;;
  esac
}

if [ -n "$whole_root" ]; then
  check_root "$whole_root" .
else
  # ルートごとに、そのルートのファイルをまとめて調べる。
  done_roots=()
  for r in "${pair_roots[@]}"; do
    seen=0
    if [ "${#done_roots[@]}" -gt 0 ]; then
      for d in "${done_roots[@]}"; do
        if [ "$d" = "$r" ]; then
          seen=1
          break
        fi
      done
    fi
    [ "$seen" -eq 0 ] || continue
    done_roots+=("$r")
    scope=()
    i=0
    while [ "$i" -lt "${#pair_roots[@]}" ]; do
      if [ "${pair_roots[$i]}" = "$r" ]; then
        scope+=(":(literal)${pair_rels[$i]}")
      fi
      i=$((i + 1))
    done
    check_root "$r" "${scope[@]}"
  done
fi

if [ -n "$found" ]; then
  echo "$prefix 全角の丸括弧 (U+FF08、U+FF09) が見つかりました。" >&2
  echo "$found" >&2
  echo "$prefix 半角の括弧に直してください。日本語との間には、外側に半角スペースを入れます。" >&2
  echo "$prefix 詳しくは .claude/rules/writing-style.md を参照してください。" >&2
fi

# 検査の失敗は、見つかったことより優先する。結果が分からないので。
if [ "$failed" -ne 0 ]; then
  exit "$failed"
fi
if [ -n "$found" ]; then
  exit 1
fi
exit 0

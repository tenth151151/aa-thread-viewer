#!/usr/bin/env bash
# =============================================================
# block-shell-file-edits.sh
#   Bash ツールのコマンドが、sed や perl でファイルをその場で書き換える形かを調べ、
#   そうなら止めるスクリプト。worker のエージェント定義 (.claude/agents/worker.md) の
#   frontmatter から、PreToolUse のフックとして呼ばれる。
#
# 使い方:
#   フックの入力 (JSON) を標準入力から読む。jq と python3 は使わない。
#   入力の JSON から "command" の文字列を取り出して解釈し、その文字列を調べる。
#
# 終了コード (check-*.sh とは意味が違う):
#   0  通す。止める形が見つからなかったとき、と、判定できなかったとき。
#   2  止める。ファイルをその場で書き換える形が見つかった。理由を標準エラー出力に出す。
#      Claude Code は、PreToolUse のフックが 2 で終わると、ツールの呼び出しを止め、
#      標準エラー出力を止めた理由として Claude に見せる。
#   check-*.sh の「2 以上は検査が失敗した、結果は分からない」とは違い、ここでの 2 は
#   「止める」だけを表す。判定できないときに止める側に倒すと、このスクリプトの不具合で
#   worker の Bash がすべて止まるので、判定できないとき (入力が読めない、"command" が
#   ない、文字列が閉じていないなど) は、標準エラー出力に警告を出して 0 で通す。
#   そのため、set -e、set -u、ERR の trap は使わない (予期しない失敗で 2 を返さないため)。
#
# 判定の方針:
#   正確に解析せず、止める側に寄せた簡単な判定にする。文字を 1 つずつ読むことはせず、
#   bash のパラメータ展開と、書き換えない sed と grep -E で、文字列全体を一括で処理する。
#   1. "command" の値を取り出し、JSON のエスケープを戻す。
#   2. 行の継続 (バックスラッシュと改行) を空白につなぐ。
#   3. 閉じた引用符の中身を空白に置き換える。ただし、-i'' や "-i" のように、フラグに
#      くっついた引用符と、フラグそのものを囲む引用符は、先に外してフラグを残す。
#   4. 引用符が閉じていない (数が合わない) ときは、引用符を消さず、そのまま調べる。
#   5. sed か perl が語として現れ、同じ単純なコマンド (;、&、|、改行の手前まで) の中に、
#      i を含む短いフラグの束 (-i、-Ei、-pi、-i.bak など) か、--i で始まる長いオプション
#      (--in-place、--i=.bak など) があれば止める。
#   if、while、timeout、doas などのキーワードやラッパーの一覧は持たず、どこに現れても見る。
#
# 止める形:
#   - sed -i、sed -i.bak、sed -Ei、sed --in-place、sed --i=.bak、sed -e s/a/b/ -i f。
#   - perl -i、perl -pi、perl -i.bak、perl -0777 -pi。
#   - /usr/bin/sed のようなパスつきの呼び出し。
#   - 行の継続をまたぐ形 (sed -e s/a/b/ バックスラッシュと改行 -i f)。
#   - sed -i''、sed -i'.bak'、sed "-i" のように、フラグに引用符がくっついた形。
#   - 引用符が閉じていないコマンド (ヒアドキュメントの本文の it's など) の中の sed -i。
#
# 通す形:
#   - 引用符の中の文字列として出てくるだけのもの (grep 'sed -i' file、echo "perl -i" など)。
#   - 書き換えない sed と perl (sed -n 1,5p file、sed s/a/b/ file、perl -pe s/a/b/ file)。
#   - sed と perl 以外のコマンド (ls -i、git diff、./gradlew spotlessApply など)。
#   - mysed、sedx、perldoc のように、sed や perl で始まるだけの別のコマンド。
#
# 止めすぎる形 (止められたら、worker は Edit か Write に切り替える):
#   - perl の -Mstrict や -Ilib のように、i を含むほかのフラグ。
#   - perl tools/x.pl -i input のように、スクリプトに渡す -i。
#   - xargs grep -l sed -i のように、sed を引数として含むコマンド。
#   - sed p -- -i のように、-- のあとのファイル名。
#   - コメント (# sed -i) と、ヒアドキュメントの本文の sed -i の行。
#     ファイルの内容は Write で書く。
#
# 止めない形 (よくある形を止める柵で、完全な判定は目指さない):
#   - リダイレクト (>、>>)、tee、cp、mv、awk -i inplace、install、dd など、
#     sed と perl 以外でファイルを書き換えるもの。
#   - sh -c と bash -c に渡した文字列の中。引用符の中身として消えるため。
#   - ダブルクォートの中の $(…) の中 (x="$(sed -i s/a/b/ f)" など)。同じ理由。
#   - 変数やエイリアスを経由した呼び出し (s=sed; $s -i f)。
#   - JSON の \uXXXX は戻さない。
#
# ※ 速さのため、取り出した文字列に sed と perl のどちらも含まれないときは、
#    解釈せずにすぐ 0 で終わる。
# =============================================================

# バイト列で調べるので、locale に左右されないようにする。
export LC_ALL=C

warn() {
  echo "[block-shell-file-edits] $1 判定できないので、通します。" >&2
}

# 標準入力が端末のときは、読むと待ち続けるので読まない。
if [ -t 0 ]; then
  warn "標準入力が端末です。"
  exit 0
fi

input="$(cat 2>/dev/null)"
if [ -z "$input" ]; then
  warn "入力が空です。"
  exit 0
fi

# JSON の "command" の値を取り出す。キーのあとの、最初の引用符までを切り落とす。
key_re='"command"[[:space:]]*:[[:space:]]*"'
if ! [[ "$input" =~ $key_re ]]; then
  warn "入力に \"command\" がありません。"
  exit 0
fi
rest="${input#*"${BASH_REMATCH[0]}"}"

# JSON のエスケープを戻す。順序が大事で、先に \\ と \" を別の 1 バイトに退避し、
# 閉じる引用符を探してから、ほかのエスケープを戻し、最後に退避したものを戻す。
bs=$'\001'
dq=$'\002'
rest="${rest//'\\'/$bs}"
rest="${rest//'\"'/$dq}"
if [[ "$rest" != *'"'* ]]; then
  warn "\"command\" の文字列が閉じていません。"
  exit 0
fi
cmd="${rest%%\"*}"
cmd="${cmd//'\n'/$'\n'}"
cmd="${cmd//'\t'/$'\t'}"
cmd="${cmd//'\r'/$'\r'}"
cmd="${cmd//'\/'/\/}"
cmd="${cmd//$dq/\"}"
cmd="${cmd//$bs/\\}"

# 速さのため、sed と perl のどちらも含まないなら、すぐ通す。
case "$cmd" in
  *sed* | *perl*) ;;
  *) exit 0 ;;
esac

# 行の継続を空白につなぐ。
cmd="${cmd//$'\\\n'/ }"

# 引用符の処理。改行をまたぐ引用符も扱うため、sed -z で全体を 1 つの塊として処理する。
# 後方参照は遅くなるので使わず、単引用符と二重引用符を別の置換に分ける。
#   1, 2: フラグそのものを囲む引用符 ('-i'、"--in-place") を外す。
#   3, 4: フラグにくっついた引用符 (-i''、-i'.bak') を外す。
#   5: 閉じた引用符の中身を、引用符ごと空白に置き換える。
flags_kept="$(printf '%s' "$cmd" | sed -z -E \
  -e "s/'(--?[^[:space:]'\"]*)'/\\1/g" \
  -e "s/\"(--?[^[:space:]'\"]*)\"/\\1/g" \
  -e "s/(-[[:alnum:]-]*)'([^[:space:]'\"]*)'/\\1\\2/g" \
  -e "s/(-[[:alnum:]-]*)\"([^[:space:]'\"]*)\"/\\1\\2/g" 2>/dev/null)"
if [ -z "$flags_kept" ]; then
  warn "引用符の処理に失敗しました。"
  exit 0
fi
stripped="$(printf '%s' "$flags_kept" | sed -z -E "s/'[^']*'|\"[^\"]*\"/ /g" 2>/dev/null)"

# 引用符が残っているときは、閉じていないので、引用符を消さずに調べる (止める側に倒す)。
case "$stripped" in
  *\'* | *\"*) target="$flags_kept" ;;
  *) target="$stripped" ;;
esac

# sed か perl が語として現れる (前が行頭か、英数字、_、.、- 以外の文字) あと、同じ
# 単純なコマンドの中に、i を含む短いフラグの束か、--i で始まる長いオプションがあれば止める。
# 改行はコマンドの区切りなので、grep が行ごとに調べるのに任せる。
# grep 自身の失敗 (終了コード 2) を止める判定と取り違えないよう、0 (見つかった) のときだけ止める。
pattern='(^|[^[:alnum:]_.-])(sed|perl)[[:space:]]([^;&|]*[[:space:]])?(-[[:alnum:]]*i|--i)'
printf '%s\n' "$target" | grep -Eq -e "$pattern"
if [ "$?" = 0 ]; then
  echo "[block-shell-file-edits] sed -i や perl -i のように、ファイルをその場で書き換える形です。ファイルの編集は Edit か Write のツールで行ってください。" >&2
  exit 2
fi
exit 0

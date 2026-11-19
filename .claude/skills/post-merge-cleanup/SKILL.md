---
name: post-merge-cleanup
description: PR が main にマージされたあとに、ローカルの main を更新し、マージ済みのブランチとバックアップのタグを片付け、マージした PR と Issue をふりかえり、見つけた点を記録に貯めて、2 回目になったものをスキルやルールへの反映点として提案する手順です。ユーザーが「マージした」「PR をマージした」と言ったときに使います。
---

# PR をマージしたあとの後片付け

PR が main にマージされたあとに、ローカルの main を更新し、マージ済みのブランチとバックアップのタグを片付けます。そのあと、マージした PR と Issue をふりかえり、見つけた点を記録に貯めて、同じ種類の点が 2 回目になったときに、スキルやルールへの反映を提案します。
実行するのは supervisor (監督) です。ブランチの切り替え、main の更新、タグやブランチの削除、`.tmp/` の下のディレクトリの削除は、ユーザーの確認を取ってから helper が行う決まりです (`CLAUDE.md`、`.claude/rules/upstream-skills.md`、`.claude/agents/helper.md` の「できること」)。

## 1. 状態を調べる

helper に、次を読むだけで調べてもらいます。ブランチの切り替え、pull、タグやブランチの削除、コミットはさせません。`git fetch origin --prune` だけは実行させます。

- `gh pr view <番号> --json state,mergedAt,mergeCommit,headRefName` (PR がマージ済みか、マージコミット、ブランチ名)。ブランチ名 (`headRefName`) は、手順 5 の「対象」でも使います。ユーザーが番号を言わなかったときは、今のブランチ名で `gh pr list --state merged --head <ブランチ名> --json number` を実行して探します。見つからないとき、または 2 つ以上見つかったときは、ユーザーに番号を確かめます。
- `git fetch origin --prune` のあと、`git status -sb`、`git log --oneline -3 origin/main`、`git branch -vv`
- `git tag -l 'backup/*'`
- マージした PR のブランチと、`git branch -vv` でリモートが消えた (`gone` の) ほかのローカルのブランチの、それぞれについて `git merge-base --is-ancestor <ブランチ> origin/main` の終了コード
- バックアップのタグのそれぞれについて、タグの名前、`git log -1 --format='%h %ci %s' <タグ>` (指しているコミットと、その日時と件名)、元のブランチ (タグの名前から先頭の `backup/` と末尾の `/<日時>` を除いたもの) がローカルにあるか、あるときはその先頭が `origin/main` に入っているか (`git merge-base --is-ancestor <ブランチ> origin/main` の終了コード)。
- `git worktree list`
- メインの作業ツリーの `.tmp/` の下の、一時ファイル (確認の証拠) のディレクトリ。メインの作業ツリーは、`git rev-parse --path-format=absolute --git-common-dir` が返すパスの 1 つ上のディレクトリです (手順 5 の記録の置き場所と同じです)。次のコマンドを、そのまま実行させます。`<PR の番号>` の 1 か所だけを、監督が書き換えて渡します。
  - 監督が書き込んでよい値は、PR の番号 (数字だけ) です。ブランチ名は、git が `$`、`` ` ``、`"` を含む名前を許すので、監督が書き込まず、コマンドが `gh pr view` で自分で取ります。数字以外を書き込みません。
  - 証拠のディレクトリは、`.tmp/` からの相対パスが、ブランチ名と完全に一致するディレクトリです。ブランチ名は、ローカルのブランチ、`origin` のブランチ、マージした PR の `headRefName` です。ブランチ名に `/` があるので、一致するのは、相対パスの先頭側 (自分か祖先) です。その下のサブディレクトリは、一致した親にまとめ、出しません。
  - 出力の `証拠 (<照合>): <パス>` の `<照合>` は、どの照合で当たったかです。`ローカル`、`ローカル (リモートが消えた)`、`リモート`、`マージ済み PR` の組み合わせで出ます。`ローカル (リモートが消えた)` は、`git branch -vv` で `gone` のブランチです。
  - `マージ済み PR` と出たブランチ名で、ローカルかリモートの同じ名前のブランチの先頭が `origin/main` に入っていないときは、行の最後に `[注意: …]` を付けます。マージした PR のブランチ名を、新しい作業に使い回しているおそれがあるためです。
  - ファイルのない空のディレクトリも出ます。`.tmp/retro-log` と、その下だけを外します (`feature/retro-log/x` のようなブランチの下は外しません)。
  - どのブランチとも一致せず、ブランチ名の途中 (`chore/` など) でもないディレクトリは、いちばん上の 1 つだけを「対応するブランチなし」として出します。
  - `.tmp/` の直下と、ブランチ名の途中のディレクトリの直下にあるファイルは、「ファイル (報告のみ)」として出します。消す対象ではなく、ユーザーに報告します。

  ```bash
  main="$(dirname "$(git rev-parse --path-format=absolute --git-common-dir)")"
  pr="<PR の番号>"
  case "$pr" in '' | *[!0-9]*) echo "PR の番号が数字ではありません。" >&2; exit 1 ;; esac
  merged="$(gh pr view "$pr" --json headRefName -q .headRefName)" || exit 1
  [ -n "$merged" ] || { echo "ブランチ名を取れませんでした。" >&2; exit 1; }
  local_branches="$(git for-each-ref --format='%(refname:lstrip=2)' refs/heads)"
  gone_branches="$(git for-each-ref --format='%(refname:lstrip=2) %(upstream:track)' refs/heads | awk '$2 == "[gone]" { print $1 }')"
  remote_branches="$(git for-each-ref --format='%(refname:lstrip=3)' refs/remotes/origin | grep -vx HEAD)"
  all_branches="$(printf '%s\n%s\n%s\n' "$local_branches" "$remote_branches" "$merged")"
  in_list() { printf '%s\n' "$2" | grep -Fxq -- "$1"; }
  [ -d "$main/.tmp" ] || { echo ".tmp がありません。"; exit 0; }
  cd "$main/.tmp" || exit 1
  # .tmp 直下のファイルは、報告のみとして出す。
  find . -mindepth 1 -maxdepth 1 -type f | sed 's|^\./||; s|^|ファイル (報告のみ): |'
  # retro-log は、先頭の 1 つだけを外す。
  find . -mindepth 1 -type d | sed 's|^\./||' | grep -v -e '^retro-log$' -e '^retro-log/' | sort | while IFS= read -r d; do
    # 自分か祖先のパスが、ブランチ名と完全に一致するかを調べる。
    p="$d"; hit=""
    while :; do
      label=""
      if in_list "$p" "$gone_branches"; then label="ローカル (リモートが消えた)"
      elif in_list "$p" "$local_branches"; then label="ローカル"; fi
      in_list "$p" "$remote_branches" && label="$label${label:+ と }リモート"
      [ "$p" = "$merged" ] && label="$label${label:+ と }マージ済み PR"
      if [ -n "$label" ]; then hit="$p"; break; fi
      case "$p" in */*) p="${p%/*}" ;; *) break ;; esac
    done
    if [ -n "$hit" ]; then
      # 祖先が一致したものは、親にまとめて出さない。
      if [ "$hit" = "$d" ]; then
        note=""
        if [ "$d" = "$merged" ]; then
          # 名前を使い回した新しい作業は、先頭が origin/main に入っていない。
          for ref in "refs/heads/$d" "refs/remotes/origin/$d"; do
            if git rev-parse -q --verify "$ref" > /dev/null && ! git merge-base --is-ancestor "$ref" origin/main; then
              note="$note [注意: $ref の先頭が origin/main に入っていません。名前を使い回した新しい作業のおそれがあります]"
            fi
          done
        fi
        echo "証拠 ($label): $d$note"
      fi
      continue
    fi
    # ブランチ名の途中のディレクトリは、直下のファイルだけを出す。
    if printf '%s\n' "$all_branches" | awk -v d="$d/" 'index($0, d) == 1 { f = 1 } END { exit !f }'; then
      find "$d" -mindepth 1 -maxdepth 1 -type f | sed 's|^|ファイル (報告のみ): |'
      continue
    fi
    # 一致せず、途中でもないものは、いちばん上の 1 つだけを出す。
    parent="${d%/*}"
    if [ "$parent" = "$d" ] || printf '%s\n' "$all_branches" | awk -v d="$parent/" 'index($0, d) == 1 { f = 1 } END { exit !f }'; then
      echo "対応するブランチなし: $d"
    fi
  done
  ```
- マージした PR の本文とコメント (`gh pr view <番号> --json body,comments`) にある `#<番号>` を拾い、それぞれ `gh issue view <番号> --json state,labels` で調べて、`wayfinder:map` を除く `wayfinder:<type>` のラベルが付いた、開いている Issue。

PR がマージされていないときは、先に進まず、ユーザーに伝えます。

## 2. 片付けるものを選んでもらう

調べた結果をユーザーに見せ、AskUserQuestion で、片付けるものを 1 回で選んでもらいます (複数選択)。選択肢は、次のものです。

- main に切り替えて、`origin/main` まで fast-forward で更新します。
- バックアップのタグを消します。手順 1 の一覧を見せ、消すタグをユーザーに選んでもらいます。
- マージした PR のローカルのブランチを消します (ブランチを消すときは、1 つ目の選択肢 (main の更新) も一緒に選んでもらいます)。
- リモートが消えた、ほかのローカルのブランチを消します (main に入っているものだけ。入っていないものは消さずに報告します。ブランチを消すときは、1 つ目の選択肢 (main の更新) も一緒に選んでもらいます)。
- 手順 1 で `証拠 (…)` と出た `.tmp/<ブランチ名>/` のうち、同じ回の手順 3 で消すブランチのものを消します。選択肢に出すのは、次のものだけです。
  - `マージ済み PR` と出たもの。ただし、行に `[注意: …]` が付いたものは、名前を使い回した新しい作業の証拠かもしれないので、選択肢に出さず、消さずに報告します。
  - `ローカル (リモートが消えた)` と出たもの。ただし、手順 3 の `git merge-base --is-ancestor <ブランチ> origin/main` の終了コードが 0 のブランチのものだけです。0 でないブランチは、手順 3 で消さずに残すので、証拠も残します。
  - `ローカル` だけ、`リモート` だけと出たもの (作業が続いているブランチ) は、選択肢に出しません。
  - パスに `'` を含むものは、手順 3 のコマンドに渡せないので、選択肢に出さず、報告します。
- 手順 1 で「対応するブランチなし」と出た `.tmp/` の下のディレクトリを、一覧で見せ、消すものをユーザーに選んでもらって消します。「ファイル (報告のみ)」は、消さずに報告します。パスに `'` を含むものは、同じく報告するだけにします。

## 3. 片付ける

選ばれたものだけを、helper に頼みます。helper への依頼には、次を書きます。

- 先に `git status --porcelain` が空であることを確かめ、空でなければ何もせずに止めて報告します。
- main の更新は、`git switch main` のあと `git merge --ff-only origin/main` で行います。fast-forward できないときは止めて報告します。更新したあと、`git merge-base --is-ancestor <マージコミット> HEAD` の終了コードが 0 であることを確かめます。`<マージコミット>` には、手順 1 の `gh pr view` で調べた `mergeCommit` を、監督が依頼に書いて渡します。
- main に切り替えて更新しないときは、ブランチを消さずに報告します。
- ブランチは、消す直前に `git merge-base --is-ancestor <ブランチ> origin/main` の終了コードが 0 であることを確かめてから、`git branch -d` で消します。0 でないものは消さずに報告します。
- バックアップのタグは、ユーザーが選んだものだけを `git tag -d` で消します。選ばれていないタグは消しません。
- 消す順は、次のとおりです。ブランチの証拠 (`.tmp/<ブランチ名>/`) を、ブランチより先に消すのは、消すコマンドが、同じ回で消すブランチの証拠を、ほかのブランチの証拠として数えられるようにするためです。一方、ブランチが残るのに証拠だけ消えるのを防ぐため、先にブランチの確かめを済ませます。
  1. ブランチの `git merge-base --is-ancestor <ブランチ> origin/main` の確かめを、すべて先に行います。
  2. `.tmp/` の下のディレクトリを消します。0 でなかったブランチの証拠は、消さずに報告します。入れ子になったものを複数選んだときは、下のものを先に消します。
  3. ブランチを `git branch -d` で消します。
- `.tmp/` の下のディレクトリは、ユーザーが選んだものだけを、選んだ 1 つずつ、次のコマンドで消します。`<PR の番号>` と `<パス>` の 2 か所だけを、監督が書き換えて渡します。書き込む値を次のように決めるのは、`$`、`` ` ``、`"` を含む値を書き込むと、helper のシェルが解釈してしまうおそれがあるためです。
  - `<PR の番号>`: 手順 1 の PR の番号。数字だけを書きます。ブランチ名は書き込みません。コマンドが `gh pr view` で自分で取ります。
  - `<パス>`: 手順 1 の出力のディレクトリの絶対パス (`<メインの作業ツリー>/.tmp/<相対パス>`)。一重引用符 (`'…'`) の中に書きます。二重引用符の中には書きません。パスに `'` を含むときは、書き込まず、そのディレクトリを消さずに止めて報告します。

  コマンドは、消す前に、次を確かめて、1 つでも当たらなければ何も消さずに止まります。
  - `<パス>` の末尾の `/` を、いくつあってもすべて外します。末尾に `/` があると、リンクが辿られて、`-L` の確かめをすり抜けるためです。外したあと、`<パス>` 自身がリンクではないこと。リンクは、辿らずに消さず、報告します。
  - `<パス>` の途中にリンクがないこと。`realpath -s` で `..` を畳んだ見かけのパスが、`.tmp/` の下にあり、`realpath -e` で解決した実体のパスが、`.tmp/` を `realpath -e` で解決したものに、その見かけの相対パスを足したものと同じであることを確かめます。食い違うときは、途中にリンクがあるので、消さずに報告します。最後の要素だけを `-L` で調べると、途中のリンクを通った別の場所を消してしまうためです。
  - `<パス>` を `realpath` で実体に解決した結果が、メインの作業ツリーの `.tmp/` (これも `realpath` で解決したもの) の下にあること。文字列で比べると、`..` やリンクを通った別の場所を見逃すので、実体で比べます。`.tmp` 自身と `.tmp/retro-log` と、その下のものは、選ばれていても消さずに報告します。
  - 消す先の下に、手順 1 の照合 (ローカル、リモート、マージした PR のブランチ名) で証拠になる、ほかのディレクトリがないこと。あるときは、消さずに止めて報告します。たとえば `.tmp/feature` を消すと、その下の `.tmp/feature/x` (ブランチ `feature/x` の証拠) まで消えるためです。

  消すときは、実体のディレクトリを、中身ごと `rm -r` で消します (`-f` とワイルドカードは使いません)。`rm -r` が失敗したときは、止めて報告します。消したあとに親のディレクトリが空になったら、空の親を 1 段ずつ `rmdir` で消します (例: `.tmp/chore/tmp-evidence-dir/` を消したあと、`.tmp/chore/` が空なら消します)。空かどうかは、`rmdir` の前に確かめ、空でなければそこで止まります (エラーは出しません)。`.tmp` まで上がったら止め、`.tmp` 自身は消しません。`rmdir -p` は、`.tmp` まで消すので使いません。

  ```bash
  main="$(dirname "$(git rev-parse --path-format=absolute --git-common-dir)")"
  pr="<PR の番号>"
  arg='<パス>'
  case "$pr" in '' | *[!0-9]*) echo "PR の番号が数字ではありません。" >&2; exit 1 ;; esac
  merged="$(gh pr view "$pr" --json headRefName -q .headRefName)" || exit 1
  [ -n "$merged" ] || { echo "ブランチ名を取れませんでした。" >&2; exit 1; }
  # 末尾の / を、いくつあってもすべて外す。
  while [ "$arg" != "${arg%/}" ]; do arg="${arg%/}"; done
  base="$(realpath -e -- "$main/.tmp")" || { echo ".tmp がありません。" >&2; exit 1; }
  base_s="$(realpath -s -- "$main/.tmp")"
  if [ -L "$arg" ]; then echo "リンクなので消しません: $arg" >&2; exit 1; fi
  # 見かけのパス (.. は畳むが、リンクは辿らない) が .tmp の下にあること。
  arg_s="$(realpath -s -- "$arg")"
  case "$arg_s" in "$base_s/"?*) ;; *) echo ".tmp の外のパスです: $arg" >&2; exit 1 ;; esac
  rel="${arg_s#"$base_s/"}"
  target="$(realpath -e -- "$arg")" || { echo "パスがありません: $arg" >&2; exit 1; }
  # 実体のパスが、見かけのパスと同じであること。食い違えば、途中にリンクがある。
  [ "$target" = "$base/$rel" ] || { echo "途中にリンクがあるので消しません: $arg" >&2; exit 1; }
  case "$target" in
    "$base" | "$base/retro-log" | "$base/retro-log/"*) echo "消してはいけないパスです: $target" >&2; exit 1 ;;
    "$base/"?*) ;;
    *) echo ".tmp の外のパスです: $target" >&2; exit 1 ;;
  esac
  [ -d "$target" ] || { echo "ディレクトリではありません: $target" >&2; exit 1; }
  # 消す先の下に、ほかのブランチの証拠がないかを調べる。
  all_branches="$(
    git for-each-ref --format='%(refname:lstrip=2)' refs/heads
    git for-each-ref --format='%(refname:lstrip=3)' refs/remotes/origin
    printf '%s\n' "$merged"
  )"
  nested=""
  while IFS= read -r s; do
    rel="${s#"$base/"}"
    if printf '%s\n' "$all_branches" | grep -Fxq -- "$rel"; then nested="$nested $rel"; fi
  done < <(find "$target" -mindepth 1 -type d)
  if [ -n "$nested" ]; then echo "下にほかのブランチの証拠があるので消しません:$nested" >&2; exit 1; fi
  rm -r -- "$target" || { echo "消せませんでした: $target" >&2; exit 1; }
  # 空になった親を、.tmp の手前まで 1 段ずつ消す。
  parent="$(dirname "$target")"
  while [ "$parent" != "$base" ]; do
    [ -z "$(find "$parent" -mindepth 1 -maxdepth 1 -print -quit)" ] || break
    rmdir -- "$parent" || { echo "親を消せませんでした: $parent" >&2; exit 1; }
    parent="$(dirname "$parent")"
  done
  ```
- コミット、push、リモートのブランチやタグの削除、`-D` や `--force` での削除はしません。
- 最後に、`git status -sb`、`git branch -vv`、`git tag -l 'backup/*'`、`git worktree list` の出力と、手順 1 の `.tmp` の一覧のコマンドを出し直した結果を、そのまま報告します。

ブランチに `-d` と消す直前の確かめを使うのは、main に入っていないものを誤って消さないためです。タグをユーザーに選んでもらうのは、ブランチ名を使い回したときや、元のブランチを先に消したときに、消してよいかを自動では判断できないためです。

## 4. 確かめて報告する

supervisor が自分で `git status -sb`、`git branch -vv`、`git tag -l 'backup/*'`、`git worktree list` を実行し、手順 1 の `.tmp` の一覧のコマンドを出し直して、helper の報告と合っているかを確かめます。そのうえで、ユーザーに次を報告します。

- main がどのコミットまで進んだか。
- 消したタグとブランチ。消さなかったものがあれば、その理由。
- 消した `.tmp/` の下のディレクトリと、残っているもの (出し直した一覧のとおり)。残したものがあれば、その理由。
- 手順 1 で見つけた、開いている wayfinder のチケット。`docs/agents/issue-tracker.md` の「Resolve」の手順で閉じることを案内します。wayfinder のチケットには `Closes` を書かないので、マージでは閉じないためです。Resolve は Issue への書き込み (コメント、クローズ、マップの本文の書き換え) なので、案内だけで書き込まず、チケットごとに文面をユーザーに見せて確認を取ってから行います (`.claude/rules/upstream-skills.md` の「Issue」の節)。Resolve は地図の本文を書き換えるので、同じ地図のチケットが複数あるときは、1 件ずつ順に行います (並行して書き換えず、書き換える直前に読み直します。`docs/agents/issue-tracker.md` と、上の `upstream-skills.md` の節のとおりです)。該当するものがなければ、ないと一言伝えます。

報告のあと、手順 5 に進みます。

## 5. ふりかえる

マージした PR と Issue をふりかえり、スキルやルールに反映するべき点を探します。見つけた点はローカルの記録に貯め、同じ種類の点が 2 回目になったときに、反映を提案します。実行するのは supervisor です。`.tmp/retro-log/` の記録のほかは、提案を見せて、扱いをユーザーに選んでもらうまで、ファイルを編集せず、GitHub にも書き込みません (手順 4 の Resolve は、これとは別に、確認を取ったあとで行います)。上流の `retro` スキルは、`mattpocock-skills` v1.3.2 では、モデルから呼べない設定で、中で `writing-for-agents` を呼びます。後者も deny で止めているため、使いません。

ふりかえらずに飛ばしたときは、飛ばしたことと理由をユーザーに伝えます。`retro/` のときは、頼めばふりかえることも一言添えます。

ふりかえりの最後に、記録に足した点と、回数を増やした点を、ユーザーに報告します。

手順 5 をどう終えても (飛ばしたとき、提案するものがないとき、扱いが決まったとき)、最後に、ユーザーに次に進められること (Issue や、メモの未完了の事項から) を伝えます。

### 対象

- 手順 1 で調べた PR が、Dependabot が作った PR で、コミットがすべて Dependabot のもの (CI を直すコミットなどを足していないもの) のときは、ふりかえらずに飛ばします。`gh pr view <番号> --json author,commits` で、PR の作者 (`author.login`) が `app/dependabot` で、すべてのコミットの作者 (`commits[].authors[].login`) が `dependabot[bot]` のときです。
- 手順 1 で調べた PR のブランチ名 (`headRefName`) が `retro/` で始まるときは、ふりかえらずに飛ばします。ふりかえりの反映の PR をふりかえると、そこから新しい反映の PR ができ、繰り返しが止まらなくなるためです。ユーザーがふりかえりを頼んだときだけ、ふりかえります。ふりかえりの反映と、ほかの作業を混ぜた PR は、`retro/` で始まらない名前にするので、ふりかえります (「提案の扱い」のとおりです)。
- それ以外のときは、ふりかえります。
- ふりかえらずに飛ばした PR (Dependabot の PR と `retro/` の PR) は、記録にも足しません。ユーザーに頼まれてふりかえったときは、ほかの PR と同じく記録に足します。

### 読むもの

- GitHub は、読むだけです。
  - PR の本文、コメント、レビュー、コミットメッセージ、PR が閉じた Issue (`gh pr view <番号> --json body,comments,reviews,commits,closingIssuesReferences`)。
  - レビューの行ごとのコメント (`gh api --paginate repos/{owner}/{repo}/pulls/<番号>/comments`。`gh api` は読むだけに使います。書き込みの形は deny で止まります)。
  - 本文やコメントで関わる Issue の、本文とコメント (`gh issue view <番号> --comments`)。
- その PR や Issue の作業を同じセッションでしたときは、その会話も見ます。ユーザーが直したこと、決めたこと、エージェントの間違い、やり直したこと、手で繰り返した操作、見つけにくかった情報などです。

### 探す観点

- ユーザーが直したことや決めたことのうち、この PR だけでなく今後にも当てはまるのに、スキルやルールに書かれていないもの。
- エージェントの間違いや見落としで、スキル、ルール、エージェントの定義、自動の検査で防げたもの。決まった形で見つけられる間違いは、文章の決まりより、自動の検査 (CI、フックなど) を先に考えます。
- 足りない、あいまい、互いに食い違う、守られていない、書いてあってもエージェントの動きが変わらない決まり。
- 手で繰り返した手順で、スキルの手順にできるもの。
- 見つけにくかった情報や、エージェントが持っていなかった情報で、置き場所や参照を足すと見つけやすくなるもの。

次のものは、提案に入れません。必要なら、どこで扱うかを一言添えます。

- アプリの設計の決定 (ADR や用語集。上流の `domain-modeling` などで扱います)。
- アプリのコードの不具合や今後の作業 (Issue で扱います)。
- すでに開いている Issue で扱っているもの。ただし、記録にある節 (決定の履歴に「Issue にした」があるものを含む) がまた起きたときは、開いている Issue があっても、記録の回数に数えます。提案するかは、「提案するもの」の節のとおりです。

### 記録に貯める

見つけた点は、すぐには反映を提案せず、ローカルの記録に貯めます。決まりを増やしすぎないためです。公式のガイドラインは、決まりを足すきっかけとして、2 回目を挙げています。

- https://code.claude.com/docs/en/memory の「When to add to CLAUDE.md」の節は、"Claude makes the same mistake a second time" と、"You type the same correction or clarification into chat that you typed last session" を挙げています。同じページは、"Longer files consume more context and reduce adherence." とも書いています。
- https://code.claude.com/docs/en/best-practices の「Keep CLAUDE.md short」の節は、足すものの例に "Anything you've had to correct twice already" を挙げ、"every line in it takes a little of Claude's attention on every task, whether it's relevant or not." と書いています。同じページは、"If you keep adding emphasis to a CLAUDE.md line and Claude still misses it, that's a sign the line belongs in a hook." とも書いています。
- 公式に、1 回の失敗では足さないとはっきり書いたところは、見つかっていません。数として出てくるのは 2 回目だけです。閾値を 2 にするのは、これにもとづいてこのプロジェクトで決めたことです。

記録の決まりは、次のとおりです。

- 置き場所は、メインの作業ツリーの `.tmp/retro-log/observations.md` です。メインの作業ツリーは、`git rev-parse --path-format=absolute --git-common-dir` が返すパスの 1 つ上のディレクトリで求めます。パスを固定しないのは、ホストや別の場所につないだコンテナから動かしたときに、記録が分かれないようにするためです。`.claude/worktrees/` の中で作業しているときも、同じ場所を指します。今のコンテナでは、`/workspaces/aa-thread-viewer/.tmp/retro-log/observations.md` になります。`.tmp/` は git に入れず、GitHub でも共有しません。後片付けでも、`.tmp/retro-log/` は消しません。
- 書くのは supervisor です。git に入らないファイルなので、worker に頼まず、reviewer も通しません。supervisor が書いてよいのは、`.tmp/retro-log/` の中だけです。
- 1 件ごとに 1 つの節にします。

  ```markdown
  ## <短い見出し (同じ種類を照らし合わせるための名前)>

  - 回数: 1
  - 起きた PR: #<番号> (<YYYY-MM-DD>)
  - 内容: …
  - 反映先の候補: .claude/rules/writing-style.md
  - 決定の履歴: (まだない)
  ```

- 新しく見つけた点は、記録と照らし合わせます。同じ種類の節があれば、回数を増やし、起きた PR を足します。なければ、新しい節を足します。
- 回数は、その点が起きた PR の数です。同じ PR をもう一度ふりかえったときや、1 つの PR の中で同じことが何度か起きたときは、その PR がすでに節に書いてあれば、回数を増やしません。
- 提案の扱いが決まったら、「決定の履歴」に「#<番号> のふりかえりで反映した」「#<番号> のふりかえりで Issue にした」「#<番号> のふりかえりで何もしない」のように足します。最初の決定を書くときは、「(まだない)」を消して書きます。決め直したときも、前の決定を消さずに足します。節を消すと、また 1 回目から数え直すことになるためです。決定の履歴が「(まだない)」のままの節は、まだ扱いを決めていない節です。決定の履歴に決定が 1 つ以上ある節は、扱いを決めた節です。
- 扱いを決めた点が、そのあとまた起きたときも、回数を増やし、起きた PR を足します。決定の履歴は変えず、前の決定をあわせて見せて、もう一度提案します。決めたあとも起きるのは、決定を見直す合図と考えるためです。最新の決定が「反映した」の点がまた起きたときは、反映した決まりが効いていない合図なので、文章を足す前に、自動の検査 (CI やフック) で防げないかも考えます。

### 提案するもの

提案するのは、次のものだけです。

- 決定の履歴が「(まだない)」のままで、回数が 2 以上の節。そのふりかえりで回数を増やしたかにかかわらず、いつでも提案します。扱いを決める前にセッションが終わったときに、取り残されないためです。
- 決定の履歴に決定が 1 つ以上ある節のうち、今回また起きたもの (そのふりかえりで回数を増やしたもの)。記録の回数と決定の履歴だけでは、扱いを決めたあとにまた起きたのかを見分けられないため、今回また起きていない、決定が 1 つ以上ある節は、提案し直しません。
- 1 回目でも提案してよい、次の例外。
  - 安全や、データ、設定を失うことに関わるもの。
  - ユーザーがはっきり方針として決めたこと。間違いの直しではなく決定であるためです。
  - 決まった形で見つけられて、CI やフックで自動に検査できるもの。

### 提案の見せ方

提案するものがないときは、ないと伝えます。あるときは、1 つの表にまとめて見せます。

| 内容 | 根拠 (回数と、どこで起きたか) | 反映先 | 提案 |
|---|---|---|---|

根拠には、回数と、それぞれの PR を書きます (例: 「2 回 (`#<番号>`、`#<番号>`)」)。例外で提案するときは、どの例外に当たるかを書きます。

反映先は、`CLAUDE.md`、`.claude/rules/`、`.claude/skills/`、`.claude/agents/`、`docs/agents/`、`README.md`、CI とフックの設定などです。

### 提案の扱い

1 件ごとに、AskUserQuestion で「新しいブランチで反映する」「Issue にする」「何もしない」から選んでもらいます。1 回で聞ける問いは 4 つまでなので、多いときは分けて聞きます。選ばれたら、記録の決定の履歴に足します。

- 新しいブランチで反映する: いつもの流れで進めます (helper がユーザーの確認を取って main からブランチを切る、worker が編集する、reviewer が確かめる、`/pre-pr-check`、`/squash-commits`、PR)。複数の提案を 1 つのブランチにまとめるかは、ユーザーに確かめます。ブランチを切る前に、ローカルの main が `origin/main` と同じであることを確かめます。手順 2 で main を更新しなかったときは、先に更新します。更新と切り替えは、ユーザーの確認を取ってから helper が行います。ブランチの名前は `retro/<名前>` にします。この名前は、次回の後片付けで、この PR をふりかえらずに飛ばす印になります (手順 5 の「対象」)。ふりかえりの反映と、ほかの作業を 1 つの PR に混ぜるときは、`retro/` で始まらない名前にします。その PR は、マージしたあとに、ふつうにふりかえります。`retro/` のブランチは、`prototype` の試作や `research/<名前>` の使い捨てのブランチと違い、PR を作って main に入れるので、`/pre-pr-check` と `/squash-commits` も、ふつうのブランチと同じように行います。
- Issue にする: 文面をユーザーに見せて確認を取ってから作ります (`.claude/rules/upstream-skills.md` の「Issue」の節)。
- 何もしない: 何もしません。

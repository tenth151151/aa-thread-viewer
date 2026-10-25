---
name: post-merge-cleanup
description: PR が main にマージされたあとに、ローカルの main を更新し、マージ済みのブランチとバックアップのタグを片付ける手順です。ユーザーが「マージした」「PR をマージした」と言ったときに使います。
---

# PR をマージしたあとの後片付け

PR が main にマージされたあとに、ローカルの main を更新し、マージ済みのブランチとバックアップのタグを片付けます。
実行するのは supervisor (監督) です。ブランチの切り替え、main の更新、タグやブランチの削除は、ユーザーの確認を取ってから helper が行う決まりです (`CLAUDE.md`、`.claude/rules/upstream-skills.md`、`.claude/agents/helper.md` の「できること」)。

## 1. 状態を調べる

helper に、次を読むだけで調べてもらいます。ブランチの切り替え、pull、タグやブランチの削除、コミットはさせません。`git fetch origin --prune` だけは実行させます。

- `gh pr view <番号> --json state,mergedAt,mergeCommit,headRefName` (PR がマージ済みか、マージコミット、ブランチ名)。ユーザーが番号を言わなかったときは、今のブランチ名で `gh pr list --state merged --head <ブランチ名> --json number` を実行して探します。見つからないとき、または 2 つ以上見つかったときは、ユーザーに番号を確かめます。
- `git fetch origin --prune` のあと、`git status -sb`、`git log --oneline -3 origin/main`、`git branch -vv`
- `git tag -l 'backup/*'`
- マージした PR のブランチと、`git branch -vv` でリモートが消えた (`gone` の) ほかのローカルのブランチの、それぞれについて `git merge-base --is-ancestor <ブランチ> origin/main` の終了コード
- バックアップのタグのそれぞれについて、タグの名前、`git log -1 --format='%h %ci %s' <タグ>` (指しているコミットと、その日時と件名)、元のブランチ (タグの名前から先頭の `backup/` と末尾の `/<日時>` を除いたもの) がローカルにあるか、あるときはその先頭が `origin/main` に入っているか (`git merge-base --is-ancestor <ブランチ> origin/main` の終了コード)。
- `git worktree list`

PR がマージされていないときは、先に進まず、ユーザーに伝えます。

## 2. 片付けるものを選んでもらう

調べた結果をユーザーに見せ、AskUserQuestion で、片付けるものを 1 回で選んでもらいます (複数選択)。選択肢は、次のものです。

- main に切り替えて、`origin/main` まで fast-forward で更新します。
- バックアップのタグを消します。手順 1 の一覧を見せ、消すタグをユーザーに選んでもらいます。
- マージした PR のローカルのブランチを消します (ブランチを消すときは、1 つ目の選択肢 (main の更新) も一緒に選んでもらいます)。
- リモートが消えた、ほかのローカルのブランチを消します (main に入っているものだけ。入っていないものは消さずに報告します。ブランチを消すときは、1 つ目の選択肢 (main の更新) も一緒に選んでもらいます)。

## 3. 片付ける

選ばれたものだけを、helper に頼みます。helper への依頼には、次を書きます。

- 先に `git status --porcelain` が空であることを確かめ、空でなければ何もせずに止めて報告します。
- main の更新は、`git switch main` のあと `git merge --ff-only origin/main` で行います。fast-forward できないときは止めて報告します。更新したあと、`git merge-base --is-ancestor <マージコミット> HEAD` の終了コードが 0 であることを確かめます。`<マージコミット>` には、手順 1 の `gh pr view` で調べた `mergeCommit` を、監督が依頼に書いて渡します。
- main に切り替えて更新しないときは、ブランチを消さずに報告します。
- ブランチは、消す直前に `git merge-base --is-ancestor <ブランチ> origin/main` の終了コードが 0 であることを確かめてから、`git branch -d` で消します。0 でないものは消さずに報告します。
- バックアップのタグは、ユーザーが選んだものだけを `git tag -d` で消します。選ばれていないタグは消しません。
- コミット、push、リモートのブランチやタグの削除、`-D` や `--force` での削除はしません。
- 最後に、`git status -sb`、`git branch -vv`、`git tag -l 'backup/*'`、`git worktree list` の出力をそのまま報告します。

ブランチに `-d` と消す直前の確かめを使うのは、main に入っていないものを誤って消さないためです。タグをユーザーに選んでもらうのは、ブランチ名を使い回したときや、元のブランチを先に消したときに、消してよいかを自動では判断できないためです。

## 4. 確かめて報告する

supervisor が自分で `git status -sb`、`git branch -vv`、`git tag -l 'backup/*'`、`git worktree list` を実行し、helper の報告と合っているかを確かめます。そのうえで、ユーザーに次を報告します。

- main がどのコミットまで進んだか。
- 消したタグとブランチ。消さなかったものがあれば、その理由。
- 次に進められること (Issue や、メモの未完了の事項から)。

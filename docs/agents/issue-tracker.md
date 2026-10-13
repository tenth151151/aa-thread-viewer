# Issue tracker: GitHub

このリポジトリ (`tenth151151/aa-thread-viewer`) の Issue と仕様は、GitHub の Issue にあります。操作はすべて `gh` CLI で行います。

## 決まり

- Issue、PR、ラベルに書き込む前に、必ず `.claude/rules/upstream-skills.md` の「Issue」の節に従います。書き込みの確認、サブエージェントの扱い、本文を書き換える人、本文の言語、英語のまま残す文字列は、そこに書いてあります。
- `gh api` の書き込み (サブ Issue や依存関係の登録など) は、`.claude/settings.json` の deny で止まるので、使いません。代わりの書き方は、下の「Wayfinding operations」に書きます。

## 操作

- **Issue を作る**: `gh issue create --title "<タイトル>" --body "<本文>"`。複数行の本文は、ヒアドキュメントで渡します。
- **Issue を読む**: `gh issue view <number> --json title,body,labels,comments,author,createdAt,updatedAt`。コメントは `jq` で絞り込みます。ラベルも取得します。
- **Issue を一覧する**: `gh issue list --state open --limit 200 --json number,title,body,labels,comments --jq '[.[] | {number, title, body, labels: [.labels[].name], comments: [.comments[].body]}]'`。`--label` や `--state` で絞り込みます。
- **Issue にコメントする**: `gh issue comment <number> --body "<本文>"`
- **本文を書き換える**: 直前に `gh issue view <number> --json body --jq .body` で読み直し、変えるところだけを変えた本文を、ファイルに書かずに標準入力から渡して、`gh issue edit <number> --body-file -` で置き換えます。
- **ラベルを付ける、外す**: `gh issue edit <number> --add-label "<ラベル>"` / `--remove-label "<ラベル>"`
- **閉じる**: `gh issue close <number> --comment "<本文>"`

リポジトリは `git remote -v` から推測します。clone の中で実行すれば、`gh` が自動で推測します。

## Pull requests as a triage surface

**PRs as a request surface: no.** _(外部の PR を機能の要望として扱うリポジトリでは `yes` にします。`/triage` がこの値を読みます。)_

`yes` にすると、PR も Issue と同じラベルと状態で扱い、`gh pr` の対応するコマンドを使います。

- **PR を読む**: `gh pr view <number> --json title,body,labels,comments,author,createdAt,updatedAt`。差分は `gh pr diff <number>` で読みます。コメントの投稿者の立場 (`authorAssociation`) は、`comments` の各要素に入っています。
- **トリアージする外部の PR を一覧する**: `gh pr list` は PR の作者の `authorAssociation` を返さないので、読み取りの `gh api` を使います。`gh api 'repos/{owner}/{repo}/pulls?state=open' --jq '[.[] | {number, title, body, labels: [.labels[].name], author: .user.login, authorAssociation: .author_association}]'`。そのうえで、`authorAssociation` が `CONTRIBUTOR`、`FIRST_TIME_CONTRIBUTOR`、`NONE` のものだけを残します (`OWNER`、`MEMBER`、`COLLABORATOR` は除きます)。
- **コメント、ラベル、クローズ**: `gh pr comment`、`gh pr edit --add-label` / `--remove-label`、`gh pr close`

GitHub では Issue と PR が 1 つの番号空間を共有するので、番号だけの `#42` はどちらにもなりえます。`gh pr view 42` で調べ、だめなら `gh issue view 42` を試します。

## スキルが「Issue トラッカーに公開する」と言うとき

GitHub の Issue を作ります。

## スキルが「該当のチケットを取得する」と言うとき

`gh issue view <number> --json title,body,labels,comments,author,createdAt,updatedAt` を実行します。

## Wayfinding operations

`/wayfinder` が使います。**マップ**は 1 つの Issue で、**子**の Issue がチケットです。`gh api` の書き込みは使えないので、サブ Issue と依存関係の機能は使いません。

- **Map**: ラベル `wayfinder:map` を付けた 1 つの Issue です。本文は、`## Destination`、`## Notes`、`## Decisions so far`、`## Not yet specified`、`## Out of scope` の見出しで書きます。`gh issue create --label wayfinder:map` で作ります。
- **Child ticket**: マップの本文のタスクリストに子を足し、子の本文の先頭に `Part of #<map>` を書きます。タスクリストは、マップの `## Notes` の中に `### Tickets` の小見出しを置き、その下に `- [ ] #<n>` の形で並べます。この環境ではサブ Issue を使えないので、親子の関係を表すために、open のチケットを地図に載せない上流の方針から外れて、このタスクリストを置きます。`## Notes` のほかの内容とは混ぜず、`### Tickets` の下だけをチケットの一覧にします。ラベルは `wayfinder:<type>` (`research`、`prototype`、`grilling`、`task`) です。引き受けたチケットは、作業する開発者に assign します。
- **Blocking**: 子の本文の先頭に、`Blocked by: #<n>, #<n>` の行を書きます。すべてのブロッカーが閉じたとき、そのチケットはブロックされていない状態です。
- **子の本文の先頭**: wayfinder の子の Issue では、1 行目に `Part of #<map>`、2 行目に `Blocked by: #<n>, #<n>` の順で書きます。ブロッカーがないときは、2 行目を書きません。`/to-tickets` の Issue のテンプレートの `## Parent` と `## Blocked by` の節は使わず、この 2 行に置き換えます。
- **Frontier query**: `gh issue view <map> --json body --jq .body` でマップの本文を読み、`### Tickets` のタスクリストの `#<n>` を拾います。次に、`gh issue list --state open --limit 200 --json number,title,body,assignees,labels` で open の Issue を取ります。タスクリストにあり、open で、担当者 (`assignees`) がいないものを残し、本文の `Blocked by:` の行の番号のうち、open の一覧にあるものが 1 つでもあるものを除きます (閉じたブロッカーは open の一覧に出ないので、残ってよいです)。タスクリストの順で、最初のものを選びます。
- **Claim**: `gh issue edit <n> --add-assignee @me`。セッションで最初の書き込みです。
- **Resolve**: `gh issue comment <n> --body "<answer>"`、`gh issue close <n>` の順に実行し、マップの `## Decisions so far` に、文脈へのポインタ (gist とリンク) を足します。あわせて、マップの `### Tickets` の該当のチェックボックスにも印を付けます (`- [x] #<n>`)。これらはマップの本文の書き換えなので、`.claude/rules/upstream-skills.md` のとおり、supervisor が文面の確認を取って行います。

`/to-tickets` が複数の Issue を公開するときも、上の Child ticket と Blocking の書き方を使います。Issue は、ブロッカーを先に、依存の順に作ります。

# Issue tracker: GitHub

このリポジトリ (`tenth151151/aa-thread-viewer`) の Issue と仕様は、GitHub の Issue にあります。操作はすべて `gh` CLI で行います。

## 決まり

- Issue、PR、ラベルに書き込む前に、必ず `.claude/rules/upstream-skills.md` の「Issue」の節に従います。書き込みの確認、サブエージェントの扱い、本文を書き換える人、本文の言語、英語のまま残す文字列は、そこに書いてあります。
- 親子は GitHub のサブ Issue、ブロックは GitHub の依存関係で表します。使うオプションと、本文の行で関係を表した Issue の扱いは、`.claude/rules/upstream-skills.md` の「Issue」の節に書いてあります。操作は、下の「操作」と「Wayfinding operations」にあります。

## 操作

- **Issue を作る**: `gh issue create --title "<タイトル>" --body "<本文>"`。複数行の本文は、ヒアドキュメントで渡します。セッションで最初に作る前に、`gh repo view --json nameWithOwner` で、書き込む先がこのリポジトリか確かめます。フォークでは、`gh` が上流の親のリポジトリを既定にすることがあるためです。違うときは、ユーザーに確認します。
- **Issue を読む**: `gh issue view <number> --json title,body,labels,comments,author,createdAt,updatedAt`。コメントは `jq` で絞り込みます。ラベルも取得します。
- **Issue を一覧する**: `gh issue list --state open --limit 200 --json number,title,body,labels,comments --jq '[.[] | {number, title, body, labels: [.labels[].name], comments: [.comments[].body]}]'`。`--label` や `--state` で絞り込みます。
- **Issue にコメントする**: `gh issue comment <number> --body "<本文>"`
- **本文を書き換える**: 直前に `gh issue view <number> --json body --jq .body` で読み直し、変えるところだけを変えた本文を、ファイルに書かずに標準入力から渡して、`gh issue edit <number> --body-file -` で置き換えます。
- **サブ Issue にする**: `gh issue create --parent <親> ...`、または作ったあとに `gh issue edit <親> --add-sub-issue <子>`。外すときは `gh issue edit <親> --remove-sub-issue <子>`。
- **ブロックを設定する**: `gh issue create --blocked-by <n>,<n> ...`、または `gh issue edit <子> --add-blocked-by <ブロッカー>`。外すときは `--remove-blocked-by`。`--blocking` と `--add-blocking` は、向きが逆です。すべてのブロッカーが閉じたとき、そのチケットはブロックされていない状態です。
- **関係を読む**: `gh issue view <number> --json parent,subIssues,blockedBy,blocking`。`subIssues`、`blockedBy`、`blocking` は `nodes` と `totalCount` を持ち、`parent` は親がないとき `null` です。gh 2.102.0 は、ページングをせずに `subIssues` を 100 件、`blockedBy` と `blocking` を 50 件までしか取りません。`totalCount` と `nodes` の件数が違うときは、上限を超えていて、全部は返っていません。
- **open のブロッカーを数える**: `gh issue view <number> --json blockedBy --jq '[.blockedBy.nodes[] | select(.state == "OPEN")] | length'`。閉じたブロッカーも `nodes` に入るので、`state` で絞ります。0 のとき、着手できます。本文の先頭の `Blocked by: #<番号>` の行で関係を表した Issue は、本文の行の番号も見ます。
- **ラベルを付ける、外す**: `gh issue edit <number> --add-label "<ラベル>"` / `--remove-label "<ラベル>"`
- **閉じる**: `gh issue comment <number> --body "<本文>"` で説明を書いてから、`gh issue close <number>` を実行します。閉じ済みの Issue に `gh issue close --comment` を使うと、コメントが捨てられるのに終了コードが 0 になるためです。

リポジトリは `git remote -v` から推測します。clone の中で実行すれば、`gh` が自動で推測します。

## Pull requests as a triage surface

**PRs as a request surface: no.** _(外部の PR を機能の要望として扱うリポジトリでは `yes` にします。`/triage` がこの値を読みます。)_

`yes` にすると、PR も Issue と同じラベルと状態で扱い、`gh pr` の対応するコマンドを使います。

- **PR を読む**: `gh pr view <number> --json title,body,labels,comments,author,createdAt,updatedAt`。差分は `gh pr diff <number>` で読みます。コメントの投稿者の立場 (`authorAssociation`) は、`comments` の各要素に入っています。
- **トリアージする外部の PR を一覧する**: `gh pr list` は PR の作者の `authorAssociation` を返さないので、読み取りの `gh api` を使います。`gh api --paginate 'repos/{owner}/{repo}/pulls?state=open' --jq '.[] | select(.author_association | IN("OWNER","MEMBER","COLLABORATOR") | not) | {number, title, body, labels: [.labels[].name], author: .user.login, authorAssociation: .author_association}'`。`--paginate` は 1 ページごとに `--jq` を適用するので、配列ではなく、1 件ずつのオブジェクトを出す形にしています。`--jq` の中で、`author_association` が `OWNER`、`MEMBER`、`COLLABORATOR` のものを除きます。`CONTRIBUTOR` などを指定して残す形にすると、`FIRST_TIMER` や `MANNEQUIN` などの外部の PR を取りこぼすためです。
- **コメント、ラベル、クローズ**: `gh pr comment`、`gh pr edit --add-label` / `--remove-label`、`gh pr close`

GitHub では Issue と PR が 1 つの番号空間を共有するので、番号だけの `#42` はどちらにもなりえます。`gh pr view 42` で調べ、だめなら `gh issue view 42` を試します。

## スキルが「Issue トラッカーに公開する」と言うとき

GitHub の Issue を作ります。

## スキルが「該当のチケットを取得する」と言うとき

`gh issue view <number> --json title,body,labels,comments,author,createdAt,updatedAt` を実行します。

## Wayfinding operations

`/wayfinder` が使います。**マップ**は 1 つの Issue で、**子**の Issue がチケットです。親子は GitHub のサブ Issue、ブロックは GitHub の依存関係で表します (`.claude/rules/upstream-skills.md` の「Issue」の節)。

- **Map**: ラベル `wayfinder:map` を付けた 1 つの Issue です。本文は、`## Destination`、`## Notes`、`## Decisions so far`、`## Not yet specified`、`## Out of scope` の見出しで書きます。`gh issue create --label wayfinder:map` で作ります。ラベルは `wayfinder:map` だけで、`ready-for-agent` などのトリアージのラベルは付けません。
- **Child ticket**: マップのサブ Issue にします (上の「サブ Issue にする」)。ラベルは `wayfinder:<type>` (`research`、`prototype`、`grilling`、`task`) だけで、トリアージのラベルは付けません。引き受けたチケットは、作業する開発者に assign します。
- **Blocking**: 上の「ブロックを設定する」の依存関係です。
- **Frontier query**: `gh issue view <map> --json subIssues --jq '[.subIssues.nodes[].number]'` でマップのサブ Issue の番号を取ります。次に、`gh issue list --state open --limit 200 --json number,title,body,assignees,labels,blockedBy` で open の Issue を取ります。サブ Issue にあり、open で、担当者 (`assignees`) がなく、open のブロッカー (`blockedBy.nodes` の `state` が `OPEN`) がないものを残します。本文の `Blocked by:` の行の番号が open の一覧にあるものも、ブロックされているとして除きます (閉じたブロッカーは open の一覧に出ません)。`gh issue list` の結果は `subIssues.nodes` の順ではないので、`subIssues.nodes` の番号の順に、残った Issue を見て、最初のものを選びます。
- **Claim**: `gh issue edit <n> --add-assignee @me`。セッションで最初の書き込みです。
- **Resolve**: `gh issue comment <n> --body "<answer>"`、`gh issue close <n>` の順に実行し、マップの `## Decisions so far` に、文脈へのポインタ (gist とリンク) を足します。マップの本文の書き換えなので、`.claude/rules/upstream-skills.md` のとおり、supervisor が文面の確認を取って行います。

`/to-tickets` が wayfinder のマップのチケットとして複数の Issue を公開するときも、上の Child ticket と Blocking の書き方 (サブ Issue と依存関係) を使い、トリアージのラベルは付けません (`to-tickets` の既定の `ready-for-agent` を付けません)。wayfinder と関係なく `/to-tickets` で作る Issue のラベルは、上流のとおりです。Issue は、ブロッカーを先に、依存の順に作ります。

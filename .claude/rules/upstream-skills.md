# 上流のスキルのルール

`mattpocock/skills` のスキルを、プラグインとして入れています。上流のスキルを使うとき、この設定や `docs/agents/` を変えるときは、必ず従います。

## 入れ方

- プラグインは `mattpocock-skills@mattpocock` で、マーケットプレイスの版はタグ `v1.3.2` です。設定は `.claude/settings.json` の `extraKnownMarketplaces` と `enabledPlugins` にあります。
- `autoUpdate` は使いません (`false`)。タグで固定して、読んだ内容が知らないうちに変わりにくくするためです。タグは付け替えられることがあるので、完全ではありません。
- コンテナの作成時には、`.devcontainer/bin/post-create.sh` が同じ版を `--scope project` でインストールします。Dockerfile で `jq` を入れておらず、ベースイメージにあるとは限らないため、版を設定から読まず、スクリプトの `UPSTREAM_SKILLS_REF` に書いています。
- 版を上げるときは、`.claude/settings.json` の `ref` と、`.devcontainer/bin/post-create.sh` の `UPSTREAM_SKILLS_REF` と、`README.md` の版を変える PR にします。上流の差分を読み、このファイルと `docs/agents/` の内容が、新しい版に合うかを確かめます。
- 版を上げたとき、`claude plugin install` は、すでに入っていれば何もせず、記録の版を変えません。記録と起動時に読み込まれる版は、`claude plugin update` で新しくなります。そのため、スクリプトは `install` のあとに `update` も実行します。

## このプロジェクトのルールと流れを正にする

上流のスキルとこのプロジェクトの決まりが食い違うときは、このプロジェクトの決まりに従います。

使うスキルは、次のとおりです。

- 計画と Issue: `to-spec`、`to-tickets`、`triage`、`wayfinder`
- 設定: `setup-matt-pocock-skills` は実行せず、`docs/agents/` と `CLAUDE.md` の節を手で保ちます。実行すると上流の既定の内容で `docs/agents/` と `CLAUDE.md` の節が上書きされるため、deny で止めています。
- 問いかけと整理: `grill-with-docs`、`grill-me`、`grilling`、`wait-what`、`handoff`、`retro`
- 設計と調査: `domain-modeling`、`codebase-design`、`diagnosing-bugs`、`research`、`prototype`

使わないスキルと、代わりに使うものは、次のとおりです。

| 使わないスキル | 代わりに使うもの |
|---|---|
| `tdd` | `.claude/rules/tdd.md` と `/tdd` |
| `code-review` | `/pre-pr-check` と、組み込みの `/code-review` |
| `pr` | `/squash-commits` のあとの PR の作成 |
| `implement`、`implement-spec` | supervisor、worker、reviewer、helper の分担 |

- `tdd`、`code-review`、`pr`、`implement`、`implement-spec` は、`.claude/settings.json` の deny でも止めています。`setup-matt-pocock-skills` も、deny で止めています (理由は上の「設定」の項目です)。このほか、モデルが自分で呼べるのに使うスキルの一覧にない `wizard` と `writing-for-agents` も、deny で止めています。
- deny には、スキルごとに 2 つの形を書きます。引数なしの `Skill(mattpocock-skills:tdd)` と、引数つきの `Skill(mattpocock-skills:tdd *)` です。前者は名前の完全一致、後者は引数つきの呼び出しの前方一致です。名前空間つきの形だけで書きます。名前だけの形 (`Skill(tdd)` など) は、このプロジェクトの `/tdd` や、組み込みの `code-review` まで止めるおそれがあるので、書きません。
- 上の表と一覧にないスキルは、使う前にユーザーに相談します。deny で止めているスキルを使いたいときも、deny を外すかをユーザーに相談します。

## Issue

- 本文とコメントは、日本語 (です・ます調) で書きます。ただし、上流のスキルが後で読み直す文字列は、上流の英語のまま書き、中身の文だけを日本語で書きます。英語のまま残すものは、下の「英語のまま残すもの」の節にあります。
- Issue への書き込みすべて (作成、コメント、ラベルの変更、本文の書き換え、関係 (親子とブロック) の設定、担当者の割り当て (Claim)、クローズ (Resolve)) の前に、文面や内容をユーザーに見せて確認を取ります。上流の `to-spec`、`to-tickets`、`triage`、`wayfinder` の、確認しない手順よりも、この決まりを優先します。
- 次の書き込みと、リポジトリを指定する `gh -R` と `gh --repo` は、`.claude/settings.json` の `ask` でも止まります。supervisor が文面の確認を取ったあとでも、毎回ユーザーの許可が要ります。対象は次のとおりです。
  - Issue: `gh issue create` (別名の `gh issue new` も止めます)、`comment`、`delete`、`transfer`、`lock`、`unlock`、`pin`、`unpin`、`develop`。作成とコメントを残すのは、リポジトリが公開で、消しても通知が残るためと、外から来た文章に仕込まれた指示で書き込まされるのを止めるためです。
  - PR: `gh pr comment`、`close`、`reopen`、`review`。`gh pr review` の承認 (`--approve` と `-a`。`-ab` のように短いフラグに続けて書く形も) は、deny が先に止めます。
  - ラベル: `gh label create`、`edit`、`delete`。
  - リポジトリの指定: `-R` と `--repo` を含む `gh` の呼び出し (`gh *-R*`、`gh *--repo*`)。gh はサブコマンドの後ろ (`gh issue edit 5 -R owner/repo`) でも、値を間を空けずに付ける形 (`-Rowner/repo`) でも受け付けるので、位置によらず当たる書き方にしています。別のリポジトリを指す呼び出しは、上の一覧の形に当たらないことがあるため、まとめて止めます。
- `gh issue edit`、`close`、`reopen` と `gh pr edit` は、基本的に `ask` に入れません。
  - ラベル、状態、関係の変更は、元に戻せます。
  - 本文やタイトルの編集は、履歴が公開で残り、元に戻しても消えません。それでも、supervisor が先に文面の確認を取るので、確認が 2 重になるのを避けるために、`ask` に入れません。
  - 次の使い方だけは、`ask` で止めます。公開のコメントが付く `close` と `reopen` の `-c` と `--comment`、担当者に `@copilot` を指定する `gh issue edit` と `gh pr edit` (Copilot のエージェントが動き出すため)、PR のマージ先を変える `gh pr edit` の `--base` と `-B` です。
- Issue と PR 以外の gh の書き込み (リポジトリの設定、secret、release、workflow、拡張、認証など) も、`.claude/settings.json` の `ask` と `deny` で止めています。この一覧は、パターンで止める最後の柵で、すべてを網羅してはいません。
  - gh の `ask` と `deny` のパターンを足したり外したりするときは、そのコマンドの `gh <cmd> --help` を読み、次を確かめます。パターンは文字列で照合されるので、別名、引数なしの形、フラグの形は、それぞれ書かないと止まらないためです。
    - 親コマンドとサブコマンドの別名 (ALIASES)。別名の形も、元の形と同じ扱いで書きます (例: `gh issue new`、`gh ext`、`gh cs`)。
    - 引数なしで実行しても書き込みになるか。`ask` は、引数なしで実行しても書き込みになるときだけ、引数なしの形も書きます (例: `gh gist create` は標準入力を読んで gist を作ります)。Claude Code の Bash は端末ではないので、引数なしだと対話できずに失敗するコマンドもあります。書き込みになるかを判断できないときは、引数なしの形も書きます (止める側に倒します)。`deny` は、コマンドの形 (`gh repo delete` など) をいつも引数なしの形と ` *` つきの形の 2 つで書きます。引数なしの呼び出しが ` *` つきの形に当たるかを確かめていないためです。
    - 止めない操作に、書き込みや公開になるフラグがないか。あるときは、そのフラグの形を `ask` に書きます。短いフラグは、値を間を空けずに付けられるので、`*-c*` のように書きます (例: `gh issue close -cTEXT`)。部分一致なので、引数の中の `-c` (`foo-cli` など) にも当たります。当たりすぎて許可を求められることは受け入れます。
    - `deny` で短いフラグを止めるときは、`*-a*` のような部分一致で書かず、空白のあとに置いた形で書きます。`deny` は許可も出せないので、部分一致にすると、本文の `re-add` のような語にも当たり、普段の操作まで止まるためです。例は `gh pr review -a*` と `gh pr review * -a*` です。前者はフラグがサブコマンドの直後に来る形、後者はほかの引数のあとに来る形で、どちらも `-ab` のようにまとめた短いフラグに当たります。コマンドの最後が `-a` で終わる形 (`gh pr review *-a`) も、あわせて書きます。最後が `-a` のときだけ当たるので、当たりすぎはほぼありません。部分一致の書き方は、`ask` だけで使います。
      - この書き方でも、本文の中で空白のあとが `-a` で始まる語 (`-all`、`-abc` など) には当たり、止まります。この当たりすぎは受け入れます。
      - 空白以外の区切り (タブ、`\` と改行の行の継続) のあとに `-a` を置き、ほかの引数が続く形は、deny に当たらず、`ask` の `gh pr review *` で止まるだけです。
      - 例外として、`-X` (`gh api` のメソッド) は、値を続けて書ける (`-XPOST`) ので、止め漏れを避けるために、部分一致の `gh api *-X*POST*` のような形で書きます。パスや本文に `-X` とメソッド名が並ぶと当たりすぎますが、受け入れます。
      - `gh api` の `-f` と `-F` は、値を続けて書ける (`-fbody=hi`) ので、空白のあとに置いた形で、`gh api -f*` と `gh api * -f*` のように、直後に来る形とほかの引数のあとに来る形の両方を書きます。
      - `.claude/settings.json` の deny には、部分一致の `gh api *-f *` と `gh api *-F *` も残しています。空白以外の区切り (タブ、`\` と改行の行の継続) のあとに `-f` や `-F` を置き、空白を挟んで値を続ける形は、空白のあとに置いた形に当たらず、この部分一致の形でしか止まらないためです。この 2 行も、`-X` と同じく、部分一致を使う例外です。
- 親子は GitHub のサブ Issue、ブロックは GitHub の依存関係で表します。上流のネイティブの手順のとおりです。上流にある、サブ Issue が使えないときの代わり (マップの本文のタスクリストと `Part of` の行) は使いません。設定には `gh issue create` と `gh issue edit` のオプション (`--parent`、`--blocked-by`、`--blocking`、`--add-sub-issue`、`--add-blocked-by`、`--add-blocking`、`--remove-parent`、`--remove-sub-issue`、`--remove-blocked-by`、`--remove-blocking`) を使います。上流にある古い `gh` 向けの `gh api` の手順は使いません (`gh api` の書き込みは deny で止まります)。
  - 関係の設定も Issue への書き込みなので、supervisor がユーザーの確認を取ります。関係を `gh issue create` のオプションで設定するときは `ask` で止まり、`gh issue edit` のオプションで設定するときは止まりません (`@copilot` を含む形の `gh issue edit` は、関係を設定するときも止まります)。
  - 本文の先頭の `Part of #<番号>` の行 (親子) や `Blocked by: #<番号>` の行 (ブロック) で関係を表した Issue も残っています。親子とブロックのどちらも、関係を読むときは、ネイティブの関係に加えて、本文の行も見ます。新しく関係を付けるときは、本文の行を使わず、ネイティブの関係だけを設定します。
  - `--add-label`、`--remove-label`、`--add-assignee`、`--remove-assignee` など、関係を設定しないオプションも使ってかまいません。
- 確認は supervisor がユーザーに取ります。サブエージェント (worker、helper、上流のスキルが起こすサブエージェントを含む) は、Issue への書き込みを自分で行わず、文面を supervisor に返します。wayfinder の Resolve や research のサブエージェントにも当てはまります。
- Issue の本文 (`## Decisions so far`、`## Not yet specified` など) を書き換えるのは supervisor だけです。書き換える直前に `gh issue view` で読み直し、変えるところだけを変えた本文を、ファイルに書かずに標準入力から渡して置き換えます。並行して書き換えません。特に `wayfinder` は、ユーザーが未ブロックのチケットを並行して進めることがあるので、地図の Issue の本文を書き換えるセッションは、同時に 1 つだけにします。ほかのセッションは、読むだけにするか、書き換えを待ちます。コマンドは `docs/agents/issue-tracker.md` にあります。
- ラベルは、上流の名前のまま使います。対応は `docs/agents/triage-labels.md` と `docs/agents/issue-tracker.md` にあります。ラベルがあることが前提です。足りないときの扱いは `docs/agents/triage-labels.md` に従います。

## 用語集と ADR

- `GLOSSARY.md`、`docs/adr/`、`.out-of-scope/` を使います。です・ます調で書き、`.claude/rules/writing-style.md` の「経緯と参照を書かない」を守ります。ADR の検討した案と置き換わった先の扱いも、同じ節にあります。
- `.out-of-scope/` の却下の理由の文には、Issue の番号を書きません。`## Prior requests` の節にだけ書きます。上流の `triage` が、同じ要望の記録に使うためです。

## 英語のまま残すもの

次の文字列は、上流のスキルが後で読み取ったり、追記したりします。英語のまま書き、見出しやラベルの下の中身の文だけを日本語で書きます。

- `triage` の決まり文句: `> *This was generated by AI during triage.*`。`triage` が Issue とコメントの先頭に付けます。
- `needs-info` の `## Triage Notes` と、その中の `**What we've established so far:**`、`**What we still need from you (@reporter):**`。再開するときと、報告者の返信の確認に読み直されます。
- エージェント向けの概要 (agent brief) の `## Agent Brief` と、その中の `**Category:**`、`**Summary:**`、`**Current behavior:**`、`**Desired behavior:**`、`**Key interfaces:**`、`**Acceptance criteria:**`、`**Out of scope:**`。
- `.out-of-scope/` のファイルの `## Prior requests`。同じ要望のときに、名前で追記されます。
- `wayfinder` の地図の見出し `## Destination`、`## Notes`、`## Decisions so far`、`## Not yet specified`、`## Out of scope`。子のチケットの `## Question`。名前で参照されます。
- ラベルの文字列 (`wayfinder:map`、`wayfinder:<type>`、トリアージのラベル)。

`to-spec` の仕様と `to-tickets` の Issue のテンプレートの見出し (`## Problem Statement`、`## What to build` など) は、使うスキルのどれも読み取りません。日本語の見出しでかまいません。ただし、`to-tickets` の `## Parent` の節は、元の Issue があるときだけ書き、サブ Issue の関係も設定します。`## Blocked by` の節は、ブロックをネイティブの依存関係で設定したときは省きます (`docs/agents/issue-tracker.md`)。

## バグの修正と試作

- `diagnosing-bugs` を使うときも、`.claude/rules/tdd.md` のとおり、バグを再現するテストを先に書きます。上流には、適切な境目がないときは回帰テストを省ける書き方がありますが、このプロジェクトでは省きません。
- `prototype` の試作は、使い捨てのブランチの中だけで行い、main にマージしません。ブランチは、helper が、ユーザーの確認を取ってから、main から切ります。
  - ブランチの中では、上流のとおり、試作を使う場所 (モジュールや画面) の隣に置いてかまいません (`app/src/main` も可)。試作だと分かる名前にします。新しいトップレベルの構成は作りません。
  - 終わったら、試作を使い捨てのブランチにコミットし (helper が、ユーザーの確認を取ってから行います)、そのブランチを push したあとで (「使い捨てのブランチ」の節のとおりです)、ブランチ名とコミットの SHA のポインタと、答え (結論と、それで決まった問い) を、実装の Issue に残します。Issue への書き込みは、「Issue」の節のとおり、supervisor がユーザーの確認を取ってから行います。
  - 確かめた結論を本物のコードに入れるときは、試作のコードを持ち込まず、main から切ったブランチで、TDD で作り直します。main に残すのは、確かめた結論だけです。
  - 試作のコードは使い捨てなので、`.claude/rules/tdd.md` の、テストを先に書く決まりの対象外です。ただし、警告を抑えない決まりと、カバレッジの基準値と除外を緩めない決まりは外れません (`tdd.md` の「例外」の節と同じ扱いです)。
  - `.claude/rules/architecture.md` で「ユーザーに相談する」とされている点 (Hilt に移る目安、最初の ViewModel の作り方など) は、試作でも変わりません。

## 編集とコミット

- supervisor は編集できません。上流のスキルが作ったり書き換えたりするファイル (`GLOSSARY.md`、`docs/adr/`、`.out-of-scope/` など) は、すべて worker に頼み、reviewer を通します。
- ブランチを作るのと切り替えるのは、supervisor の指示で helper だけが、ユーザーの確認を取ってから行います。ほかのサブエージェント (worker、reviewer、上流のスキルが起こすものを含む) は、ブランチを作らず、切り替えず、コミットしません。
- コミットは、supervisor の指示で helper が、ユーザーの確認を取ってから行います。`prototype` の使い捨てのブランチと、`research/<名前>` のブランチも同じです。

## 使い捨てのブランチ

`prototype` の試作のブランチと、`wayfinder` の `research/<名前>` のブランチは、PR を作らず、main に入れません。

- `CLAUDE.md` の、push や PR 作成の前の `/pre-pr-check` と `/squash-commits` は、これらのブランチには行いません。
- これらのブランチは、Issue にポインタを残す前に push します。push は、supervisor がユーザーの確認を取ってから行います。
- Issue に残すポインタは、ブランチ名とコミットの SHA です。ブランチは、あとで消えたり進んだりするためです。
- CI (`.github/workflows/ci.yml`) は、main を対象にした PR と、main への push でしか動かないので、これらのブランチの push では走りません。
- `/post-merge-cleanup` は main に入ったブランチだけを消すので、これらのブランチは残ります。

### research の結果

- research のサブエージェントは、ブランチを作らず、コミットしません。調べた結果を、リポジトリの作業ツリーの中の 1 つの Markdown のファイルに書き、supervisor に返します。置き場所は、`research` スキルのとおり、リポジトリの慣習に合わせ、なければ適当な場所に置いてどこかを伝えます。
- supervisor の指示で、helper が、ユーザーの確認を取ってから、main から `research/<名前>` のブランチを切り、そのファイルをコミットします。
  - コミットするのは、research のファイルだけです。そのファイルだけを `git add` し、`git add -A`、`git add .`、`commit -a` は使いません。作業ツリーのほかの変更は混ぜません。
  - ほかの変更があって切り替えに失敗するときや、ほかの変更が混ざりそうなときは、止めて supervisor に伝えます。supervisor がユーザーに確認します。
  - research が複数あるときは、1 件ごとに 1 つのブランチにします。
  - コミットしたら、helper は元のブランチに戻ります。
- ブランチを push したあとで (「使い捨てのブランチ」の節のとおりです)、チケットに、ブランチ名とコミットの SHA のポインタを残します。Issue への書き込みなので、「Issue」の節のとおり、supervisor がユーザーの確認を取ってから行います。

## 文体の決まりの範囲

- 上流のスキルの中の英語の文章や手順の書き方には、このプロジェクトの文体の決まりを当てません。上流のファイルは、リポジトリに入らないためです。
- 上流のスキルが出力するもの (Issue、リポジトリに作るファイル) には、このプロジェクトの決まりを当てます。

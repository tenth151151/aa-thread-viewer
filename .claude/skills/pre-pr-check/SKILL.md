---
name: pre-pr-check
description: ブランチを push / PR 作成する前に、差分をまとめて確認する手順です。`/squash-commits` より前に使います。ユーザーが「PR 前の確認をして」「PR を出す前にチェックして」と言ったときにも使います。
---

# PR を作る前の確認

ブランチと `origin/main` の差分を、`/code-review` と `claude doctor` で確認し、指摘をユーザーに見せて直すものを選んでもらいます。
実行するのは supervisor（監督）です。**指摘を勝手に直しません**。

## 流れの中での位置

worker の作業 → reviewer の確認 → helper のコミット → **`/pre-pr-check`** → (指摘を直したら、worker → reviewer → helper のコミット) → `/squash-commits` → push と PR の作成

- **`/squash-commits` より前に行います**。指摘を直したコミットを、`/squash-commits` で元のテーマのコミットにまとめられるためです。まとめたあとに確認すると、直したあとにまとめ直しが必要になります。

## 1. 状態を確認する

```bash
git fetch origin
git fetch origin main:main               # ローカルの main を origin/main まで進める。
git status --short                       # 未コミットの変更が無いこと。
git branch --show-current
git log --oneline origin/main..HEAD      # 確認の対象。
git diff --name-only origin/main...HEAD  # 変更されたファイル。
```

- `git fetch origin` のあとは、すべて `origin/main` と比べます。このリポジトリでは PR を GitHub でマージするので、ローカルの `main` は古いままのことが多いためです。`git fetch origin` で更新した `origin/main` と比べれば、ローカルの `main` の新しさに左右されません。
- `git fetch origin main:main` で、ブランチを切り替えずに、ローカルの `main` を `origin/main` まで進めます。`/code-review` はローカルの `main` と比べることがあり、続けて行う `/squash-commits` もローカルの `main` を基点にするためです。`main` がそろっていれば、どちらと比べても同じ差分になります。
- `git fetch origin main:main` が失敗したとき (ローカルの `main` に `origin/main` にないコミットがある、など) は、先に進まずユーザーに伝えます。
- `git fetch origin` が失敗したときは、`origin/main` が古いまま比べることになるので、先に進まずユーザーに伝えます。
- 未コミットの変更がある場合は、先にコミットするかをユーザーに聞きます。差分に含まれない変更は、確認の対象になりません。

## 2. `/code-review` を実行する

ブランチと `origin/main` の差分 (`origin/main...HEAD`) を対象にします。

- Skill ツールで呼び出します。ブランチ名は `git branch --show-current` で確かめます。
  - 例: `skill: code-review`、`args: "high --max-findings all <現在のブランチ名>"`
- effort level は `high` に固定します。指定しないと前回の level が使われ、深さが毎回変わるためです。PR を出す前の最後の確認なので、`high` にします。
- `--max-findings` は `all` にします。`default` では high のとき最大 10 件に絞られます。指摘をすべて表で見せてユーザーに選んでもらうので、上限をなくします。`--max-findings` は前回の値が引き継がれるので、毎回指定します。
- 対象を照合します。
  - `/code-review` の結果の冒頭には、対象の説明 (例: `git diff main...HEAD`、コミット数、ファイル数) が出ます。この説明が、`git log --oneline origin/main..HEAD` のコミット数と `git diff --name-only origin/main...HEAD` のファイル数と一致することを確かめます。一致しないときは、指摘を使う前にユーザーに伝え、どうするかを決めてもらいます。
  - 説明が出ないときは、「対象を照合できなかった」と報告に書きます。
  - 差分にないファイルへの指摘 (呼び出し元や、関係するほかのファイルへの影響) は、食い違いではありません。普通の指摘として扱います。
  - 2026-10-11 に、ブランチ名を渡すと `main` との差分が対象になることを一度確かめました。ただし、いつもそうなるかは分からないので、照合は毎回行います。
- 結果はバックグラウンドで届きます。届くまで待ちます。
- 結果は英語で返ることがあります。ユーザーには日本語で伝えます。

## 3. `claude doctor` を実行する

差分に Claude 関係のファイル (`CLAUDE.md`、`.claude/` 配下、`.mcp.json`) があるときだけ実行します。手順 1 で未コミットの変更がないことを確かめた前提で、`origin/main...HEAD` (コミット済みの差分) を見ます。

```bash
git diff --name-only origin/main...HEAD | grep -E '^(CLAUDE\.md|\.mcp\.json)$|^\.claude/'
```

- 該当するファイルがある場合: supervisor が自分で、リポジトリのルートで `claude doctor < /dev/null` を実行し、出力の全文を見ます。ほかのエージェントを通すと、出力が要約されて「Invalid settings」を見落とすおそれがあるためです。
- 該当するファイルがない場合: 実行せず、「対象外」として報告します。

`claude doctor` の性質は、次のとおりです。

- カレントディレクトリの settings ファイルを読んで検証します。permission のルールの書式の誤りや、値の型の誤りは、「Invalid settings」の節に出ます。
- git に入っていない `.claude/settings.local.json` も検証するので、差分にないファイルの指摘が出ることがあります。そのときは、差分の問題ではないことを分けて報告します。
- 問題があっても終了コードは 0 になります。**出力に「Invalid settings」があるかで判断します**。
- 未知のキーは指摘しません。
- スキルやエージェントの定義、`CLAUDE.md` の中身が検証の対象かどうかは、確かめていません。
- セッションの中の `/doctor` は Claude からは実行できません。必要ならユーザーに頼みます。

## 4. 指摘を表にまとめて、ユーザーに選んでもらう

`/code-review` と `claude doctor` の結果を、次の列の表にまとめてユーザーに見せます。

| 深刻度 | 場所 | 内容 | おすすめの対応 |
|---|---|---|---|

- `/code-review` は深刻度を出さないので、深刻度 (高、中、低) は supervisor が判断してつけます。判断でつけたことを、表の前に一言書きます。
- 指摘が無いときは、手順 6 に進みます。
- 指摘は、すべて表でユーザーに見せます。「おすすめの対応」の列に、「直す」「直さない」をはっきり書きます。
- AskUserQuestion で、どれを直すか選んでもらいます。選択肢は 4 つまでなので、指摘を 1 件ずつ選択肢にしません。「おすすめどおり (Recommended)」「深刻度が中以上だけ」「全部」のように、まとめた選択肢で聞きます。**おすすめどおりの選択肢を先頭に置きます**。個別に変えたいときは、ユーザーが「Other」で書きます。
- 選ばれなかった指摘は直しません。

## 5. 指摘を直す

- 直すと決まった指摘は、worker → reviewer → helper のコミット の流れで直します。
- 直したあとに `/code-review` をもう一度実行するかは、修正の大きさを見て、ユーザーに確認します。再実行した `/code-review` や `claude doctor` に新しい指摘があれば、手順 4 に戻ります。再実行しないとき、または新しい指摘がないときは、手順 6 に進みます。
- 直した差分に Claude 関係のファイルがあれば、手順 3 の `claude doctor` を supervisor が自分でもう一度実行します。すぐに終わるので、毎回行います。

## 6. 報告する

- `/code-review` の結果の要約（日本語）を書きます。
- `claude doctor` の結果、または「対象外」であることを書きます。
- 直した指摘と、直さなかった指摘を書きます。
- 次は `/squash-commits` に進むことを書きます。

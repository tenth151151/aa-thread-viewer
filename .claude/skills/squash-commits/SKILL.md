---
name: squash-commits
description: ブランチ内の作業コミットを、意味のある単位にまとめ直す (スカッシュする) 手順。ユーザーが「コミットをまとめて」「スカッシュして」「履歴を整理して」と言ったとき、またはブランチを push / PR 作成する前に使う。その場合は `/pre-pr-check` のあとに行う。
---

# ブランチのコミットをまとめる

ブランチ内の細かい作業コミットを、レビューしやすい「意味のある単位」に組み直す。
**最終的なファイルの中身は1バイトも変えない**。変えるのは履歴の区切り方だけ。

`/pre-pr-check` で差分を確認してからまとめる。指摘を直したコミットも、いっしょにまとめられるため。

## 1. 状態を確認する

```bash
git status --short                      # 未コミットの変更が無いこと。
git branch --show-current
git log --oneline main..HEAD            # まとめる対象。
git rev-parse --abbrev-ref @{upstream}  # upstream があるか (無ければエラーになる)。
git log --oneline @{upstream}..HEAD     # upstream がある場合: まだ push していないコミット。
```

- **未コミットの変更がある**: まとめに勝手に含めない。そのまま残すか、先にコミットするかをユーザーに聞く。
- **push 済みのコミットが対象に含まれる**: 止めてユーザーに聞く。書き換えると `git push --force-with-lease` が必要になり、他の人の作業に影響しうる。許可が無ければ push 済みの部分には触らない。
- 基点は `main` とする。`git merge-base HEAD main` で分岐点を求め、**rebase はしない** (基点を動かすと中身が変わるため)。

## 2. まとめ方を提案して、ユーザーに選んでもらう

**毎回必ず確認する。勝手にまとめない。**

- `git log --reverse --stat --format='%h %s' main..HEAD` で、各コミットがどのファイルを触ったかを見る。
- テーマごとのまとめ方を 2〜3 案作り、AskUserQuestion で選んでもらう。各案には、完成後の `git log --oneline` のイメージをプレビューとして付ける。
- 案を作るときの考え方:
  - 後から入った修正 (レビュー対応、typo 修正、「〜を修正した」) は、別のコミットとして残さず、元のテーマのコミットに入れ込む。
  - 1コミット = 1つの目的。コミットメッセージを1行で言えない組み合わせにはしない。
  - **途中のコミットが壊れた状態にならない順番にする**。例: スクリプトを呼び出す設定は、スクリプト本体と同じコミットか、それより後のコミットに入れる。
  - 関係ない変更 (`.gitignore` の追加など) は、無理に他と混ぜず独立させる。
- コミットメッセージは、`.claude/rules/writing-style.md` のコミットメッセージの決まり (件名と本文) に従って、日本語で書く。

## 3. バックアップのタグを作る

```bash
git tag "backup/$(git branch --show-current)/$(date +%Y%m%d-%H%M%S)"
```

- **軽量タグを使う** (`-a` は付けない)。注釈付きタグは `--follow-tags` で push されることがある。
- **バックアップのタグは push しない**。`git push --tags` は使わない。

## 4. コミットを組み直す

作業ツリーには触らず、index だけを組み立ててコミットする。

```bash
ORIG=$(git rev-parse HEAD)
git reset --soft "$(git merge-base HEAD main)"
git reset                     # 全部 unstage する (作業ツリーはそのまま)。
```

各コミットについて、そのコミットに入れるファイルを、**最終版 (`$ORIG`) の中身で** index に入れてコミットする。

```bash
git update-index --add --cacheinfo "100644,$(git rev-parse "$ORIG:path/to/file"),path/to/file"
git commit -F - <<'AATV_COMMIT_MESSAGE_END'
<メッセージ>

<システム指示に出る帰属の行 (.claude/settings.json の attribution.commit で決まる)>
AATV_COMMIT_MESSAGE_END
```

- 区切りの語は、メッセージに出てこない語にする。`EOF` だけの行がメッセージにあると、そこでヒアドキュメントが終わり、残りの行がコマンドとして実行されるため。
- システム指示に帰属の行がないときは、その行と直前の空行を省く。
- ファイル単位で分けられるなら、`git add <path>` でもよい。
- **削除・リネームも忘れずにコミットに入れる**。`git reset` 直後の index は分岐点の状態なので、最終版で消えたファイルは放っておくと index に残り続ける。先に、ブランチ全体で何が起きたかを一覧する。

  ```bash
  git diff --name-status -M "$(git merge-base HEAD main)" "$ORIG"   # A:追加 M:変更 D:削除 R:リネーム T:モード変更。
  ```

  - 削除 (`D`): 該当のコミットで `git update-index --force-remove -- <path>` (または `git rm --cached -- <path>`) を実行する。
  - リネーム (`R`): 「旧パスの削除」と「新パスの追加」を**同じコミット**に入れる。分けると途中のコミットで同じファイルが二重にある、または消えている状態になる。新パスは `update-index --cacheinfo` で追加する。
  - どのコミットにも入れ忘れたものが無いかは、手順 5 の `git diff --stat "$ORIG" HEAD` で検出できる。
- 1つのファイルを複数のコミットに分けたい場合 (例: 設定の1行だけ後のコミットに入れる) は、途中の版を作って `git hash-object -w --stdin` で blob にし、`update-index --cacheinfo` で入れる。作業ツリーのファイルは書き換えない。
- 実行ビットがあるファイルは `100755` にする。`git ls-tree "$ORIG" -- <path>` でモードを確認する。
- `git add -A` / `git add .` / `git add --renormalize .` は使わない。関係ない変更が混ざる。

## 5. 中身が変わっていないことを確認する

```bash
git diff --stat "$ORIG" HEAD        # 何も出なければ OK。
git log --reverse --stat --format='%h %s' main..HEAD
git status --short                  # 元々あった未コミットの変更だけが残っていること。
```

`git diff` に差分が出たら、組み直しを失敗とみなして `git reset --hard "$ORIG"` (またはバックアップのタグ) で元に戻し、原因を調べてからやり直す。

## 6. 報告する

- まとめた後の `git log --oneline`
- 元の中身と差分ゼロであること
- バックアップのタグ名と、戻し方 (`git reset --hard <タグ>`)
- push はしていないこと (push 済みのものを書き換えた場合は `--force-with-lease` が必要なこと)

## バックアップのタグの片付け

ブランチが main にマージされたら、`/post-merge-cleanup` の手順で消す。

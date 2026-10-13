# CLAUDE.md

## Git のコミット

- ブランチ内の作業コミットは、push や PR 作成の前に意味のある単位にまとめる。手順は `/squash-commits` スキル (`.claude/skills/squash-commits/SKILL.md`) に従う。
- まとめ方は毎回ユーザーに確認する。勝手にまとめない。
- まとめる前のバックアップはタグ `backup/<ブランチ名>/<日時>` で残し、push しない。
- push 済みのコミットは、ユーザーの明示的な許可なしに書き換えない。

## PR の作成

- push や PR 作成の前に、`/pre-pr-check` スキル (`.claude/skills/pre-pr-check/SKILL.md`) で差分を確認する。`/squash-commits` より前に行う。

## Agent skills

- 上流の `mattpocock/skills` をプラグインとして入れている。使うスキルと使わないスキル、Issue の書き方などの決まりは `.claude/rules/upstream-skills.md` を見る。

### Issue tracker

- GitHub の Issue を使う (`gh` CLI)。詳細は `docs/agents/issue-tracker.md`
- Issue へ書き込む前に、文面をユーザーに見せて確認を取る。決まりは `.claude/rules/upstream-skills.md`

### Triage labels

- ラベルは上流の既定の名前のまま使う。詳細は `docs/agents/triage-labels.md`

### Domain docs

- シングルコンテキスト。`GLOSSARY.md` と `docs/adr/` を読む。詳細は `docs/agents/domain.md`

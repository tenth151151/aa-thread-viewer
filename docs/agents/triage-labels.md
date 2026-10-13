# Triage Labels

スキルは、5 つの標準のトリアージの役割で話します。このファイルは、役割を、このリポジトリのトラッカーで実際に使うラベルの文字列に対応づけます。

| Label in mattpocock/skills | Label in our tracker | Meaning                                  |
| -------------------------- | -------------------- | ---------------------------------------- |
| `needs-triage`             | `needs-triage`       | メンテナーが評価する必要があります。     |
| `needs-info`               | `needs-info`         | 報告者からの追加の情報を待っています。   |
| `ready-for-agent`          | `ready-for-agent`    | 仕様が固まっていて、AFK のエージェントに任せられます。 |
| `ready-for-human`          | `ready-for-human`    | 人が実装する必要があります。             |
| `wontfix`                  | `wontfix`            | 対応しません。                           |

スキルが役割に触れるとき (例: 「AFK 向けのトリアージのラベルを付ける」) は、この表の対応するラベルの文字列を使います。

使う語彙が変わったときは、右の列を書き換えます。

この表と `issue-tracker.md` の wayfinder のラベルは、リポジトリに作ってあることが前提です。ないと、`gh issue create --label` や `--add-label` が失敗します。足りないときは、作る前にユーザーに相談します。

`/wayfinder` のラベル (`wayfinder:map`、`wayfinder:<type>`) は、このファイルではなく `docs/agents/issue-tracker.md` に書きます。

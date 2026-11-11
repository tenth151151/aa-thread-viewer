# Triage Labels

スキルは、トリアージの役割で話します。役割は、分類 (2 つ) と状態 (5 つ) の 2 種類です。上流の `triage` は、トリアージした Issue に、分類の役割を 1 つと、状態の役割を 1 つ付けます (wayfinder の地図とチケットには、分類と状態のどちらのラベルも付けません。付けるのは、地図の `wayfinder:map` と、チケットの `wayfinder:<type>` だけです。`docs/agents/issue-tracker.md` を参照)。このファイルは、役割を、このリポジトリのトラッカーで実際に使うラベルの文字列に対応づけます。

## 分類

| Label in mattpocock/skills | Label in our tracker | Meaning                                  |
| -------------------------- | -------------------- | ---------------------------------------- |
| `bug`                      | `bug`                | 動くはずのものが動いていません。         |
| `enhancement`              | `enhancement`        | 新しい機能の要望や、既存の機能の改善です。 |

## 状態

| Label in mattpocock/skills | Label in our tracker | Meaning                                  |
| -------------------------- | -------------------- | ---------------------------------------- |
| `needs-triage`             | `needs-triage`       | メンテナーが評価する必要があります。     |
| `needs-info`               | `needs-info`         | 報告者からの追加の情報を待っています。   |
| `ready-for-agent`          | `ready-for-agent`    | 仕様が固まっていて、AFK のエージェントに任せられます。 |
| `ready-for-human`          | `ready-for-human`    | 人が実装する必要があります。             |
| `wontfix`                  | `wontfix`            | 対応しません。                           |

スキルが役割に触れるとき (例: 「AFK 向けのトリアージのラベルを付ける」) は、これらの表の対応するラベルの文字列を使います。

使う語彙が変わったときは、右の列を書き換えます。

これらの表と `issue-tracker.md` の wayfinder のラベルは、リポジトリに作ってあることが前提です。ないと、`gh issue create --label` や `--add-label` が失敗します。足りないときは、作る前にユーザーに相談します。

`/wayfinder` のラベル (`wayfinder:map`、`wayfinder:<type>`) は、このファイルではなく `docs/agents/issue-tracker.md` に書きます。

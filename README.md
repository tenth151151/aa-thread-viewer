# aa-thread-viewer

## 開発

開発環境は devcontainer です。JDK と Android SDK のバージョンは、リポジトリのルートの `versions.properties` にまとめています。devcontainer、CI、Gradle がこのファイルを読むので、バージョンを変えるときは、このファイルだけを書き換えます。JDK は `JDK_VERSION` と `JDK_SDKMAN_ID` の両方を直します。`targetSdk` と `minSdk` は、アプリの挙動に関わる設定なので、`app/build.gradle.kts` に書いています。

```bash
# CI と同じ確認をする (全角の丸括弧の検査、単体テスト、Android Lint、format のチェック、カバレッジの基準値の検査、デバッグビルド、カバレッジのレポートの作成)。
bash bin/check-fullwidth-parens.sh
./gradlew check assembleDebug koverLog koverXmlReport koverHtmlReport --continue

# format の崩れを自動で直す。
./gradlew spotlessApply
```

カバレッジは Kover で測ります。数字とレポートは、debug と release を合わせた全体のものです。「CI と同じ確認」のコマンドは、行のカバレッジを標準出力に出し、レポートを `app/build/reports/kover/` に作ります。HTML は `html/index.html`、XML は `report.xml` です。JVM の単体テストだけで測るので、`@Composable` の関数 (`@Preview` の関数を含みます) と、Activity と Application のクラスは、測る対象から外しています。行のカバレッジが 80% を下回ると、`check` が失敗します。基準値と除外は `app/build.gradle.kts` の `kover` に書いています。CI は、行のカバレッジをジョブのサマリーに書き、レポートをアーティファクト `coverage` に上げます。

### Claude Code のスキル

上流の `mattpocock/skills` は、プロジェクトの設定 (`.claude/settings.json` の `extraKnownMarketplaces` と `enabledPlugins`) で、プラグインとして入れています。このプラグインは、マーケットプレイスの中の相対パスのプラグインです。Claude Code は、起動したときにインストールの記録 (`~/.claude/plugins/installed_plugins.json`) とキャッシュだけを読み、マーケットプレイスの確認は起動のあとに背景で行います。インストールの記録がない間は、起動しただけではスキルが出ず、セッションごとに `/reload-plugins` が要ります。そのため、`.devcontainer/bin/post-create.sh` が、コンテナの作成時に `claude plugin marketplace add` と `claude plugin install` を `--scope project` で実行し、記録を作っておきます。記録は `~/.claude` (ボリューム) に残ります。このインストールに失敗してもセットアップは止まりません。手で入れるときは、リポジトリのフォルダで次のコマンドを実行します。

```bash
claude plugin marketplace add mattpocock/skills#v1.3.1 --scope project
claude plugin install mattpocock-skills@mattpocock --scope project
```

- `--scope project` の 2 つのコマンドは、`.claude/settings.json` を書き換えます (キーの並び替えだけで、中身は同じです)。手で実行したあとは、`git diff .claude/settings.json` で差分を確かめ、並び替えだけなら `git restore .claude/settings.json` で戻します。`settings.json` にまだコミットしていない自分の変更があるときは、`git restore` で消えてしまうので、先に退避してください。スクリプトは、実行の前後で内容を戻しています。
- フォルダを最初に開いたときは、信頼のダイアログを承認します。承認しないと、マーケットプレイスの設定は読まれません。
- インストールしていないとき (スクリプトが失敗したときなど) は、起動しただけではスキルが出ません。`/reload-plugins` を実行するか、上のコマンドで入れてから開き直します。
- 固定する版と入れ方、使うスキルと使わないスキル、Issue の書き方などの決まりは、`.claude/rules/upstream-skills.md` にあります。

### git worktree

worktree は `.claude/worktrees/<名前>/` に作ります。Claude Code の worktree と wtp (`.wtp.yml`) が同じ場所を使い、`.gitignore` と spotless の対象から外しています。

worktree は、相対パスで作ります。ホストとコンテナで `.git` を共有していて、リポジトリのパスが違うため、相対パスにすると両方から使えるからです。設定は、`.devcontainer/bin/post-create.sh` が `worktree.useRelativePaths` で行います。

`post-create.sh` はコンテナを作ったときにだけ動きます。すでにあるコンテナと、コンテナを作り直す前のホストでは、リポジトリのルートで次のコマンドを一度実行します。

```bash
git config worktree.useRelativePaths true
```

設定は `.git/config` に入り、ホストとコンテナで共有されます。そのため、どちらかで一度実行すれば足ります。

設定を入れる前に作った絶対パスの worktree は、そのままでは絶対パスのままです。相対パスに直すには、リポジトリのルートで、直したい worktree のパスをすべて渡して、次のコマンドを実行します。

```bash
git worktree repair --relative-paths .claude/worktrees/*
```

worktree が 1 つもないときは、パスが見つからずエラーで終わりますが、直すものがないので実行は不要です。

これで、渡した worktree の `.git` ファイルと `.git/worktrees/<名前>/gitdir` が、相対パスになります。`extensions.relativeWorktrees` と `core.repositoryformatversion = 1` も、同時に書かれます。

パスは省かないでください。パスを渡した worktree は、必ず直ります。渡さなかった worktree は、git がこちら側のパスで見つけられるときだけ、いっしょに直ります。ホストとコンテナのうち、反対側で作った worktree は、記録された絶対パスがこちら側にないので、渡さないと、終了コード 0 のまま何も直らず、絶対パスのまま残ります。

最初の相対パスの worktree を作ると、`.git/config` に `extensions.relativeWorktrees = true` が書かれ、`core.repositoryformatversion` が 1 になります。worktree を消しても、この 2 つは残ります。そのため、ホストでこのリポジトリを扱う git と、git を使うツール (Android Studio など) は、git 2.48 以上が必要です。

相対パスをやめるときは、worktree を消さずに、次の順に戻せます。

1. `git config --unset worktree.useRelativePaths`
2. 相対パスの worktree ごとに、パスを渡して `git worktree repair --no-relative-paths .claude/worktrees/<名前>` を実行します。`.git` ファイルと `gitdir` の両方が絶対パスに戻ります。パスを渡さないと、何も直りません。
3. `git config --unset extensions.relativeWorktrees`
4. `git config --get-regexp '^extensions\.'` を実行します。ほかの拡張 (`extensions.worktreeConfig` など) が残っているときは、手順 5 に進まず、版を 1 のままにします。何も出ないときだけ、手順 5 に進みます。
5. `git config core.repositoryformatversion 0`

手順 2 は、手順 3 から 5 より前に行います。

コンテナを作り直すと、`post-create.sh` が `worktree.useRelativePaths` を設定し直します。やめたままにするときは、`post-create.sh` の該当の節も消します。

### 依存関係の更新

依存関係の更新は、Dependabot (`.github/dependabot.yml`) と手作業で分担しています。

- Dependabot が更新するもの: `gradle/libs.versions.toml` の `[libraries]` と `[plugins]` (ktlint は `[versions]` にしかないため対象外)、Gradle Wrapper、GitHub Actions。毎週月曜の朝に確認し、PR を作ります。
- 手で更新するもの: `versions.properties` (JDK と Android SDK)、`.devcontainer/Dockerfile` の `ARG` (`WTP_VERSION`、`GIT_CORE_PPA_KEYS`)、ktlint。ktlint は `gradle/libs.versions.toml` の `[versions]` にしかなく、Dependabot が読まないためです。spotless を上げるときは、ktlint もいっしょに見直します。

PR の分け方は次のとおりです。

- AGP と Gradle Wrapper は、影響が大きいので、それぞれ単独の PR になります。
- AndroidX (Compose BOM を含む)、Kotlin、その他 (minor と patch)、GitHub Actions は、それぞれまとめた 1 つの PR になります。
- ビルド用の constraints (`gradle/libs.versions.toml` の bouncycastle、jose4j、jdom2、commons-lang3、httpclient、freemarker) の minor と patch は、プラグインが試していない版で壊れたときに切り分けやすいよう、その他とは別に、まとめた 1 つの PR になります。
- その他の major の更新は、1 件ずつ単独の PR になります。

Kotlin のバージョンは、AGP が要求する KGP 以上にそろえます。詳しくは `gradle/libs.versions.toml` のコメントを参照してください。

### 脆弱性の更新 (セキュリティ更新)

脆弱性の検出と更新には、Dependabot alerts と Dependabot security updates を使います。Gradle は、リポジトリのファイルを読むだけでは推移的な依存関係まで分からないため、`.github/workflows/dependency-submission.yml` が依存関係のグラフを GitHub に送ります。このワークフローは、`main` への push と手動実行 (`workflow_dispatch`) で動きます。定期実行はしません。

Dependabot alerts と Dependabot security updates を使うには、リポジトリの設定で両方を有効にします。Dependabot alerts を有効にすると、Dependency graph も生成されます。Dependency graph が無効だと `dependency-submission.yml` のグラフの送信が失敗するので、ワークフローより先に Dependabot alerts を有効にします。

- セキュリティ更新の PR は、`dependabot.yml` のグループに入らず、1 件ずつ単独の PR になります。ただし、リポジトリの設定の Grouped security updates を有効にしないことが前提です。有効にすると、`dependabot.yml` にグループがなくても、エコシステムごとにまとめた PR になるためです。
- alerts は、推移的な依存関係を含むグラフ全体に出ます。PR が作られるのは、`gradle/libs.versions.toml` のように、ファイルに書いてある依存関係で、修正版に上げられるときだけです。推移的な依存関係は、alert だけで PR が来ないことがあります。
- GitHub Actions は SHA で固定しているため、alerts の対象になりません。Actions の更新は、バージョン更新の PR で行います。

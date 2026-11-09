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

### エミュレータ (Windows)

コンテナには KVM (`/dev/kvm`) がないので、エミュレータはホストの Windows で動かし、コンテナの `adb` からは、ホストの adb server を通して使います。

前提は、ホストの Windows に Android Studio を入れ、Android SDK を入れてあることです。SDK Manager の SDK Tools で、Android SDK Command-line Tools (latest)、Android Emulator、Android SDK Platform-Tools を入れておきます。SDK の場所は、環境変数 `ANDROID_HOME` か、Android Studio の既定の場所 (`%LOCALAPPDATA%\Android\Sdk`) から探します。

ホストの Windows で、リポジトリのフォルダから次のバッチファイルを実行します。本体は PowerShell のスクリプト (`bin\windows\setup-emulator.ps1`) で、バッチファイルはそれを呼ぶだけの入口です。

```bat
bin\windows\setup-emulator.bat
bin\windows\setup-emulator.bat 37.0 my-avd
```

引数は 2 つで、どちらも省略できます。1 つ目はシステムイメージの API の版で、既定は、`versions.properties` の `ANDROID_PLATFORM` (`android-<版>`) の版です。`system-images;android-<版>;google_apis;x86_64` を使います。2 つ目は AVD の名前で、既定は `aatv-api-<版>` です。スクリプトは、次のことをこの順で行います。

1. システムイメージがなければ入れます。Android SDK のライセンスに同意していないときは、`sdkmanager` が同意を尋ねます。
2. AVD がなければ作ります。すでにあるシステムイメージと AVD は、作り直しません。
3. adb server が、すべての口 (IPv4 の `0.0.0.0` か IPv6 の `[::]`) で待ち受けていなければ、待ち受ける形 (`adb -a`) で起動し直します。すでに待ち受けているときは、起動し直さずにそのまま使います。
4. 指定した AVD のエミュレータが動いていなければ、空いているポートで、コールドブート (`-no-snapshot`) で起動します。スナップショットからの再開 (クイックブート) に失敗して、画面が暗いまま操作を受け付けないことがあるためです。起動のときにスナップショットを読まず、終わるときにも保存しないので、起動は毎回遅くなり、端末の状態 (開いていたアプリなど) は戻りません。入れたアプリとデータは、ディスクに残ります。すでに動いているエミュレータは、起動し直さずに使うので、起動し直したいときは、エミュレータを終了してから実行してください。起動の完了は、そのエミュレータの serial (`emulator-<ポート>`) を指定して待ちます。別の AVD のエミュレータが動いていても、指定した AVD は別に起動します。

`adb -a start-server` で待ち受けにならないときは、`adb -a nodaemon server` を、最小化した別のウィンドウ (タイトルは `adb server`) で動かします。このウィンドウを閉じると adb server が止まるので、エミュレータを使っている間は閉じないでください。

`/?`、`-h`、`--help` を付けると、使い方を表示します。`.ps1` を PowerShell から直接実行するときは、ExecutionPolicy で止まることがあるので、`powershell -NoProfile -ExecutionPolicy Bypass -File bin\windows\setup-emulator.ps1` のように指定します。PowerShell のセッションから版を渡すときは、`.\setup-emulator.ps1 '37.0'` のように引用符で囲みます。囲まずに `37.0` と書くと数値になって `37` に変わるため、スクリプトはエラーで止まります。

2 回目以降の実行では、システムイメージと AVD は作らず、指定した AVD のエミュレータが動いていれば、そのまま使います。

コンテナで使うには、`.devcontainer/.env` で `ADB_SERVER_SOCKET` を有効にし、値の `<Windows のアドレス>` を Windows のアドレスに書き換えて、コンテナを作り直します (VS Code では `Dev Containers: Rebuild Container`)。NAT モードの WSL では、Windows の vEthernet (WSL) の IPv4 アドレスです。Windows では、`ipconfig` に出る vEthernet (WSL) の IPv4 アドレスを見ます。WSL では、devcontainer のコンテナではなく、WSL のシェル (Ubuntu のターミナルなど) で `ip route show | grep -i default | awk '{ print $3}'` を実行して調べます。コンテナの中で実行すると、既定経路の `172.17.0.1` (docker0) が出ますが、Windows のアドレスではないので使えません。`.env` の扱いは次のとおりです。

- すでに `.devcontainer/.env` がある人は、`.devcontainer/.env.example` の `ADB_SERVER_SOCKET` の行を、`.env` に足し、値の `<Windows のアドレス>` を書き換えます。
- `.env.example` から作られた `.env` (なければ、コンテナを作るときに `.env.example` からコピーされます) なら、`ADB_SERVER_SOCKET` の行のコメントを外し、値の `<Windows のアドレス>` を書き換えます。

エミュレータを使わない人は、この行を有効にしないので、コンテナの `adb` は今のままです。

確かめ方は次のとおりです。

```bash
# エミュレータが出る。
adb devices
# エミュレータにアプリが入る。
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

コンテナからは `./gradlew installDebug` を使えません。Gradle (Android Gradle Plugin の ddmlib) は `ADB_SERVER_SOCKET` を見ず、`127.0.0.1:5037` にだけつなぐと見込んでいるためです (ddmlib 32.4.1 の jar を調べた結果で、実行では確かめていません)。そのため、コンテナでは、APK を作ってから、`adb install` で入れます。

#### 注意

- スクリプトを実行するときは、Android Studio を閉じてください。スクリプトは adb server を止めて起動し直しますが、Android Studio が開いていると、localhost だけで待ち受ける adb を起動し直し、すべての口で待ち受ける adb server を立てられないおそれがあります (確かめていません)。閉じられないときは、Android Studio の設定に adb server を手動で管理する項目があるようなので、それを使う方法があります (項目の名前と場所は確かめていません)。
- adb server をすべての口で待ち受けにすると、同じネットワークの別の機械からも、ポート 5037 を通して、このエミュレータを操作できてしまいます。スクリプトの初回の実行で、Windows Defender ファイアウォールが `adb.exe` の許可を尋ねます。許可するのは「プライベート ネットワーク」だけにし、信頼できないネットワーク (公共の Wi-Fi など) に接続しているときは、エミュレータを使い終わったら `adb kill-server` で止めてください。
  - WSL2 の仮想アダプタ (vEthernet) は、「パブリック」として扱われることがあるようです。その場合、パブリックを許可しないと、コンテナから届かないおそれがあります。これは確かめていません。
  - 届かないときは、パブリックを許可する代わりに、ポート 5037 を WSL のアドレス範囲に限って許可する規則を作る方法があります。管理者権限の PowerShell で、次のように実行します (コマンドの形は確かめていません)。`172.16.0.0/12` は例で、実際のアドレス範囲は、`ipconfig` に出る vEthernet (WSL) のアドレスで確かめてください。使い終わったら、規則を消します。
    ```powershell
    New-NetFirewallRule -DisplayName "adb server (WSL)" -Direction Inbound -Action Allow -Protocol TCP -LocalPort 5037 -RemoteAddress 172.16.0.0/12
    Remove-NetFirewallRule -DisplayName "adb server (WSL)"
    ```
- Docker Engine を WSL2 の中で動かし (Docker Desktop ではない)、WSL のネットワークが NAT モードのとき、`ADB_SERVER_SOCKET` に `host.docker.internal` (`--add-host` で結んだ host-gateway) は使えません。`host.docker.internal` では届きませんでした (`Connection refused` でした)。host-gateway は WSL の中の docker0 の `172.17.0.1` で、Windows のアドレスではありません。Windows の vEthernet (WSL) の IPv4 アドレスを使うと、コンテナの `adb devices` にエミュレータが出ました (Windows 10.0.26300、adb 37.0.1 で確かめました)。WSL2 の mirrored モードと Docker Desktop では、届く宛先を確かめていません。
- Windows を再起動すると、vEthernet (WSL) のアドレスが変わることがあります。`.devcontainer/.env` はコンテナの作成時にだけ読まれるので、書き直してコンテナを作り直してください。作り直さずに試すときは、コンテナのシェルで `export ADB_SERVER_SOCKET=tcp:<Windows のアドレス>:5037` とします。
- コンテナの `adb` は、Dockerfile が platform-tools の版を固定していないので、コンテナを作った時点の最新の版です。ホストとコンテナの `adb version` を比べてください。版が違っても、つながるかは、まだ確かめていません。つながらないときは、ホストの platform-tools を更新するか、コンテナを作り直して、版をそろえてください。
- `.ps1` は、Windows PowerShell 5.1 で日本語が化けないように、BOM 付きの UTF-8 で保存します。`.bat` は、cmd.exe が日本語の行を読み違えるため、ASCII の文字だけで書きます。文字化けしたり、途中で止まったりしたときは、この文字コードの扱いを確かめてください。

### Claude Code のスキル

上流の `mattpocock/skills` は、プロジェクトの設定 (`.claude/settings.json` の `extraKnownMarketplaces` と `enabledPlugins`) で、プラグインとして入れています。このプラグインは、マーケットプレイスの中の相対パスのプラグインです。Claude Code は、起動したときにインストールの記録 (`~/.claude/plugins/installed_plugins.json`) とキャッシュだけを読み、マーケットプレイスの確認は起動のあとに背景で行います。インストールの記録がない間は、起動しただけではスキルが出ず、セッションごとに `/reload-plugins` が要ります。そのため、`.devcontainer/bin/post-create.sh` が、コンテナの作成時に `claude plugin marketplace add`、`claude plugin install`、`claude plugin update` を `--scope project` で実行し、記録を作っておきます。記録は `~/.claude` (ボリューム) に残ります。このインストールに失敗してもセットアップは止まりません。手で入れるときは、リポジトリのフォルダで次のコマンドを実行します。

```bash
claude plugin marketplace add mattpocock/skills#v1.3.2 --scope project
claude plugin install mattpocock-skills@mattpocock --scope project
claude plugin update mattpocock-skills@mattpocock --scope project
```

`install` は、すでに入っているときは何もしません。版を上げたときにも新しくするため、`update` も実行します。

- `--scope project` の `install` は `.claude/settings.json` のキーの順番を変え、`marketplace add` は順番に加えて `autoUpdate: false` を消すことがあります。手で実行したあとは、`git diff .claude/settings.json` で差分を確かめ、差分がこれらのコマンドによるものだけなら、`git restore .claude/settings.json` で戻します。`settings.json` にまだコミットしていない自分の変更があるときは、`git restore` で消えてしまうので、先に退避してください。スクリプトは、実行の前後で内容を戻しています。
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
- ビルド用の constraints (`gradle/libs.versions.toml` の bouncycastle、jose4j、jdom2、commons-lang3、httpclient、freemarker、guava) の minor と patch は、プラグインが試していない版で壊れたときに切り分けやすいよう、その他とは別に、まとめた 1 つの PR になります。
- その他の major の更新は、1 件ずつ単独の PR になります。

Kotlin のバージョンは、AGP が要求する KGP 以上にそろえます。詳しくは `gradle/libs.versions.toml` のコメントを参照してください。

### 脆弱性の更新 (セキュリティ更新)

脆弱性の検出と更新には、Dependabot alerts と Dependabot security updates を使います。Gradle は、リポジトリのファイルを読むだけでは推移的な依存関係まで分からないため、`.github/workflows/dependency-submission.yml` が依存関係のグラフを GitHub に送ります。このワークフローは、`main` への push と手動実行 (`workflow_dispatch`) で動きます。定期実行はしません。

Dependabot alerts と Dependabot security updates を使うには、リポジトリの設定で両方を有効にします。Dependabot alerts を有効にすると、Dependency graph も生成されます。Dependency graph が無効だと `dependency-submission.yml` のグラフの送信が失敗するので、ワークフローより先に Dependabot alerts を有効にします。

- セキュリティ更新の PR は、`dependabot.yml` のグループに入らず、1 件ずつ単独の PR になります。ただし、リポジトリの設定の Grouped security updates を有効にしないことが前提です。有効にすると、`dependabot.yml` にグループがなくても、エコシステムごとにまとめた PR になるためです。
- alerts は、推移的な依存関係を含むグラフ全体に出ます。PR が作られるのは、`gradle/libs.versions.toml` のように、ファイルに書いてある依存関係で、修正版に上げられるときだけです。推移的な依存関係は、alert だけで PR が来ないことがあります。
- GitHub Actions は SHA で固定しているため、alerts の対象になりません。Actions の更新は、バージョン更新の PR で行います。

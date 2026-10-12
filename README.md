# aa-thread-viewer

## 開発

開発環境は devcontainer です。JDK と Android SDK のバージョンは、リポジトリのルートの `versions.properties` にまとめています。devcontainer、CI、Gradle がこのファイルを読むので、バージョンを変えるときは、このファイルだけを書き換えます。JDK は `JDK_VERSION` と `JDK_SDKMAN_ID` の両方を直します。`targetSdk` と `minSdk` は、アプリの挙動に関わる設定なので、`app/build.gradle.kts` に書いています。

```bash
# CI と同じ確認をする (単体テスト、Android Lint、format のチェック、デバッグビルド)。
./gradlew check assembleDebug

# format の崩れを自動で直す。
./gradlew spotlessApply
```

### 依存関係の更新

依存関係の更新は、Dependabot (`.github/dependabot.yml`) と手作業で分担しています。

- Dependabot が更新するもの: `gradle/libs.versions.toml` の `[libraries]` と `[plugins]` (ktlint は `[versions]` にしかないため対象外)、Gradle Wrapper、GitHub Actions。毎週月曜の朝に確認し、PR を作ります。
- 手で更新するもの: `versions.properties` (JDK と Android SDK)、`.devcontainer/Dockerfile` の `ARG` (`WTP_VERSION`、`GIT_CORE_PPA_KEYS`)、ktlint。ktlint は `gradle/libs.versions.toml` の `[versions]` にしかなく、Dependabot が読まないためです。spotless を上げるときは、ktlint もいっしょに見直します。

PR の分け方は次のとおりです。

- AGP と Gradle Wrapper は、影響が大きいので、それぞれ単独の PR になります。
- AndroidX (Compose BOM を含む)、Kotlin、その他 (minor と patch)、GitHub Actions は、それぞれまとめた 1 つの PR になります。
- ビルド用の constraints (`gradle/libs.versions.toml` の bouncycastle、jose4j、jdom2、commons-lang3、httpclient) の minor と patch は、AGP が試していない版で壊れたときに切り分けやすいよう、その他とは別に、まとめた 1 つの PR になります。
- その他の major の更新は、1 件ずつ単独の PR になります。

Kotlin のバージョンは、AGP が要求する KGP 以上にそろえます。詳しくは `gradle/libs.versions.toml` のコメントを参照してください。

### 脆弱性の更新 (セキュリティ更新)

脆弱性の検出と更新には、Dependabot alerts と Dependabot security updates を使います。Gradle は、リポジトリのファイルを読むだけでは推移的な依存関係まで分からないため、`.github/workflows/dependency-submission.yml` が依存関係のグラフを GitHub に送ります。このワークフローは、`main` への push と手動実行 (`workflow_dispatch`) で動きます。定期実行はしません。

Dependabot alerts と Dependabot security updates を使うには、リポジトリの設定で両方を有効にします。Dependabot alerts を有効にすると、Dependency graph も生成されます。Dependency graph が無効だと `dependency-submission.yml` のグラフの送信が失敗するので、ワークフローより先に Dependabot alerts を有効にします。

- セキュリティ更新の PR は、`dependabot.yml` のグループに入らず、1 件ずつ単独の PR になります。ただし、リポジトリの設定の Grouped security updates を有効にしないことが前提です。有効にすると、`dependabot.yml` にグループがなくても、エコシステムごとにまとめた PR になるためです。
- alerts は、推移的な依存関係を含むグラフ全体に出ます。PR が作られるのは、`gradle/libs.versions.toml` のように、ファイルに書いてある依存関係で、修正版に上げられるときだけです。推移的な依存関係は、alert だけで PR が来ないことがあります。
- GitHub Actions は SHA で固定しているため、alerts の対象になりません。Actions の更新は、バージョン更新の PR で行います。

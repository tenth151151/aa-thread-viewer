# aa-thread-viewer

## 開発

開発環境は devcontainer です。JDK と Android SDK のバージョンは、リポジトリのルートの `versions.properties` にまとめています。devcontainer、CI、Gradle がこのファイルを読むので、バージョンを変えるときは、このファイルだけを書き換えます。JDK は `JDK_VERSION` と `JDK_SDKMAN_ID` の両方を直します。`targetSdk` と `minSdk` は、アプリの挙動に関わる設定なので、`app/build.gradle.kts` に書いています。

```bash
# CI と同じ確認をする（単体テスト、Android Lint、format のチェック、デバッグビルド）。
./gradlew check assembleDebug

# format の崩れを自動で直す。
./gradlew spotlessApply
```

### 依存関係の更新

依存関係の更新は、Dependabot（`.github/dependabot.yml`）と手作業で分担しています。

- Dependabot が更新するもの: `gradle/libs.versions.toml` の `[libraries]` と `[plugins]`（ktlint は `[versions]` にしかないため対象外）、Gradle Wrapper、GitHub Actions。毎週月曜の朝に確認し、PR を作ります。
- 手で更新するもの: `versions.properties`（JDK と Android SDK）、`.devcontainer/Dockerfile` の `ARG`（`WTP_VERSION`、`GIT_CORE_PPA_KEYS`）、ktlint。ktlint は `gradle/libs.versions.toml` の `[versions]` にしかなく、Dependabot が読まないためです。spotless を上げるときは、ktlint もいっしょに見直します。

PR の分け方は次のとおりです。

- AGP と Gradle Wrapper は、影響が大きいので、それぞれ単独の PR になります。
- AndroidX（Compose BOM を含む）、Kotlin、その他（minor と patch）、GitHub Actions は、それぞれまとめた 1 つの PR になります。
- その他の major の更新は、1 件ずつ単独の PR になります。

Kotlin のバージョンは、AGP が要求する KGP 以上にそろえます。詳しくは `gradle/libs.versions.toml` のコメントを参照してください。

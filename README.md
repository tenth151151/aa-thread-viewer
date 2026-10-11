# aa-thread-viewer

## 開発

開発環境は devcontainer です。JDK と Android SDK のバージョンは、リポジトリのルートの `versions.properties` にまとめています。devcontainer、CI、Gradle がこのファイルを読むので、バージョンを変えるときは、このファイルだけを書き換えます。JDK は `JDK_VERSION` と `JDK_SDKMAN_ID` の両方を直します。`targetSdk` と `minSdk` は、アプリの挙動に関わる設定なので、`app/build.gradle.kts` に書いています。

```bash
# CI と同じ確認をする（単体テスト、Android Lint、format のチェック、デバッグビルド）。
./gradlew check assembleDebug

# format の崩れを自動で直す。
./gradlew spotlessApply
```

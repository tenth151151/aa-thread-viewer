# aa-thread-viewer

## 開発

開発環境は devcontainer です。JDK は Temurin 25、Android SDK は platform android-37.0 と build-tools 36.0.0 を使います。CI も同じバージョンにそろえています。

```bash
# CI と同じ確認をする（単体テスト、Android Lint、format のチェック、デバッグビルド）。
./gradlew check assembleDebug

# format の崩れを自動で直す。
./gradlew spotlessApply
```

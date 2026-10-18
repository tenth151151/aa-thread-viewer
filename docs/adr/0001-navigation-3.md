# 画面の切り替えに Navigation 3 を使う

画面の切り替えには Navigation 3 (`androidx.navigation3`) を使い、画面ごとの ViewModel は、`androidx.lifecycle:lifecycle-viewmodel-navigation3` で、画面のエントリーにスコープします。Navigation Compose (`androidx.navigation:navigation-compose`) は使いません。Navigation のリリースノート (`androidx.navigation`) の冒頭に、保守だけを続ける (maintenance mode) という注意があります。Navigation 3 には安定版があり、公式は、Navigation 3 を本番のアプリで使うよう案内し、Navigation Compose を使っているアプリには移行を検討するよう案内しています。これから画面が、板の一覧、スレッドの一覧、スレッドと増えるので、Navigation 3 で始め、あとで移る手間を避けます。

確かめた版は、Navigation 3 が 1.2.0、Navigation (Navigation Compose を含む) が 2.10.2、Lifecycle が 2.11.0 です。

## 検討した案

- Navigation Compose の型安全なルート。上の理由で選びませんでした。

## 出典

- https://developer.android.com/jetpack/androidx/releases/navigation
- https://developer.android.com/jetpack/androidx/releases/navigation3
- https://developer.android.com/blog/posts/jetpack-navigation-3-is-stable
- https://developer.android.com/guide/navigation/navigation-3/get-started

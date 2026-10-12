# アーキテクチャのルール

本プロジェクトの設計の方針と、パッケージの分け方を定めます。アプリのコードを追加、変更するときは、必ず従います。決めた経緯は Issue #10 にあります。

## 画面の設計

MVVM + UDF (単方向データフロー) で作ります。

- 画面ごとに ViewModel を置き、再利用する小さな UI の部品には置きません。状態や操作を持たない表示だけの画面 (今の `HomeScreen` など) には、ViewModel を置きません。状態や操作が要るようになったときに置きます。
- ViewModel は `StateFlow<XxxUiState>` で UI state を公開します。
- 画面からの操作は、ViewModel のメソッドで受けます。
- UI state を sealed にするか data class にするかは、画面ごとに決めます。
- ViewModel を持つ画面は、2 つの Composable に分けます。ViewModel を受け取る画面 (例: `XxxScreen(viewModel)`) と、UI state と操作のラムダだけを受け取る、状態を持たない Composable (例: `XxxContent(uiState, onXxx)`) です。プレビューは、状態を持たない Composable で行います。Compose の UI テストは、今は書きません (テストは JVM の JUnit 4 で動かしていて、エミュレータを使う計装テスト (`androidTest`) は使わない前提のためです)。UI テストが要るときは、ユーザーに相談します。
- 画面 (`XxxScreen`) は、`collectAsStateWithLifecycle` (`androidx.lifecycle:lifecycle-runtime-compose`) で UI state を集め、状態を持たない Composable に渡します。このライブラリを足す時期は、「最初の ViewModel を作るときにすること」の節を参照してください。

```kotlin
@Composable
fun XxxScreen(viewModel: XxxViewModel, modifier: Modifier = Modifier) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    XxxContent(uiState = uiState, onXxx = viewModel::onXxx, modifier = modifier)
}

@Composable
fun XxxContent(uiState: XxxUiState, onXxx: () -> Unit, modifier: Modifier = Modifier) {
    // uiState を表示し、操作は onXxx などのラムダで通知する。ViewModel は参照しない。
    // modifier は、最上位のレイアウトに渡す。
}
```

公式の推奨のうち、特に守るものは次のとおりです。

- ViewModel に `Activity`、`Context`、`Resources` を渡しません。ViewModel で `Context` が要りそうなときは、置き場所の層が正しいかを見直します。
- ViewModel から UI にイベントを送りません。イベントは ViewModel で処理し、結果を UI state の更新として反映します。
- ViewModel は、データ層とのやり取りに Kotlin の Flow と、`viewModelScope` で呼ぶ suspend 関数を使います。

## 層

UI 層とデータ層の 2 層で作ります。

- データは、Repository を通して公開します。データ源が 1 つだけでも、Repository を作ります。
- UI 層 (Composable、ViewModel) は、データベース、DataStore、SharedPreferences、Firebase の API などのデータ源を直接使いません。
- domain 層 (UseCase) は、必要になるまで作りません。足すのは、次のどちらかのときです。
  - 複数の ViewModel で、データ層とやり取りする同じロジックを使うとき。
  - ViewModel のロジックが複雑になったとき。

## DI

手動の DI から始め、コンストラクタ注入を守ります。

- `Application` に `AppContainer` を置きます (JetNews と同じ形)。`AppContainer` は、Repository などを作って ViewModel に渡す入れ物です。
- `Application` のクラスは、`AndroidManifest.xml` の `<application>` に `android:name` で登録します (今のマニフェストには `android:name` がありません)。
- `AppContainer` は、最初の Repository を作るときに作ります。それまでは作りません。
- クラスは、依存するものをコンストラクタで受け取ります。クラスの中で依存先を生成したり、グローバルな状態から取り出したりしません。
- 手動の DI での ViewModel の作り方は、今は決めません。決める時期は、「最初の ViewModel を作るときにすること」の節を参照してください。

### Hilt に移る目安

公式の Architecture recommendations は、Hilt か手動の DI を、小さなアプリで選べるものとしています。プロジェクトが複雑なら Hilt を使うとして、次を例に挙げています。

- 複数の画面が ViewModel を持つ。
- WorkManager を使う。
- ナビゲーションのバックスタックにスコープした ViewModel がある。

本プロジェクトでは、次のどれかに当たるときを、移る目安にします。

- ViewModel を持つ画面が 2 つ以上になったとき。数えるのは ViewModel を持つ画面だけです。表示だけの画面は数えません。ViewModel を持つ 2 つ目の画面を作る変更 (または、表示だけの画面に ViewModel を足して 2 つ目にする変更) が、ちょうど目安に当たり、必ず相談になります。
- WorkManager を使うとき。
- ナビゲーションのグラフにスコープして、画面をまたいで共有する ViewModel ができたとき。行き先ごとの既定のスコープの ViewModel は、この目安には数えません。

依頼を出す監督と、依頼を受けた worker は、どちらも作業を始める前に、依頼が目安に当たるかを確かめます。当たるときは、worker は作業を始めずに監督に伝えます。監督はユーザーに、移るかを相談します。監督も worker も、自分で移ることはしません。監督や worker を通さずにアプリのコードを変えるときも、変える人が作業を始める前に同じように確かめ、当たるときは作業を始めずにユーザーに相談します。reviewer は、変更が目安に当たるときに報告します。これは、確かめ漏れに気づくための二重の確認です。

相談の結果、移らないと決めたときは、その決定と日付を、下の「移らないと決めたこと」の小見出しに書き足します。「移らないと決めたこと」に書いた目安に当たる状態のまま作業を続けるときは、同じことを相談し直しません。新しく別の目安に当たったときは、相談します。

#### 移らないと決めたこと

まだありません。

### Hilt に移るときの手順と注意

2026-10-12 に、使い捨てのプロジェクトで、Hilt 2.60.1、KSP 2.3.12、androidx.hilt 1.4.0 (`hilt-lifecycle-viewmodel-compose`) が、JDK 25、AGP 9.4.1、Kotlin 2.4.21 で、回避策なしで `assembleDebug testDebugUnitTest check` を通ることを確かめました。`kotlin-metadata-jvm` の回避策は要りませんでした。移るときは、その時点の最新の版で、もう一度ビルドを確かめます。

手順は次のとおりです。

1. `gradle/libs.versions.toml` に、次を足します。
   - 版を 3 つ (`ksp`、`hilt`、`androidxHilt`)。
   - ライブラリを 3 つ (`hilt-android`、`hilt-compiler`、`androidx.hilt:hilt-lifecycle-viewmodel-compose`)。
   - プラグインを 2 つ (`com.google.devtools.ksp`、`com.google.dagger.hilt.android`)。
2. root の `build.gradle.kts` の `plugins` に、2 つのプラグインを `apply false` で足します。
3. `app/build.gradle.kts` に、2 つのプラグインと、`implementation(hilt-android)`、`implementation(androidx-hilt-lifecycle-viewmodel-compose)`、`ksp(hilt-compiler)` を足します。
4. 今の `Application` (`AppContainer` を持つもの) に `@HiltAndroidApp` を付けます。`Application` がまだないときは作り、`AndroidManifest.xml` の `<application>` に `android:name` で登録します。`MainActivity` に `@AndroidEntryPoint` を付けます。
5. Repository は、interface と、`@Inject constructor` の実装にします。`@Module` と `@InstallIn(SingletonComponent::class)` を付けたクラスの `@Binds` で、interface に実装を結び付けます。
6. ViewModel に `@HiltViewModel` と `@Inject constructor` を付けます。
7. `AppContainer` と、手動の ViewModel の Factory を外します。Hilt の Module と `@HiltViewModel` に置き換えるので、2 つの DI を並べて残しません。
8. 画面は、`androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel` を使います。`androidx.hilt.navigation.compose.hiltViewModel` は非推奨なので使いません。ViewModel を受け取る画面と、状態を持たない Composable の分け方は、「画面の設計」の節のとおりです。
9. 単体テストは Hilt を使いません。Fake を、ViewModel のコンストラクタに渡します。

注意は次のとおりです。

- KSP 用のクラスパス (`ksp` で始まる `…ProcessorClasspath` など) には、#24 で入れた constraints (root の `buildscript` と `androidLintTool`) が効きません。移ったあとに Dependabot alerts が出たら、constraints を足す必要があるかを確かめます。
- Dagger 2.60 から、Hilt の Gradle プラグインを使うとき、プラグインが管理するフラグを設定すると、エラーになります (以前は無視されていました)。
- `.github/dependabot.yml` のグループは、KSP と Hilt をどう扱うかを、移るときに決めます。
- WorkManager がきっかけで移るときは、Worker に依存を注入するための追加の設定が要ります。公式の Hilt と Jetpack のページ (出典の節) では、`androidx.hilt:hilt-work` と、追加の注釈プロセッサ (`androidx.hilt:hilt-compiler`) を足し、Worker に `@HiltWorker` を付け、`Application` で WorkManager の設定に `HiltWorkerFactory` を渡し、マニフェストから WorkManager の既定の初期化を外すとしています。この `androidx.hilt:hilt-compiler` は、手順 3 の `hilt-compiler` (Dagger の `com.google.dagger:hilt-compiler`) とは別のライブラリなので、カタログの別名も分けます。版と書き方は、移るときに公式のページで確かめます。
- 移るときに決めることは、ユーザーに相談します。

## パッケージ

機能ごとに分け、共通の `data/` を置きます (architecture-samples と同じ形)。`:app` の 1 モジュールのまま、パッケージで分けます。

- 機能のパッケージに、Screen、ViewModel、UiState をまとめます。
- Repository とデータ源は、`data/` に置きます。dat を取得したり保存したりするデータ源と Repository も `data/` に置き、`dat/` の解析を使います。
- `dat/` には、dat 形式の解析 (文字列から `Post` などを作る、取得や保存をしないコード) だけを置きます。
- `dat/` と `ui/theme/` は、共通として今の場所に置きます。
- `MainActivity` と、将来の `Application`、`AppContainer` は、ルートのパッケージに置きます。

ルートのパッケージは `io.github.tenth151151.aathreadviewer` で、ファイルは `app/src/main/kotlin/` の下に置きます。次は、ホーム画面に ViewModel と Repository を足したあとの例です。`(未作成)` を付けたファイルは、今はありません。

```text
io/github/tenth151151/aathreadviewer/
├── MainActivity.kt
├── AatvApplication.kt      (未作成。AppContainer を持つ)
├── AppContainer.kt         (未作成。最初の Repository を作るときに作る)
├── dat/                    (dat 形式の解析。取得や保存はしない)
├── ui/theme/               (共通のテーマ)
├── data/                   (未作成)
│   └── ThreadRepository.kt (interface。実装も data/ に置く)
└── home/
    ├── HomeScreen.kt
    ├── HomeViewModel.kt    (未作成)
    └── HomeUiState.kt      (未作成)
```

`Application` のクラス名は例であり、作るときに決めます。

## 最初の ViewModel を作るときにすること

最初の ViewModel を作るときに、次を行います。

- Lifecycle のライブラリ (`lifecycle-runtime-compose`、`lifecycle-viewmodel-compose`) を、`gradle/libs.versions.toml` と `app/build.gradle.kts` に足します。
- `kotlinx-coroutines-test` などを入れます。
- 手動の DI での ViewModel の作り方 (`ViewModelProvider.Factory` など) を決めます。**ViewModel を作る前に**、ユーザーに相談します。
- `/tdd` スキル (`.claude/skills/tdd/SKILL.md`) に、ViewModel のテストの書き方 (Fake を渡す、Main ディスパッチャーの差し替えなど) と、`stateIn(…, SharingStarted.WhileSubscribed(…), …)` で作った `StateFlow` の注意を足します。注意とは、集める側がいないと `value` が初期値のままになることです。テストで `value` を検証するときは、`backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.xxx.collect {} }` のように、空の collector を用意します (出典は「出典」の節の Flow のテストのページ)。

最初の Repository を同時に作るときは、`AppContainer` も作ります (DI の節のとおり)。

## テスト

Fake を使います。

- Fake に差し替えられるよう、ViewModel は Repository の interface に依存します。
- `FakeXxxRepository` を、テスト用のソースセット (`app/src/test/kotlin/`) に置き、ViewModel のコンストラクタに渡します。Fake の名前は、Repository の interface の名前の前に `Fake` を付けます (`ThreadRepository` なら `FakeThreadRepository`)。
- ViewModel のテストに要るライブラリと、`/tdd` スキルへの追記は、「最初の ViewModel を作るときにすること」の節を参照してください。
- モックのライブラリは、使いません。Fake では書きにくいと分かったときだけ、ユーザーに相談します。
- `StateFlow` は、`value` で検証できる形にします (`stateIn` の注意は、「最初の ViewModel を作るときにすること」の節を参照してください)。
- テストの進め方は、`.claude/rules/tdd.md` に従います。

## 出典

- Issue #10 (決めた内容と、Hilt の確認の結果)。
- Guide to app architecture: https://developer.android.com/topic/architecture
- Architecture recommendations: https://developer.android.com/topic/architecture/recommendations
- UI layer: https://developer.android.com/topic/architecture/ui-layer
- Data layer: https://developer.android.com/topic/architecture/data-layer
- Domain layer: https://developer.android.com/topic/architecture/domain-layer
- Manual dependency injection: https://developer.android.com/training/dependency-injection/manual
- Test doubles: https://developer.android.com/training/testing/fundamentals/test-doubles
- Lifecycle のリリースノート: https://developer.android.com/jetpack/androidx/releases/lifecycle
- androidx.hilt のリリースノート (`hiltViewModel` を新しい artifact とパッケージに移したこと): https://developer.android.com/jetpack/androidx/releases/hilt
- androidx.hilt.navigation.compose の `hiltViewModel` のリファレンス (非推奨の表示): https://developer.android.com/reference/kotlin/androidx/hilt/navigation/compose/hiltViewModel.composable
- Hilt と Jetpack (WorkManager への注入): https://developer.android.com/training/dependency-injection/hilt-jetpack
- Flow のテスト (`stateIn` と `backgroundScope`): https://developer.android.com/kotlin/flow/test
- Dagger 2.60 のリリースノート: https://github.com/google/dagger/releases/tag/dagger-2.60

Hilt 2.60.1 の動作確認は、公式のページではなく、Issue #10 と 2026-10-12 の検証にもとづきます。

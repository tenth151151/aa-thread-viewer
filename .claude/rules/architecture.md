# アーキテクチャのルール

本プロジェクトの設計の方針と、パッケージの分け方を定めます。アプリのコードを追加、変更するときは、必ず従います。

## 画面の設計

MVVM + UDF (単方向データフロー) で作ります。

- ViewModel は画面ごとに置きます。置くのは、画面が状態を持つときか、データ層とやり取りする操作があるときです。再利用する小さな UI の部品には置きません。
  - ここでの状態は、画面に表示するデータなどの、画面の UI state です。スクロールの位置や開け閉めのフラグのような UI 要素の状態 (`remember` や `rememberSaveable` で Composable に持つもの) は入りません。UI 要素の状態は Composable に置いてよく、それだけでは ViewModel を置く理由にしません。公式の state holder のページが、UI 要素の状態を Composable や普通のクラスの state holder に持たせるものとし、UI のロジックを UI の中に直接書くことも認めているためです。
- ViewModel は `StateFlow<XxxUiState>` で UI state を公開します。
- 画面からの操作は、次のように受けます。
  - ビジネスロジックが要らない画面の切り替えと、Activity の仕組み (ファイルの選択画面など) を開く操作は、ViewModel を持つ画面でも持たない画面でも、ラムダで呼び出し元に知らせます (今の `HomeScreen` の `onOpenDat` など)。ViewModel を持つ画面では、`XxxScreen` がこのラムダを呼び出し元から受け取り、ViewModel を経由せずに `XxxContent` へそのまま渡します。公式の UI events のページが、画面の切り替えを UI が扱うもの (UI behavior logic) とし、呼び出し元の Composable にラムダで知らせる例を示しているためです。Activity の仕組みを開く操作は、ViewModel に `Activity` や `Context` を渡さない決まりがあるので、UI で扱います。
  - 画面を切り替える前にビジネスロジック (確かめごとなど) が要るときは、操作を ViewModel のメソッドで受け、結果を UI state に反映します。画面はその UI state を見て、切り替えます (呼び出し元のラムダを呼びます)。ViewModel は画面を切り替えず、UI にイベントも送りません。
    - 切り替え (呼び出し元のラムダを呼ぶこと) は、Composable の本体で直接呼ばず、`LaunchedEffect` の中で呼びます。本体で呼ぶと、組み直すたびに呼ばれて、同じ画面が何度もバックスタックに積まれるためです。呼び出し元のラムダは、`rememberUpdatedState` で持ちます。公式の UI events のページの例も、この形です。
    - 切り替えたあとに、戻ってきたときにまた切り替わらないようにします。ViewModel は画面のエントリーにスコープしていて、次の画面にいる間も残るので、ViewModel の UI state だけで切り替えを決めると、戻ってきたときにまた切り替わるためです。公式のページは、元の画面がバックスタックに残るときは、切り替えるかどうかを示す追加の状態を UI に持つとしています (画面の切り替えのロジックは UI の関心ごとであるため)。例では、操作を受けたときに `rememberSaveable` のフラグを true にし、フラグが true の間だけ UI state を見て切り替え、切り替える直前にフラグを false に戻しています。このフラグと上の `LaunchedEffect` は、UI state を集める `XxxScreen` に置き、状態を持たない `XxxContent` には置きません。
  - それ以外の操作 (状態を変える操作、データ層とやり取りする操作) は、ViewModel のメソッドで受けます。ただし、UI 要素の状態だけを変える操作 (開け閉めの切り替えなど) は、Composable の中で扱ってかまいません。
- UI state を sealed にするか data class にするかは、画面ごとに決めます。
- ViewModel を持つ画面は、2 つの Composable に分けます。ViewModel を受け取る画面 (例: `XxxScreen(viewModel)`) と、UI state と操作のラムダだけを受け取る、状態を持たない Composable (例: `XxxContent(uiState, onXxx)`) です。プレビューは、状態を持たない Composable で行います。Compose の UI テストは、今は書きません (テストは JVM の JUnit 4 で動かしていて、エミュレータを使う計装テスト (`androidTest`) は使わない前提のためです)。UI テストが要るときは、ユーザーに相談します。
- 画面 (`XxxScreen`) は、`collectAsStateWithLifecycle` (`androidx.lifecycle:lifecycle-runtime-compose`) で UI state を集め、状態を持たない Composable に渡します。このライブラリの版は、「画面の切り替え」の節を参照してください。

```kotlin
@Composable
fun XxxScreen(
    viewModel: XxxViewModel,
    onNavigateToYyy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    // 画面の切り替えのラムダは、呼び出し元から受け取り、ViewModel を通さずにそのまま渡す。
    XxxContent(
        uiState = uiState,
        onXxx = viewModel::onXxx,
        onNavigateToYyy = onNavigateToYyy,
        modifier = modifier,
    )
}

@Composable
fun XxxContent(
    uiState: XxxUiState,
    onXxx: () -> Unit,
    onNavigateToYyy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // uiState を表示し、操作は onXxx や onNavigateToYyy などのラムダで通知する。ViewModel は参照しない。
    // modifier は、最上位のレイアウトに渡す。
}
```

公式の推奨のうち、特に守るものは次のとおりです。

- ViewModel に `Activity`、`Context`、`Resources` を渡しません。ViewModel で `Context` が要りそうなときは、置き場所の層が正しいかを見直します。
- ViewModel から UI にイベントを送りません。イベントは ViewModel で処理し、結果を UI state の更新として反映します。
- ViewModel は、データ層とのやり取りに Kotlin の Flow と、`viewModelScope` で呼ぶ suspend 関数を使います。

### 画面の切り替え

この節が、画面の切り替えの決まりの正です。ほかの節からは、この節を指します。

- 画面の切り替えには Navigation 3 を使います (`docs/adr/0001-navigation-3.md`)。Navigation Compose は使いません。

#### 足すライブラリと時期

- 画面を 2 つ以上にするとき (`NavDisplay` を入れるとき) に、`androidx.navigation3` の `navigation3-runtime` と `navigation3-ui` を足します。あわせて、`kotlinx-serialization-core` と、Kotlin の serialization のプラグイン (`org.jetbrains.kotlin.plugin.serialization`) を足します。`kotlinx-serialization-core` は、画面のキーをシリアライズできるようにするためです (出典は「出典」の節の Navigation 3 のはじめかた)。プラグインは公式では任意とされていますが、本プロジェクトでは、画面のキーに `@Serializable` を付けてシリアライザーを生成するので、足します。serialization のプラグインの版は、Kotlin の版にそろえます。
- `NavDisplay` と ViewModel を持つ画面の両方がそろったときに、`lifecycle-viewmodel-navigation3` を足します。
- Lifecycle のライブラリ (`lifecycle-runtime-compose`、`lifecycle-viewmodel-compose`、`lifecycle-viewmodel-navigation3`) は、同じ安定版にそろえます。版は Lifecycle のリリースノートで確かめます。Navigation 3 が 1.2.0 のとき、公式の Navigation 3 のはじめかたのページの例は、`lifecycle-viewmodel-navigation3` に alpha の版を示していました。

#### デコレーター

`NavDisplay` の `entryDecorators` は、`NavDisplay` 全体で 1 つのリストです。ViewModel を持つ画面があるときは、このリストに `rememberSaveableStateHolderNavEntryDecorator()` と `rememberViewModelStoreNavEntryDecorator()` を、この順で並べます。`entryDecorators` を指定すると既定のリストが置き換わるので、`rememberSaveableStateHolderNavEntryDecorator()` も必ず並べます。並べないと、エントリーごとの `rememberSaveable` の状態が保たれないおそれがあります。出典は「出典」の節の Navigation 3 のレシピです。

#### ViewModel のスコープ

- `NavDisplay` を使うときは、ViewModel を画面のエントリーにスコープします (同じ画面がバックスタックに複数あるときは、エントリーごとに別の ViewModel になります)。
- 画面をまたいで ViewModel を共有したいときは、Hilt に移る目安に当たるので、必ず相談します。

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
- `Application` のクラス (`AatvApplication`) は、`AndroidManifest.xml` の `<application>` に `android:name` で登録します。
- Repository は、`AppContainer` で作ります。
- クラスは、依存するものをコンストラクタで受け取ります。クラスの中で依存先を生成したり、グローバルな状態から取り出したりしません。
- ViewModel は、`ViewModelProvider.Factory` で作ります。ViewModel の `companion object` に、`viewModelFactory { initializer { … } }` で Factory を作る関数を置きます。`initializer` の中で、`ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY` から `Application` を取り、その `AppContainer` の Repository を ViewModel のコンストラクタに渡します。画面のキーの値 (Uri など) は、この関数の引数からコンストラクタに渡します (例: `ThreadViewModel.factory(uri)`)。画面 (`XxxScreen` を呼ぶ側) は、`viewModel(factory = …)` で ViewModel を受け取ります。

### Hilt に移る目安

公式の Architecture recommendations は、Hilt か手動の DI を、小さなアプリで選べるものとしています。プロジェクトが複雑なら Hilt を使うとして、次を例に挙げています。

- 複数の画面が ViewModel を持つ。
- WorkManager を使う。
- ナビゲーションのバックスタックにスコープした ViewModel がある。

本プロジェクトでは、次のどれかに当たるときを、移る目安にします。

- ViewModel を持つ画面が 2 つ以上になったとき。数えるのは ViewModel を持つ画面だけです。ViewModel を持たない画面は数えません。ViewModel を持つ 2 つ目の画面を作る変更 (または、ViewModel を持たない画面に ViewModel を足して 2 つ目にする変更) が、ちょうど目安に当たり、必ず相談になります。
- WorkManager を使うとき。
- 画面をまたいで共有するために作る ViewModel (Activity にスコープして複数の画面で使うものや、複数の画面のエントリーで共有するもの) を作るとき。
  - 公式は「ナビゲーションのバックスタックにスコープした ViewModel」を例に挙げています。本プロジェクトでは、画面をまたいで共有するものだけを数えます。どの ViewModel が共有に当たらないかは、「画面の切り替え」の節の「ViewModel のスコープ」にあります。

依頼を出す監督と、依頼を受けた worker は、どちらも作業を始める前に、依頼が目安に当たるかを確かめます。当たるときは、worker は作業を始めずに監督に伝えます。監督はユーザーに、移るかを相談します。監督も worker も、自分で移ることはしません。監督や worker を通さずにアプリのコードを変えるときも、変える人が作業を始める前に同じように確かめ、当たるときは作業を始めずにユーザーに相談します。reviewer は、変更が目安に当たるときに報告します。これは、確かめ漏れに気づくための二重の確認です。

相談の結果、移らないと決めたときは、その決定を、下の「移らないと決めたこと」の小見出しに書き足します。「移らないと決めたこと」に書いた目安に当たる状態のまま作業を続けるときは、同じことを相談し直しません。新しく別の目安に当たったときは、相談します。

#### 移らないと決めたこと

まだありません。

### Hilt に移るときの手順と注意

使い捨てのプロジェクトで、Hilt 2.60.1、KSP 2.3.12、androidx.hilt 1.4.0 (`hilt-lifecycle-viewmodel-compose`) が、JDK 25、AGP 9.4.1、Kotlin 2.4.21 で、回避策なしで `assembleDebug testDebugUnitTest check` を通ることを確かめています。`kotlin-metadata-jvm` の回避策は要りませんでした。移るときは、その時点の最新の版で、もう一度ビルドを確かめます。

手順は次のとおりです。

1. `gradle/libs.versions.toml` に、次を足します。
   - 版を 3 つ (`ksp`、`hilt`、`androidxHilt`)。
   - ライブラリを 3 つ (`hilt-android`、`hilt-compiler`、`androidx.hilt:hilt-lifecycle-viewmodel-compose`)。
   - プラグインを 2 つ (`com.google.devtools.ksp`、`com.google.dagger.hilt.android`)。
2. root の `build.gradle.kts` の `plugins` に、2 つのプラグインを `apply false` で足します。
3. `app/build.gradle.kts` に、2 つのプラグインと、`implementation(hilt-android)`、`implementation(androidx-hilt-lifecycle-viewmodel-compose)`、`ksp(hilt-compiler)` を足します。
4. 今の `Application` (`AppContainer` を持つもの) に `@HiltAndroidApp` を付けます。`MainActivity` に `@AndroidEntryPoint` を付けます。
5. Repository は、interface と、`@Inject constructor` の実装にします。`@Module` と `@InstallIn(SingletonComponent::class)` を付けたクラスの `@Binds` で、interface に実装を結び付けます。
6. ViewModel に `@HiltViewModel` と `@Inject constructor` を付けます。
7. `AppContainer` と、手動の ViewModel の Factory を外します。Hilt の Module と `@HiltViewModel` に置き換えるので、2 つの DI を並べて残しません。
8. 画面は、`androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel` を使います。`androidx.hilt.navigation.compose.hiltViewModel` は非推奨なので使いません。ViewModel を受け取る画面と、状態を持たない Composable の分け方は、「画面の設計」の節のとおりです。
9. 単体テストは Hilt を使いません。Fake を、ViewModel のコンストラクタに渡します。

注意は次のとおりです。

- KSP 用のクラスパス (`ksp` で始まる `…ProcessorClasspath` など) には、脆弱性の対策で入れているビルド用の constraints (root の `buildscript` と `androidLintTool` のもの) が効きません。移ったあとに Dependabot alerts が出たら、constraints を足す必要があるかを確かめます。
- 警告を厳しく扱う設定を入れているため、移った直後に、Hilt や KSP が生成したコードの警告でビルドが落ちることがあります。
  - 設定の内容: `app/build.gradle.kts` で、Kotlin のコンパイラの `allWarningsAsErrors` と `extraWarnings`、lint の `checkGeneratedSources` と `checkTestSources` を有効にしています。Java のコンパイラの警告は、エラーにしていません (`JavaCompile` の `-Werror` がありません)。
  - 生成コードで効くもの: Dagger と Hilt は、KSP で動かしても Java のコードを生成するとされています (公式のページでは確かめられていません。移るときに確かめます)。そのとおりなら、生成されたコードの警告で効くのは、主に lint の `checkGeneratedSources` です。Kotlin の `allWarningsAsErrors` と `extraWarnings` は、手で書いた Kotlin のコードに効きます。Kotlin を生成するプロセッサの出力に効くかは、下の「未確認のこと」のとおり確かめていません。テストのソースも、lint の対象です。
  - 未確認のこと: KSP には、警告をエラーにする KSP 自身の設定があるとされています。そのため、Kotlin の `allWarningsAsErrors` で、KSP のプロセッサの警告がエラーになるとは限りません。この設定の有無と、プロセッサの警告がエラーになるかは、移るときに公式のページとビルドで確かめます。上の確認は、警告をエラーにする設定がない状態で行ったもので、これらは確かめていません。
  - 移るときにすること: `./gradlew check assembleDebug` で警告を確かめます。直せない警告があるときは、どれを外すか (生成コードを対象から外す、検査を無効にするなど) をユーザーに相談します。
- Dagger 2.60 以降では、Hilt の Gradle プラグインを使うとき、プラグインが管理するフラグを設定すると、エラーになります。
- `.github/dependabot.yml` のグループは、KSP と Hilt をどう扱うかを、移るときに決めます。
- WorkManager がきっかけで移るときは、Worker に依存を注入するための追加の設定が要ります。公式の Hilt と Jetpack のページ (出典の節) では、`androidx.hilt:hilt-work` と、追加の注釈プロセッサ (`androidx.hilt:hilt-compiler`) を足し、Worker に `@HiltWorker` を付け、`Application` で WorkManager の設定に `HiltWorkerFactory` を渡し、マニフェストから WorkManager の既定の初期化を外すとしています。この `androidx.hilt:hilt-compiler` は、手順 3 の `hilt-compiler` (Dagger の `com.google.dagger:hilt-compiler`) とは別のライブラリなので、カタログの別名も分けます。版と書き方は、移るときに公式のページで確かめます。
- 移るときに決めることは、ユーザーに相談します。

## パッケージ

機能ごとに分け、共通の `data/` を置きます (architecture-samples と同じ形)。`:app` の 1 モジュールのまま、パッケージで分けます。

- 機能のパッケージに、Screen、ViewModel、UiState と、画面のキー (`HomeKey` など。Navigation 3 の `NavKey`) をまとめます。
- Repository とデータ源は、`data/` に置きます。dat を取得したり保存したりするデータ源と Repository も `data/` に置き、`dat/` の解析を使います。
- `dat/` には、dat 形式の解析 (文字列から `Post` などを作る、取得や保存をしないコード) だけを置きます。
- `dat/` と `ui/theme/` は、共通として今の場所に置きます。
- `MainActivity` と、`NavDisplay` を出す `AatvNavDisplay`、`AatvApplication`、`AppContainer` は、ルートのパッケージに置きます。

ルートのパッケージは `io.github.tenth151151.aathreadviewer` で、ファイルは `app/src/main/kotlin/` の下に置きます。次は、今のファイルの構成です。

```text
io/github/tenth151151/aathreadviewer/
├── MainActivity.kt
├── AatvNavDisplay.kt       (NavDisplay と、画面のキーごとの画面の対応)
├── AatvApplication.kt      (AppContainer を持つ)
├── AppContainer.kt         (Repository などを作る)
├── dat/                    (dat 形式の解析。取得や保存はしない)
├── ui/theme/               (共通のテーマ)
├── data/
│   ├── ThreadRepository.kt (interface)
│   ├── DefaultThreadRepository.kt
│   ├── DatSource.kt        (interface)
│   ├── ContentResolverDatSource.kt
│   ├── DatFile.kt          (データ源が返す、バイト列とファイル名)
│   ├── LoadedThread.kt     (Repository が返す、読み込んだスレッド)
│   ├── ReadBytesWithLimit.kt (上限つきでバイト列を読む)
│   ├── ThreadLoadException.kt (読めなかった原因と、上限を超えたときの例外)
│   └── BoardType.kt
├── home/                   (ViewModel を持たない)
│   ├── HomeKey.kt
│   └── HomeScreen.kt
└── thread/
    ├── ThreadKey.kt
    ├── ThreadScreen.kt
    ├── ThreadErrorMessage.kt (読めなかった原因から文字列の ID を返す)
    ├── ThreadViewModel.kt
    └── ThreadUiState.kt
```

## テスト

Fake を使います。

- Fake に差し替えられるよう、ViewModel は Repository の interface に依存します。
- `FakeXxxRepository` を、テスト用のソースセット (`app/src/test/kotlin/`) に置き、ViewModel のコンストラクタに渡します。Fake の名前は、Repository の interface の名前の前に `Fake` を付けます (`ThreadRepository` なら `FakeThreadRepository`)。
- ViewModel のテストは、コルーチンのテスト用のライブラリ (`kotlinx-coroutines-test`) を使います。書き方は、`/tdd` スキル (`.claude/skills/tdd/SKILL.md`) の「ViewModel のテスト」の節にあります。
- モックのライブラリは、使いません。Fake では書きにくいと分かったときだけ、ユーザーに相談します。
- `StateFlow` は、`value` で検証できる形にします。`stateIn(…, SharingStarted.WhileSubscribed(…), …)` で作った `StateFlow` は、集める側がいないと `value` が初期値のままになります。その場合の空の collector の用意の仕方は、`/tdd` スキルの「ViewModel のテスト」の節にあります (出典は「出典」の節の Flow のテストのページ)。
- テストの進め方は、`.claude/rules/tdd.md` に従います。

## 出典

- Guide to app architecture: https://developer.android.com/topic/architecture
- Architecture recommendations: https://developer.android.com/topic/architecture/recommendations
- UI layer: https://developer.android.com/topic/architecture/ui-layer
- UI events: https://developer.android.com/topic/architecture/ui-layer/events
- State holders and UI state: https://developer.android.com/topic/architecture/ui-layer/stateholders
- Data layer: https://developer.android.com/topic/architecture/data-layer
- Domain layer: https://developer.android.com/topic/architecture/domain-layer
- Manual dependency injection: https://developer.android.com/training/dependency-injection/manual
- Test doubles: https://developer.android.com/training/testing/fundamentals/test-doubles
- Lifecycle のリリースノート: https://developer.android.com/jetpack/androidx/releases/lifecycle
- androidx.hilt のリリースノート (`hiltViewModel` を新しい artifact とパッケージに移したこと): https://developer.android.com/jetpack/androidx/releases/hilt
- androidx.hilt.navigation.compose の `hiltViewModel` のリファレンス (非推奨の表示): https://developer.android.com/reference/kotlin/androidx/hilt/navigation/compose/hiltViewModel.composable
- Hilt と Jetpack (WorkManager への注入): https://developer.android.com/training/dependency-injection/hilt-jetpack
- Flow のテスト (`stateIn` と `backgroundScope`): https://developer.android.com/kotlin/flow/test
- Navigation 3 のはじめかた: https://developer.android.com/guide/navigation/navigation-3/get-started
- Navigation 3 のレシピ (画面ごとの ViewModel のスコープ): https://developer.android.com/guide/navigation/navigation-3/recipes/passingarguments
- Dagger 2.60 のリリースノート: https://github.com/google/dagger/releases/tag/dagger-2.60

Hilt 2.60.1 の動作確認は、公式のページではなく、使い捨てのプロジェクトでの検証にもとづきます。

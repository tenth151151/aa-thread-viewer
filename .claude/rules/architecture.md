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
- 画面をまたいで ViewModel を共有したいとき (Activity にスコープして複数の画面で使うものや、複数の画面のエントリーで共有するものを作るとき) は、必ずユーザーに相談します。画面のエントリーにスコープする今の形から外れるためです。

## 層

UI 層とデータ層の 2 層で作ります。

- データは、Repository を通して公開します。データ源が 1 つだけでも、Repository を作ります。
- UI 層 (Composable、ViewModel) は、データベース、DataStore、SharedPreferences、Firebase の API などのデータ源を直接使いません。
- domain 層 (UseCase) は、必要になるまで作りません。足すのは、次のどちらかのときです。
  - 複数の ViewModel で、データ層とやり取りする同じロジックを使うとき。
  - ViewModel のロジックが複雑になったとき。

### スレッドの切り替え

スレッドを塞ぐ処理 (ブロッキングの I/O、重い CPU の処理) をする suspend 関数を持つクラスは、その関数を main-safe (メインスレッドから呼んでも安全) にします。層は問いません。Repository、データ源、UseCase が対象です。公式の Coroutines のベストプラクティスのページは、これを「処理をするクラスの責任」とし、アプリのすべてのクラスに当てはまるとしています。データ層のページも、長く塞ぐ処理をするクラスが、自分の処理を適切なスレッドに移す責任を持つとしています。

- 切り替えは、処理をするクラス自身が `withContext` で行います。呼び出し側 (ViewModel など) には任せません。
- 切り替えに使うディスパッチャーは、コンストラクタで受け取ります。`Dispatchers.IO` などを `withContext` やコルーチンの起動に直接書きません。公式のページが、`Dispatchers` を直接書かず、アプリではディスパッチャーを注入するよう求めているためです。JVM のテストで動かさないデータ源の実装 (`ContentResolverDatSource` など) も対象です。
- ディスパッチャーの引数に、既定値を付けません。Hilt の Module (`di/` の `DispatchersModule`) の qualifier `@Dispatcher(AatvDispatchers.<種類>)` で必ず渡します。`AatvDispatchers` の種類は今は `IO` だけです。`Default` などが要るときは、enum に種類を足し、`DispatchersModule` に `@Provides` を足します。公式のページの例は、既定値 (`Dispatchers.Default`) を付けていますが、ページに既定値の理由の説明はありません。本プロジェクトでは、渡し忘れをコンパイルで見つけるため、テストで差し替え忘れたときに本物のディスパッチャーで動くのを防ぐために、既定値を付けません。渡し忘れの確かめは、「Hilt の注意」の「渡し忘れ」の項にあります。
- `DispatchersModule` で渡すディスパッチャーは、ブロッキングの I/O をするなら `Dispatchers.IO`、CPU の処理だけなら `Dispatchers.Default`、両方を 1 つの流れでするなら `Dispatchers.IO` にします。この使い分けは、公式のページがはっきり書いているものではなく、公式の例に合わせた本プロジェクトの決まりです。
- データ源と、それを使う Repository が、どちらもブロッキングの処理を含むときは、両方が自分で切り替えます (`ContentResolverDatSource` と `DefaultThreadRepository`)。両方に、同じ `@Dispatcher(AatvDispatchers.IO)` の `Dispatchers.IO` を渡します。解析は、データ源の `read` に渡すブロックの中で動き、データ源のディスパッチャーで動くので、Repository に別のディスパッチャーを渡しても、解析のスレッドは変わらないためです。Repository の切り替えは、データ源の実装が切り替えるかどうかに頼らず、自分の関数を main-safe にするためのものです。二重の `withContext` は、この決まりどおりです。
- ViewModel は対象外です。`viewModelScope` は Main に決まっているので、ディスパッチャーを注入しません。テストでは `Dispatchers.setMain` で差し替えます (`/tdd` スキルの「ViewModel のテスト」の節)。
- JVM でテストするクラスは、渡したディスパッチャーで処理が動くことを、テストで確かめます。書き方は、`/tdd` スキルの「ディスパッチャーを受け取るクラスのテスト」の節にあります。

## DI

Hilt を使い、コンストラクタ注入を守ります。

- `Application` のクラス (`AatvApplication`) に `@HiltAndroidApp` を付け、`AndroidManifest.xml` の `<application>` に `android:name` で登録します。`MainActivity` には `@AndroidEntryPoint` を付けます。
- クラスは、依存するものをコンストラクタで受け取ります。クラスの中で依存先を生成したり、グローバルな状態から取り出したりしません。
- Repository とデータ源は、interface と、`@Inject constructor` の実装にします。Module の `@Binds` で、interface に実装を結び付けます。
- 自分で持っていないクラス (Android の framework のクラスや外部のライブラリのクラス。`ContentResolver`) は、コンストラクタに `@Inject` を付けられないので、Module の `@Provides` で渡します。公式のページは、外部のライブラリのクラス (Retrofit、OkHttpClient、Room のデータベースなど) は自分で持っていないので、コンストラクタ注入ができず、Hilt の Module の `@Provides` を付けた関数で渡せるとしています。
- enum の値のように、`@Inject constructor` で作れない値 (`BoardType`) も、Module の `@Provides` で渡します。
- Hilt の Module は、`di/` に置きます。`@InstallIn(SingletonComponent::class)` を付けます。
- 同じ型の束縛が複数あるときは、qualifier で区別します (ディスパッチャーは、「層」の節の「スレッドの切り替え」のとおり `@Dispatcher(AatvDispatchers.IO)` です)。`Context` が要るときは、`@ApplicationContext` で受け取ります。
- ディスパッチャーの受け取り方は、「層」の節の「スレッドの切り替え」に従います。
- ViewModel には `@HiltViewModel` を付けます。
  - 画面のキーの値 (Uri など) を受け取る ViewModel は、assisted injection にします。コンストラクタに `@AssistedInject` を付け、キーの値の引数に `@Assisted` を付けます。ViewModel の中に `@AssistedFactory` の interface (`Factory`) を置き、`@HiltViewModel(assistedFactory = ThreadViewModel.Factory::class)` で結び付けます。
  - 画面 (`XxxScreen` を呼ぶ側。`AatvNavDisplay` のエントリー) は、`hiltViewModel<XxxViewModel, XxxViewModel.Factory>(creationCallback = { it.create(key.uri) })` で ViewModel を受け取ります。公式の Navigation 3 のレシピは、この形 (assisted injection と `creationCallback`) で、画面のキーを ViewModel に渡しています。
  - `hiltViewModel` は、`androidx.hilt.lifecycle.viewmodel.compose` のものを使います。`androidx.hilt.navigation.compose` のものは、非推奨なので使いません。
- 単体テストは Hilt を使いません。Fake を、ViewModel のコンストラクタに渡します。

### Hilt の注意

使っている版は、Hilt 2.60.1、KSP 2.3.14、androidx.hilt 1.4.0 です。AGP 9.5.1 と Kotlin 2.4.21 (どちらも `gradle/libs.versions.toml`)、JDK 25 (`versions.properties`) で、`./gradlew check assembleDebug` が通ることを確かめています。次は、版を上げたり、足したりするときの注意です。

- 生成コードの警告:
  - 確かめた事実: 上の版では、KSP が生成したものは `.java` のファイルだけでした。lint の `checkGeneratedSources` を有効にしたまま、警告は出ませんでした。
  - 警告を厳しく扱う設定 (`app/build.gradle.kts` の Kotlin のコンパイラの `allWarningsAsErrors` と `extraWarnings`、lint の `checkGeneratedSources` と `checkTestSources`) は、版を上げたあとに、生成コードの警告でビルドが落ちることがあります。Java のコンパイラの警告は、エラーにしていません (`JavaCompile` の `-Werror` がありません)。
  - KSP 自身の `allWarningsAsErrors` (KSP の既定は false) を、`app/build.gradle.kts` の `ksp` で有効にしています。KSP の警告が出たことがないので、この設定で実際にエラーになるところは確かめていません。
  - 直せない警告があるときは、どれを外すか (生成コードを対象から外す、検査を無効にするなど) を、ユーザーに相談します。
- 生成クラスのカバレッジ: Hilt と Dagger が生成したクラスは、Kover の測る対象から外しています (`.claude/rules/tdd.md` の「運用」の節)。手で書いた Module (`di/`) は測る対象に残します。
  - Dagger のコンポーネント (`DaggerAatvApplication_HiltComponents_SingletonC`) は、今の Kover の除外のパターンのどれにも当たりません。それでも、今の Kover の報告 (`report.xml`) には出ていません (Hilt 2.60.1、Kover 0.9.11 で確かめました)。報告に出ない理由は、確かめていません。
  - Kover や AGP などの版を上げて報告に出てきたときは、生成クラスの除外として、`classes` のパターン (例: `*_HiltComponents_*`) を足します。足してよい条件は、`.claude/rules/tdd.md` の「運用」の節のとおりです。
- プロセッサのクラスパスの脆弱性対策: KSP のプロセッサのクラスパス (`ksp` で始まり `ProcessorClasspath` で終わる configuration) と、Hilt の Gradle プラグインが作る注釈プロセッサのクラスパス (`hiltAnnotationProcessor` で始まる configuration) にも、`androidLintTool` と同じ constraints を足しています (`app/build.gradle.kts`)。Hilt を上げたあとに Dependabot alerts が出たら、constraints を見直します。
- Dagger 2.60 以降では、Hilt の Gradle プラグインを使うとき、プラグインが管理するフラグを設定すると、エラーになります。
- `.github/dependabot.yml` の `hilt-ksp` グループに、Dagger と Hilt のライブラリ、Hilt の Gradle プラグイン、KSP のプラグインをまとめています。生成コードの警告で CI が落ちたときに、ほかの更新と切り分けやすくするためです。
- 渡し忘れ: ディスパッチャー (`@Dispatcher(AatvDispatchers.IO)` など) の渡し忘れは、Dagger のコンパイル時の検査で見つかります。ほかの束縛の渡し忘れは確かめていません。使い捨てのコピーで `DispatchersModule` の `@Provides` を外すと、`./gradlew assembleDebug` が `hiltJavaCompileDebug` で `[Dagger/MissingBinding]` のエラー (`cannot be provided without an @Provides-annotated method`) になりました (Hilt 2.60.1)。
- WorkManager を使うときは、Worker に依存を注入するための追加の設定が要ります。公式の Hilt と Jetpack のページ (出典の節) では、`androidx.hilt:hilt-work` と、追加の注釈プロセッサ (`androidx.hilt:hilt-compiler`) を足し、Worker に `@HiltWorker` を付け、`Application` で WorkManager の設定に `HiltWorkerFactory` を渡し、マニフェストから WorkManager の既定の初期化を外すとしています。この `androidx.hilt:hilt-compiler` は、`com.google.dagger:hilt-compiler` とは別のライブラリなので、カタログの別名も分けます。版と書き方は、足すときに公式のページで確かめます。

## パッケージ

機能ごとに分け、共通の `data/` を置きます (architecture-samples と同じ形)。`:app` の 1 モジュールのまま、パッケージで分けます。

- 機能のパッケージに、Screen、ViewModel、UiState と、画面のキー (`HomeKey` など。Navigation 3 の `NavKey`) をまとめます。
- Repository とデータ源は、`data/` に置きます。dat を取得したり保存したりするデータ源と Repository も `data/` に置き、`dat/` の解析を使います。
- `dat/` には、dat 形式の解析 (`Reader` から読んで `Post` などを作る、取得や保存をしないコード) だけを置きます。
- `dat/` と `ui/theme/` は、共通として今の場所に置きます。
- `MainActivity` と、`NavDisplay` を出す `AatvNavDisplay`、`AatvApplication` は、ルートのパッケージに置きます。
- Hilt の Module と、qualifier (`@Dispatcher`) は、`di/` に置きます。

ルートのパッケージは `io.github.tenth151151.aathreadviewer` で、ファイルは `app/src/main/kotlin/` の下に置きます。次は、今のファイルの構成です。

```text
io/github/tenth151151/aathreadviewer/
├── MainActivity.kt
├── AatvNavDisplay.kt       (NavDisplay と、画面のキーごとの画面の対応)
├── AatvApplication.kt      (@HiltAndroidApp を付けた Application)
├── di/                     (Hilt の Module と qualifier)
│   ├── DataModule.kt       (Repository とデータ源の @Binds、ContentResolver と BoardType の @Provides)
│   ├── DispatchersModule.kt (ディスパッチャーの @Provides)
│   └── Dispatcher.kt       (ディスパッチャーを選ぶ qualifier と、その種類の enum)
├── dat/                    (dat 形式の解析。Reader から読み、取得や保存はしない)
├── ui/theme/               (共通のテーマ)
├── data/
│   ├── ThreadRepository.kt (interface)
│   ├── DefaultThreadRepository.kt
│   ├── DatSource.kt        (interface。開いたストリームとファイル名をブロックに渡す)
│   ├── ContentResolverDatSource.kt
│   ├── LoadedThread.kt     (Repository が返す、読み込んだスレッド)
│   ├── LimitedInputStream.kt (読んだバイト数が上限を超えたら例外を投げるストリーム)
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
- Test doubles: https://developer.android.com/training/testing/fundamentals/test-doubles
- Lifecycle のリリースノート: https://developer.android.com/jetpack/androidx/releases/lifecycle
- androidx.hilt のリリースノート (`hiltViewModel` を新しい artifact とパッケージに移したこと): https://developer.android.com/jetpack/androidx/releases/hilt
- androidx.hilt.navigation.compose の `hiltViewModel` のリファレンス (非推奨の表示): https://developer.android.com/reference/kotlin/androidx/hilt/navigation/compose/hiltViewModel.composable
- Hilt と Jetpack (WorkManager への注入): https://developer.android.com/training/dependency-injection/hilt-jetpack
- Hilt の基本 (@Provides、qualifier、@ApplicationContext): https://developer.android.com/training/dependency-injection/hilt-android
- Flow のテスト (`stateIn` と `backgroundScope`): https://developer.android.com/kotlin/flow/test
- Coroutines のベストプラクティス (ディスパッチャーの注入と main-safe): https://developer.android.com/kotlin/coroutines/coroutines-best-practices
- コルーチンのテスト (`StandardTestDispatcher` と `advanceUntilIdle`): https://developer.android.com/kotlin/coroutines/test
- Navigation 3 のはじめかた: https://developer.android.com/guide/navigation/navigation-3/get-started
- Now in Android の DispatchersModule (@Provides で Dispatchers.IO を返すモジュール): https://github.com/android/nowinandroid/blob/main/core/common/src/main/kotlin/com/google/samples/apps/nowinandroid/core/common/network/di/DispatchersModule.kt
- Now in Android の NiaDispatchers (enum を引数に取る @Dispatcher の qualifier): https://github.com/android/nowinandroid/blob/main/core/common/src/main/kotlin/com/google/samples/apps/nowinandroid/core/common/network/NiaDispatchers.kt
- Navigation 3 のレシピ (画面ごとの ViewModel のスコープと、assisted injection と creationCallback で画面のキーを ViewModel に渡す書き方): https://developer.android.com/guide/navigation/navigation-3/recipes/passingarguments
- Dagger 2.60 のリリースノート: https://github.com/google/dagger/releases/tag/dagger-2.60

Hilt、KSP、androidx.hilt の版と、ビルドの結果は、このリポジトリで確かめたものです (「Hilt の注意」の節)。公式のページに書いてあることではありません。

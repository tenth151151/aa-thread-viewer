import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.artifacts.result.UnresolvedDependencyResult

// ビルド用の constraints (脆弱性の対策で、推移的な依存関係を修正版以上に引き上げるもの) が、効いているかを検査する。この検査の説明は、このファイルを正とする。
// タスクは checkBuildConstraints で、root と :app にそれぞれ登録し、check が頼る。root の build.gradle.kts と app/build.gradle.kts が apply(from = …) で読み込み、それぞれ自分のプロジェクトの configuration だけを解決する。
// - 調べるもの: ビルド用の configuration で、constraints の対象のライブラリが、catalog の版より古く解決されていないか。入っていないものと、catalog の版以上のものは、失敗にしない。constraints の印 (c) は問わない。
// - root は buildscript の classpath を調べる。:app は、解決できるすべての configuration から、アプリに入るものの configuration (名前が appConfigurationName に当たるもの) を除いたものを調べる。アプリに入るものには constraints を当てない。
// - 外す線: アプリ (APK やテストの端末) に入るものを外し、ビルドのときに動くもの (注釈プロセッサ、KSP、lint の実行用クラスパス、Kover、Kotlin のコンパイラなど) は検査に入れる。新しい configuration は、名前に関係なく検査に入る。
//   - 外す名前: *CompileClasspath、*RuntimeClasspath、*DependenciesMetadata (アプリやテストのクラスパス)、androidTestUtil (テストの端末に入る)、coreLibraryDesugaring (アプリに入る)、lintChecks と *LintChecksClasspath (アプリの依存を引き込むので、アプリに入るものと同じに扱う)。
//   - 判断に迷う名前は、中身にアプリの依存 (implementation で宣言したもの) が入るかで決める。入るなら外す。
// - 期待する版は、root の build.gradle.kts の buildscript が、root の extra の buildConstraintNotations ("group:name:version" の一覧) で渡す。root と :app は、この 1 つの一覧から constraints と検査の期待する版を作る。
//   一覧から外したライブラリは、検査からも外れる。一覧に残したまま当て先から漏れたものは、検査が見つける。一覧が空のときと、版が空のライブラリがあるときは、検査が効かなくなるので失敗にする。
// - 解決された版は ResolutionResult で読む。ResolutionResult は、解決できない依存関係があっても例外を投げないので、対象の configuration で解決できなかった依存関係は、どれも失敗にする (その下にある古い版を見落とすため)。
// - 版の比べ方は compareVersions のコメントを見る。
// - AGP が configuration を遅れて作るので、対象の configuration は、タスクを使うときに集める。

abstract class CheckBuildConstraints : DefaultTask() {
    // "group:name" から、catalog の版へ。
    @get:Input
    abstract val expectedVersions: MapProperty<String, String>

    // configuration の名前から、解決結果のルートへ。
    // 出力を持たないタスクなので、毎回実行される。ResolvedComponentResult は configuration cache に保存できる。
    @get:Internal
    abstract val resolvedRoots: MapProperty<String, ResolvedComponentResult>

    @TaskAction
    fun check() {
        val expected = expectedVersions.get()
        if (expected.isEmpty()) {
            throw GradleException("検査する constraints が 1 つもありません。")
        }
        val emptyVersions = expected.filterValues { it.isBlank() }.keys.sorted()
        if (emptyVersions.isNotEmpty()) {
            throw GradleException(
                "期待する版が空のライブラリがあります (catalog で prefer だけの版にしていないかを確かめてください)。\n" +
                    emptyVersions.joinToString("\n") { "  - $it" },
            )
        }
        val violations = mutableListOf<String>()
        resolvedRoots.get().toSortedMap().forEach { (configurationName, root) ->
            val visited = mutableSetOf<ResolvedComponentResult>()
            val pending = ArrayDeque(listOf(root))
            while (pending.isNotEmpty()) {
                val component = pending.removeLast()
                if (!visited.add(component)) continue
                val module = component.moduleVersion
                val catalogVersion = module?.let { expected["${it.group}:${it.name}"] }
                if (module != null && catalogVersion != null && compareVersions(module.version, catalogVersion) < 0) {
                    violations +=
                        "configuration $configurationName: ${module.group}:${module.name} が ${module.version} に解決されました " +
                        "(catalog の版は $catalogVersion)。"
                }
                component.dependencies.forEach { edge ->
                    when (edge) {
                        is ResolvedDependencyResult -> {
                            pending.addLast(edge.selected)
                        }

                        // ResolutionResult は、解決できなかった依存関係があっても例外を投げずに、このように返す。その下に古い版があっても見えないので、どれも失敗にする。
                        is UnresolvedDependencyResult -> {
                            violations +=
                                "configuration $configurationName: ${edge.requested.displayName} を解決できませんでした (${edge.failure.message})。"
                        }
                    }
                }
            }
        }
        if (violations.isNotEmpty()) {
            throw GradleException(
                "ビルド用の constraints が当たっていない configuration があります。\n" +
                    violations.sorted().joinToString("\n") { "  - $it" },
            )
        }
    }

    // 版を、区切り (. - _) で分ける。先頭から続く数字の部分を、数の大小で比べる (足りない部分は 0 として扱う)。
    // 数字の部分が同じときは、プレリリースの印 (preReleaseMarker に当たる部分。大文字と小文字は区別しない) がある版を、ない版より古いとみなす。
    // jre や android のようなそれ以外の印は、同じとみなす。プレリリースどうしの細かい順番 (rc1 と rc2 など) は問わず、同じとみなす。
    private fun compareVersions(
        a: String,
        b: String,
    ): Int {
        val preReleaseMarker =
            Regex("(alpha|beta|rc|m|milestone|snapshot|cr|ea|dev|preview)\\d*", RegexOption.IGNORE_CASE)
        val partsA = a.split('.', '-', '_')
        val partsB = b.split('.', '-', '_')
        val numbersA = partsA.takeWhile { it.toLongOrNull() != null }.map { it.toLong() }
        val numbersB = partsB.takeWhile { it.toLongOrNull() != null }.map { it.toLong() }
        for (i in 0 until maxOf(numbersA.size, numbersB.size)) {
            val result = (numbersA.getOrNull(i) ?: 0L).compareTo(numbersB.getOrNull(i) ?: 0L)
            if (result != 0) return result
        }
        val preReleaseA = partsA.drop(numbersA.size).any { preReleaseMarker.matches(it) }
        val preReleaseB = partsB.drop(numbersB.size).any { preReleaseMarker.matches(it) }
        return preReleaseB.compareTo(preReleaseA)
    }
}

// 検査の対象から外す、アプリに入るものの configuration。アプリに入るものには constraints を当てない。線の引き方は、先頭のコメントを見る。
// 名前で外し、それ以外は新しい configuration も含めて対象にする。
val appConfigurationName =
    Regex(".*(CompileClasspath|RuntimeClasspath|LintChecksClasspath|DependenciesMetadata)|androidTestUtil|coreLibraryDesugaring|lintChecks")

// 検査の対象の configuration を、遅延して集める。AGP が configuration を遅れて作るため、タスクを使う時点で集める。
fun targetConfigurations(): List<Configuration> {
    val container =
        if (project == rootProject) project.buildscript.configurations else project.configurations
    // 一覧を回している間に、遅延して作る configuration が実体になって一覧が変わる (ConcurrentModificationException になる) ので、名前の写しを取って 1 つずつ実体にする。
    // 実体にしたことで増えた名前があれば、増えなくなるまで繰り返す。
    var names: List<String>
    do {
        names = container.names.toList()
        names.forEach { container.getByName(it) }
    } while (container.names.size != names.size)
    return names
        .map { container.getByName(it) }
        .filter { it.isCanBeResolved && !(project != rootProject && appConfigurationName.matches(it.name)) }
}

val checkBuildConstraints =
    tasks.register<CheckBuildConstraints>("checkBuildConstraints") {
        group = "verification"
        description = "ビルド用の constraints の対象のライブラリが、catalog の版より古く解決されていないかを検査します。"
        // "group:name:version" の一覧。constraints を当てるときと同じ一覧を、root が extra で渡す。
        val notations = (rootProject.extra.properties["buildConstraintNotations"] as? List<*>)?.filterIsInstance<String>().orEmpty()
        notations.forEach {
            val (group, name, version) = it.split(':')
            expectedVersions.put("$group:$name", version)
        }
        targetConfigurations().forEach { configuration ->
            resolvedRoots.put(configuration.name, configuration.incoming.resolutionResult.rootComponent)
        }
    }

tasks.named("check") { dependsOn(checkBuildConstraints) }

import kotlinx.kover.gradle.plugin.dsl.CoverageUnit
import java.io.StringReader
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kover)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// JDK と Android SDK のバージョンは、リポジトリのルートの versions.properties が唯一の正である。
// providers.fileContents で読むので、ファイルの内容が configuration cache の入力として追跡される。
val versions =
    Properties().apply {
        val text = providers.fileContents(rootProject.layout.projectDirectory.file("versions.properties")).asText
        load(StringReader(text.get()))
    }

fun requiredVersion(key: String): String {
    // Properties は値の末尾の空白を残すので、trim してから使う。
    val value = versions.getProperty(key)?.trim()
    if (value.isNullOrEmpty()) {
        throw GradleException("versions.properties に $key がないか、値が空です。")
    }
    return value
}

val androidPlatform = requiredVersion("ANDROID_PLATFORM")
val androidPlatformMatch =
    Regex("""^android-(\d+)\.(\d+)$""").matchEntire(androidPlatform)
        ?: throw GradleException(
            "versions.properties の ANDROID_PLATFORM が android-<major>.<minor> の形式ではありません " +
                "(例: android-37.0)。現在の値: \"$androidPlatform\"",
        )
val androidPlatformMajor = androidPlatformMatch.groupValues[1].toInt()
val androidPlatformMinor = androidPlatformMatch.groupValues[2].toInt()

android {
    namespace = "io.github.tenth151151.aathreadviewer"
    compileSdk {
        version =
            release(androidPlatformMajor) {
                minorApiLevel = androidPlatformMinor
            }
    }
    buildToolsVersion = requiredVersion("ANDROID_BUILD_TOOLS")

    defaultConfig {
        applicationId = "io.github.tenth151151.aathreadviewer"
        minSdk = 29
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        compose = true
    }

    lint {
        // 警告もエラーとして扱う (エラーがあればビルドは既定で失敗する)。
        warningsAsErrors = true

        // テストのコードと、生成されたコード (Hilt や KSP) も lint の対象にする。
        checkTestSources = true
        checkGeneratedSources = true

        // 依存や AGP のバージョンが古いことの検査は、Google Maven への問い合わせなど外部の状況で結果が変わり、
        // コードを変えていなくても CI が日によって失敗する原因になる。
        // バージョンの更新は Dependabot (`.github/dependabot.yml`) で扱うため、無効にする。
        disable += listOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion")
    }
}

kotlin {
    compilerOptions {
        // Kotlin のコンパイラの警告 (非推奨の API など) をエラーにする。lint の warningsAsErrors は Kotlin のコンパイラの警告を対象にしないため。
        // アプリの Kotlin のソース (メインとテスト) で使っている API が非推奨になると、CI が落ちることがある。Dependabot が AGP や Kotlin を上げた PR でも起こり得る。これは意図した動きで、警告に対処してから取り込む。
        // ビルドスクリプト (*.gradle.kts) の DSL の非推奨と、Gradle の非推奨の警告は対象外である。後者は gradle.properties のコメントを参照。
        allWarningsAsErrors = true

        // 使っていない変数、いらない可視性の修飾子などの追加の検査も有効にする。
        // Kotlin の新しい版で検査が増えることがあり、コードを変えていなくても、Kotlin を上げる PR で CI が落ちることがある。これも意図した動きである。
        extraWarnings = true
    }
}

ksp {
    // Kotlin と lint にそろえて、KSP の警告もエラーにする。KSP の既定は false である。
    // KSP を上げた PR で、プロセッサの警告により CI が落ちることがある。これは意図した動きで、警告に対処してから取り込む。
    allWarningsAsErrors = true
}

// カバレッジの測定 (Kover)。測るのは JVM の単体テストだけで、計装テスト (androidTest) は使わない。
// そのため、画面 (Composable) と Activity は測れない。測る対象から外し、ViewModel や Repository などのロジックを残す。
kover {
    reports {
        filters {
            excludes {
                annotatedBy("androidx.compose.runtime.Composable")
                // Activity と Application は、計装テストを使わないので測れない。
                // Kover はライブラリのクラスの継承をたどれないので、ほかのライブラリのクラス (例: AppCompatActivity) を親にするときは、そのクラスもここに足す。
                inheritedFrom("android.app.Activity", "androidx.activity.ComponentActivity", "android.app.Application")
                // Compose コンパイラが、ラムダなどを置くために作るクラス。
                classes("*ComposableSingletons*")
                // Hilt と Dagger が生成したクラス。単体テストは Hilt を使わないので測れない。
                // 手で書いた Module (di/ の DataModule など) は、外さずに測る。
                classes(
                    "*_Factory",
                    "*_Factory_Impl",
                    "*Module_*Factory*",
                    "*_HiltModules*",
                    "*_MembersInjector",
                    "*.Hilt_*",
                    "*_GeneratedInjector",
                    "hilt_aggregated_deps.*",
                    "dagger.hilt.internal.*",
                )
            }
        }

        verify {
            rule("行のカバレッジ") {
                // TDD ならほとんどの行がテストを通るが、少し下がっても失敗しないよう、余裕を持たせて 80% にする。
                minBound(80, CoverageUnit.LINE)
            }
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.core)
    implementation(libs.commons.text)
    implementation(libs.hilt.android)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.kotlinx.serialization.json)
}

// ビルド用の constraints を当てるライブラリ ("group:name:version")。一覧は root の build.gradle.kts の buildscript にだけあり、extra から読む (root の configuration は解決しない)。
// 当て先の configuration すべてと、検査 (checkBuildConstraints) の期待する版が、この一覧から決まる。版が空のものは、検査が失敗にする。
val buildConstraintNotations =
    (rootProject.extra["buildConstraintNotations"] as? List<*>)?.filterIsInstance<String>().orEmpty()

// lint の実行用クラスパス (androidLintTool) に入る推移的な依存関係のうち、脆弱性の修正版がまだ取り込まれていないものを引き上げる。
// 考え方は root の build.gradle.kts の buildscript と同じで、strictly や force は使わない。当てる一覧は、上の buildConstraintNotations である。
// 今の androidLintTool に出てこないもの (jose4j、jdom2、freemarker) も、AGP を上げて入ってきたときに備えて書いておく。グラフにないものへの constraint は何もしない。
// AGP を上げたときは、./gradlew :app:dependencies --configuration androidLintTool で、ここにない依存関係が入っていないか、要らなくなったものがないかを確かめ直す。
dependencies {
    constraints {
        buildConstraintNotations.filterNot { it.endsWith(":") }.forEach { add("androidLintTool", it) }
    }
}

// KSP のプロセッサのクラスパス (kspDebugKotlinProcessorClasspath など) と、Hilt の注釈プロセッサのクラスパス (hiltAnnotationProcessorDebug など) と、名前が kspClasspath で終わる configuration にも、上と同じ constraints を足す。
// ksp で始まり ProcessorClasspath で終わる名前と、hiltAnnotationProcessor で始まる名前と、kspClasspath で終わる名前の configuration を、作られたときに名前で当てる。
// AGP の variant やテストの configuration が増えても、書き足さずに当たるようにするため。
// kspClasspath で終わる名前に今当たるのは、AGP の内部の KSP 用クラスパス (_agp_internal_*_kspClasspath) である。広い条件にしているのは、AGP の版で名前が変わったときに漏れを減らすためだが、名前が変わっても当たり続けるとは限らない。KSP を動かすときに使われるかは確かめていない。
// 当て漏れは checkBuildConstraints (gradle/build-constraints-check.gradle.kts) が見つける。
// Hilt のコンパイラ (KSP のプロセッサ) と Hilt の Gradle プラグインの注釈プロセッサが、guava などの推移的な依存関係を引き込む。
configurations
    .matching {
        (it.name.startsWith("ksp") && it.name.endsWith("ProcessorClasspath")) ||
            it.name.startsWith("hiltAnnotationProcessor") ||
            it.name.endsWith("kspClasspath")
    }.configureEach {
        val configurationName = name
        dependencies {
            constraints {
                buildConstraintNotations.filterNot { it.endsWith(":") }.forEach { add(configurationName, it) }
            }
        }
    }

// ビルド用の constraints の検査。
apply(from = rootProject.file("gradle/build-constraints-check.gradle.kts"))

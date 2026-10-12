import java.io.StringReader
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
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

        // テストのコードと、生成されたコード (将来の Hilt や KSP) も lint の対象にする。
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

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
}

// lint の実行用クラスパス (androidLintTool) に入る推移的な依存関係のうち、脆弱性の修正版がまだ取り込まれていないものを引き上げる。
// 考え方は root の build.gradle.kts の buildscript と同じで、strictly や force は使わない。
// 今の androidLintTool に出てこないもの (jose4j、jdom2) も、AGP を上げて入ってきたときに備えて書いておく。グラフにないものへの constraint は何もしない。
// AGP を上げたときは、./gradlew :app:dependencies --configuration androidLintTool で、ここにない依存関係が入っていないか、要らなくなったものがないかを確かめ直す。
dependencies {
    constraints {
        add("androidLintTool", libs.bouncycastle.bcprov)
        add("androidLintTool", libs.bouncycastle.bcpkix)
        add("androidLintTool", libs.bouncycastle.bcutil)
        add("androidLintTool", libs.jose4j)
        add("androidLintTool", libs.jdom2)
        add("androidLintTool", libs.commons.lang3)
        add("androidLintTool", libs.httpclient)
    }
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "io.github.tenth151151.aathreadviewer"
    compileSdk {
        version =
            release(37) {
                minorApiLevel = 0
            }
    }
    buildToolsVersion = "36.0.0"

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
        // 警告もエラーとして扱い、エラーがあればビルドを失敗させる。
        warningsAsErrors = true
        abortOnError = true

        // 依存や AGP のバージョンが古いことの検査は、Google Maven への問い合わせなど外部の状況で結果が変わり、
        // コードを変えていなくても CI が日によって失敗する原因になる。
        // バージョンの更新は #9 で Dependabot を導入して扱うため、無効にする。
        disable += listOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion")
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

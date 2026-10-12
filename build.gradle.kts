// AGP のビルド用クラスパスに入る推移的な依存関係のうち、脆弱性の修正版がまだ取り込まれていないものを引き上げる。
// 版は gradle/libs.versions.toml の [versions] にあり、[libraries] から version.ref で参照する。
// Dependabot が版を上げ続けるので、AGP が追いついても、この constraints は効き続ける。
// AGP を上げたときは、./gradlew buildEnvironment で、AGP 自身が要求する版が修正版に追いついたかを確かめる。
// 追いついて要らなくなっていれば、外す。
// strictly や force は使わず、最低でもこの版にする指定にとどめる。
buildscript {
    dependencies {
        constraints {
            classpath(libs.bouncycastle.bcprov)
            classpath(libs.bouncycastle.bcpkix)
            classpath(libs.bouncycastle.bcutil)
            classpath(libs.jose4j)
            classpath(libs.jdom2)
            classpath(libs.commons.lang3)
            classpath(libs.httpclient)
        }
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.spotless)
}

val spotlessTargetExclude = arrayOf("**/build/**", "**/.gradle/**", "**/.kotlin/**")

spotless {
    kotlin {
        target("**/*.kt")
        targetExclude(*spotlessTargetExclude)
        ktlint(libs.versions.ktlint.get())
    }
    kotlinGradle {
        target("**/*.gradle.kts")
        targetExclude(*spotlessTargetExclude)
        ktlint(libs.versions.ktlint.get())
    }
}

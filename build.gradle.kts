// AGP や Kover など、プラグインのビルド用クラスパスに入る推移的な依存関係のうち、脆弱性の修正版がまだ取り込まれていないものを引き上げる。
// 版は gradle/libs.versions.toml の [versions] にあり、[libraries] から version.ref で参照する。
// Dependabot が版を上げ続けるので、プラグインが追いついても、この constraints は効き続ける。
// AGP や Kover などのプラグインを上げたときは、./gradlew buildEnvironment で、プラグイン自身が要求する版が修正版に追いついたかを確かめる。
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
            classpath(libs.freemarker)
        }
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kover) apply false
    alias(libs.plugins.spotless)
}

// .claude/worktrees/ には git worktree が入る。作業中の .kt をメインの spotless が拾って check が落ちるのを防ぐため除外する。
val spotlessTargetExclude = arrayOf("**/build/**", "**/.gradle/**", "**/.kotlin/**", "**/.claude/worktrees/**")

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

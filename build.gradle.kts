// AGP や Kover など、プラグインのビルド用クラスパスに入る推移的な依存関係のうち、脆弱性の修正版がまだ取り込まれていないものを引き上げる。
// 版は gradle/libs.versions.toml の [versions] にあり、[libraries] から version.ref で参照する。
// Dependabot が版を上げ続けるので、プラグインが追いついても、この constraints は効き続ける。
// AGP や Kover などのプラグインを上げたときは、./gradlew buildEnvironment で、プラグイン自身が要求する版が修正版に追いついたかを確かめる。
// 追いついて要らなくなっていれば、外す。
// strictly や force は使わず、最低でもこの版にする指定にとどめる。
buildscript {
    // constraints を当てるライブラリの、唯一の一覧 ("group:name:version")。root の classpath の当て先と、:app の当て先と、両方の検査の期待する版が、この一覧から決まる。
    // buildscript のブロックの中の val は、ブロックの外から見えないので、extra で渡す。:app は rootProject の extra から読む。
    // 版が空のものは、検査 (checkBuildConstraints) が失敗にするので、ここでは空のまま渡す。
    val buildConstraintNotations =
        listOf(
            libs.bouncycastle.bcprov,
            libs.bouncycastle.bcpkix,
            libs.bouncycastle.bcutil,
            libs.jose4j,
            libs.jdom2,
            libs.commons.lang3,
            libs.httpclient,
            libs.freemarker,
            libs.guava,
        ).map {
            val dependency = it.get()
            "${dependency.module}:${dependency.versionConstraint.requiredVersion}"
        }
    extra["buildConstraintNotations"] = buildConstraintNotations

    dependencies {
        constraints {
            buildConstraintNotations.filterNot { it.endsWith(":") }.forEach { classpath(it) }
        }
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kover) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.spotless)
}

// 対象は広く取り、外す場所を targetExclude に足す。対象を絞ると、新しいソースの場所を書き足し忘れたときに、検査から黙って漏れるため。
// .claude/worktrees/ には git worktree が入る。作業中の .kt をメインの spotless が拾って check が落ちるのを防ぐため除外する。
// .tmp/ には確認の証拠などの一時ファイルが入る。使い捨ての .kt が check を落とさないよう除外する。
val spotlessTargetExclude = arrayOf("**/build/**", "**/.gradle/**", "**/.kotlin/**", "**/.claude/worktrees/**", "**/.tmp/**")

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

// ビルド用の constraints の検査 (checkBuildConstraints)。上の buildscript の constraints から期待する版を読み、buildscript の classpath を調べる。
apply(from = "gradle/build-constraints-check.gradle.kts")

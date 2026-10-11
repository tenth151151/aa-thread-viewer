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

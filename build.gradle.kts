plugins {
    alias(libs.plugins.spotless)
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.graalvm.native) apply false
    alias(libs.plugins.shadow) apply false
}

group = "io.askimo"
version = "0.2.0"

extensions.extraProperties["spotlessSetLicenseHeaderYearsFromGitHistory"] = true

spotless {
    ratchetFrom("origin/main")
    format("json") {
        target("**/*.json")
        targetExclude("**/build/**")
        prettier().config(mapOf("parser" to "json"))
        trimTrailingWhitespace()
        endWithNewline()
    }
    format("html") {
        target("**/*.html")
        targetExclude("**/build/**")
        prettier().config(mapOf("parser" to "html"))
        trimTrailingWhitespace()
        endWithNewline()
    }
    format("javascript") {
        target("**/*.js")
        targetExclude("**/build/**")
        prettier().config(mapOf("parser" to "babel"))
        trimTrailingWhitespace()
        endWithNewline()
    }
    format("css") {
        target("**/*.css")
        targetExclude("**/build/**")
        prettier().config(mapOf("parser" to "css"))
        trimTrailingWhitespace()
        endWithNewline()
    }
}

subprojects {
    apply(plugin = "com.diffplug.spotless")

    plugins.withId("org.jetbrains.kotlin.jvm") {
        configure<com.diffplug.gradle.spotless.SpotlessExtension> {
            kotlin {
                ktlint().editorConfigOverride(
                    mapOf(
                        "ktlint_standard_no-unused-imports" to "enabled",
                    ),
                )
                licenseHeaderFile(
                    rootProject.file("HEADER-SRC"),
                    "(package|import|@file:)",
                )
                trimTrailingWhitespace()
                leadingTabsToSpaces(4)
                endWithNewline()
            }
            kotlinGradle {
                ktlint()
                trimTrailingWhitespace()
                leadingTabsToSpaces(4)
                endWithNewline()
            }
        }
    }
}

repositories {
    mavenCentral()
}

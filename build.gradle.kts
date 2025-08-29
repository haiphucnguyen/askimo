plugins {
    id("com.diffplug.spotless") version "7.2.1"
    kotlin("jvm") version "2.2.10" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.2.10" apply false
    id("org.graalvm.buildtools.native") version libs.versions.graalvm.plugin apply false
}

allprojects {
    group = "io.askimo"
    version = "0.1.2"

    repositories {
        mavenCentral()
    }

    // Apply Kotlin plugins to all projects
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "org.jetbrains.kotlin.plugin.serialization")

    // Apply Spotless to all projects
    apply(plugin = "com.diffplug.spotless")

    extensions.extraProperties["spotlessSetLicenseHeaderYearsFromGitHistory"] = true

    configure<com.diffplug.gradle.spotless.SpotlessExtension> {
        ratchetFrom("origin/main")
        kotlin {
            ktlint()
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

    plugins.withId("org.jetbrains.kotlin.jvm") {
        extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension> {
            compilerOptions {
                javaParameters.set(true)
            }
        }
    }
}

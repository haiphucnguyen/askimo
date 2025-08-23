/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) ${YEAR} Hai Nguyen
 */
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

plugins {
    application
    id("org.jetbrains.kotlin.jvm") version "2.2.10"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.2.10"
    id("org.graalvm.buildtools.native") version "0.11.0"
    id("com.diffplug.spotless") version "7.2.1"
    id("com.gradleup.shadow") version "9.0.1"
}

group = "io.askimo"
version = "0.0.1"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jline:jline:3.30.5")
    implementation("org.jline:jline-terminal-jansi:3.30.5")
    implementation("dev.langchain4j:langchain4j:1.3.0")
    implementation("dev.langchain4j:langchain4j-open-ai:1.3.0")
    implementation("dev.langchain4j:langchain4j-ollama:1.3.0")
    implementation("org.commonmark:commonmark:0.25.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json-jvm:1.9.0")
    implementation("io.ktor:ktor-server-cio:3.2.3")
    implementation("io.ktor:ktor-server-core:3.2.3")
    implementation("io.ktor:ktor-server-content-negotiation:3.2.3")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.2.3")
    implementation(kotlin("stdlib"))
    runtimeOnly("org.slf4j:slf4j-nop:2.0.17")
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

tasks.test {
    useJUnitPlatform()
}

application {
    mainClass.set("io.askimo.cli.ChatCliKt")
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = application.mainClass.get()
    }
}

sourceSets {
    main {
        kotlin.srcDirs("src/main/kotlin")
        resources.srcDirs("src/main/resources")
    }
    test {
        kotlin.srcDirs("src/test/kotlin")
        resources.srcDirs("src/test/resources")
    }
}

val author = "Hai Nguyen"
val licenseId = "MIT"
val homepage = "https://github.com/haiphucnguyen/askimo"

val aboutDir = layout.buildDirectory.dir("generated-resources/about")
val aboutFile = aboutDir.map { it.file("about.properties") }

val generateAbout =
    tasks.register("generateAbout") {
        outputs.file(aboutFile)

        doLast {
            val buildDate =
                DateTimeFormatter.ISO_LOCAL_DATE
                    .withZone(ZoneOffset.UTC)
                    .format(Instant.now())

            val text =
                """
                name=Askimo
                version=${project.version}
                author=$author
                buildDate=$buildDate
                license=$licenseId
                homepage=$homepage
                buildJdk=${System.getProperty("java.version") ?: "unknown"}
                """.trimIndent()

            val f = aboutFile.get().asFile
            f.parentFile.mkdirs()
            f.writeText(text)
        }
    }

tasks.named<ProcessResources>("processResources") {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    dependsOn(generateAbout)
    from(aboutDir)
}

graalvmNative {
    binaries {
        named("main") {
            javaLauncher.set(
                javaToolchains.launcherFor {
                    languageVersion.set(JavaLanguageVersion.of(21))
                },
            )
            buildArgs.addAll(
                listOf(
                    "--enable-url-protocols=https",
                    "--report-unsupported-elements-at-runtime",
                    "--initialize-at-build-time=kotlin.DeprecationLevel,kotlin.jvm.internal.Intrinsics,kotlin.enums.EnumEntries",
                ),
            )
            resources.autodetect()
        }
    }
}

extensions.extraProperties["spotlessSetLicenseHeaderYearsFromGitHistory"] = true

spotless {
    ratchetFrom("origin/main")
    kotlin {
        ktlint()
        licenseHeaderFile(
            rootProject.file("LICENSE-HEADER-SRC"),
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

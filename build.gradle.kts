import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

plugins {
    application
    id("org.jetbrains.kotlin.jvm") version "2.2.20"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.2.20"
    id("org.graalvm.buildtools.native") version "0.11.1"
    id("com.diffplug.spotless") version "7.2.1"
    id("com.gradleup.shadow") version "9.0.1"
}

group = "io.askimo"
version = "0.1.2"

dependencies {
    implementation(libs.jline)
    implementation(libs.jline.terminal.jansi)
    implementation(libs.langchain4j)
    implementation(libs.langchain4j.open.ai)
    implementation(libs.langchain4j.ollama)
    implementation(libs.langchain4j.google.ai.gemini)
    implementation("dev.langchain4j:langchain4j-anthropic:1.7.1")
    implementation(libs.commonmark)
    implementation(libs.kotlinx.serialization.json.jvm)
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.postgresql)
    implementation(libs.langchain4j.pgvector)
    implementation(libs.testcontainers.postgresql)
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.19.2")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.19.2")
    implementation(kotlin("stdlib"))
    runtimeOnly(libs.slf4j.nop)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    compilerOptions {
        javaParameters.set(true)
    }
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
val licenseId = "Apache 2"
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

tasks.named<JavaExec>("run") {
    standardInput = System.`in`
}

graalvmNative {
    binaries {
        agent {
            enabled.set(true)
            // valid values: "standard", "conditional", "direct"
            defaultMode.set("standard")

            modes {
                direct {
                    // where to dump the configs
                    options.add("config-output-dir=${project.layout.buildDirectory.get().asFile}/native/agent")
                    // optionally:
                    // options.add("experimental-configuration-with-origins")
                    metadataCopy {
                        // run the `run` task under the agent and copy results here:
                        outputDirectories.add("src/main/resources/META-INF/native-image/")
                        mergeWithExisting = true
                    }
                }
                // Optional: automatically copy collected metadata into your sources
            }

            // (Optional) instrument more tasks than just `run`/`test`
            // tasksToInstrumentPredicate = { true }
        }
        named("main") {
            javaLauncher.set(
                javaToolchains.launcherFor {
                    languageVersion.set(JavaLanguageVersion.of(21))
                },
            )
            buildArgs.addAll(
                listOf(
                    "-J-Xmx8g",
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

repositories {
    mavenCentral()
}

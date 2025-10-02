import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

plugins {
    application
    id("org.graalvm.buildtools.native")
    id("com.gradleup.shadow") version "9.0.1"
    kotlin("jvm")
    id("org.jetbrains.kotlin.plugin.serialization")
}

dependencies {
    implementation(libs.jline)
    implementation(libs.jline.terminal.jansi)
    implementation(libs.langchain4j)
    implementation(libs.langchain4j.open.ai)
    implementation(libs.langchain4j.ollama)
    implementation(libs.langchain4j.google.ai.gemini)
    implementation(libs.commonmark)
    implementation(libs.kotlinx.serialization.json.jvm)
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.postgresql)
    implementation(libs.langchain4j.pgvector)
    implementation(libs.testcontainers.postgresql)
    implementation(kotlin("stdlib"))
    runtimeOnly(libs.slf4j.nop)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
}

tasks.test {
    useJUnitPlatform()
}

application {
    mainClass.set("io.askimo.cli.ChatCliKt")
    applicationName = "askimo"
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = application.mainClass.get()
    }
}

val author: String by project
val licenseId: String by project
val homepage: String by project

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

val agentEnabled = providers.gradleProperty("agent").map { it.toBoolean() }.orElse(false)

graalvmNative {
    binaries {
        agent {
//            enabled.set(agentEnabled)
            // valid values: "standard", "conditional", "direct"
            defaultMode.set("standard")

            modes {
                direct {
                    options.add("config-output-dir=${project.layout.buildDirectory.get().asFile}/native/agent")
                }
            }

            // Optional: automatically copy collected metadata into your sources
            metadataCopy {
                // run the `run` task under the agent and copy results here:
                inputTaskNames.add("run")
                outputDirectories.add("src/main/resources/META-INF/native-image/")
                mergeWithExisting = true
            }
        }
        named("main") {
            imageName.set("askimo")
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

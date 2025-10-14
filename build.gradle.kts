import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

plugins {
    application
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.graalvm.native)
    alias(libs.plugins.spotless)
    alias(libs.plugins.shadow)
}

group = "io.askimo"
version = "0.1.2"

dependencies {
    compileOnly(libs.graalvm.nativeimage.svm)
    implementation(libs.jline)
    implementation(libs.jline.terminal.jansi)
    implementation(libs.langchain4j)
    implementation(libs.langchain4j.open.ai)
    implementation(libs.langchain4j.ollama)
    implementation(libs.langchain4j.google.ai.gemini)
    implementation(libs.langchain4j.anthropic)
    implementation(libs.commonmark)
    implementation(libs.kotlinx.serialization.json.jvm)
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.postgresql)
    implementation(libs.langchain4j.pgvector)
    implementation(libs.testcontainers.postgresql)
    implementation(libs.jackson.module.kotlin)
    implementation(libs.jackson.dataformat.yaml)
    implementation(kotlin("stdlib"))
    runtimeOnly(libs.slf4j.nop)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.testcontainers.ollama)
    testImplementation(libs.testcontainers.junit.jupiter)
}

tasks.test {
    useJUnitPlatform {
        excludeTags("native")
    }

    jvmArgs =
        listOf(
            "-XX:+EnableDynamicAgentLoading",
        )
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

val traceAgent = (findProperty("traceAgent") as String?) == "true"

tasks.register<Sync>("syncGraalMetadata") {
    from("$buildDir/graal-agent")
    include("**/*-config.json")
    exclude("**/agent-extracted-predefined-classes/**", "**/predefined-classes-*.json")
    into("src/main/resources/META-INF/native-image")
}

tasks.test {
    useJUnitPlatform()

    if (traceAgent) {
        val mergeDir = "$projectDir/src/main/resources/META-INF/native-image"
        val accessFilter = "$projectDir/src/main/resources/graal-access-filter.json"
        val callerFilter = "$projectDir/src/main/resources/graal-caller-filter.json"
        val outDir = "$buildDir/graal-agent"

        jvmArgs(
            "-agentlib:native-image-agent=" +
                "config-output-dir=$outDir," +
                "access-filter-file=$accessFilter," +
                "caller-filter-file=$callerFilter",
        )

        doFirst {
            println("🔎 Graal tracing agent ON")
            println("   Merge -> $mergeDir")
            println("   Filters: access=$accessFilter ; caller=$callerFilter")
        }
        finalizedBy("syncGraalMetadata")
    }
}

graalvmNative {
    metadataRepository {
        enabled.set(true)
    }

    binaries {
        named("main") {
            javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) })

            buildArgs.addAll(
                listOf(
                    "-J-Xmx8g",
                    "--enable-url-protocols=https",
                    "--report-unsupported-elements-at-runtime",
                    "--features=io.askimo.core.graal.AskimoFeature",
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

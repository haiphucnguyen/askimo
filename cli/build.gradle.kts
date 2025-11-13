import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Properties

plugins {
    application
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.graalvm.native)
    alias(libs.plugins.shadow)
}

group = "io.askimo"
version = "0.2.0"

repositories {
    mavenCentral()
}

// Function to load environment variables from .env file
fun loadEnvFile(): Map<String, String> {
    val envFile = File(rootProject.projectDir, ".env")
    val envVars = mutableMapOf<String, String>()

    if (envFile.exists()) {
        val props = Properties()
        envFile.inputStream().use { props.load(it) }
        props.forEach { key, value ->
            envVars[key.toString()] = value.toString()
        }
        println("📁 Loaded ${envVars.size} environment variables from .env file")
    } else {
        println("ℹ️  No .env file found, skipping environment variable loading")
    }

    return envVars
}

dependencies {
    compileOnly(libs.graalvm.nativeimage.svm)
    implementation(libs.jline)
    implementation(libs.jline.terminal.jansi)
    implementation(libs.commonmark)
    implementation(kotlin("stdlib"))
    implementation(project(":shared"))
    runtimeOnly(libs.slf4j.nop)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.testcontainers.ollama)
    testImplementation(libs.testcontainers.junit.jupiter)
}

val traceAgent = (findProperty("traceAgent") as String?) == "true"

tasks.test {
    useJUnitPlatform {
        excludeTags("native")
    }

    // Load environment variables from .env file if it exists
    val envVars = loadEnvFile()
    envVars.forEach { (key, value) ->
        environment(key, value)
    }

    jvmArgs = listOf("-XX:+EnableDynamicAgentLoading")

    if (traceAgent) {
        val mergeDir = "$projectDir/src/main/resources/META-INF/native-image"
        val accessFilter = "$projectDir/src/main/resources/graal-access-filter.json"
        val callerFilter = "$projectDir/src/main/resources/graal-caller-filter.json"
        val outDir =
            layout.buildDirectory
                .dir("graal-agent")
                .get()
                .asFile

        jvmArgs(
            "-XX:+EnableDynamicAgentLoading",
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

tasks.register<Sync>("syncGraalMetadata") {
    from(layout.buildDirectory.dir("graal-agent"))
    include("**/*-config.json")
    exclude("**/agent-extracted-predefined-classes/**", "**/predefined-classes-*.json")
    into("src/main/resources/META-INF/native-image")
}

graalvmNative {
    metadataRepository {
        enabled.set(true)
    }

    binaries {
        named("main") {
            imageName.set("askimo")
            javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) })

            buildArgs.addAll(
                listOf(
                    "-J-Xmx8g",
                    "--enable-url-protocols=https",
                    "--report-unsupported-elements-at-runtime",
                    "--features=io.askimo.core.graal.AskimoFeature",
                    "--initialize-at-build-time=kotlin.DeprecationLevel,kotlin.jvm.internal.Intrinsics,kotlin.enums.EnumEntries",
                    "--initialize-at-run-time=kotlinx.coroutines,kotlin.coroutines,io.askimo.core.project.ProjectFileWatcher",
                    "--allow-incomplete-classpath",
                    "-H:+ReportExceptionStackTraces",
                ),
            )
            resources.autodetect()
            configurationFileDirectories.from(file("src/main/resources/META-INF/native-image"))
        }
    }
}

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Properties

plugins {
    application
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.graalvm.native)
}

group = rootProject.group
version = rootProject.version

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
    implementation(libs.bundles.jline)
    implementation(libs.bundles.commonmark)
    implementation(kotlin("stdlib"))
    implementation(project(":shared"))
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.bundles.testcontainers)
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

    jvmArgs =
        listOf(
            "-XX:+EnableDynamicAgentLoading",
            "--add-modules",
            "jdk.incubator.vector",
        )

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

tasks.processTestResources {
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
}

val author = property("author") as String
val licenseId = property("licenseId") as String
val homepage = property("homepage") as String

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

            val graalvmMemory = project.findProperty("graalvm.native-image.memory") as? String ?: "8g"

            buildArgs.addAll(
                listOf(
                    "-J-Xmx$graalvmMemory",
                    "--enable-url-protocols=https",
                    "--report-unsupported-elements-at-runtime",
                    "--features=io.askimo.cli.graal.AskimoFeature",
                    "-Dorg.apache.lucene.store.MMapDirectory.enableMemorySegments=false",
                    "--initialize-at-build-time=kotlin.DeprecationLevel,kotlin.jvm.internal.Intrinsics,kotlin.enums.EnumEntries, com.github.benmanes.caffeine",
                    "--initialize-at-run-time=kotlinx.coroutines,kotlin.coroutines,io.askimo.core.project.ProjectFileWatcher",
                    "-H:IncludeResources=logback.xml|logback-.*\\.xml",
                    "--allow-incomplete-classpath",
                    "-H:+ReportExceptionStackTraces",
                ),
            )
            resources.autodetect()
            configurationFileDirectories.from(file("src/main/resources/META-INF/native-image"))
        }
    }
}

// Remove macOS quarantine before compilation to prevent GraalVM component blocking
tasks.named("nativeCompile") {
    doFirst {
        if (System.getProperty("os.name").contains("Mac", ignoreCase = true)) {
            val nativeDir = file("build/native")
            if (nativeDir.exists()) {
                try {
                    exec {
                        commandLine("xattr", "-dr", "com.apple.quarantine", nativeDir.absolutePath)
                        isIgnoreExitValue = true
                    }
                    println("🔓 Pre-cleared quarantine from build/native directory")
                } catch (e: Exception) {
                    // Ignore errors
                }
            }
        }
    }

    doLast {
        if (System.getProperty("os.name").contains("Mac", ignoreCase = true)) {
            val nativeDir = file("build/native")
            val binary = file("build/native/nativeCompile/askimo")

            try {
                // Remove quarantine from entire native build directory
                exec {
                    commandLine("xattr", "-dr", "com.apple.quarantine", nativeDir.absolutePath)
                    isIgnoreExitValue = true
                }
                println("✅ Removed quarantine from all native build artifacts")

                // Also specifically remove from the binary
                if (binary.exists()) {
                    exec {
                        commandLine("xattr", "-d", "com.apple.quarantine", binary.absolutePath)
                        isIgnoreExitValue = true
                    }
                    println("✅ Removed quarantine from binary: ${binary.name}")
                }
            } catch (e: Exception) {
                println("⚠️  Could not remove quarantine attribute (this is normal if it doesn't exist)")
            }
        }
    }
}

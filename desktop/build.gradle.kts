import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import java.time.Instant
import java.time.Year
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}

// Load environment variables from .env file if it exists
val envFile = rootProject.file(".env")
val envVars = mutableMapOf<String, String>()

if (envFile.exists()) {
    envFile.readLines().forEach { line ->
        val trimmed = line.trim()
        if (trimmed.isNotEmpty() && !trimmed.startsWith("#") && "=" in trimmed) {
            val key = trimmed.substringBefore("=").trim().removePrefix("export ")
            val value = trimmed.substringAfter("=").trim().removeSurrounding("\"")
            envVars[key] = value
            System.setProperty(key, value)
        }
    }
    println("✅ Loaded ${envVars.size} variables from .env file")
}

// Helper function to get value from either environment variable or .env file
fun getEnvOrProperty(key: String): String? = System.getenv(key) ?: envVars[key] ?: System.getProperty(key)
group = rootProject.group
version = rootProject.version

repositories {
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    google()
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation(project(":shared"))
    implementation(libs.bundles.commonmark)
    implementation(libs.commonmark.ext.autolink)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.jlatexmath)

    testImplementation(kotlin("test"))
    testImplementation(libs.bundles.koin.test)
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

// Generate about.properties with version info
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
                name=Askimo Desktop
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
    dependsOn(generateAbout)
    from(aboutDir)
    filteringCharset = "UTF-8"
}

compose.desktop {
    application {
        mainClass = "io.askimo.desktop.MainKt"

        // Enable Vector API for better JVector performance
        jvmArgs("--add-modules", "jdk.incubator.vector")

        nativeDistributions {
            targetFormats(
                TargetFormat.Dmg,
                TargetFormat.Msi,
                TargetFormat.Deb,
            )
            packageName = "Askimo"
            packageVersion = project.version.toString()
            description = "AI-powered user assistant with local RAG and semantic search capabilities"
            copyright = "© ${Year.now()} $author. All rights reserved."
            vendor = "Askimo"

            // Automatically include all Java modules to support dependencies
            // This ensures modules like java.sql, java.naming, etc. are available
            includeAllModules = true

            macOS {
                bundleID = "io.askimo.desktop"
                iconFile.set(project.file("src/main/resources/images/askimo.icns"))

                // Code signing configuration
                // Reads from environment variables or .env file
                val macosIdentity = getEnvOrProperty("MACOS_IDENTITY")

                // Only configure signing if credentials are provided
                if (!macosIdentity.isNullOrBlank()) {
                    signing {
                        sign.set(true)
                        identity.set(macosIdentity)
                    }
                }
            }
            windows {
                iconFile.set(project.file("src/main/resources/images/askimo.ico"))
                menuGroup = "Askimo"
                perUserInstall = true
            }
            linux {
                iconFile.set(project.file("src/main/resources/images/askimo_512.png"))
            }
        }
    }
}

// Fix for "Archive contains more than 65535 entries" error
// Enable ZIP64 format for Compose Desktop packaging (supports unlimited entries)
tasks.withType<Zip> {
    isZip64 = true
}

tasks.test {
    useJUnitPlatform()

    // Enable Vector API for better JVector performance
    jvmArgs("--add-modules", "jdk.incubator.vector")
}

kotlin {
    jvmToolchain(21)
}

abstract class ExecSupport
    @Inject
    constructor(
        val execOps: ExecOperations,
    )
val execSupport = objects.newInstance(ExecSupport::class)

// Task to notarize both app bundle and DMG
tasks.register("packageNotarizedDmg") {
    group = "distribution"
    description = "Build app, notarize+staple app, create DMG from stapled app, notarize+staple DMG"

    // Build the app bundle first. If you don't have a dedicated task, keep packageDmg,
    // but we will RECREATE the DMG ourselves from the stapled app.
    dependsOn("packageDmg")

    doLast {
        val os = System.getProperty("os.name").lowercase()
        if (!os.contains("mac")) return@doLast

        val appleId = getEnvOrProperty("APPLE_ID") ?: error("Missing APPLE_ID")
        val applePassword = getEnvOrProperty("APPLE_PASSWORD") ?: error("Missing APPLE_PASSWORD")
        val appleTeamId = getEnvOrProperty("APPLE_TEAM_ID") ?: error("Missing APPLE_TEAM_ID")

        // ---- Locate APP (produced by compose) ----
        val appDir = file("build/compose/binaries/main/app")
        val appFile =
            appDir.listFiles()?.firstOrNull { it.name.endsWith(".app") }
                ?: error("No .app found in ${appDir.absolutePath}")

        println("🔐 Step 1: Notarize APP: ${appFile.name}")

        // Apple accepts .app directly too, but ZIP is fine.
        val zipFile = file("build/compose/binaries/main/${appFile.nameWithoutExtension}.zip")
        if (zipFile.exists()) zipFile.delete()

        execSupport.execOps.exec {
            commandLine("ditto", "-c", "-k", "--keepParent", appFile.absolutePath, zipFile.absolutePath)
        }

        val appNotarize =
            execSupport.execOps.exec {
                commandLine(
                    "xcrun",
                    "notarytool",
                    "submit",
                    zipFile.absolutePath,
                    "--apple-id",
                    appleId,
                    "--team-id",
                    appleTeamId,
                    "--password",
                    applePassword,
                    "--wait",
                )
                isIgnoreExitValue = true
                standardOutput = System.out
                errorOutput = System.err
            }
        zipFile.delete()

        if (appNotarize.exitValue != 0) error("❌ App notarization failed")

        // Staple APP (retry once if CDN delay)
        fun stapleApp(): Int =
            execSupport.execOps
                .exec {
                    commandLine("xcrun", "stapler", "staple", appFile.absolutePath)
                    isIgnoreExitValue = true
                    standardOutput = System.out
                    errorOutput = System.err
                }.exitValue

        println("📎 Stapling APP ticket...")
        var stapleExit = stapleApp()
        if (stapleExit != 0) {
            println("⚠️  APP stapling failed (CDN delay). Retrying once...")
            stapleExit = stapleApp()
        }
        if (stapleExit != 0) {
            // For a release, I'd rather fail than ship something that won't work offline.
            error("❌ APP stapling still failed. Try again in ~10 minutes.")
        }

        // Verify APP is now notarized
        println("🔍 Verifying APP with spctl...")
        val appSpctl =
            execSupport.execOps.exec {
                commandLine("spctl", "-a", "-vv", appFile.absolutePath)
                isIgnoreExitValue = true
                standardOutput = System.out
                errorOutput = System.err
            }
        if (appSpctl.exitValue != 0) error("❌ APP is not accepted by Gatekeeper")

        // ---- Create a NEW DMG from the stapled APP ----
        println("📀 Step 2: Create DMG from stapled APP")

        val outDir = file("build/compose/notarized").apply { mkdirs() }
        val dmgOut = outDir.resolve("Askimo-${project.version}.dmg")
        if (dmgOut.exists()) dmgOut.delete()

        execSupport.execOps.exec {
            commandLine(
                "hdiutil",
                "create",
                "-volname",
                "Askimo",
                "-srcfolder",
                appFile.absolutePath,
                "-ov",
                "-format",
                "UDZO",
                dmgOut.absolutePath,
            )
            isIgnoreExitValue = false
            standardOutput = System.out
            errorOutput = System.err
        }

        println("🔐 Step 3: Notarize DMG: ${dmgOut.name}")
        val dmgNotarize =
            execSupport.execOps.exec {
                commandLine(
                    "xcrun",
                    "notarytool",
                    "submit",
                    dmgOut.absolutePath,
                    "--apple-id",
                    appleId,
                    "--team-id",
                    appleTeamId,
                    "--password",
                    applePassword,
                    "--wait",
                )
                isIgnoreExitValue = true
                standardOutput = System.out
                errorOutput = System.err
            }
        if (dmgNotarize.exitValue != 0) error("❌ DMG notarization failed")

        println("📎 Stapling DMG ticket...")
        val dmgStaple =
            execSupport.execOps.exec {
                commandLine("xcrun", "stapler", "staple", dmgOut.absolutePath)
                isIgnoreExitValue = true
                standardOutput = System.out
                errorOutput = System.err
            }
        if (dmgStaple.exitValue != 0) error("❌ DMG stapling failed. Try again in ~10 minutes.")

        println("✅ Done. Verify:")
        println("  spctl -a -t open -vv \"${dmgOut.absolutePath}\"")
        println("  hdiutil attach \"${dmgOut.absolutePath}\"")
        println("  spctl -a -vv \"/Volumes/Askimo/Askimo.app\"")
    }
}

// Task to detect unused localization keys
tasks.register("detectUnusedLocalizations") {
    group = "verification"
    description = "Detect unused localization keys in properties files"

    doLast {
        val i18nDir = file("src/main/resources/i18n")
        val desktopSrcDir = file("src/main/kotlin")
        val sharedSrcDir = file("../shared/src/main/kotlin")
        val reportFile = file("${layout.buildDirectory.get()}/reports/unused-localizations.txt")

        // Read all keys from messages.properties
        val basePropertiesFile = file("$i18nDir/messages.properties")
        if (!basePropertiesFile.exists()) {
            println("❌ Base properties file not found: ${basePropertiesFile.path}")
            return@doLast
        }

        val allKeys = mutableMapOf<String, String>() // key -> value

        basePropertiesFile.forEachLine { line ->
            val trimmed = line.trim()
            if (trimmed.isNotEmpty() && !trimmed.startsWith("#") && "=" in trimmed) {
                val key = trimmed.substringBefore("=").trim()
                val value = trimmed.substringAfter("=").trim()
                allKeys[key] = value
            }
        }

        println("📋 Found ${allKeys.size} localization keys in messages.properties")

        // Scan all Kotlin files for key usage in both desktop and shared modules
        val usedKeys = mutableSetOf<String>()
        val keyUsageMap = mutableMapOf<String, MutableList<String>>() // key -> list of files

        fun scanDirectory(
            srcDir: File,
            moduleName: String,
        ) {
            if (!srcDir.exists()) {
                println("⚠️  Directory not found: ${srcDir.path}")
                return
            }

            println("🔍 Scanning $moduleName module: ${srcDir.path}")
            var fileCount = 0

            srcDir
                .walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .forEach { file ->
                    fileCount++
                    val content = file.readText()
                    val relativePath = "$moduleName/${file.relativeTo(srcDir).path}"

                    // Pattern 1: stringResource("key")
                    Regex("""stringResource\s*\(\s*"([^"]+)"""").findAll(content).forEach { match ->
                        val key = match.groupValues[1]
                        usedKeys.add(key)
                        keyUsageMap.getOrPut(key) { mutableListOf() }.add(relativePath)
                    }

                    // Pattern 2: LocalizationManager.getString("key")
                    Regex("""LocalizationManager\.getString\s*\(\s*"([^"]+)"""").findAll(content).forEach { match ->
                        val key = match.groupValues[1]
                        usedKeys.add(key)
                        keyUsageMap.getOrPut(key) { mutableListOf() }.add(relativePath)
                    }
                }

            println("   Scanned $fileCount Kotlin files")
        }

        // Scan both modules
        scanDirectory(desktopSrcDir, "desktop")
        scanDirectory(sharedSrcDir, "shared")

        println("✅ Found ${usedKeys.size} used keys across both modules")

        // Find unused keys
        val unusedKeys = allKeys.keys - usedKeys

        // Generate report
        reportFile.parentFile.mkdirs()
        reportFile.writeText(
            buildString {
                appendLine("=".repeat(80))
                appendLine("UNUSED LOCALIZATION KEYS REPORT")
                appendLine("=".repeat(80))
                appendLine("Generated: ${Instant.now()}")
                appendLine("Total keys: ${allKeys.size}")
                appendLine("Used keys: ${usedKeys.size}")
                appendLine("Unused keys: ${unusedKeys.size}")
                appendLine("=".repeat(80))
                appendLine()

                if (unusedKeys.isNotEmpty()) {
                    appendLine("UNUSED KEYS:")
                    appendLine("-".repeat(80))
                    unusedKeys.sorted().forEach { key ->
                        appendLine("Key: $key")
                        appendLine("Value: ${allKeys[key]}")
                        appendLine()
                    }
                } else {
                    appendLine("✅ All localization keys are being used!")
                }

                appendLine()
                appendLine("=".repeat(80))
                appendLine("KEY USAGE DETAILS:")
                appendLine("-".repeat(80))
                usedKeys.sorted().forEach { key ->
                    appendLine("Key: $key")
                    val files = keyUsageMap[key] ?: emptyList()
                    appendLine("Used in ${files.size} file(s):")
                    files.forEach { file ->
                        appendLine("  - $file")
                    }
                    appendLine()
                }
            },
        )

        println("\n📊 Report generated: ${reportFile.absolutePath}")

        if (unusedKeys.isEmpty()) {
            println("🎉 All localization keys are being used!")
        } else {
            println("⚠️  Found ${unusedKeys.size} unused localization keys\n")
            println("Unused keys:")
            unusedKeys.sorted().take(10).forEach { key ->
                println("  - $key = ${allKeys[key]}")
            }
            if (unusedKeys.size > 10) {
                println("  ... and ${unusedKeys.size - 10} more (see report)")
            }
        }
    }
}

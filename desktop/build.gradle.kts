import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import java.net.URI
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
    implementation(libs.commonmark)
    implementation(libs.commonmark.ext.gfm.tables)
    implementation(libs.commonmark.ext.autolink)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.jlatexmath)

    testImplementation(kotlin("test"))
    testImplementation(libs.koin.test)
    testImplementation(libs.koin.test.junit5)
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

        // Configure JVM args for SQLite temp directory
        val sqliteTmpDir =
            layout.buildDirectory
                .dir("sqlite-tmp")
                .get()
                .asFile
        val javaTmpDir =
            layout.buildDirectory
                .dir("tmp")
                .get()
                .asFile

        jvmArgs +=
            listOf(
                "-Dorg.sqlite.tmpdir=${sqliteTmpDir.absolutePath}",
                "-Djava.io.tmpdir=${javaTmpDir.absolutePath}",
            )

        nativeDistributions {
            targetFormats(
                TargetFormat.Dmg,
                TargetFormat.Msi,
                TargetFormat.Deb,
            )
            packageName = "Askimo"
            packageVersion = project.version.toString()
            description = "Askimo Desktop Application"
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
                val appleId = getEnvOrProperty("APPLE_ID")
                val applePassword = getEnvOrProperty("APPLE_PASSWORD")
                val appleTeamId = getEnvOrProperty("APPLE_TEAM_ID")

                // Only configure signing if credentials are provided
                if (!macosIdentity.isNullOrBlank()) {
                    signing {
                        sign.set(true)
                        identity.set(macosIdentity)
                    }
                }

                // Only configure notarization if all credentials are provided
                if (!appleId.isNullOrBlank() && !applePassword.isNullOrBlank() && !appleTeamId.isNullOrBlank()) {
                    notarization {
                        appleID.set(appleId)
                        password.set(applePassword)
                        teamID.set(appleTeamId)
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

    // Configure SQLite temp directory for tests
    val sqliteTmpDir =
        layout.buildDirectory
            .dir("sqlite-tmp")
            .get()
            .asFile
    val javaTmpDir =
        layout.buildDirectory
            .dir("tmp")
            .get()
            .asFile

    doFirst {
        sqliteTmpDir.mkdirs()
        javaTmpDir.mkdirs()
    }

    systemProperty("org.sqlite.tmpdir", sqliteTmpDir.absolutePath)
    systemProperty("java.io.tmpdir", javaTmpDir.absolutePath)
}

kotlin {
    jvmToolchain(21)
}

// Task to manually notarize and staple the macOS DMG
// Note: Renamed to avoid conflicts with Compose Desktop plugin's built-in notarization
tasks.register("notarizeAndStapleDmg") {
    group = "distribution"
    description = "Manually notarize and staple the macOS DMG with Apple (waits for completion)"

    // This task should run after packageDmg
    mustRunAfter("packageDmg")

    doLast {
        val os = System.getProperty("os.name").lowercase()
        if (!os.contains("mac")) {
            println("⏭️  Skipping notarization (not macOS)")
            return@doLast
        }

        // Get credentials from environment
        val appleId = getEnvOrProperty("APPLE_ID")
        val applePassword = getEnvOrProperty("APPLE_PASSWORD")
        val appleTeamId = getEnvOrProperty("APPLE_TEAM_ID")

        if (appleId.isNullOrBlank() || applePassword.isNullOrBlank() || appleTeamId.isNullOrBlank()) {
            println("⚠️  Skipping notarization - credentials not configured")
            println("   Set APPLE_ID, APPLE_PASSWORD, and APPLE_TEAM_ID in .env to enable notarization")
            return@doLast
        }

        // Find the DMG file
        val dmgDir = file("build/compose/binaries/main/dmg")
        if (!dmgDir.exists()) {
            println("❌ DMG directory not found: ${dmgDir.absolutePath}")
            return@doLast
        }

        val dmgFiles = dmgDir.listFiles()?.filter { it.extension == "dmg" }
        if (dmgFiles.isNullOrEmpty()) {
            println("❌ No DMG file found in ${dmgDir.absolutePath}")
            return@doLast
        }

        val dmgFile = dmgFiles.first()
        println("📦 Found DMG: ${dmgFile.name}")
        println("🔐 Starting notarization process...")
        println("")

        // Submit for notarization (without --wait to get immediate feedback)
        println("📤 Uploading to Apple...")
        val submitResult =
            exec {
                commandLine(
                    "xcrun",
                    "notarytool",
                    "submit",
                    dmgFile.absolutePath,
                    "--apple-id",
                    appleId,
                    "--team-id",
                    appleTeamId,
                    "--password",
                    applePassword,
                )
                isIgnoreExitValue = true
                standardOutput = System.out
                errorOutput = System.err
            }

        if (submitResult.exitValue != 0) {
            println("❌ Failed to submit for notarization")
            println("   Check your APPLE_ID, APPLE_PASSWORD, and APPLE_TEAM_ID credentials")
            throw GradleException("Notarization submission failed")
        }

        // Extract submission ID from output (we'll need to capture it)
        println("")
        println("⏳ Waiting for Apple to process the submission...")
        println("   This typically takes 5-60 minutes")
        println("   You can check status later with: ./tools/macos/check-notarization.sh")
        println("")

        // Wait for notarization to complete
        val waitResult =
            exec {
                commandLine(
                    "xcrun",
                    "notarytool",
                    "submit",
                    dmgFile.absolutePath,
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

        if (waitResult.exitValue != 0) {
            println("❌ Notarization failed or was rejected by Apple")
            println("")
            println("To debug:")
            println("  1. Check recent submissions: ./tools/macos/check-notarization.sh")
            println("  2. Get detailed log with the submission ID")
            throw GradleException("Notarization failed")
        }

        println("")
        println("✅ Notarization successful!")
        println("")

        // Staple the notarization ticket
        println("📎 Stapling notarization ticket to DMG...")
        val stapleResult =
            exec {
                commandLine("xcrun", "stapler", "staple", dmgFile.absolutePath)
                isIgnoreExitValue = true
                standardOutput = System.out
                errorOutput = System.err
            }

        if (stapleResult.exitValue == 0) {
            println("✅ Notarization ticket stapled successfully")
        } else {
            println("⚠️  Failed to staple ticket (DMG is still notarized)")
        }

        println("")

        // Verify the stapling
        println("🔍 Verifying notarization...")
        exec {
            commandLine("xcrun", "stapler", "validate", dmgFile.absolutePath)
            isIgnoreExitValue = true
            standardOutput = System.out
            errorOutput = System.err
        }

        println("")
        println("========================================")
        println("✅ Notarization Complete!")
        println("========================================")
        println("")
        println("Your fully signed and notarized DMG:")
        println("  ${dmgFile.absolutePath}")
        println("")
        println("Users will NOT see any security warnings when opening this app!")
    }
}

// Task to build, sign, and notarize in one command
tasks.register("packageNotarizedDmg") {
    group = "distribution"
    description = "Build, sign, and notarize the macOS DMG (complete release build with manual wait)"

    dependsOn("packageDmg")
    finalizedBy("notarizeAndStapleDmg")

    doLast {
        println("========================================")
        println("📦 Complete Release Package")
        println("========================================")
        println("")
        println("This will:")
        println("  1. Build the application")
        println("  2. Create and sign the DMG")
        println("  3. Submit for notarization (5-60 min wait)")
        println("  4. Staple the notarization ticket")
        println("  5. Verify everything worked")
        println("")
        println("Please be patient - notarization can take up to an hour.")
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

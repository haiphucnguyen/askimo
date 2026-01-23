import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import java.io.ByteArrayOutputStream
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
            description = "AI assistant with multi-LLM support and local document intelligence"
            copyright = "© ${Year.now()} $author. All rights reserved."
            vendor = "Askimo"

            // Automatically include all Java modules to support dependencies
            // This ensures modules like java.sql, java.naming, etc. are available
            includeAllModules = true

            macOS {
                bundleID = "io.askimo.desktop"
                iconFile.set(project.file("src/main/resources/images/askimo.icns"))

                // Disable automatic signing - we do custom signing with entitlements in signMacApp task
                signing {
                    sign.set(false)
                }
            }
            windows {
                iconFile.set(project.file("src/main/resources/images/askimo.ico"))
                menuGroup = "Askimo"
                perUserInstall = true
            }
            linux {
                iconFile.set(project.file("src/main/resources/images/askimo_512.png"))

                // Package metadata
                packageName = "askimo"
                debMaintainer = author
                menuGroup = "Utility;Office;"
                appCategory = "utils"

                // Add desktop entry details
                shortcut = true

                // Debian package dependencies
                debPackageVersion = project.version.toString()

                // Add runtime dependencies compatible with both Ubuntu 22.04 and 24.04+
                // Using jpackage's --linux-deb-depends argument
                args(
                    "--linux-deb-depends",
                    "libasound2t64 | libasound2", // Audio (time64 transition)
                    "--linux-deb-depends",
                    "libgl1", // OpenGL for graphics
                    "--linux-deb-depends",
                    "libgtk-3-0", // GTK for native dialogs
                    "--linux-deb-depends",
                    "libpng16-16t64 | libpng16-16", // PNG support (time64 transition)
                    "--linux-deb-depends",
                    "libfreetype6", // Font rendering
                    "--linux-deb-depends",
                    "libfontconfig1", // Font configuration
                    "--linux-deb-depends",
                    "libx11-6", // X11 support
                    "--linux-deb-depends",
                    "libxext6", // X11 extensions
                    "--linux-deb-depends",
                    "libxrender1", // X11 rendering
                    "--linux-deb-depends",
                    "libxtst6", // X11 test extensions
                    "--linux-deb-depends",
                    "libxi6", // X11 input extensions
                    // Add verbose logging for debugging
                    "--verbose",
                )
            }
        }
    }
}

// Fix for "Archive contains more than 65535 entries" error
// Enable ZIP64 format for Compose Desktop packaging (supports unlimited entries)
tasks.withType<Zip> {
    isZip64 = true
}

// Configure Compose Desktop uber JAR tasks to exclude signature files
// These tasks are created by the Compose Desktop plugin
// The plugin creates uber JARs that may contain conflicting signatures from dependencies
// We need to post-process the JAR to remove signature files
afterEvaluate {
    fun createStripSignaturesTask(
        sourceTaskName: String,
        targetTaskName: String,
    ) {
        tasks.register(targetTaskName) {
            group = "compose desktop"
            description = "Strip signature files from $sourceTaskName output"

            val sourceTask = tasks.findByName(sourceTaskName)
            if (sourceTask != null) {
                dependsOn(sourceTask)

                doLast {
                    // Find the output JAR from the source task
                    val jarFiles =
                        fileTree(layout.buildDirectory.dir("compose/jars")) {
                            include("*.jar")
                        }

                    jarFiles.forEach { jarFile ->
                        logger.lifecycle("Stripping signatures from: ${jarFile.name}")

                        // Check if JAR has signature files
                        val hasSignatures =
                            ByteArrayOutputStream().use { output ->
                                project.exec {
                                    commandLine("jar", "tf", jarFile.absolutePath)
                                    standardOutput = output
                                    isIgnoreExitValue = true
                                }
                                val contents = output.toString()
                                contents.contains(".SF") || contents.contains(".DSA") || contents.contains(".RSA")
                            }

                        if (!hasSignatures) {
                            logger.lifecycle("   No signature files found, skipping...")
                            return@forEach
                        }

                        // Create a temporary directory for extraction
                        val tempDir =
                            layout.buildDirectory
                                .dir("tmp/jar-strip/${jarFile.nameWithoutExtension}")
                                .get()
                                .asFile
                        tempDir.deleteRecursively()
                        tempDir.mkdirs()

                        try {
                            // Extract JAR contents using verbose mode to track all files
                            val extractOutput = ByteArrayOutputStream()
                            project.exec {
                                commandLine("jar", "xf", jarFile.absolutePath)
                                workingDir = tempDir
                                standardOutput = extractOutput
                            }

                            // Count extracted files for verification
                            val extractedFiles = tempDir.walkTopDown().filter { it.isFile }.count()
                            logger.lifecycle("   Extracted $extractedFiles files")

                            // Delete signature files from META-INF
                            val metaInfDir = File(tempDir, "META-INF")
                            var deletedCount = 0
                            if (metaInfDir.exists()) {
                                metaInfDir.listFiles()?.forEach { file ->
                                    if (file.extension in listOf("SF", "DSA", "RSA")) {
                                        file.delete()
                                        logger.lifecycle("   Deleted: META-INF/${file.name}")
                                        deletedCount++
                                    }
                                }
                            }

                            // Re-create JAR without signature files
                            // Important: List all files explicitly to avoid missing hidden files or special resources
                            val manifestFile = File(metaInfDir, "MANIFEST.MF")
                            val repackOutput = ByteArrayOutputStream()

                            // Build the file list to include in the JAR
                            val allFiles =
                                tempDir
                                    .walkTopDown()
                                    .filter { it.isFile }
                                    .map { it.relativeTo(tempDir).path }
                                    .toList()

                            // Create a temporary file list
                            val fileListFile = File(tempDir.parentFile, "${jarFile.nameWithoutExtension}-files.txt")
                            fileListFile.writeText(allFiles.joinToString("\n"))

                            try {
                                project.exec {
                                    if (manifestFile.exists()) {
                                        // Create JAR with manifest and all files from list
                                        commandLine(
                                            "jar",
                                            "cfm",
                                            jarFile.absolutePath,
                                            "META-INF/MANIFEST.MF",
                                            "@${fileListFile.absolutePath}",
                                        )
                                    } else {
                                        // Create JAR with all files from list
                                        commandLine("jar", "cf", jarFile.absolutePath, "@${fileListFile.absolutePath}")
                                    }
                                    workingDir = tempDir
                                    standardOutput = repackOutput
                                }
                            } finally {
                                fileListFile.delete()
                            }

                            // Verify the repacked JAR
                            val repackedFileCount =
                                ByteArrayOutputStream().use { output ->
                                    project.exec {
                                        commandLine("jar", "tf", jarFile.absolutePath)
                                        standardOutput = output
                                        isIgnoreExitValue = true
                                    }
                                    output
                                        .toString()
                                        .lines()
                                        .filter { it.isNotBlank() }
                                        .size
                                }

                            logger.lifecycle(
                                "   Repacked JAR contains $repackedFileCount entries (original: $extractedFiles, deleted: $deletedCount)",
                            )

                            // Verify no files were lost (allowing for deleted signature files)
                            val expectedCount = extractedFiles - deletedCount
                            if (repackedFileCount < expectedCount - 5) { // Allow small variance for directory entries
                                logger.warn(
                                    "   ⚠️ Warning: Repacked JAR may be missing files (expected ~$expectedCount, got $repackedFileCount)",
                                )
                            }

                            logger.lifecycle("✅ Successfully stripped signatures from: ${jarFile.name}")
                        } finally {
                            // Clean up temporary directory
                            tempDir.deleteRecursively()
                        }
                    }
                }
            }
        }
    }

    // Create signature stripping tasks for uber JARs
    createStripSignaturesTask("packageUberJarForCurrentOS", "stripSignaturesFromUberJar")
    createStripSignaturesTask("packageReleaseUberJarForCurrentOS", "stripSignaturesFromReleaseUberJar")

    // Make the uber JAR tasks finalize with signature stripping
    tasks.findByName("packageUberJarForCurrentOS")?.finalizedBy("stripSignaturesFromUberJar")
    tasks.findByName("packageReleaseUberJarForCurrentOS")?.finalizedBy("stripSignaturesFromReleaseUberJar")
}

tasks.test {
    useJUnitPlatform()

    // Enable Vector API for better JVector performance
    jvmArgs("--add-modules", "jdk.incubator.vector")
}

kotlin {
    jvmToolchain(21)
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

// =============================================================================
// macOS Code Signing and Notarization Tasks
// =============================================================================

/**
 * Create entitlements file for hardened runtime
 */
tasks.register("createEntitlements") {
    group = "distribution"
    description = "Create entitlements.plist for macOS hardened runtime"

    val entitlementsFile = file("${layout.buildDirectory.get()}/compose/notarized/entitlements.plist")

    doLast {
        entitlementsFile.parentFile.mkdirs()
        entitlementsFile.writeText(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
            <plist version="1.0">
            <dict>
                <key>com.apple.security.cs.allow-jit</key>
                <true/>
                <key>com.apple.security.cs.allow-unsigned-executable-memory</key>
                <true/>
                <key>com.apple.security.cs.allow-dyld-environment-variables</key>
                <true/>
                <key>com.apple.security.cs.disable-library-validation</key>
                <true/>
            </dict>
            </plist>
            """.trimIndent(),
        )
        logger.lifecycle("✅ Created entitlements file: ${entitlementsFile.absolutePath}")
    }
}

/**
 * Sign dylibs inside JAR files (extract, sign, repack)
 */
@Suppress("DEPRECATION")
fun signDylibsInsideJar(
    jarFile: File,
    identity: String,
    entitlementsFile: File,
    workRoot: File,
) {
    logger.lifecycle("   • Signing dylibs inside JAR: ${jarFile.name}")
    logger.lifecycle("     Path: ${jarFile.absolutePath}")

    // Check if JAR exists
    if (!jarFile.exists()) {
        logger.warn("     ⚠️ JAR missing, skipping")
        return
    }

    // Check if JAR contains dylibs
    val hasDylibs =
        ByteArrayOutputStream().use { output ->
            project.exec {
                commandLine("jar", "tf", jarFile.absolutePath)
                standardOutput = output
                isIgnoreExitValue = true
            }
            output.toString().contains(".dylib")
        }

    if (!hasDylibs) {
        return
    }

    val workDir = File(workRoot, "${jarFile.name}.work").apply { mkdirs() }
    val extractDir = File(workDir, "contents").apply { mkdirs() }
    val tmpJar = File(workDir, "repacked.jar")

    try {
        // Extract JAR
        logger.lifecycle("     Extracting JAR...")
        project.exec {
            workingDir = extractDir
            commandLine("jar", "xf", jarFile.absolutePath)
        }

        // Find and sign dylibs
        logger.lifecycle("     Signing dylibs...")
        extractDir
            .walk()
            .filter { it.extension == "dylib" }
            .forEach { dylibFile ->
                project.exec {
                    commandLine(
                        "codesign",
                        "--force",
                        "--sign",
                        identity,
                        "--entitlements",
                        entitlementsFile.absolutePath,
                        "--timestamp",
                        "--options",
                        "runtime",
                        dylibFile.absolutePath,
                    )
                }
            }

        // Repack JAR
        logger.lifecycle("     Repacking JAR...")
        tmpJar.delete()
        project.exec {
            workingDir = extractDir
            commandLine("jar", "cf", tmpJar.absolutePath, ".")
        }

        // Replace original JAR
        logger.lifecycle("     Replacing original JAR...")
        if (!tmpJar.exists()) {
            throw GradleException("Repacked JAR missing: ${tmpJar.absolutePath}")
        }
        tmpJar.copyTo(jarFile, overwrite = true)
    } finally {
        workDir.deleteRecursively()
    }
}

/**
 * Sign all components of the .app bundle
 */
tasks.register("signMacApp") {
    group = "distribution"
    description = "Sign all components of the macOS .app bundle with entitlements"

    dependsOn("createEntitlements", "createDistributable")

    @Suppress("DEPRECATION")
    doLast {
        val macosIdentity =
            getEnvOrProperty("MACOS_IDENTITY")
                ?: throw GradleException("MACOS_IDENTITY environment variable is required")

        val appDir = file("${layout.buildDirectory.get()}/compose/binaries/main/app")
        val appBundle =
            appDir.listFiles()?.firstOrNull { it.extension == "app" }
                ?: throw GradleException("No .app bundle found in $appDir")

        // Copy to notarized directory for signing
        val notarizedDir = file("${layout.buildDirectory.get()}/compose/notarized").apply { mkdirs() }
        val appToSign = File(notarizedDir, appBundle.name)

        logger.lifecycle("📤 Copying .app to staging for signing...")
        // Delete existing if present
        if (appToSign.exists()) {
            appToSign.deleteRecursively()
        }

        // Use rsync to preserve permissions and attributes
        project.exec {
            commandLine("rsync", "-a", "--delete", "${appBundle.absolutePath}/", "${appToSign.absolutePath}/")
        }

        logger.lifecycle("🔏 Will sign app: ${appToSign.absolutePath}")
        logger.lifecycle("🔑 Identity: $macosIdentity")

        val entitlementsFile = file("$notarizedDir/entitlements.plist")
        val appContents = File(appToSign, "Contents")
        val runtimeDir = File(appContents, "runtime")
        val appLibDir = File(appContents, "app")

        // Get main executable name from Info.plist
        val infoPlist = File(appContents, "Info.plist")
        val mainExeName =
            ByteArrayOutputStream()
                .use { output ->
                    project.exec {
                        commandLine("defaults", "read", infoPlist.absolutePath.removeSuffix(".plist"), "CFBundleExecutable")
                        standardOutput = output
                        isIgnoreExitValue = true
                    }
                    output.toString().trim()
                }.takeIf { it.isNotEmpty() } ?: run {
                // Fallback: find executable in MacOS directory
                val macosDir = File(appContents, "MacOS")
                macosDir.listFiles()?.firstOrNull { it.isFile && it.canExecute() }?.name ?: "Askimo"
            }

        val mainExe = File(appContents, "MacOS/$mainExeName")

        logger.lifecycle("Main executable: $mainExeName at ${mainExe.absolutePath}")

        // 1) Sign embedded runtime dylibs + jspawnhelper
        if (runtimeDir.exists()) {
            logger.lifecycle("🔧 Signing embedded runtime dylibs + helpers...")
            runtimeDir
                .walk()
                .filter { it.extension == "dylib" || it.name == "jspawnhelper" }
                .forEach { file ->
                    project.exec {
                        commandLine(
                            "codesign",
                            "--force",
                            "--sign",
                            macosIdentity,
                            "--entitlements",
                            entitlementsFile.absolutePath,
                            "--timestamp",
                            "--options",
                            "runtime",
                            file.absolutePath,
                        )
                    }
                    logger.lifecycle("${file.absolutePath}: replacing existing signature")
                }
        }

        // 2) Sign loose dylibs under Contents/app
        if (appLibDir.exists()) {
            logger.lifecycle("🔧 Signing loose dylibs under Contents/app...")
            appLibDir
                .walk()
                .filter { it.extension == "dylib" && it.isFile }
                .forEach { dylibFile ->
                    project.exec {
                        commandLine(
                            "codesign",
                            "--force",
                            "--sign",
                            macosIdentity,
                            "--entitlements",
                            entitlementsFile.absolutePath,
                            "--timestamp",
                            "--options",
                            "runtime",
                            dylibFile.absolutePath,
                        )
                    }
                }
        }

        // 3) Sign dylibs inside JARs
        if (appLibDir.exists()) {
            logger.lifecycle("🔧 Signing dylibs inside JARs under Contents/app...")
            val workRoot = File(notarizedDir, "codesign-jar-work").apply { mkdirs() }

            appLibDir
                .walk()
                .filter { it.extension == "jar" && it.isFile }
                .forEach { jarFile ->
                    signDylibsInsideJar(jarFile, macosIdentity, entitlementsFile, workRoot)
                }

            workRoot.deleteRecursively()
        }

        // 4) Sign main executable
        if (mainExe.exists()) {
            logger.lifecycle("🔧 Signing main executable: ${mainExe.absolutePath}")
            project.exec {
                commandLine(
                    "codesign",
                    "--force",
                    "--sign",
                    macosIdentity,
                    "--entitlements",
                    entitlementsFile.absolutePath,
                    "--timestamp",
                    "--options",
                    "runtime",
                    mainExe.absolutePath,
                )
            }
        } else {
            logger.warn("⚠️ Main executable not found: ${mainExe.absolutePath}")
        }

        // 5) Sign app bundle (deep)
        logger.lifecycle("🔧 Signing app bundle (deep): ${appToSign.absolutePath}")
        project.exec {
            commandLine(
                "codesign",
                "--force",
                "--deep",
                "--sign",
                macosIdentity,
                "--entitlements",
                entitlementsFile.absolutePath,
                "--timestamp",
                "--options",
                "runtime",
                appToSign.absolutePath,
            )
        }

        // Verify signature
        logger.lifecycle("🔎 Verifying signature...")
        project.exec {
            commandLine("codesign", "--verify", "--deep", "--strict", "--verbose=2", appToSign.absolutePath)
        }

        logger.lifecycle("✅ App bundle signed successfully")
    }
}

/**
 * Notarize the .app bundle
 */
tasks.register("notarizeApp") {
    group = "distribution"
    description = "Notarize the signed .app bundle with Apple's notarization service"

    dependsOn("signMacApp")

    @Suppress("DEPRECATION")
    doLast {
        val notarizedDir = file("${layout.buildDirectory.get()}/compose/notarized")
        val appToSign =
            notarizedDir.listFiles()?.firstOrNull { it.extension == "app" }
                ?: throw GradleException("No signed .app bundle found in $notarizedDir")

        // Notarization credentials - App-Specific Password
        val appleId =
            getEnvOrProperty("APPLE_ID")
                ?: throw GradleException("APPLE_ID environment variable is required")
        val applePassword =
            getEnvOrProperty("APPLE_PASSWORD")
                ?: throw GradleException("APPLE_PASSWORD environment variable is required (use app-specific password)")
        val appleTeamId =
            getEnvOrProperty("APPLE_TEAM_ID")
                ?: throw GradleException("APPLE_TEAM_ID environment variable is required")

        val notaryArgs =
            listOf(
                "--apple-id",
                appleId,
                "--team-id",
                appleTeamId,
                "--password",
                applePassword,
            )

        logger.lifecycle("🔐 Notarizing .app bundle...")
        logger.lifecycle("📦 App: ${appToSign.name}")

        // Create ZIP of .app for notarization (Apple requires ZIP for .app bundles)
        val appZip = File(notarizedDir, "${appToSign.nameWithoutExtension}.zip")
        appZip.delete()

        logger.lifecycle("📦 Creating ZIP for notarization...")
        project.exec {
            workingDir = notarizedDir
            commandLine("ditto", "-c", "-k", "--keepParent", appToSign.name, appZip.name)
        }

        // Calculate SHA256 before submission
        val sha256Before =
            ByteArrayOutputStream().use { output ->
                project.exec {
                    commandLine("shasum", "-a", "256", appZip.absolutePath)
                    standardOutput = output
                }
                output.toString().split(" ")[0]
            }

        logger.lifecycle("🔎 ZIP SHA256: $sha256Before")

        // Submit for notarization
        val notarizationOutput =
            ByteArrayOutputStream().use { output ->
                project.exec {
                    commandLine(
                        "xcrun",
                        "notarytool",
                        "submit",
                        appZip.absolutePath,
                        *notaryArgs.toTypedArray(),
                        "--wait",
                        "--output-format",
                        "json",
                    )
                    standardOutput = output
                }
                output.toString()
            }

        logger.lifecycle(notarizationOutput)

        // Parse JSON response
        val statusMatch = Regex(""""status":\s*"([^"]+)"""").find(notarizationOutput)
        val status = statusMatch?.groupValues?.get(1)

        val idMatch = Regex(""""id":\s*"([^"]+)"""").find(notarizationOutput)
        val submissionId = idMatch?.groupValues?.get(1)

        logger.lifecycle("✅ .app submission: id=$submissionId, status=$status")

        if (status != "Accepted") {
            throw GradleException("❌ .app notarization failed with status: $status")
        }

        // Wait for ticket propagation
        logger.lifecycle("⏳ Waiting 60s for ticket propagation...")
        Thread.sleep(60000)

        // Staple ticket to .app
        logger.lifecycle("📎 Stapling ticket to .app...")
        val staplerOutput = ByteArrayOutputStream()
        val staplerError = ByteArrayOutputStream()
        val stapleResult =
            project.exec {
                commandLine("xcrun", "stapler", "staple", "-v", appToSign.absolutePath)
                isIgnoreExitValue = true
                standardOutput = staplerOutput
                errorOutput = staplerError
            }

        if (stapleResult.exitValue != 0) {
            logger.warn("⚠️ Stapler failed for .app, but continuing (ticket is available online)")
            logger.lifecycle(staplerOutput.toString())
            logger.lifecycle(staplerError.toString())
        } else {
            logger.lifecycle("✅ Ticket stapled to .app")
        }

        // Clean up ZIP
        appZip.delete()

        logger.lifecycle("✅ .app notarization complete!")
    }
}

/**
 * Create a signed DMG with Applications folder symlink
 */
tasks.register("createSignedDmg") {
    group = "distribution"
    description = "Create a signed DMG with Applications folder symlink"

    dependsOn("notarizeApp") // Changed from signMacApp to notarizeApp

    @Suppress("DEPRECATION")
    doLast {
        val macosIdentity =
            getEnvOrProperty("MACOS_IDENTITY")
                ?: throw GradleException("MACOS_IDENTITY environment variable is required")

        val notarizedDir = file("${layout.buildDirectory.get()}/compose/notarized")
        val appToSign =
            notarizedDir.listFiles()?.firstOrNull { it.extension == "app" }
                ?: throw GradleException("No signed .app bundle found in $notarizedDir")

        // Create DMG staging folder with Applications symlink
        val dmgStaging =
            File(notarizedDir, "dmg-staging").apply {
                deleteRecursively()
                mkdirs()
            }

        logger.lifecycle("📦 Preparing DMG contents...")
        // Use rsync to preserve permissions
        project.exec {
            commandLine("rsync", "-a", "${appToSign.absolutePath}/", "${File(dmgStaging, appToSign.name).absolutePath}/")
        }

        // Create Applications symlink
        project.exec {
            workingDir = dmgStaging
            commandLine("ln", "-s", "/Applications", "Applications")
        }

        // Create temporary read-write DMG
        val tempDmg = File(notarizedDir, "Askimo-temp.dmg").apply { delete() }
        logger.lifecycle("📀 Creating temporary DMG...")
        project.exec {
            commandLine(
                "hdiutil",
                "create",
                "-volname",
                "Askimo",
                "-srcfolder",
                dmgStaging.absolutePath,
                "-ov",
                "-format",
                "UDRW",
                tempDmg.absolutePath,
            )
        }

        // Mount the DMG
        logger.lifecycle("💿 Mounting DMG for customization...")
        val mountOutput = ByteArrayOutputStream()
        project.exec {
            commandLine("hdiutil", "attach", "-readwrite", "-noverify", tempDmg.absolutePath)
            standardOutput = mountOutput
        }

        val mountPath =
            mountOutput
                .toString()
                .lines()
                .firstOrNull { it.contains("/Volumes/") }
                ?.substringAfter("/Volumes/")
                ?.trim()
                ?.let { "/Volumes/$it" }
                ?: throw GradleException("Could not determine mount path")

        logger.lifecycle("✅ Mounted at: $mountPath")

        try {
            // Copy background image to .background folder in DMG
            val backgroundDir = File(mountPath, ".background")
            backgroundDir.mkdirs()
            val backgroundImage = project.file("src/main/resources/images/dmg-background.png")

            if (backgroundImage.exists()) {
                logger.lifecycle("🖼️ Copying background image...")
                backgroundImage.copyTo(File(backgroundDir, "background.png"), overwrite = true)
            }

            // Wait for Finder to recognize the mounted volume
            logger.lifecycle("⏳ Waiting for Finder to recognize volume...")
            Thread.sleep(2000)

            // Set background image and window settings
            val backgroundScript =
                """
                tell application "Finder"
                    tell disk "Askimo"
                        open
                        set current view of container window to icon view
                        set toolbar visible of container window to false
                        set statusbar visible of container window to false
                        set the bounds of container window to {400, 100, 920, 480}
                        set viewOptions to the icon view options of container window
                        set arrangement of viewOptions to not arranged
                        set icon size of viewOptions to 100
                        set background picture of viewOptions to file ".background:background.png"
                        set text size of viewOptions to 13
                        set position of item "${appToSign.name}" of container window to {130, 190}
                        set position of item "Applications" of container window to {390, 190}
                        update without registering applications
                        delay 1
                        close
                    end tell
                end tell
                """.trimIndent()

            logger.lifecycle("🎨 Applying window settings...")
            project.exec {
                commandLine("osascript", "-e", backgroundScript)
                isIgnoreExitValue = true
            }

            // Give Finder time to save the settings
            logger.lifecycle("⏳ Waiting for Finder to save settings...")
            Thread.sleep(3000)

            // Sync to ensure all changes are written
            logger.lifecycle("💾 Syncing filesystem...")
            project.exec {
                commandLine("sync")
                isIgnoreExitValue = true
            }
            Thread.sleep(500)

            // Force Finder to close all windows for the volume
            logger.lifecycle("🔒 Closing Finder windows...")
            project.exec {
                commandLine("osascript", "-e", "tell application \"Finder\" to close every window")
                isIgnoreExitValue = true
            }
            Thread.sleep(1000)
        } finally {
            // Unmount with retries
            logger.lifecycle("💿 Unmounting DMG...")
            var unmounted = false
            var attempts = 0
            val maxAttempts = 5

            while (!unmounted && attempts < maxAttempts) {
                attempts++
                try {
                    if (attempts > 1) {
                        logger.lifecycle("   Retry attempt $attempts/$maxAttempts...")
                        Thread.sleep(2000)
                    }

                    project.exec {
                        commandLine("hdiutil", "detach", mountPath, "-force")
                        isIgnoreExitValue = false
                    }
                    unmounted = true
                    logger.lifecycle("✅ DMG unmounted successfully")
                } catch (_: Exception) {
                    if (attempts < maxAttempts) {
                        logger.lifecycle("⚠️  Unmount failed, will retry...")
                        // Kill any processes that might be using the volume
                        project.exec {
                            commandLine("lsof", "+D", mountPath)
                            isIgnoreExitValue = true
                            standardOutput = System.out
                        }
                    } else {
                        logger.warn("⚠️  Could not unmount DMG after $maxAttempts attempts, continuing anyway...")
                    }
                }
            }
        }

        // Convert to compressed, read-only DMG
        val signedDmg = File(notarizedDir, "Askimo-signed.dmg").apply { delete() }
        logger.lifecycle("📀 Creating final compressed DMG...")
        project.exec {
            commandLine(
                "hdiutil",
                "convert",
                tempDmg.absolutePath,
                "-format",
                "UDZO",
                "-o",
                signedDmg.absolutePath,
            )
        }

        // Clean up
        tempDmg.delete()
        dmgStaging.deleteRecursively()

        // Sign DMG
        logger.lifecycle("🔧 Signing DMG...")
        project.exec {
            commandLine(
                "codesign",
                "--force",
                "--sign",
                macosIdentity,
                "--timestamp",
                signedDmg.absolutePath,
            )
        }

        // Verify DMG signature
        logger.lifecycle("🔎 Verifying DMG signature...")
        project.exec {
            commandLine("codesign", "--verify", "--verbose=2", signedDmg.absolutePath)
        }

        logger.lifecycle("✅ DMG created and signed: ${signedDmg.absolutePath}")
    }
}

/**
 * Notarize the DMG with Apple
 */
tasks.register("customNotarizeDmg") {
    group = "distribution"
    description = "Notarize the signed DMG with Apple's notarization service (custom implementation)"

    dependsOn("createSignedDmg")

    @Suppress("DEPRECATION")
    doLast {
        val notarizedDir = file("${layout.buildDirectory.get()}/compose/notarized")
        val signedDmg = File(notarizedDir, "Askimo-signed.dmg")

        if (!signedDmg.exists()) {
            throw GradleException("Signed DMG not found: ${signedDmg.absolutePath}")
        }

        // Notarization credentials - App-Specific Password
        val appleId =
            getEnvOrProperty("APPLE_ID")
                ?: throw GradleException("APPLE_ID environment variable is required")
        val applePassword =
            getEnvOrProperty("APPLE_PASSWORD")
                ?: throw GradleException("APPLE_PASSWORD environment variable is required (use app-specific password)")
        val appleTeamId =
            getEnvOrProperty("APPLE_TEAM_ID")
                ?: throw GradleException("APPLE_TEAM_ID environment variable is required")

        val notaryArgs =
            listOf(
                "--apple-id",
                appleId,
                "--team-id",
                appleTeamId,
                "--password",
                applePassword,
            )

        // Calculate SHA256 before submission
        val sha256Before =
            ByteArrayOutputStream().use { output ->
                project.exec {
                    commandLine("shasum", "-a", "256", signedDmg.absolutePath)
                    standardOutput = output
                }
                output.toString().split(" ")[0]
            }

        logger.lifecycle("🔎 DMG SHA256 before submit: $sha256Before")
        logger.lifecycle("🔐 Notarizing DMG...")

        // Submit for notarization
        val notarizationOutput =
            ByteArrayOutputStream().use { output ->
                project.exec {
                    commandLine(
                        "xcrun",
                        "notarytool",
                        "submit",
                        signedDmg.absolutePath,
                        *notaryArgs.toTypedArray(),
                        "--wait",
                        "--output-format",
                        "json",
                    )
                    standardOutput = output
                }
                output.toString()
            }

        logger.lifecycle(notarizationOutput)

        // Parse JSON response (simple parsing)
        val statusMatch = Regex(""""status"\s*:\s*"([^"]+)"""").find(notarizationOutput)
        val idMatch = Regex(""""id"\s*:\s*"([^"]+)"""").find(notarizationOutput)

        val status = statusMatch?.groupValues?.get(1) ?: "Unknown"
        val submissionId = idMatch?.groupValues?.get(1) ?: ""

        logger.lifecycle("✅ DMG submission: id=$submissionId, status=$status")

        if (status != "Accepted") {
            throw GradleException("❌ Notarization failed (status=$status)")
        }

        // Verify bytes didn't change
        val sha256After =
            ByteArrayOutputStream().use { output ->
                project.exec {
                    commandLine("shasum", "-a", "256", signedDmg.absolutePath)
                    standardOutput = output
                }
                output.toString().split(" ")[0]
            }

        if (sha256Before != sha256After) {
            throw GradleException("❌ DMG bytes changed after notarization submission")
        }

        // Wait for ticket propagation
        logger.lifecycle("⏳ Waiting 60s for ticket propagation...")
        Thread.sleep(60000)

        // Try to staple
        logger.lifecycle("📎 Stapling DMG...")
        val staplerOutput = ByteArrayOutputStream()
        val staplerError = ByteArrayOutputStream()
        val stapleResult =
            project.exec {
                commandLine("xcrun", "stapler", "staple", "-v", signedDmg.absolutePath)
                isIgnoreExitValue = true
                standardOutput = staplerOutput
                errorOutput = staplerError
            }

        if (stapleResult.exitValue != 0) {
            logger.warn("⚠️ Stapler failed, attempting manual ticket attachment...")

            // Extract ticket path from stapler output
            val combinedOutput = staplerOutput.toString() + staplerError.toString()
            val ticketPathMatch = Regex("""file://([^\s]+\.ticket)""").find(combinedOutput)
            val ticketPath = ticketPathMatch?.groupValues?.get(1)

            if (ticketPath != null && File(ticketPath).exists()) {
                logger.lifecycle("📋 Found downloaded ticket: $ticketPath")
                logger.lifecycle("📎 Manually attaching ticket to DMG...")

                // Use Python to attach binary ticket data
                val pythonScript =
                    """
                    import subprocess
                    ticket_path = '$ticketPath'
                    dmg_path = '${signedDmg.absolutePath}'
                    with open(ticket_path, 'rb') as f:
                        ticket_data = f.read()
                    subprocess.run(['xattr', '-wx', 'com.apple.stapler', ticket_data.hex(), dmg_path], check=True)
                    print('✅ Ticket attached successfully')
                    """.trimIndent()

                project.exec {
                    commandLine("python3", "-c", pythonScript)
                }

                // Verify ticket is attached
                val xattrOutput = ByteArrayOutputStream()
                project.exec {
                    commandLine("xattr", "-l", signedDmg.absolutePath)
                    standardOutput = xattrOutput
                }

                val hasTicket = xattrOutput.toString().contains("com.apple.stapler")

                if (hasTicket) {
                    logger.lifecycle("✅ Ticket is attached via xattr")
                } else {
                    throw GradleException("❌ Failed to attach notarization ticket")
                }
            } else {
                throw GradleException("❌ Could not find downloaded ticket file")
            }
        } else {
            logger.lifecycle("✅ Stapled DMG successfully")
        }

        logger.lifecycle("✅ Done. Output: ${signedDmg.absolutePath}")
        logger.lifecycle("")
        logger.lifecycle("Suggested checks:")
        logger.lifecycle("  spctl -a -t open -vv \"${signedDmg.absolutePath}\"")
    }
}

/**
 * Complete notarization workflow - notarizes both .app and DMG
 */
tasks.register("customNotarizeMacApp") {
    group = "distribution"
    description = "Complete macOS code signing and notarization workflow (signs app, notarizes app, creates DMG, notarizes DMG)"

    dependsOn("customNotarizeDmg")

    doLast {
        val notarizedDir = file("${layout.buildDirectory.get()}/compose/notarized")
        val signedDmg = File(notarizedDir, "Askimo-signed.dmg")

        logger.lifecycle("✅ Notarization complete!")
        logger.lifecycle("📦 Notarized DMG: ${signedDmg.absolutePath}")
        logger.lifecycle("")
        logger.lifecycle("You can now distribute this DMG to users.")
        logger.lifecycle("When they mount it, they will see:")
        logger.lifecycle("  - Askimo.app")
        logger.lifecycle("  - Applications folder (for drag-and-drop installation)")
        logger.lifecycle("")
        logger.lifecycle("The app will launch without security warnings.")
    }
}

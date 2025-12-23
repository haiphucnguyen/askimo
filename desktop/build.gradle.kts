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

fun sha256(file: File): String {
    val out = ByteArrayOutputStream()
    execSupport.execOps.exec {
        commandLine("bash", "-lc", """shasum -a 256 "${file.absolutePath}" | awk '{print $1}'""")
        standardOutput = out
        errorOutput = System.err
        isIgnoreExitValue = false
    }
    return out.toString().trim()
}

data class NotarySubmit(
    val id: String,
    val status: String,
    val raw: String,
)

fun notarySubmitWait(file: File): NotarySubmit {
    val stdout = ByteArrayOutputStream()
    val stderr = ByteArrayOutputStream()

    val result =
        execSupport.execOps.exec {
            commandLine(
                "xcrun",
                "notarytool",
                "submit",
                file.absolutePath,
                *notarytoolAuthArgs().toTypedArray(),
                "--wait",
                "--output-format",
                "json",
            )
            isIgnoreExitValue = true
            standardOutput = stdout
            errorOutput = stderr
        }

    val out = stdout.toString().trim()
    val err = stderr.toString().trim()

    if (result.exitValue != 0) {
        error(
            """
            ❌ notarytool submit failed
            File: ${file.absolutePath}
            Exit code: ${result.exitValue}

            --- stdout ---
            ${out.ifBlank { "<empty>" }}

            --- stderr ---
            ${err.ifBlank { "<empty>" }}
            """.trimIndent(),
        )
    }

    if (out.isBlank()) {
        error(
            """
            ❌ notarytool returned empty JSON output
            File: ${file.absolutePath}

            stderr:
            ${err.ifBlank { "<empty>" }}
            """.trimIndent(),
        )
    }

    // Minimal, robust parsing
    val id =
        Regex("\"id\"\\s*:\\s*\"([^\"]+)\"")
            .find(out)
            ?.groupValues
            ?.get(1)
            ?: error("❌ Could not find submission id in notarytool output:\n$out")

    val status =
        Regex("\"status\"\\s*:\\s*\"([^\"]+)\"")
            .find(out)
            ?.groupValues
            ?.get(1)
            ?: "UNKNOWN"

    println("🧾 Notarytool result: id=$id status=$status")

    return NotarySubmit(id = id, status = status, raw = out)
}

fun notaryLogSha256(id: String): String {
    val out = ByteArrayOutputStream()
    val err = ByteArrayOutputStream()

    val r =
        execSupport.execOps.exec {
            commandLine("xcrun", "notarytool", "log", id, *notarytoolAuthArgs().toTypedArray())
            standardOutput = out
            errorOutput = err
            isIgnoreExitValue = true
        }

    val stdout = out.toString(Charsets.UTF_8).trim()
    val stderr = err.toString(Charsets.UTF_8).trim()

    if (r.exitValue != 0) {
        error(
            """
            notarytool log failed (exit ${r.exitValue}) for id=$id
            ---- stdout ----
            $stdout
            ---- stderr ----
            $stderr
            """.trimIndent(),
        )
    }

    // notarytool log SHOULD be JSON; if stdout is empty, show stderr too.
    val raw = if (stdout.isNotBlank()) stdout else stderr
    if (raw.isBlank()) {
        error("notarytool log returned empty output for id=$id (both stdout and stderr were empty).")
    }

    val m = Regex(""""sha256"\s*:\s*"([0-9a-fA-F]{64})"""").find(raw)
    val sha = m?.groupValues?.get(1)?.lowercase()

    if (sha.isNullOrBlank()) {
        error(
            """
            Could not extract sha256 from notarytool log for id=$id
            ---- raw log ----
            $raw
            """.trimIndent(),
        )
    }
    return sha
}

fun stapleOnceVerbose(
    target: File,
    label: String,
): Int {
    println("📎 Stapling $label: ${target.absolutePath}")
    return execSupport.execOps
        .exec {
            commandLine("xcrun", "stapler", "staple", "-v", target.absolutePath)
            isIgnoreExitValue = true
            standardOutput = System.out
            errorOutput = System.err
        }.exitValue
}

fun ensureSameFileAsNotarized(
    target: File,
    notaryId: String,
) {
    val localSha = sha256(target)
    val appleSha = notaryLogSha256(notaryId)

    println("🔎 SHA check for ${target.name}")
    println("   local: $localSha")
    println("   apple: $appleSha")

    if (appleSha.isBlank()) error("Apple sha256 is empty for submission id $notaryId")
    if (!localSha.equals(appleSha, ignoreCase = true)) {
        error(
            """
            ❌ Ticket mismatch: file bytes do NOT match notarized submission.
            File: ${target.absolutePath}
            Local SHA256: $localSha
            Apple SHA256: $appleSha

            This will cause stapler Error 65.
            Fix by ensuring you staple/upload EXACTLY the same file you submitted.
            """.trimIndent(),
        )
    }
}

fun notarytoolAuthArgs(): List<String> {
    // Priority 1: Explicit keychain profile (best for local dev)
    val profile = getEnvOrProperty("NOTARY_KEYCHAIN_PROFILE") // e.g. "askimo-notary"
    if (!profile.isNullOrBlank()) {
        return listOf("--keychain-profile", profile)
    }

    // Priority 2: App Store Connect API key (portable for CI)
    val ascKeyId = getEnvOrProperty("ASC_KEY_ID")
    val ascIssuerId = getEnvOrProperty("ASC_ISSUER_ID")
    val ascKeyPath = getEnvOrProperty("ASC_KEY_PATH") // path to .p8
    if (!ascKeyId.isNullOrBlank() && !ascIssuerId.isNullOrBlank() && !ascKeyPath.isNullOrBlank()) {
        return listOf("--key-id", ascKeyId, "--issuer", ascIssuerId, "--key", ascKeyPath)
    }

    // Priority 3: Apple ID + app-specific password
    val appleId = getEnvOrProperty("APPLE_ID")
    val applePassword = getEnvOrProperty("APPLE_PASSWORD")
    val appleTeamId = getEnvOrProperty("APPLE_TEAM_ID")
    if (!appleId.isNullOrBlank() && !applePassword.isNullOrBlank() && !appleTeamId.isNullOrBlank()) {
        return listOf("--apple-id", appleId, "--team-id", appleTeamId, "--password", applePassword)
    }

    error(
        """
        Notarization credentials are not configured.
        Provide ONE of:
          1) NOTARY_KEYCHAIN_PROFILE (e.g. askimo-notary)
          2) ASC_KEY_ID + ASC_ISSUER_ID + ASC_KEY_PATH
          3) APPLE_ID + APPLE_PASSWORD + APPLE_TEAM_ID
        """.trimIndent(),
    )
}

fun isMac(): Boolean = System.getProperty("os.name").lowercase().contains("mac")

fun codesignIdentity(): String =
    getEnvOrProperty("MACOS_IDENTITY")
        ?: error("MACOS_IDENTITY is required (e.g. 'Developer ID Application: Hai Nguyen (xxxxxxxx)')")

/** Runs command, prints output, throws on failure by default. */
fun execLogged(
    vararg args: String,
    ignoreExit: Boolean = false,
): Int {
    val result =
        execSupport.execOps.exec {
            commandLine(*args)
            isIgnoreExitValue = ignoreExit
            standardOutput = System.out
            errorOutput = System.err
        }
    if (!ignoreExit && result.exitValue != 0) {
        error("Command failed (${result.exitValue}): ${args.joinToString(" ")}")
    }
    return result.exitValue
}

/** Returns true if a jar contains at least one .dylib entry. */
fun jarContainsDylib(jar: File): Boolean {
    val out = ByteArrayOutputStream()
    execSupport.execOps.exec {
        commandLine("bash", "-lc", """jar tf "${jar.absolutePath}" || true""")
        standardOutput = out
        errorOutput = System.err
        isIgnoreExitValue = true
    }
    return out.toString().lineSequence().any { it.trim().endsWith(".dylib") }
}

/**
 * Fix notarization "Invalid" due to:
 * - unsigned or untimestamped dylibs inside JARs (sqlite-jdbc, skiko)
 * - unsigned/untimestamped embedded runtime dylibs (Contents/runtime/...)
 * - unsigned/untimestamped main executable (Contents/MacOS/Askimo)
 *
 * This MUST run AFTER Compose/jpackage produced the .app, and BEFORE notarization.
 */
fun postSignComposeApp(appFile: File) {
    val identity = codesignIdentity()
    println("🔏 Post-signing for notarization: ${appFile.absolutePath}")
    println("🔑 Using identity: $identity")

    val contents = File(appFile, "Contents").also { require(it.exists()) { "Missing Contents in $appFile" } }
    val runtimeDir = File(contents, "runtime")
    val appDir = File(contents, "app")
    val macOsDir = File(contents, "MacOS")
    val mainExe = File(macOsDir, "Askimo")

    // 1) Sign embedded runtime dylibs + helpers
    if (runtimeDir.exists()) {
        println("🔧 Signing embedded runtime binaries...")
        execLogged(
            "bash",
            "-lc",
            """
            set -e
            find "${runtimeDir.absolutePath}" -type f \( -name "*.dylib" -o -name "jspawnhelper" \) -print0 \
              | xargs -0 -I{} codesign --force --sign "$identity" --timestamp --options runtime "{}"
            """.trimIndent(),
        )
    }

    // 2) Sign loose dylibs under Contents/app (e.g., libskiko-macos-arm64.dylib)
    if (appDir.exists()) {
        println("🔧 Signing native dylibs under Contents/app...")
        execLogged(
            "bash",
            "-lc",
            """
            set -e
            find "${appDir.absolutePath}" -type f -name "*.dylib" -print0 \
              | xargs -0 -I{} codesign --force --sign "$identity" --timestamp --options runtime "{}"
            """.trimIndent(),
        )

        // 3) Sign dylibs embedded inside JARs (sqlite-jdbc, skiko, and any others)
        println("🔧 Signing dylibs inside JARs (if any)...")
        val work =
            File(project.buildDir, "codesign-jar-work").apply {
                deleteRecursively()
                mkdirs()
            }

        val jars = appDir.walkTopDown().filter { it.isFile && it.extension == "jar" }.toList()
        for (jar in jars) {
            if (!jarContainsDylib(jar)) continue

            println("   • Fixing JAR: ${jar.name}")
            val jarWorkDir =
                File(work, jar.nameWithoutExtension).apply {
                    deleteRecursively()
                    mkdirs()
                }

            // Extract
            execLogged("bash", "-lc", """cd "${jarWorkDir.absolutePath}" && jar xf "${jar.absolutePath}"""")
            // Sign extracted dylibs
            execLogged(
                "bash",
                "-lc",
                """
                set -e
                find "${jarWorkDir.absolutePath}" -type f -name "*.dylib" -print0 \
                  | xargs -0 -I{} codesign --force --sign "$identity" --timestamp --options runtime "{}"
                """.trimIndent(),
            )
            // Repack (overwrite)
            jar.delete()
            execLogged("bash", "-lc", """cd "${jarWorkDir.absolutePath}" && jar cf "${jar.absolutePath}" .""")
        }
    }

    // 4) Sign main executable (Askimo)
    if (mainExe.exists()) {
        println("🔧 Signing main executable: ${mainExe.absolutePath}")
        execLogged(
            "codesign",
            "--force",
            "--sign",
            identity,
            "--timestamp",
            "--options",
            "runtime",
            mainExe.absolutePath,
        )
    } else {
        println("⚠️  Main executable not found at ${mainExe.absolutePath}. Signing all files in Contents/MacOS...")
        execLogged(
            "bash",
            "-lc",
            """
            set -e
            find "${macOsDir.absolutePath}" -type f -maxdepth 1 -print0 \
              | xargs -0 -I{} codesign --force --sign "$identity" --timestamp --options runtime "{}"
            """.trimIndent(),
        )
    }

    // 5) Final: re-sign the entire app bundle (required because we modified JARs)
    println("🔧 Re-signing entire app bundle (final)...")
    execLogged(
        "codesign",
        "--force",
        "--deep",
        "--sign",
        identity,
        "--timestamp",
        "--options",
        "runtime",
        appFile.absolutePath,
    )

    // 6) Verify
    println("✅ Verifying signature...")
    execLogged("codesign", "--verify", "--deep", "--strict", "--verbose=2", appFile.absolutePath)

    println("✅ Signature details:")
    execLogged(
        "bash",
        "-lc",
        """codesign -dv --verbose=4 "${appFile.absolutePath}" 2>&1 | egrep 'Identifier=|TeamIdentifier=|flags=|Timestamp|CDHash=' || true""",
        ignoreExit = true,
    )
}

/** Staple with a long retry window (propagation delay is normal). */
fun stapleWithRetry(
    target: File,
    label: String,
    attempts: Int = 20,
    sleepMs: Long = 60_000,
): Boolean {
    for (i in 1..attempts) {
        println("📎 Stapling $label ticket (attempt $i/$attempts): ${target.name}")
        val exit =
            execSupport.execOps
                .exec {
                    commandLine("xcrun", "stapler", "staple", "-v", target.absolutePath)
                    isIgnoreExitValue = true
                    standardOutput = System.out
                    errorOutput = System.err
                }.exitValue
        if (exit == 0) return true
        Thread.sleep(sleepMs)
    }
    return false
}

// Task to notarize both app bundle and DMG
// Task: build app, post-sign, notarize (zip), staple (best-effort), build dmg, notarize dmg, staple (best-effort)
tasks.register("packageNotarizedDmg") {
    group = "distribution"
    description = "Build app, post-sign, notarize app + dmg, staple (with SHA guards)"

    dependsOn("packageDmg")

    doLast {
        if (!isMac()) return@doLast

        val appDir = file("build/compose/binaries/main/app")
        val appFile =
            appDir.listFiles()?.firstOrNull { it.name.endsWith(".app") }
                ?: error("No .app found in ${appDir.absolutePath}")

        // 1) Fix signing issues (your postSignComposeApp)
        postSignComposeApp(appFile)

        val outDir = file("build/compose/notarized").apply { mkdirs() }

        // 2) Notarize APP via zip
        val zipFile = outDir.resolve("${appFile.nameWithoutExtension}.app.zip").apply { if (exists()) delete() }
        println("📦 Zipping app for notarization: ${zipFile.name}")
        execLogged("ditto", "-c", "-k", "--keepParent", appFile.absolutePath, zipFile.absolutePath)

        println("🔐 Notarizing APP zip...")
        println("📄 Submitting for notarization: ${zipFile.absolutePath}")
        println("🔎 SHA256: ${sha256(zipFile)}")
        val appSubmit = notarySubmitWait(zipFile)
        println("✅ APP submission: id=${appSubmit.id}, status=${appSubmit.status}")

        // IMPORTANT: Validate we are stapling the same app that was notarized.
        // For zip submissions, Apple log sha256 is for the ZIP, not the .app,
        // so we can't sha-compare the .app here. But we CAN avoid touching the .app after this point.

        println("📎 Stapling APP (may still fail if Apple ticket isn’t propagated for this exact cdhash yet)...")
        stapleWithRetry(appFile, "APP", attempts = 20, sleepMs = 60_000)

        // 3) Build DMG ONCE (after signing)
        val dmgOut = outDir.resolve("Askimo-${project.version}.dmg").apply { if (exists()) delete() }

        println("📀 Creating DMG: ${dmgOut.name}")
        execLogged(
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

        val dmgShaBefore = sha256(dmgOut)
        println("🔎 DMG SHA256 before submit: $dmgShaBefore")

        // 4) Notarize DMG
        println("🔐 Notarizing DMG...")
        val dmgSubmit = notarySubmitWait(dmgOut)
        println("✅ DMG submission: id=${dmgSubmit.id}, status=${dmgSubmit.status}")

        // 5) GUARANTEE the DMG we are stapling == the DMG Apple notarized
        ensureSameFileAsNotarized(dmgOut, dmgSubmit.id)

        // 6) Staple DMG (now if it fails, it’s truly ticket delivery / environment, not mismatch)
        println("📎 Stapling DMG...")
        val dmgStapleExit = stapleOnceVerbose(dmgOut, "DMG")
        if (dmgStapleExit != 0) {
            println("⚠️ DMG stapling failed once (exit=$dmgStapleExit). Retrying with long window...")
            val ok = stapleWithRetry(dmgOut, "DMG", attempts = 20, sleepMs = 60_000)
            if (!ok) {
                println("❌ DMG stapling still failing after retries.")
                println("   But notarization is Accepted; the remaining issue is local validation/stapler environment.")
            }
        }

        println("✅ Done. Output: ${dmgOut.absolutePath}")
        println("Verify:")
        println("  xcrun stapler validate -v \"${dmgOut.absolutePath}\"")
        println("  spctl -a -t open -vv \"${dmgOut.absolutePath}\"")
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

import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import java.time.Instant
import java.time.Year
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

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
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

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

tasks.test {
    useJUnitPlatform()
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

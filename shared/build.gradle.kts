plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

group = rootProject.group
version = rootProject.version

repositories {
    mavenCentral()
}

dependencies {
    // LangChain4j libraries
    api(libs.bundles.langchain4j)

    // Kotlin libraries
    api(libs.kotlinx.serialization.json.jvm)
    api(libs.kotlinx.coroutines.core)
    api(kotlin("stdlib"))

    // Lucene for keyword search
    api(libs.bundles.lucene)

    // Jackson for YAML/JSON
    api(libs.bundles.jackson)

    // Database
    api(libs.sqlite.jdbc)
    api(libs.hikaricp)
    api(libs.bundles.exposed)

    // Dependency Injection
    api(libs.bundles.koin)

    // Logging
    api(libs.bundles.logging)

    // Tika for file parsing
    implementation(libs.bundles.tika) {
        exclude(group = "org.eclipse.angus", module = "angus-activation")
    }

    // Markdown
    implementation(libs.bundles.commonmark)

    // Testing
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.bundles.testcontainers)
    testImplementation(libs.bundles.koin.test)
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

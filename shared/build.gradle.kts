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
    api(libs.langchain4j)
    api(libs.langchain4j.open.ai)
    api(libs.langchain4j.ollama)
    api(libs.langchain4j.google.ai.gemini)
    api(libs.langchain4j.anthropic)
    api(libs.langchain4j.http.client)
    api(libs.langchain4j.localai)
    api(libs.kotlinx.serialization.json.jvm)
    api(libs.kotlinx.coroutines.core)
    api(libs.postgresql)
    api(libs.langchain4j.pgvector)
    api(libs.testcontainers.postgresql)
    api(libs.jackson.module.kotlin)
    api(libs.jackson.dataformat.yaml)
    api(libs.sqlite.jdbc)
    api(libs.hikaricp)
    api(libs.exposed.core)
    api(libs.exposed.jdbc)
    api(libs.exposed.java.time)
    api(libs.koin.core)
    api(kotlin("stdlib"))
    api(libs.slf4j.api)
    api(libs.logback.classic)
    implementation(libs.tika.core)
    implementation(libs.tika.parser.pdf) {
        exclude(group = "org.eclipse.angus", module = "angus-activation")
    }
    implementation(libs.tika.parser.microsoft)
    implementation(libs.commonmark)
    implementation(libs.commonmark.ext.gfm.tables)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.testcontainers.ollama)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.koin.test)
    testImplementation(libs.koin.test.junit5)
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}

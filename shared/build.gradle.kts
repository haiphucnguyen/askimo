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
    api(libs.kotlinx.serialization.json.jvm)
    api(libs.kotlinx.coroutines.core)
    api(libs.postgresql)
    api(libs.langchain4j.pgvector)
    api(libs.testcontainers.postgresql)
    api(libs.jackson.module.kotlin)
    api(libs.jackson.dataformat.yaml)
    api(libs.sqlite.jdbc)
    api(libs.hikaricp)
    api(kotlin("stdlib"))
    runtimeOnly(libs.slf4j.nop)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.testcontainers.ollama)
    testImplementation(libs.testcontainers.junit.jupiter)
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}

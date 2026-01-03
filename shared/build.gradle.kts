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
    api(libs.bundles.langchain4j)

    api(libs.kotlinx.serialization.json.jvm)
    api(libs.kotlinx.coroutines.core)
    api(kotlin("stdlib"))

    api(libs.bundles.lucene)

    api(libs.bundles.jackson)

    api(libs.sqlite.jdbc)
    api(libs.hikaricp)
    api(libs.bundles.exposed)

    api(libs.bundles.koin)

    implementation(libs.caffeine)

    api(libs.bundles.logging)

    implementation(libs.bundles.tika) {
        exclude(group = "org.eclipse.angus", module = "angus-activation")
    }

    implementation(libs.okhttp)
    implementation(libs.jsoup)

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

    // Enable Vector API for better JVector performance
    jvmArgs("--add-modules", "jdk.incubator.vector")
}
kotlin {
    jvmToolchain(21)
}

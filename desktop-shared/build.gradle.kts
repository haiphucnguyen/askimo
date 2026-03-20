plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation(project(":shared"))
    implementation(libs.konform)
    implementation(libs.bundles.commonmark)
    implementation(libs.commonmark.ext.autolink)
    implementation(libs.jlatexmath)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    // Terminal support
    implementation(libs.bundles.jediterm)
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
}

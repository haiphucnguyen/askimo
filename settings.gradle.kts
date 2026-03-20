rootProject.name = "askimo"

include("cli")
include("shared")
include("desktop")
include("desktop-shared")

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
        maven("https://packages.jetbrains.team/maven/p/ij/intellij-dependencies")
        google()
    }
}

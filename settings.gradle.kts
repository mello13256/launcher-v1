pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "HexaLauncher"

include(":core")

// `-Phexa.coreOnly=true` builds and tests only the pure-Kotlin core, without
// resolving the Android Gradle Plugin (useful on machines without Google Maven).
if (providers.gradleProperty("hexa.coreOnly").orNull != "true") {
    include(":app")
}

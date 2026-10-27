buildscript {
    // AGP is put on the root classpath (next to the Kotlin plugins below) so that
    // the Android and Kotlin plugins share a classloader. It is skipped for
    // core-only builds.
    if (findProperty("hexa.coreOnly") != "true") {
        repositories {
            google()
            mavenCentral()
        }
        dependencies {
            classpath(libs.agp)
        }
    }
}

plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}

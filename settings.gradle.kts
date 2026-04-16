pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

rootProject.name = "fonts-bitsnpicas"

// Stage S4 — KMP port of the frozen Java logic lives under modules/**.
// Root project (legacy Java profile) stays JVM-only.
include("modules:core")
project(":modules:core").projectDir = file("modules/core")

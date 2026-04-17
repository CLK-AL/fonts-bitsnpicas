pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
        google()
    }
}

rootProject.name = "fonts-bitsnpicas"

// Stage S4 — KMP port of the frozen Java logic lives under modules/**.
// Root project (legacy Java profile) stays JVM-only.
include("modules:core")
project(":modules:core").projectDir = file("modules/core")

// Stage S5 — UiDriver expect/actual infrastructure + rendering modules.
include("modules:ui-shared")
project(":modules:ui-shared").projectDir = file("modules/ui-shared")

include("modules:ui-swing")
project(":modules:ui-swing").projectDir = file("modules/ui-swing")

include("modules:ui-compose-desktop")
project(":modules:ui-compose-desktop").projectDir = file("modules/ui-compose-desktop")

// Stage S6 — Compose Web (wasmJs) scaffold.
include("modules:ui-compose-html")
project(":modules:ui-compose-html").projectDir = file("modules/ui-compose-html")

plugins {
    // Version inherited from root's plugin classpath (settings.gradle.kts
    // loads kotlin 2.3.20 via the root project's alias(libs.plugins.kotlin.jvm)).
    id("org.jetbrains.kotlin.multiplatform")
}

group = "com.kreative.bitsnpicas"
version = "0.1.0-SNAPSHOT"

// Stage S4 — pure Kotlin port of the frozen Java logic.
// Only JVM target is enabled today; js/wasmJs/native targets land in
// later S4 sub-stages once the JVM port achieves differential parity
// against the frozen Java tree.
kotlin {
    jvm {
        compilations.all {
            compilerOptions.configure {
                jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8)
            }
        }
    }

    sourceSets {
        val commonMain by getting
        val commonTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }
        val jvmMain by getting {
            dependencies {
                // Delegates over the frozen Java for differential parity tests.
                implementation(rootProject)
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(libs.kotlin.test.junit5)
                implementation(libs.junit.jupiter)
                runtimeOnly(libs.junit.platform.launcher)
            }
        }
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging {
        events("passed", "failed", "skipped")
    }
}

plugins {
    id("org.jetbrains.kotlin.multiplatform")
}

group = "com.kreative.bitsnpicas"
version = "0.1.0-SNAPSHOT"

kotlin {
    jvm {
        compilations.all {
            compilerOptions.configure {
                jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8)
                freeCompilerArgs.add("-Xexpect-actual-classes")
            }
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(project(":modules:core"))
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }
        val jvmMain by getting {
            dependencies {
                // Apache FontBox for TTF outline rendering (JVM only).
                implementation(libs.fontbox)
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
    // Ensure headless mode for CI environments without a display.
    jvmArgs("-Djava.awt.headless=true")
    testLogging {
        events("passed", "failed", "skipped")
    }
}

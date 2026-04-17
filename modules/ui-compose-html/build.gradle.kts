// Compose Web module — JVM scaffold with Compose deps.
//
// The wasmJs target requires modules/core to also declare a wasmJs target
// (variant matching). That's a follow-up once the full KMP target matrix
// is enabled on core. For now this compiles as JVM with Compose so the
// web-specific composables can be authored and tested via JVM previews.
plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.compose") version "1.8.2"
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.20"
}

group = "com.kreative.bitsnpicas"
version = "0.1.0-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":modules:core"))
    implementation(compose.runtime)
    implementation(compose.foundation)
}

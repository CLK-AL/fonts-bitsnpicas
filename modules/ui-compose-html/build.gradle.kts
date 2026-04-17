// Compose Web (wasmJs) module — scaffold / stub.
//
// Full Compose Multiplatform dependencies (org.jetbrains.compose 1.8.2) are
// not wired here because the JetBrains Space Maven repository currently
// returns HTTP 503 for transitive AndroidX lifecycle dependencies, and the
// wasmJs target requires additional Compose HTML/Canvas libraries that are
// not resolvable in this CI environment.
//
// When the repository issue resolves, re-enable:
//   id("org.jetbrains.compose") version "1.8.2"
//   id("org.jetbrains.kotlin.plugin.compose") version "2.3.20"
//   wasmJs { browser() }
//   implementation(compose.runtime) / compose.foundation
//
// Until then this module compiles as a plain Kotlin/JVM stub placeholder.
plugins {
    id("org.jetbrains.kotlin.jvm")
}

group = "com.kreative.bitsnpicas"
version = "0.1.0-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8)
    }
}

dependencies {
    implementation(project(":modules:core"))
}

// Compose Desktop module — scaffold / stub.
//
// Full Compose dependencies (org.jetbrains.compose 1.8.2) are not wired here
// because the JetBrains Space Maven repository currently returns HTTP 503 for
// transitive AndroidX lifecycle dependencies.  When that resolves, re-enable:
//   id("org.jetbrains.compose") version "1.8.2"
//   id("org.jetbrains.kotlin.plugin.compose") version "2.3.20"
//   implementation(compose.runtime)  / compose.foundation / compose.desktop.currentOs
//
// Until then this module compiles as a plain Kotlin/JVM stub and hosts no tests.
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
    implementation(project(":modules:ui-shared"))
    implementation(project(":modules:core"))
}

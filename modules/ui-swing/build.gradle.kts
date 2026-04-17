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
    // Root project provides the frozen Java BitmapFont for legacy interop.
    implementation(rootProject)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
    // Ensure headless mode for CI environments without a display.
    jvmArgs("-Djava.awt.headless=true")
    testLogging {
        events("passed", "failed", "skipped")
    }
}

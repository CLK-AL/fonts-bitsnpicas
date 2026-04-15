plugins {
    java
    alias(libs.plugins.kotlin.jvm)
    jacoco
}

group = "com.kreative.bitsnpicas"
version = "0.1.0-SNAPSHOT"

// Java-only source set for the legacy codebase under main/java/BitsNPicas/src.
// Kotlin tests live under src/test/kotlin and exercise those classes directly.
sourceSets {
    main {
        java {
            setSrcDirs(listOf("main/java/BitsNPicas/src"))
            // Exclude UI / editor / mover code paths whose legacy dependencies
            // (internal CommonMenuItems, com.apple.eawt, etc.) are out of scope for
            // the Phase A/B importer-correctness tests.
            exclude("com/kreative/bitsnpicas/mover/**")
            exclude("com/kreative/bitsnpicas/edit/**")
            exclude("com/kreative/bitsnpicas/main/**")
            exclude("com/kreative/bitsnpicas/geos/mover/**")
            exclude("com/kreative/keyedit/**")
            exclude("com/kreative/mapedit/**")
            // NFNT importer/exporter depend on the excluded mover package.
            exclude("com/kreative/bitsnpicas/importer/NFNTBitmapFontImporter.java")
            exclude("com/kreative/bitsnpicas/exporter/NFNTBitmapFontExporter.java")
        }
        resources {
            setSrcDirs(emptyList<String>())
        }
    }
    test {
        java {
            setSrcDirs(emptyList<String>())
        }
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    // The legacy sources use raw types and deprecated APIs; don't fail the build on those.
    options.compilerArgs.addAll(listOf("-Xlint:none", "-nowarn"))
    options.isWarnings = false
}

// Use the current JDK for Kotlin compilation (no toolchain auto-provisioning).
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // Legacy Java dep used by a handful of classes (universal application library).
    implementation(files("main/java/BitsNPicas/dep/ual.jar"))

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotest.assertions)
    testImplementation(libs.kotest.property)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "failed", "skipped")
        showStandardStreams = false
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
    finalizedBy(tasks.jacocoTestReport)
}

jacoco {
    toolVersion = "0.8.12"
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

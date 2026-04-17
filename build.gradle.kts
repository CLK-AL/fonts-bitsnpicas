plugins {
    java
    alias(libs.plugins.kotlin.jvm)
    jacoco
    // NOTE: The ProGuard Gradle plugin (com.guardsquare.proguard 7.7.0) is NOT available
    // in the Gradle Plugin Portal. The artifact com.guardsquare.proguard:
    // com.guardsquare.proguard.gradle.plugin:7.7.0 does not resolve.
    // We use a manual JavaExec task with proguard-base instead — see proguardRelease below.
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
            // The importer classes load their DTDs via Class.getResourceAsStream(),
            // so treat the legacy src directory as a resources root too. Without
            // this the Kbitx/Kpcax importers fail with MalformedURLException when
            // the parser can't resolve <!DOCTYPE> references.
            setSrcDirs(listOf("main/java/BitsNPicas/src"))
            include("**/*.dtd")
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
// Match Kotlin's target to the Java source/target compatibility to keep the
// Gradle validation happy.
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8)
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
    toolVersion = "0.8.13"
}

// Option-C coverage scope: core top-level classes only (not subpackages)
// plus importer, exporter, truetype, puaa subpackages.
val optionCIncludes = listOf(
    "com/kreative/bitsnpicas/*.class",
    "com/kreative/bitsnpicas/importer/**",
    "com/kreative/bitsnpicas/exporter/**",
    "com/kreative/bitsnpicas/truetype/**",
    "com/kreative/bitsnpicas/puaa/**",
)

// Classes whose remaining uncovered paths are platform-specific or
// otherwise unreachable from a headless Linux CI run.
val optionCExcludes = listOf(
    // MacUtility shells out to /usr/bin/SetFile and /usr/bin/GetFileInfo
    // (Apple Developer Tools) - the Process-success branches only execute
    // on macOS. The IOException branches are covered.
    "com/kreative/bitsnpicas/MacUtility.class",
)

// Filter the JaCoCo class-directories against the scope so the XML/HTML
// reports only contain in-scope classes.
fun scopedClassDirs(): FileCollection {
    val mainOutput = sourceSets.main.get().output
    return files(mainOutput.classesDirs.map { dir ->
        fileTree(dir) {
            include(optionCIncludes)
            exclude(optionCExcludes)
        }
    })
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    classDirectories.setFrom(scopedClassDirs())
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

/**
 * Per-package verification. The current bundle floor is set to the
 * coverage actually reached on this branch (>= 85% line, >= 70% branch)
 * so CI fails on regressions. The next pass should split this into one
 * PACKAGE rule per in-scope package and continue tightening toward 100%.
 */
tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.test)
    classDirectories.setFrom(scopedClassDirs())
    violationRules {
        rule {
            element = "BUNDLE"
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.88".toBigDecimal()
            }
            limit {
                counter = "BRANCH"
                value = "COVEREDRATIO"
                minimum = "0.74".toBigDecimal()
            }
        }
        // TODO follow-up: split this into one PACKAGE rule per in-scope
        // package once truetype/puaa reach their own 90%/100% goals.
    }
}

// Wire verification into check so CI fails when coverage regresses.
tasks.named("check") { dependsOn(tasks.jacocoTestCoverageVerification) }

// ---------------------------------------------------------------------------
// ProGuard shrinking — produces a minimised legacy JAR.
// ---------------------------------------------------------------------------
// BLOCKER: ProGuard 7.7.0 (proguard-core 9.1.10) does not support Java 25
// class files (version 69.0; max supported is 68.x = Java 24). GraalVM
// 25.0.2 ships JDK 25, so the jmod library jars can't be read.
// The ProGuard Gradle plugin (com.guardsquare.proguard) also does not
// resolve from the Gradle Plugin Portal.
//
// The tasks below are fully wired but will fail at runtime until either:
//   (a) ProGuard ships Java 25 support, or
//   (b) a JDK 24 toolchain is installed and used for this task.
// They are NOT wired into `check` to avoid blocking CI.
// ---------------------------------------------------------------------------

// Detached configuration so we can resolve the ProGuard CLI jar without
// polluting compile/runtime classpaths.
val proguardClasspath: Configuration by configurations.creating {
    isCanBeResolved = true
    isCanBeConsumed = false
}

dependencies {
    proguardClasspath(libs.proguard.base)
}

val proguardRelease by tasks.registering(JavaExec::class) {
    group = "build"
    description = "Shrink the legacy JAR with ProGuard (BLOCKED: needs JDK <=24 or ProGuard update)"
    dependsOn(tasks.jar)

    val inputJar = tasks.jar.flatMap { it.archiveFile }
    val shrunkJarName = tasks.jar.flatMap { it.archiveBaseName }.map { "$it-legacy-shrunk.jar" }
    val outputJarFile = layout.buildDirectory.dir("libs").map { dir ->
        File(dir.asFile, shrunkJarName.get())
    }
    val mappingFile = layout.buildDirectory.file("proguard/mapping.txt")

    inputs.file(inputJar)
    inputs.files(fileTree("proguard") { include("*.pro") })
    outputs.file(outputJarFile)
    outputs.file(mappingFile)

    mainClass.set("proguard.ProGuard")
    classpath = proguardClasspath

    // Build the ProGuard arguments lazily
    doFirst {
        val libraryJars = configurations.named("runtimeClasspath").get().files

        args(
            "-injars", inputJar.get().asFile.absolutePath,
            "-outjars", outputJarFile.get().absolutePath,
            "-printmapping", mappingFile.get().asFile.absolutePath,
            // JDK modules as library jars (Java 9+)
            "-libraryjars", "<java.home>/jmods/java.base.jmod(!**.jar;!module-info.class)",
            "-libraryjars", "<java.home>/jmods/java.desktop.jmod(!**.jar;!module-info.class)",
            "-libraryjars", "<java.home>/jmods/java.datatransfer.jmod(!**.jar;!module-info.class)",
            "-libraryjars", "<java.home>/jmods/java.xml.jmod(!**.jar;!module-info.class)",
            "-libraryjars", "<java.home>/jmods/java.logging.jmod(!**.jar;!module-info.class)",
            "-libraryjars", "<java.home>/jmods/java.prefs.jmod(!**.jar;!module-info.class)",
        )

        // Add runtime dependencies as library jars
        libraryJars.forEach { jar ->
            args("-libraryjars", jar.absolutePath)
        }

        // Apply rule files
        args("-include", file("proguard/proguard-rules-common.pro").absolutePath)
        args("-include", file("proguard/proguard-rules-java.pro").absolutePath)

        // Ensure output directories exist
        outputJarFile.get().parentFile.mkdirs()
        mappingFile.get().asFile.parentFile.mkdirs()
    }
}

// ---------------------------------------------------------------------------
// Verify the ProGuarded JAR — run the test suite against the shrunk JAR.
// ---------------------------------------------------------------------------
val verifyProguardedJar by tasks.registering(Test::class) {
    group = "verification"
    description = "Run the test suite against the ProGuard-shrunk JAR"
    dependsOn(proguardRelease)

    useJUnitPlatform()
    testLogging {
        events("passed", "failed", "skipped")
        showStandardStreams = false
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }

    // Use the same test classes as the main test task
    testClassesDirs = sourceSets.test.get().output.classesDirs

    // Replace the normal runtime classpath: swap the project classes for the shrunk JAR
    val shrunkJar = proguardRelease.map {
        val name = tasks.jar.get().archiveBaseName.get() + "-legacy-shrunk.jar"
        File(layout.buildDirectory.dir("libs").get().asFile, name)
    }
    classpath = files(shrunkJar) +
        sourceSets.test.get().output.classesDirs +
        configurations.named("testRuntimeClasspath").get()
}
// NOTE: proguardRelease and verifyProguardedJar are NOT wired into `check`
// due to the JDK 25 / ProGuard 7.7.0 incompatibility documented above.

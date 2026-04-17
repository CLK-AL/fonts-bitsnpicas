plugins {
    // Version inherited from root's plugin classpath (settings.gradle.kts
    // loads kotlin 2.3.20 via the root project's alias(libs.plugins.kotlin.jvm)).
    id("org.jetbrains.kotlin.multiplatform")
    // GraalVM native-image support. The plugin needs the `application` plugin
    // to register nativeCompile tasks; we apply it and configure the main class
    // via graalvmNative DSL below.
    alias(libs.plugins.graalvm.native)
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
    js(IR) {
        browser()
        nodejs()
    }
    wasmJs {
        browser()
    }
    // Native targets — desktop tier
    linuxX64()
    macosX64()
    macosArm64()
    mingwX64()

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

// Use system-installed Node.js instead of downloading via Gradle.
// Kotlin 2.3 moved the NodeJs config to the new plugin API.
rootProject.plugins.withType<org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsPlugin> {
    rootProject.extensions.getByType<org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsEnvSpec>().apply {
        download.set(false)
    }
}

// ---------------------------------------------------------------------------
// GraalVM native-image — compile the KMP core CLI to a native binary.
// ---------------------------------------------------------------------------
// The GraalVM native buildtools plugin (0.10.6) requires the `application`
// plugin to register the `nativeCompile` task. In a KMP project the JVM
// compilation output is used as the classpath; we wire it manually via an
// Exec task since the plugin's auto-detection does not find KMP JVM jars.
// ---------------------------------------------------------------------------
val nativeCompileCli by tasks.registering(Exec::class) {
    group = "build"
    description = "Compile FontStudioCli to a GraalVM native-image binary"

    val jvmJar = tasks.named<org.gradle.jvm.tasks.Jar>("jvmJar")
    dependsOn(jvmJar)

    val outputDir = layout.buildDirectory.dir("native/nativeCompile")
    val binaryName = "font-studio-cli"

    inputs.files(jvmJar.map { it.outputs.files })
    outputs.dir(outputDir)

    doFirst {
        outputDir.get().asFile.mkdirs()

        val jvmRuntimeClasspath = configurations.named("jvmRuntimeClasspath").get().files
        val jarFile = jvmJar.get().archiveFile.get().asFile
        val cp = (listOf(jarFile) + jvmRuntimeClasspath).joinToString(File.pathSeparator) { it.absolutePath }

        commandLine(
            "native-image",
            "--no-fallback",
            "-cp", cp,
            "-o", File(outputDir.get().asFile, binaryName).absolutePath,
            "com.kreative.bitsnpicas.core.FontStudioCliKt"
        )
    }
}

// Also configure the plugin's own DSL for when `application` plugin is
// present (future migration).  Currently a no-op since the plugin does
// not register binaries without `application`.
graalvmNative {
    binaries {
        all {
            mainClass.set("com.kreative.bitsnpicas.core.FontStudioCliKt")
            buildArgs.add("--no-fallback")
        }
    }
}

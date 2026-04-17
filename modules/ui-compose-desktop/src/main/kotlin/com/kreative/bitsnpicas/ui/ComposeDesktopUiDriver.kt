package com.kreative.bitsnpicas.ui

/**
 * Compose Desktop rendering driver — scaffold / stub.
 *
 * The [UiDriver] actual for JVM is provided by `:modules:ui-shared` jvmMain
 * (Swing-based off-screen rendering). This module will eventually replace
 * that actual with a Compose-based renderer using Skia/Skiko canvases.
 *
 * Stub status: S5 desktop sprint will implement Compose-native rendering.
 * Until then, all JVM rendering goes through the Swing actual in ui-shared.
 */
// No ComposeDesktopUiDriver actual needed — the JVM actual from ui-shared
// provides the rendering implementation. This module exists to wire in
// Compose Desktop dependencies for the future desktop application shell.

package com.kreative.bitsnpicas.ui

/**
 * Swing / AWT-based rendering driver for bitmap fonts.
 *
 * The actual [UiDriver] implementation lives in `:modules:ui-shared` jvmMain.
 * This module re-exports that actual for convenience and hosts the
 * Swing-specific "blue oracle" integration tests.
 *
 * Legacy Java [com.kreative.bitsnpicas.BitmapFont] can be adapted to the
 * KMP [com.kreative.bitsnpicas.core.BitmapFont] via the core module's
 * [com.kreative.bitsnpicas.core.JavaLegacyAdapter].
 */
// No additional code needed — UiDriver actual is provided by ui-shared jvmMain.

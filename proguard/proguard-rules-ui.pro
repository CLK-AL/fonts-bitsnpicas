# ProGuard rules for Compose Desktop + Skiko UI layer (placeholder)
# These rules will be refined when the ProGuard Gradle plugin is wired in.

# Keep Compose runtime and UI classes
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# Keep Skiko native bindings
-keep class org.jetbrains.skia.** { *; }
-keep class org.jetbrains.skiko.** { *; }
-dontwarn org.jetbrains.skia.**
-dontwarn org.jetbrains.skiko.**

# Keep Compose Desktop application entry points
-keep class * extends androidx.compose.ui.window.ApplicationScope { *; }

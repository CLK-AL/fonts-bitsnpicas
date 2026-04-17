# ProGuard rules for fonts-bitsnpicas legacy Java main classes
# Keep all entry-point classes with public static void main(String[])

-keep class com.kreative.bitsnpicas.edit.Main { public static void main(java.lang.String[]); }
-keep class com.kreative.bitsnpicas.main.** { public static void main(java.lang.String[]); }
-keep class com.kreative.keyedit.Main { public static void main(java.lang.String[]); }
-keep class com.kreative.keyedit.edit.Main { public static void main(java.lang.String[]); }
-keep class com.kreative.mapedit.Main { public static void main(java.lang.String[]); }
-keep class com.kreative.mapedit.Remap { public static void main(java.lang.String[]); }
-keep class com.kreative.unicode.ttfbin.** { public static void main(java.lang.String[]); }

# Keep reflective entry points
-keep class com.kreative.bitsnpicas.puaa.PuaaLookup { public static void main(java.lang.String[]); }
-keep class com.kreative.keyedit.ConvertKeyboard { public static void main(java.lang.String[]); }
-keep class com.kreative.keyedit.DumpChars { public static void main(java.lang.String[]); }

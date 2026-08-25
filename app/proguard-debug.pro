# Instrumentation reaches debug harnesses and product seams from a separately compiled APK.
# Keep this code readable and addressable; R8 may only remove unused dependency internals.
-keep class de.thonktank.autosecretary.** { *; }
-keepattributes SourceFile,LineNumberTable

# AndroidX Test references these compile-time annotations without requiring them at runtime.
-dontwarn com.google.errorprone.annotations.CanIgnoreReturnValue
-dontwarn com.google.errorprone.annotations.MustBeClosed

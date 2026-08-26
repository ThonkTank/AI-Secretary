# Keep the compact debug product's app-owned code readable and addressable; R8 may only remove
# unused dependency internals. Instrumented tests use the separate unshrunk instrumentation type.
-keep class de.thonktank.autosecretary.** { *; }
-keepattributes SourceFile,LineNumberTable

# AndroidX Test references these compile-time annotations without requiring them at runtime.
-dontwarn com.google.errorprone.annotations.CanIgnoreReturnValue
-dontwarn com.google.errorprone.annotations.MustBeClosed

# Shrink and optimize the Compose product graph without making production diagnostics opaque.
-dontobfuscate
-keepattributes SourceFile,LineNumberTable

# External upgrade instrumentation shares the target application's class loader on every
# supported API. Keep the complete Kotlin standard-library ABI instead of exposing R8-pruned
# runtime classes that can shadow the test runtime's compatible implementation.
-keep class kotlin.** { *; }

# Shrink and optimize the Compose product graph without making production diagnostics opaque.
-dontobfuscate
-keepattributes SourceFile,LineNumberTable

# External upgrade instrumentation shares the target application's class loader on every
# supported API. Keep the Kotlin runtime ABI complete instead of exposing an R8-pruned Intrinsics
# class that can shadow the test runtime's compatible implementation.
-keep class kotlin.jvm.internal.Intrinsics { *; }

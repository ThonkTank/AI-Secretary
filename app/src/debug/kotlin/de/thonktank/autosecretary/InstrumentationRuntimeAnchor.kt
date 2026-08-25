package de.thonktank.autosecretary

/**
 * Makes the AndroidX Test runner's cross-APK Kotlin dependency visible to target-APK shrinking.
 *
 * AndroidJUnitRunner creates its TestDirCalculator before discovering tests. That class calls
 * Kotlin's single-argument lazy facade from the separately shrunk test APK, an edge R8 cannot see
 * while shrinking the target APK. Debug rules already retain this app-owned field, so this exact
 * matching call keeps the runner dependency without retaining the complete Kotlin facade graph.
 */
internal object InstrumentationRuntimeAnchor {
    @JvmField
    val runnerLazyFacade: Class<*> = Class.forName("kotlin.LazyKt")

    @JvmField
    val runnerLazyDependency: Lazy<Unit> = lazy { Unit }
}

import org.gradle.buildconfiguration.tasks.UpdateDaemonJvm

plugins {
    id("com.android.application") version "9.2.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.21" apply false
}

// Keep the generated daemon criteria path-independent. The Foojay resolver in settings supplies
// platform-specific download URLs, so a checkout without a system JDK 21 remains reproducible.
tasks.named<UpdateDaemonJvm>("updateDaemonJvm") {
    languageVersion.set(JavaLanguageVersion.of(21))
}

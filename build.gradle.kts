plugins {
    id("com.android.application") version "8.7.3" apply false
    id("com.android.library") version "8.7.3" apply false
}

val checkClockBoundary = tasks.register("checkClockBoundary") {
    group = "verification"
    description = "Rejects direct wall-clock and default-zone reads outside SystemTimeProvider."
    doLast {
        val forbidden = listOf(
            "LocalDate.now(", "LocalDateTime.now(", "Instant.now(",
            "ZonedDateTime.now(", "OffsetDateTime.now(", "ZoneId.systemDefault("
        )
        val offenders = fileTree(rootDir) {
            include("core/src/main/java/**/*.java", "infrastructure/src/main/java/**/*.java",
                "presentation/src/main/java/**/*.java", "app/src/main/java/**/*.java")
            exclude("**/SystemTimeProvider.java")
        }.flatMap { source ->
            source.readLines().mapIndexedNotNull { index, line ->
                forbidden.firstOrNull(line::contains)?.let {
                    "${source.relativeTo(rootDir)}:${index + 1}: $it"
                }
            }
        }
        check(offenders.isEmpty()) {
            "Production time must enter through TimeProvider:\n${offenders.joinToString("\n")}"
        }
    }
}

tasks.register("checkArchitecture") {
    group = "verification"
    description = "Runs behavior tests and enforced architecture-boundary rules."
    dependsOn(checkClockBoundary, ":core:test", ":infrastructure:testDebugUnitTest",
        ":presentation:testDebugUnitTest", ":app:testDebugUnitTest")
}

tasks.register("qualityGate") {
    group = "verification"
    description = "Runs the complete local verification gate without publishing."
    dependsOn("checkArchitecture", ":infrastructure:lintDebug", ":presentation:lintDebug",
        ":app:lintDebug", ":app:lintRelease", ":app:assembleDebugAndroidTest",
        ":app:assembleRelease")
}

tasks.register<Copy>("stagePhoneRelease") {
    group = "distribution"
    description = "Stages the signed release APK under its stable public asset name."
    dependsOn(":app:assembleRelease")
    from(layout.projectDirectory.file("app/build/outputs/apk/release/app-release.apk"))
    into(layout.buildDirectory.dir("phone-release"))
    rename { "AutoSecretary.apk" }
    doLast {
        val apk = layout.buildDirectory.file("phone-release/AutoSecretary.apk").get().asFile
        check(apk.isFile && apk.length() > 0) {
            "Signed release APK was not staged; production signing is probably missing"
        }
    }
}

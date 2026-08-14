plugins {
    id("autosecretary.release")
    id("com.android.application") version "8.7.3" apply false
    id("com.android.library") version "8.7.3" apply false
}

val checkRoomSchemaBaseline = tasks.register("checkRoomSchemaBaseline") {
    group = "verification"
    description = "Keeps the immutable Room v35 baseline stable; later schemas require migrations."
    doLast {
        val compatibility = java.util.Properties().apply {
            file("release/compatibility.properties").inputStream().use(::load)
        }
        val version = compatibility.getProperty("roomBaselineSchema")
        val schema = file("schemas/com.autosecretary.data.FocusDatabase/$version.json")
        check(schema.isFile) { "Room baseline schema $version is missing" }
        val actual = java.security.MessageDigest.getInstance("SHA-256")
            .digest(schema.readBytes()).joinToString("") { "%02x".format(it) }
        check(actual == compatibility.getProperty("roomBaselineSchemaSha256")) {
            "Room v$version changed without a new schema version and explicit migration"
        }
    }
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
    dependsOn(checkClockBoundary, checkRoomSchemaBaseline,
        ":core:test", ":infrastructure:testDebugUnitTest",
        ":presentation:testDebugUnitTest", ":app:testDebugUnitTest")
}

tasks.register("qualityGate") {
    group = "verification"
    description = "Runs the complete local verification gate without publishing."
    dependsOn("checkArchitecture", ":infrastructure:lintDebug", ":presentation:lintDebug",
        ":app:lintDebug", ":app:lintRelease", ":app:assembleDebugAndroidTest",
        ":app:assembleRelease")
}

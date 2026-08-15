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
            , "System.currentTimeMillis("
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

val checkReleaseWorkflowContract = tasks.register("checkReleaseWorkflowContract") {
    group = "verification"
    description = "Guards the single, idempotent main-to-Latest release path."
    doLast {
        val workflows = fileTree(".github/workflows") { include("*.yml", "*.yaml") }.files
        val publishers = workflows.filter { it.readText().contains("gh release create") }
        check(publishers.map { it.name } == listOf("android-release.yml")) {
            "Exactly android-release.yml must be able to create GitHub releases: $publishers"
        }
        val workflow = publishers.single().readText()
        fun requireText(value: String, detail: String) = check(workflow.contains(value)) { detail }
        requireText("branches: [main]", "Phone releases must be triggered only from main")
        check(!workflow.contains("workflow_dispatch:")) {
            "The publishing workflow must not expose a manual release path"
        }
        listOf("README", "docs/**", "AGENTS.md").forEach { documentationPath ->
            check(!workflow.contains("- \"$documentationPath\"")) {
                "Documentation-only path $documentationPath must not publish an app release"
            }
        }
        requireText("group: android-phone-update", "Release version reservation must be serialized")
        requireText("cancel-in-progress: false", "A newer main push must not cancel a release")
        requireText("gh api --paginate --slurp", "Version selection must read every release page")
        requireText("DRAFT_TAG", "Retries must discover the draft for the same commit")
        requireText("PUBLISHED_TAG", "A published commit must be an idempotent no-op")
        requireText("--clobber", "A retry must safely replace incomplete draft assets")
        requireText("--draft=false --latest", "Only the verified draft may become Latest")
        requireText("published-release-proof", "Published assets must be downloaded and reverified")
        val stage = workflow.indexOf("Stage or resume the draft release")
        val stagedProof = workflow.indexOf("Verify the staged assets")
        val publish = workflow.indexOf("--draft=false --latest")
        val liveProof = workflow.indexOf("published-release-proof")
        check(stage >= 0 && stage < stagedProof && stagedProof < publish && publish < liveProof) {
            "Release transaction must stage, verify, publish and verify the live assets in order"
        }
        val uses = workflow.lineSequence().map(String::trim)
            .filter { it.startsWith("- uses:") || it.startsWith("uses:") }.toList()
        check(uses.isNotEmpty() && uses.all { it.matches(
            Regex("(?:- )?uses: [^@\\s]+@[0-9a-f]{40}(?:\\s+#.*)?")
        ) }) { "Every publishing action must be pinned to a full commit SHA: $uses" }
        val dependabot = file(".github/dependabot.yml").readText()
        check(dependabot.contains("package-ecosystem: github-actions")) {
            "Dependabot must maintain the pinned GitHub Action revisions"
        }
    }
}

tasks.register("checkArchitecture") {
    group = "verification"
    description = "Runs behavior tests and enforced architecture-boundary rules."
    dependsOn(checkClockBoundary, checkRoomSchemaBaseline, checkReleaseWorkflowContract,
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

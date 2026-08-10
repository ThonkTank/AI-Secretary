import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

plugins {
    id("com.android.application") version "8.7.3"
}

val versionFile = file("ops/release/version.txt")
val currentVersionCode = versionFile.readText().trim().toIntOrNull() ?: 0
val nextVersionCode = currentVersionCode + 1
val ciVersionCode = providers.gradleProperty("ciVersionCode").orNull?.toIntOrNull()
val previewApplicationId = providers.gradleProperty("previewApplicationId").orNull
val bundledModelSha256 = "0f7147f1c22eaf758b819bbf7841793e4c90096c9352cde7fbe5c631f2265ef5"
val bundledModelUrl = providers.gradleProperty("bundledModelUrl").orElse(
    "https://huggingface.co/72fstudio/gemma-3-270m-it/resolve/main/gemma3-270m-it-q8.task?download=true"
)
val bundledAssetsDirectory = layout.buildDirectory.dir("bundled-ai/assets")
val bundledModelFile = bundledAssetsDirectory.map {
    it.file("models/autosecretary-gemma3-270m-it-q8.task")
}

fun sha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().buffered().use { input ->
        val buffer = ByteArray(1024 * 1024)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

val prepareBundledModel = tasks.register("prepareBundledModel") {
    group = "build setup"
    description = "Downloads and verifies the local Gemma model embedded in the APK."
    inputs.property("modelUrl", bundledModelUrl)
    inputs.property("modelSha256", bundledModelSha256)
    outputs.file(bundledModelFile)
    doLast {
        val target = bundledModelFile.get().asFile
        if (target.isFile && sha256(target) == bundledModelSha256) return@doLast

        target.parentFile.mkdirs()
        val temporary = File(target.parentFile, target.name + ".partial")
        temporary.delete()
        try {
            URI(bundledModelUrl.get()).toURL().openStream().buffered().use { input ->
                temporary.outputStream().buffered().use { output -> input.copyTo(output) }
            }
            val actual = sha256(temporary)
            check(actual == bundledModelSha256) {
                "Bundled model checksum mismatch: expected $bundledModelSha256, got $actual"
            }
            Files.move(
                temporary.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE
            )
        } finally {
            temporary.delete()
        }
    }
}

android {
    namespace = "com.autosecretary"
    compileSdk = 35

    defaultConfig {
        applicationId = previewApplicationId ?: "com.autosecretary"
        minSdk = 26
        targetSdk = 35
        versionCode = ciVersionCode ?: nextVersionCode
        versionName = if (ciVersionCode == null) "2.0.0" else "2.0.0-preview.$ciVersionCode"
        manifestPlaceholders["appLabel"] = if (previewApplicationId == null) {
            "Auto Secretary"
        } else {
            "Auto Secretary Preview"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    androidResources {
        noCompress += "task"
    }

    sourceSets.getByName("main").assets.srcDir(bundledAssetsDirectory)

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

tasks.configureEach {
    if (name.startsWith("merge") && name.endsWith("Assets")) {
        dependsOn(prepareBundledModel)
    }
}

dependencies {
    implementation("androidx.core:core:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("com.google.android.material:material:1.12.0")

    // Local model execution. Model input and output never leave the device.
    implementation("com.google.mediapipe:tasks-genai:0.10.27")

    testImplementation("junit:junit:4.13.2")
}

tasks.register("checkArchitecture") {
    group = "verification"
    description = "Runs the small core's behavior and dependency checks."
    dependsOn(tasks.named("testDebugUnitTest"))
}

tasks.named("check").configure {
    dependsOn("checkArchitecture")
}

android.applicationVariants.all {
    if (buildType.name != "debug") return@all
    val variant = this
    outputs.all {
        val output = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
        output.outputFileName = "AutoSecretary.apk"

        val copyTask = tasks.register("copyToRelease", Copy::class) {
            dependsOn(variant.packageApplicationProvider)
            from(outputFile)
            into(layout.projectDirectory.dir("ops/release"))
            doLast { versionFile.writeText(nextVersionCode.toString()) }
        }

        tasks.register("publishGitHubRelease", Exec::class) {
            dependsOn(copyTask)
            workingDir = layout.projectDirectory.asFile
            commandLine(
                "gh", "release", "create", "build-$nextVersionCode",
                "ops/release/AutoSecretary.apk", "ops/release/version.txt",
                "--title", "AutoSecretary Build $nextVersionCode",
                "--notes", "AutoSecretary focus-core build $nextVersionCode",
                "--latest"
            )
        }

        tasks.register("publishReleaseArtifact") {
            group = "release"
            description = "Builds the APK and publishes it through the existing GitHub release path."
            dependsOn("publishGitHubRelease")
        }
    }
}

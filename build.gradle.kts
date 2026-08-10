plugins {
    id("com.android.application") version "8.7.3"
}

val versionFile = file("ops/release/version.txt")
val currentVersionCode = versionFile.readText().trim().toIntOrNull() ?: 0
val nextVersionCode = currentVersionCode + 1

android {
    namespace = "com.autosecretary"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.autosecretary"
        minSdk = 26
        targetSdk = 35
        versionCode = nextVersionCode
        versionName = "2.0.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
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

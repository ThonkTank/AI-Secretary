plugins {
    id("com.android.application") version "8.7.3"
}

// Versionsnummer aus release/version.txt lesen und inkrementieren
val versionFile = file("release/version.txt")
val currentVersion = if (versionFile.exists()) versionFile.readText().trim().toIntOrNull() ?: 0 else 0
val nextVersion = currentVersion + 1

android {
    namespace = "com.autosecretary"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.autosecretary"
        minSdk = 26
        targetSdk = 35
        versionCode = nextVersion
        versionName = "1.0.$nextVersion"
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    sourceSets {
        getByName("main") {
            java.srcDirs("src")
            res.srcDirs("res")
            manifest.srcFile("AndroidManifest.xml")
        }
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.4")
    implementation("androidx.core:core:1.12.0")
}

// APK-Dateiname und automatisches Kopieren/Pushen
android.applicationVariants.all {
    outputs.all {
        val output = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
        output.outputFileName = "AutoSecretary.apk"
        val copyTask = tasks.register("copy${name.replaceFirstChar { it.uppercase() }}ToRelease", Copy::class) {
            from(outputFile)
            into(layout.projectDirectory.dir("release"))
            doLast {
                versionFile.writeText(nextVersion.toString())
            }
        }
        val pushTask = tasks.register("push${name.replaceFirstChar { it.uppercase() }}ToGitHub", Exec::class) {
            workingDir = layout.projectDirectory.asFile
            commandLine("bash", "-c", """
                git add release/ &&
                git commit -m "build: APK aktualisiert" --allow-empty &&
                git push
            """.trimIndent())
        }
        tasks.named("assemble${name.replaceFirstChar { it.uppercase() }}") {
            finalizedBy(copyTask)
        }
        copyTask.configure {
            finalizedBy(pushTask)
        }
    }
}

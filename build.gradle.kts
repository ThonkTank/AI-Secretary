plugins {
    id("com.android.application") version "8.7.3"
}

// Versionsnummer aus release/version.txt lesen und inkrementieren
val versionFile = file("release/version.txt")
val currentVersionCode = if (versionFile.exists()) versionFile.readText().trim().toIntOrNull() ?: 0 else 0
val nextVersionCode = currentVersionCode + 1

// Semantische Versionierung (manuell aktualisieren bei neuen Releases)
val versionMajor = 1
val versionMinor = 0
val versionPatch = 0

android {
    namespace = "com.autosecretary"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.autosecretary"
        minSdk = 26
        targetSdk = 35
        versionCode = nextVersionCode
        versionName = "$versionMajor.$versionMinor.$versionPatch"
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
        getByName("test") {
            java.srcDirs("test")
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all { it.testLogging { showStandardStreams = true } }
        }
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.4")
    implementation("androidx.core:core:1.12.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core:1.6.1")
}

// APK-Dateiname und automatisches Kopieren/Pushen
android.applicationVariants.all {
    if (buildType.name != "debug") return@all
    outputs.all {
        val output = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
        output.outputFileName = "AutoSecretary.apk"
        val copyTask = tasks.register("copyToRelease", Copy::class) {
            from(outputFile)
            into(layout.projectDirectory.dir("release"))
            doLast {
                versionFile.writeText(nextVersionCode.toString())
            }
        }
        val pushTask = tasks.register("pushToGitHub", Exec::class) {
            workingDir = layout.projectDirectory.asFile
            commandLine("bash", "-c", """
                git add release/ &&
                git commit -m "build: APK aktualisiert" --allow-empty &&
                git push
            """.trimIndent())
        }
        tasks.named("assemble") {
            finalizedBy(copyTask)
        }
        copyTask.configure {
            finalizedBy(pushTask)
        }
    }
}

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

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all { it.testLogging { showStandardStreams = true } }
        }
    }

    sourceSets {
        getByName("main").java.setSrcDirs(listOf("src/main/java"))
        getByName("test").java.setSrcDirs(listOf("src/test/java"))
        getByName("androidTest").java.setSrcDirs(listOf("src/androidTest/java"))
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.4")
    implementation("androidx.core:core:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel:2.10.0")
    implementation("androidx.lifecycle:lifecycle-livedata:2.10.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("com.google.android.material:material:1.12.0")

    annotationProcessor("androidx.room:room-compiler:2.6.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core:1.6.1")
}

// APK-Dateiname für Debug-Builds, Artifact-Tasks bleiben explizit
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

        tasks.register("pushToGitHub", Exec::class) {
            workingDir = layout.projectDirectory.asFile
            commandLine("bash", "-c", """
                git add release/ &&
                git commit -m "build: APK aktualisiert" --allow-empty &&
                git push
            """.trimIndent())
        }

        tasks.register("publishReleaseArtifact") {
            group = "release"
            description = "Kopiert das APK ins release-Verzeichnis und pusht die Änderungen nach GitHub."
            dependsOn(copyTask)
            dependsOn("pushToGitHub")
        }
    }
}

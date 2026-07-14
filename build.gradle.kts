plugins {
    id("com.android.application") version "8.7.3"
}

fun extractLauncherPathData(file: File): List<String> {
    val pathRegex = Regex("""android:pathData="([^"]+)"""")
    return pathRegex.findAll(file.readText()).map { it.groupValues[1] }.toList()
}

fun extractWidgetUpdatePeriod(file: File): Long {
    val match = Regex("""android:updatePeriodMillis="(\d+)"""").find(file.readText())
        ?: throw GradleException("Widget validation failed: missing android:updatePeriodMillis in ${file.path}.")
    return match.groupValues[1].toLong()
}

fun extractWidgetUpdatePeriodConstant(file: File): Long {
    val match = Regex("""WIDGET_UPDATE_PERIOD_MILLIS\s*=\s*(\d+)L\s*;""").find(file.readText())
        ?: throw GradleException("Widget validation failed: missing WIDGET_UPDATE_PERIOD_MILLIS in ${file.path}.")
    return match.groupValues[1].toLong()
}

// Versionsnummer aus ops/release/version.txt lesen und inkrementieren
val versionFile = file("ops/release/version.txt")
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

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    sourceSets {
        getByName("main") {
            java.setSrcDirs(listOf("src/main/java"))
            res.setSrcDirs(listOf(
                "src/main/res",
                "src/main/res-task",
                "src/main/res-budget",
                "src/main/res-meal"
            ))
        }
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
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("androidx.arch.core:core-testing:2.2.0")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("com.tngtech.archunit:archunit:1.4.2")
    testAnnotationProcessor("androidx.room:room-compiler:2.6.1")
}

// The architecture rules now live in src/test as ArchUnit rules on real bytecode
// (com.autosecretary.architecture.ArchitectureRulesTest). This task is a stable alias
// so the documented `./gradlew checkArchitecture` command keeps working; it runs the
// unit-test suite, which includes the architecture rules.
val checkArchitecture = tasks.register("checkArchitecture") {
    group = "verification"
    description = "Runs the ArchUnit architecture rules (ArchitectureRulesTest) via the unit-test suite."
    dependsOn(tasks.named("testDebugUnitTest"))
}

val validateLauncherIconPaths = tasks.register("validateLauncherIconPaths") {
    group = "verification"
    description = "Verifies that launcher foreground and monochrome icons use identical pathData."

    val foregroundFile = layout.projectDirectory.file("src/main/res/drawable/ic_launcher_foreground.xml")
    val monochromeFile = layout.projectDirectory.file("src/main/res/drawable/ic_launcher_monochrome.xml")

    inputs.files(foregroundFile, monochromeFile)

    doLast {
        val foregroundPaths = extractLauncherPathData(foregroundFile.asFile)
        val monochromePaths = extractLauncherPathData(monochromeFile.asFile)

        if (foregroundPaths.isEmpty() || monochromePaths.isEmpty()) {
            throw GradleException("Launcher icon validation failed: missing android:pathData entries.")
        }
        if (foregroundPaths != monochromePaths) {
            throw GradleException(
                "Launcher icon validation failed: ic_launcher_foreground.xml and " +
                    "ic_launcher_monochrome.xml must keep identical pathData entries in the same order."
            )
        }
    }
}

val validateWidgetUpdatePeriods = tasks.register("validateWidgetUpdatePeriods") {
    group = "verification"
    description = "Verifies that widget XML update periods match WidgetConfiguration.WIDGET_UPDATE_PERIOD_MILLIS."

    val widgetConfigurationFile = layout.projectDirectory.file(
        "src/main/java/com/autosecretary/shared/WidgetConfiguration.java"
    )
    val widgetTaskInfoFile = layout.projectDirectory.file("src/main/res-task/xml/widget_task_info.xml")
    val widgetBudgetInfoFile = layout.projectDirectory.file("src/main/res-budget/xml/widget_budget_info.xml")

    inputs.files(widgetConfigurationFile, widgetTaskInfoFile, widgetBudgetInfoFile)

    doLast {
        val expectedPeriod = extractWidgetUpdatePeriodConstant(widgetConfigurationFile.asFile)
        val widgetFiles = listOf(widgetTaskInfoFile.asFile, widgetBudgetInfoFile.asFile)

        widgetFiles.forEach { widgetFile ->
            val actualPeriod = extractWidgetUpdatePeriod(widgetFile)
            if (actualPeriod != expectedPeriod) {
                throw GradleException(
                    "Widget validation failed: ${widgetFile.path} declares android:updatePeriodMillis=$actualPeriod " +
                        "but WidgetConfiguration.WIDGET_UPDATE_PERIOD_MILLIS is $expectedPeriod."
                )
            }
        }
    }
}

tasks.named("preBuild").configure {
    dependsOn(validateLauncherIconPaths)
    dependsOn(validateWidgetUpdatePeriods)
}

tasks.named("check").configure {
    dependsOn(checkArchitecture)
}

// APK-Dateiname für Debug-Builds, Artifact-Tasks bleiben explizit
android.applicationVariants.all {
    if (buildType.name != "debug") return@all
    outputs.all {
        val output = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
        output.outputFileName = "AutoSecretary.apk"

        val copyTask = tasks.register("copyToRelease", Copy::class) {
            from(outputFile)
            into(layout.projectDirectory.dir("ops/release"))
            doLast {
                versionFile.writeText(nextVersionCode.toString())
            }
        }

        tasks.register("publishGitHubRelease", Exec::class) {
            workingDir = layout.projectDirectory.asFile
            dependsOn(copyTask)
            commandLine(
                "gh",
                "release",
                "create",
                "build-$nextVersionCode",
                "ops/release/AutoSecretary.apk",
                "ops/release/version.txt",
                "--title",
                "AutoSecretary Build $nextVersionCode",
                "--notes",
                "AutoSecretary build $nextVersionCode",
                "--latest"
            )
        }

        tasks.register("publishReleaseArtifact") {
            group = "release"
            description = "Kopiert das APK ins ops/release-Verzeichnis und publiziert es als GitHub Release."
            dependsOn("publishGitHubRelease")
        }
    }
}

import java.nio.file.Files
plugins {
    id("com.android.application") version "8.7.3"
}

data class ArchitectureSource(
    val file: File,
    val relativePath: String,
    val segments: List<String>,
    val fileName: String,
    val content: String,
    val packageName: String
) {
    val isProductionJava: Boolean = relativePath.startsWith("src/main/java/")
    val isFeatureJava: Boolean = relativePath.startsWith("src/main/java/com/autosecretary/features/")
    val featureName: String = if (isFeatureJava && segments.size >= 7) segments[6] else ""
}

data class ArchitectureViolation(val source: String, val rule: String, val details: String)

data class MethodRange(val name: String, val startLine: Int, val endLine: Int) {
    fun contains(lineNumber: Int): Boolean = lineNumber in startLine..endLine
}

fun architectureSources(root: File): List<ArchitectureSource> {
    val sourceRoot = root.toPath().resolve("src/main/java")
    if (!Files.isDirectory(sourceRoot)) {
        return emptyList()
    }
    return Files.walk(sourceRoot).use { stream ->
        stream.filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".java") }
            .map { path ->
                val normalized = path.toAbsolutePath().normalize()
                val relativePath = root.toPath().toAbsolutePath().normalize()
                    .relativize(normalized)
                    .toString()
                    .replace(File.separatorChar, '/')
                val content = Files.readString(path)
                ArchitectureSource(
                    file = path.toFile(),
                    relativePath = relativePath,
                    segments = relativePath.split("/"),
                    fileName = path.fileName.toString(),
                    content = content,
                    packageName = Regex("""(?m)^\s*package\s+([A-Za-z_][\w.]*);""")
                        .find(content)
                        ?.groupValues
                        ?.get(1)
                        .orEmpty()
                )
            }
            .sorted(Comparator.comparing(ArchitectureSource::relativePath))
            .toList()
    }
}

fun architectureImportsOf(source: ArchitectureSource): List<String> =
    Regex("""(?m)^\s*import\s+([^;]+);""")
        .findAll(source.content)
        .map { it.groupValues[1] }
        .toList()

fun architectureExpectedPackage(source: ArchitectureSource): String {
    val expected = source.relativePath
        .removePrefix("src/main/java/")
        .removeSuffix(".java")
        .replace('/', '.')
    val lastDot = expected.lastIndexOf('.')
    return if (lastDot < 0) "" else expected.substring(0, lastDot)
}

fun architectureIsUiHost(source: ArchitectureSource): Boolean =
    source.fileName.endsWith("Fragment.java")
        || source.fileName.endsWith("Activity.java")
        || source.fileName.endsWith("Dialog.java")

fun architectureIsFragmentHost(source: ArchitectureSource): Boolean =
    architectureIsUiHost(source)
        && (source.content.contains("extends Fragment") || source.content.contains("extends DialogFragment"))

fun architectureIsWidgetMechanics(source: ArchitectureSource): Boolean {
    val imports = architectureImportsOf(source)
    return imports.any {
        it == "android.appwidget.AppWidgetProvider"
            || it == "android.widget.RemoteViews"
            || it == "android.widget.RemoteViewsService"
    } || source.content.contains("RemoteViews")
}

fun architectureEndsWithForbiddenOwner(importName: String): Boolean =
    listOf("Repository", "Dao", "ApiClient").any(importName::endsWith)

fun architectureIsApplicationOwnerImport(importName: String): Boolean =
    importName.contains(".application.")
        && (importName.endsWith("UseCase")
            || importName.endsWith("Service")
            || importName.endsWith("Presenter")
            || importName.contains(".application.internal."))

fun architectureIsPassiveUiHelper(source: ArchitectureSource): Boolean {
    if (!source.isFeatureJava
        || architectureIsUiHost(source)
        || source.fileName.endsWith("ViewModel.java")
        || source.fileName.endsWith("ViewModelFactory.java")
        || architectureIsWidgetMechanics(source)
    ) {
        return false
    }
    return source.fileName.endsWith("Adapter.java")
        || source.fileName.endsWith("Binder.java")
        || source.fileName.endsWith("Controller.java")
        || source.fileName.endsWith("Mapper.java")
        || (source.fileName.endsWith("View.java") && !source.fileName.endsWith("ViewModel.java"))
}

fun architectureStripLineComment(line: String): String {
    val commentIndex = line.indexOf("//")
    return if (commentIndex >= 0) line.substring(0, commentIndex) else line
}

fun architectureDetectMethodName(line: String): String? {
    val methodPattern = Regex(
        """^\s*(?:public|protected|private)?(?:\s+static)?(?:\s+final)?(?:\s+abstract)?(?:\s+synchronized)?(?:\s+native)?(?:\s+<[^>]+>\s+)?[A-Za-z0-9_<>,\[\]?@. ]+\s+([A-Za-z_]\w*)\s*\([^;]*\)\s*(?:throws\s+[^{]+)?\{\s*$"""
    )
    val methodName = methodPattern.find(line.trim())?.groupValues?.get(1) ?: return null
    return if (setOf("if", "for", "while", "switch", "catch", "return", "new", "throw").contains(methodName)) {
        null
    } else {
        methodName
    }
}

fun architectureFindMethodRanges(content: String): List<MethodRange> {
    val ranges = mutableListOf<MethodRange>()
    var braceDepth = 0
    var currentMethodName: String? = null
    var currentMethodStartLine = -1
    var currentMethodDepth = -1

    content.lineSequence().forEachIndexed { index, rawLine ->
        val line = architectureStripLineComment(rawLine)
        val braceDepthBefore = braceDepth
        if (currentMethodName == null && braceDepthBefore == 1) {
            val detected = architectureDetectMethodName(line)
            if (detected != null) {
                currentMethodName = detected
                currentMethodStartLine = index + 1
            }
        }
        val opens = line.count { it == '{' }
        val closes = line.count { it == '}' }
        if (currentMethodName != null && currentMethodDepth < 0 && opens > 0) {
            currentMethodDepth = braceDepthBefore + 1
        }
        braceDepth = braceDepthBefore + opens - closes
        if (currentMethodName != null && currentMethodDepth >= 0 && braceDepth < currentMethodDepth) {
            ranges.add(MethodRange(currentMethodName!!, currentMethodStartLine, index + 1))
            currentMethodName = null
            currentMethodStartLine = -1
            currentMethodDepth = -1
        }
    }
    return ranges
}

fun architectureFindRegistrationLines(content: String): List<Int> =
    content.lineSequence()
        .mapIndexedNotNull { index, line ->
            if (Regex("""registerForActivityResult\s*\(""").containsMatchIn(architectureStripLineComment(line))) {
                index + 1
            } else {
                null
            }
        }
        .toList()

fun architectureValidateBuildFileReleaseTasks(root: File, violations: MutableList<ArchitectureViolation>) {
    val buildFile = root.resolve("build.gradle.kts")
    if (!buildFile.isFile) {
        return
    }
    val compact = buildFile.readText().replace(Regex("""\s+"""), "")
    val releaseTaskNames = linkedSetOf("copyToRelease", "publishReleaseArtifact", "pushToGitHub")
    Regex("""\bval\s+([A-Za-z_][A-Za-z0-9_]*)\s*=\s*tasks\.register(?:<[^>]+>)?\("([^"]+)"""")
        .findAll(compact)
        .forEach {
            if (releaseTaskNames.contains(it.groupValues[2])) {
                releaseTaskNames.add(it.groupValues[1])
            }
        }

    fun firstForbiddenReference(text: String): String? =
        releaseTaskNames.firstOrNull { Regex("""(^|[^A-Za-z0-9_])${Regex.escape(it)}([^A-Za-z0-9_]|$)""").containsMatchIn(text) }

    val blockingTasks = setOf("preBuild", "assembleDebug", "check")
    val reported = mutableSetOf<String>()
    fun report(blockingTask: String, reference: String) {
        if (reported.add("$blockingTask|$reference")) {
            violations.add(ArchitectureViolation(
                "build.gradle.kts",
                "release-tasks-explicit-only",
                "Blocking task '$blockingTask' must not depend on release side-effect task reference '$reference'."
            ))
        }
    }

    Regex("""tasks\.(?:named|getByName)\("([^"]+)"\)(?:\.configure(?:Each)?)?\{([^}]*)\}""")
        .findAll(compact)
        .forEach {
            val blockingTask = it.groupValues[1]
            if (blockingTasks.contains(blockingTask)) {
                firstForbiddenReference(it.groupValues[2])?.let { reference -> report(blockingTask, reference) }
            }
        }
    Regex("""tasks\.(?:named|getByName)\("([^"]+)"\)(?:\.configure(?:Each)?)?\.dependsOn\(([^)]*)\)""")
        .findAll(compact)
        .forEach {
            val blockingTask = it.groupValues[1]
            if (blockingTasks.contains(blockingTask)) {
                firstForbiddenReference(it.groupValues[2])?.let { reference -> report(blockingTask, reference) }
            }
        }
    Regex("""\b(preBuild|assembleDebug|check)\.dependsOn\(([^)]*)\)""")
        .findAll(compact)
        .forEach {
            firstForbiddenReference(it.groupValues[2])?.let { reference -> report(it.groupValues[1], reference) }
        }
}

fun architectureViolations(root: File): List<ArchitectureViolation> {
    val violations = mutableListOf<ArchitectureViolation>()

    val sources = architectureSources(root)
    val observeCallPattern = Regex("""\.observe\s*\(\s*([^,\n\r]+)\s*,""")
    val viewModelRenderingPattern = Regex(
        """\bnew\s+(View|TextView|Button|ImageButton|LinearLayout|RecyclerView|RemoteViews|Dialog|AlertDialog)\s*\(|\.inflate\s*\(|\.findViewById\s*\(|\bToast\.makeText\s*\(|\bandroid\.widget\.RemoteViews\b|\bandroid\.view\.LayoutInflater\b"""
    )
    val viewModelRetainedImports = setOf(
        "android.app.Application",
        "android.app.Activity",
        "android.app.Dialog",
        "android.app.AlertDialog",
        "android.content.Context",
        "android.view.View",
        "android.widget.RemoteViews",
        "androidx.fragment.app.Fragment",
        "androidx.lifecycle.AndroidViewModel"
    )
    val viewModelForbiddenRenderingPrefixes = listOf(
        "android.view.",
        "android.widget.",
        "android.app.Dialog",
        "android.app.AlertDialog",
        "androidx.recyclerview.",
        "com.google.android.material."
    )
    val viewModelInfrastructurePrefixes = listOf(
        "com.autosecretary.database.",
        "android.database.",
        "java.io.",
        "java.nio.file.",
        "java.sql.",
        "java.lang.reflect."
    )
    val viewModelInfrastructureImports = setOf(
        "android.content.ContentResolver",
        "java.lang.ClassLoader",
        "java.lang.Process",
        "java.lang.ProcessBuilder"
    )
    val passiveHelperForbiddenStateImports = setOf(
        "android.os.Handler",
        "androidx.activity.result.ActivityResultLauncher",
        "androidx.lifecycle.LiveData",
        "androidx.lifecycle.MutableLiveData",
        "java.util.concurrent.Executor",
        "java.util.concurrent.ExecutorService"
    )
    val mainThreadIoImports = setOf(
        "android.content.ContentResolver",
        "android.database.Cursor",
        "java.io.File",
        "java.io.InputStream",
        "java.io.OutputStream",
        "java.nio.file.Files",
        "java.nio.file.Path"
    )
    val mainThreadIoCallPatterns = listOf(
        Regex("""\.openInputStream\s*\("""),
        Regex("""\.readAllBytes\s*\("""),
        Regex("""\bFiles\.(?:readAllBytes|readString|readAllLines|newInputStream|walk)\s*\(""")
    )

    for (source in sources) {
        if (source.isProductionJava && source.packageName.isNotBlank()) {
            val expectedPackage = architectureExpectedPackage(source)
            if (source.packageName != expectedPackage) {
                violations.add(ArchitectureViolation(
                    source.relativePath,
                    "package-path-alignment",
                    "Declared package '${source.packageName}' must match source path package '$expectedPackage'."
                ))
            }
        }

        val imports = architectureImportsOf(source)
        if (source.isFeatureJava && source.segments.contains("domain")) {
            for (importName in imports) {
                val sameFeatureDataPrefix = "com.autosecretary.features.${source.featureName}.data."
                val sameFeatureUiPrefix = "com.autosecretary.features.${source.featureName}.ui."
                val forbidden = importName.startsWith("android.")
                    || importName.startsWith("androidx.")
                    || importName.startsWith("com.autosecretary.app.")
                    || importName.startsWith("com.autosecretary.database.")
                    || importName.startsWith(sameFeatureUiPrefix)
                    || importName.contains(".ui.")
                    || (importName.startsWith("com.autosecretary.features.")
                        && importName.contains(".data.")
                        && !importName.startsWith(sameFeatureDataPrefix))
                if (forbidden) {
                    violations.add(ArchitectureViolation(
                        source.relativePath,
                        "domain-no-outer-boundary-imports",
                        "Domain code must not import Android, app, database, UI, or foreign data owners: $importName"
                    ))
                }
            }
        }

        if (source.relativePath.contains("/features/") && source.relativePath.contains("/ui/") && architectureIsUiHost(source)) {
            for (importName in imports) {
                val sameFeaturePrefix = "com.autosecretary.features.${source.featureName}."
                val forbidden = (source.featureName.isNotBlank()
                    && importName.startsWith(sameFeaturePrefix)
                    && (importName.contains(".application.")
                        || importName.contains(".data.")
                        || architectureEndsWithForbiddenOwner(importName)))
                    || importName.startsWith("com.autosecretary.database.")
                    || architectureEndsWithForbiddenOwner(importName)
                if (forbidden) {
                    violations.add(ArchitectureViolation(
                        source.relativePath,
                        "ui-host-direct-dependency-boundary",
                        "UI hosts must not directly import application/data/database/repository/DAO/API-client owners: $importName"
                    ))
                }
            }
        }

        if (architectureIsFragmentHost(source)) {
            observeCallPattern.findAll(source.content).forEach {
                val lifecycleOwner = it.groupValues[1].trim()
                if (lifecycleOwner != "getViewLifecycleOwner()") {
                    violations.add(ArchitectureViolation(
                        source.relativePath,
                        "fragment-viewmodel-observation-lifecycle",
                        "Fragment and DialogFragment observe(...) calls must use getViewLifecycleOwner(): $lifecycleOwner"
                    ))
                }
            }
        }

        if (source.fileName.endsWith("ViewModel.java")) {
            for (importName in imports) {
                if (viewModelForbiddenRenderingPrefixes.any(importName::startsWith)) {
                    violations.add(ArchitectureViolation(
                        source.relativePath,
                        "viewmodel-no-view-construction",
                        "ViewModels must not import Android view/rendering types: $importName"
                    ))
                }
                if (viewModelRetainedImports.contains(importName)) {
                    violations.add(ArchitectureViolation(
                        source.relativePath,
                        "viewmodel-no-retained-android-objects",
                        "ViewModels must not retain Android host/rendering object types: $importName"
                    ))
                }
                if (viewModelInfrastructurePrefixes.any(importName::startsWith)
                    || viewModelInfrastructureImports.contains(importName)
                ) {
                    violations.add(ArchitectureViolation(
                        source.relativePath,
                        "viewmodel-no-infrastructure-imports",
                        "ViewModels must not import infrastructure-owning types: $importName"
                    ))
                }
            }
            if (viewModelRenderingPattern.containsMatchIn(source.content)) {
                violations.add(ArchitectureViolation(
                    source.relativePath,
                    "viewmodel-no-view-construction",
                    "ViewModels must not construct Android views, inflate layouts, show dialogs, bind RecyclerViews, or create RemoteViews."
                ))
            }
            if (Regex("""\bextends\s+AndroidViewModel\b""").containsMatchIn(source.content)) {
                violations.add(ArchitectureViolation(
                    source.relativePath,
                    "viewmodel-no-retained-android-objects",
                    "ViewModels must not extend AndroidViewModel."
                ))
            }
        }

        if (architectureIsPassiveUiHelper(source)) {
            for (importName in imports) {
                if (importName.startsWith("com.autosecretary.database.")
                    || architectureIsApplicationOwnerImport(importName)
                    || architectureEndsWithForbiddenOwner(importName)
                ) {
                    violations.add(ArchitectureViolation(
                        source.relativePath,
                        "passive-ui-helper-boundary",
                        "Passive UI helpers must not import application/repository/database/API-client owners: $importName"
                    ))
                }
                if (passiveHelperForbiddenStateImports.contains(importName)) {
                    violations.add(ArchitectureViolation(
                        source.relativePath,
                        "passive-ui-helper-boundary",
                        "Passive UI helpers must not own launchers, handlers, executors, or LiveData state: $importName"
                    ))
                }
            }
        }

        val registrationLines = architectureFindRegistrationLines(source.content)
        if (registrationLines.isNotEmpty()) {
            if (!architectureIsUiHost(source)) {
                violations.add(ArchitectureViolation(
                    source.relativePath,
                    "view-activityresult-registration-stage",
                    "registerForActivityResult(...) must be owned by a Fragment, Activity, or Dialog host."
                ))
            } else {
                val methodRanges = architectureFindMethodRanges(source.content)
                for (lineNumber in registrationLines) {
                    val inAnyMethod = methodRanges.any { it.contains(lineNumber) }
                    val allowed = !inAnyMethod
                        || methodRanges.any { it.name == "onCreate" && it.contains(lineNumber) }
                    if (!allowed) {
                        violations.add(ArchitectureViolation(
                            source.relativePath,
                            "view-activityresult-registration-stage",
                            "registerForActivityResult(...) must be declared in a host field initializer or inside onCreate()."
                        ))
                    }
                }
            }
        }

        if (source.relativePath.contains("/features/")
            && source.relativePath.contains("/ui/")
            && !source.fileName.endsWith("ViewModel.java")
            && !architectureIsWidgetMechanics(source)
        ) {
            for (importName in imports) {
                if (mainThreadIoImports.contains(importName)) {
                    violations.add(ArchitectureViolation(
                        source.relativePath,
                        "view-no-main-thread-io",
                        "Feature UI code must not directly import ContentResolver, cursor, stream, or file I/O APIs: $importName"
                    ))
                }
            }
            if (mainThreadIoCallPatterns.any { it.containsMatchIn(source.content) }) {
                violations.add(ArchitectureViolation(
                    source.relativePath,
                    "view-no-main-thread-io",
                    "Feature UI code must not directly call synchronous content, stream, or file I/O APIs on the main thread."
                ))
            }
        }
    }

    architectureValidateBuildFileReleaseTasks(root, violations)
    return violations.sortedWith(compareBy(ArchitectureViolation::source, ArchitectureViolation::rule, ArchitectureViolation::details))
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
}

val checkArchitecture = tasks.register("checkArchitecture") {
    group = "verification"
    description = "Runs AutoSecretary architecture and repository policy checks."

    doLast {
        val violations = architectureViolations(layout.projectDirectory.asFile)
        if (violations.isEmpty()) {
            logger.lifecycle("Architecture checks passed.")
            return@doLast
        }
        val body = violations.joinToString(System.lineSeparator()) {
            "- [${it.rule}] ${it.source}: ${it.details}"
        }
        throw GradleException(
            "Architecture check failed with ${violations.size} violation(s):"
                + System.lineSeparator()
                + body
        )
    }
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
    dependsOn(checkArchitecture)
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

        tasks.register("pushToGitHub", Exec::class) {
            workingDir = layout.projectDirectory.asFile
            commandLine("bash", "-c", """
                git add ops/release/ &&
                git commit -m "build: APK aktualisiert" --allow-empty &&
                git push
            """.trimIndent())
        }

        tasks.register("publishReleaseArtifact") {
            group = "release"
            description = "Kopiert das APK ins ops/release-Verzeichnis und pusht die Änderungen nach GitHub."
            dependsOn(copyTask)
            dependsOn("pushToGitHub")
        }
    }
}

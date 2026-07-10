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

enum class ArchitectureLayer {
    APP,
    DATABASE,
    SHARED,
    UTIL,
    FEATURE_UI,
    FEATURE_APPLICATION,
    FEATURE_DOMAIN,
    FEATURE_DATA
}

data class ArchitectureCell(val layer: ArchitectureLayer, val feature: String = "")

data class ArchitectureClassInfo(
    val source: ArchitectureSource,
    val simpleName: String,
    val qualifiedName: String
)

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

fun architectureCellOf(source: ArchitectureSource): ArchitectureCell? {
    if (!source.isProductionJava || source.segments.size < 5) {
        return null
    }
    val base = "src/main/java/com/autosecretary/"
    if (!source.relativePath.startsWith(base)) {
        return null
    }
    val local = source.relativePath.removePrefix(base)
    val first = local.substringBefore("/")
    return when (first) {
        "app" -> ArchitectureCell(ArchitectureLayer.APP)
        "database" -> ArchitectureCell(ArchitectureLayer.DATABASE)
        "shared" -> ArchitectureCell(ArchitectureLayer.SHARED)
        "util" -> ArchitectureCell(ArchitectureLayer.UTIL)
        "features" -> {
            val parts = local.split("/")
            if (parts.size < 3) {
                null
            } else {
                val feature = parts[1]
                when (parts[2]) {
                    "ui" -> ArchitectureCell(ArchitectureLayer.FEATURE_UI, feature)
                    "application" -> ArchitectureCell(ArchitectureLayer.FEATURE_APPLICATION, feature)
                    "domain" -> ArchitectureCell(ArchitectureLayer.FEATURE_DOMAIN, feature)
                    "data" -> ArchitectureCell(ArchitectureLayer.FEATURE_DATA, feature)
                    else -> null
                }
            }
        }
        else -> null
    }
}

fun architectureClassInfo(source: ArchitectureSource): ArchitectureClassInfo? {
    if (!source.isProductionJava || source.packageName.isBlank()) {
        return null
    }
    val simpleName = source.fileName.removeSuffix(".java")
    return ArchitectureClassInfo(source, simpleName, "${source.packageName}.$simpleName")
}

fun architectureProjectImportTarget(
    importName: String,
    classesByQualifiedName: Map<String, ArchitectureClassInfo>
): ArchitectureClassInfo? {
    if (!importName.startsWith("com.autosecretary.") || importName == "com.autosecretary.R") {
        return null
    }
    var candidate = importName
    while (candidate.startsWith("com.autosecretary.")) {
        classesByQualifiedName[candidate]?.let { return it }
        val lastDot = candidate.lastIndexOf('.')
        if (lastDot <= "com.autosecretary".length) {
            return null
        }
        candidate = candidate.substring(0, lastDot)
    }
    return null
}

fun architectureIsUiAllowedDomainValueImport(importName: String): Boolean {
    val simpleName = importName.substringAfterLast('.')
    val forbiddenSuffixes = listOf("Service", "Repository", "Dao", "ApiClient", "Generator", "Manager", "Factory")
    return !importName.contains(".domain.internal.")
        && forbiddenSuffixes.none(simpleName::endsWith)
}

fun architectureMatrixAllows(source: ArchitectureSource, sourceCell: ArchitectureCell, target: ArchitectureClassInfo): Boolean {
    val targetCell = architectureCellOf(target.source) ?: return false
    if (sourceCell.layer == ArchitectureLayer.APP) {
        return true
    }
    if (target.qualifiedName == "com.autosecretary.R") {
        return true
    }
    if (sourceCell.layer == ArchitectureLayer.SHARED || sourceCell.layer == ArchitectureLayer.UTIL) {
        return targetCell.layer == ArchitectureLayer.SHARED
            && sourceCell.layer == ArchitectureLayer.SHARED
            && source.relativePath.contains("/shared/ui/")
    }
    if (sourceCell.layer == ArchitectureLayer.DATABASE) {
        return targetCell.layer == ArchitectureLayer.SHARED
            || targetCell.layer == ArchitectureLayer.FEATURE_DOMAIN
            || targetCell.layer == ArchitectureLayer.FEATURE_DATA
            || targetCell.layer == ArchitectureLayer.DATABASE
    }
    if (sourceCell.layer.name.startsWith("FEATURE_") && targetCell.layer == ArchitectureLayer.APP) {
        return false
    }
    if (!sourceCell.layer.name.startsWith("FEATURE_")) {
        return true
    }
    if (targetCell.layer == ArchitectureLayer.SHARED || targetCell.layer == ArchitectureLayer.UTIL) {
        return true
    }
    if (!targetCell.layer.name.startsWith("FEATURE_")) {
        return targetCell.layer == ArchitectureLayer.DATABASE && sourceCell.layer == ArchitectureLayer.FEATURE_DATA
    }
    val sameFeature = sourceCell.feature == targetCell.feature
    if (!sameFeature) {
        return (sourceCell.layer == ArchitectureLayer.FEATURE_APPLICATION
            || sourceCell.layer == ArchitectureLayer.FEATURE_DATA)
            && targetCell.layer == ArchitectureLayer.FEATURE_DOMAIN
    }
    return when (sourceCell.layer) {
        ArchitectureLayer.FEATURE_UI -> targetCell.layer == ArchitectureLayer.FEATURE_UI
            || targetCell.layer == ArchitectureLayer.FEATURE_APPLICATION
            || (targetCell.layer == ArchitectureLayer.FEATURE_DOMAIN
                && architectureIsUiAllowedDomainValueImport(target.qualifiedName))
        ArchitectureLayer.FEATURE_APPLICATION -> targetCell.layer == ArchitectureLayer.FEATURE_APPLICATION
            || targetCell.layer == ArchitectureLayer.FEATURE_DOMAIN
            || targetCell.layer == ArchitectureLayer.FEATURE_DATA
        ArchitectureLayer.FEATURE_DOMAIN -> targetCell.layer == ArchitectureLayer.FEATURE_DOMAIN
        ArchitectureLayer.FEATURE_DATA -> targetCell.layer == ArchitectureLayer.FEATURE_DATA
            || targetCell.layer == ArchitectureLayer.FEATURE_DOMAIN
        else -> false
    }
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

fun architectureCodeOnly(content: String): String {
    val result = StringBuilder(content.length)
    var i = 0
    var inLineComment = false
    var inBlockComment = false
    var inString = false
    var inChar = false
    var escaped = false
    while (i < content.length) {
        val c = content[i]
        val next = if (i + 1 < content.length) content[i + 1] else '\u0000'
        when {
            inLineComment -> {
                if (c == '\n') {
                    inLineComment = false
                    result.append(c)
                } else {
                    result.append(' ')
                }
            }
            inBlockComment -> {
                if (c == '*' && next == '/') {
                    inBlockComment = false
                    result.append("  ")
                    i++
                } else {
                    result.append(if (c == '\n') '\n' else ' ')
                }
            }
            inString -> {
                if (!escaped && c == '"') {
                    inString = false
                }
                escaped = !escaped && c == '\\'
                result.append(if (c == '\n') '\n' else ' ')
            }
            inChar -> {
                if (!escaped && c == '\'') {
                    inChar = false
                }
                escaped = !escaped && c == '\\'
                result.append(if (c == '\n') '\n' else ' ')
            }
            c == '/' && next == '/' -> {
                inLineComment = true
                result.append("  ")
                i++
            }
            c == '/' && next == '*' -> {
                inBlockComment = true
                result.append("  ")
                i++
            }
            c == '"' -> {
                inString = true
                escaped = false
                result.append(' ')
            }
            c == '\'' -> {
                inChar = true
                escaped = false
                result.append(' ')
            }
            else -> result.append(c)
        }
        i++
    }
    return result.toString()
}

fun architectureManifestClasses(root: File): Set<String> {
    val manifest = root.resolve("src/main/AndroidManifest.xml")
    if (!manifest.isFile) {
        return emptySet()
    }
    return Regex("""android:name="([^"]+)"""")
        .findAll(manifest.readText())
        .map { it.groupValues[1] }
        .filter { it.startsWith(".") || it.startsWith("com.autosecretary.") }
        .map { if (it.startsWith(".")) "com.autosecretary$it" else it }
        .toSet()
}

fun architectureRoomReachableClasses(source: ArchitectureSource): Set<String> {
    if (source.packageName != "com.autosecretary.database" || source.fileName != "AppDatabase.java") {
        return emptySet()
    }
    val imports = architectureImportsOf(source).associateBy { it.substringAfterLast('.') }
    val reachable = mutableSetOf("com.autosecretary.database.AppDatabase", "com.autosecretary.database.Converters")
    Regex("""\b([A-Z][A-Za-z0-9_]*)\.class""")
        .findAll(source.content)
        .mapNotNullTo(reachable) { imports[it.groupValues[1]] }
    Regex("""abstract\s+([A-Z][A-Za-z0-9_]*)\s+\w+\s*\(""")
        .findAll(source.content)
        .mapNotNullTo(reachable) { imports[it.groupValues[1]] }
    return reachable
}

fun architectureValidateReachability(
    sources: List<ArchitectureSource>,
    classInfos: List<ArchitectureClassInfo>,
    root: File,
    violations: MutableList<ArchitectureViolation>
) {
    val manifestClasses = architectureManifestClasses(root)
    val roomReachable = sources.flatMap { architectureRoomReachableClasses(it) }.toSet()
    val allowlist = setOf(
        "com.autosecretary.app.AutoSecretaryApplication"
    )
    val codeBySource = sources.associateWith { architectureCodeOnly(it.content) }
    for (info in classInfos) {
        val reachable = info.qualifiedName in manifestClasses
            || info.qualifiedName in roomReachable
            || info.qualifiedName in allowlist
            || sources.any { other ->
                other != info.source && (
                    architectureImportsOf(other).any { importName ->
                        importName == info.qualifiedName || importName.startsWith("${info.qualifiedName}.")
                    }
                        || codeBySource.getValue(other).contains(Regex("""\b${Regex.escape(info.simpleName)}\b"""))
                    )
            }
        if (!reachable) {
            violations.add(ArchitectureViolation(
                info.source.relativePath,
                "unreferenced-class",
                "Top-level production class is not referenced by code, manifest, or Room wiring: ${info.qualifiedName}"
            ))
        }
    }
}

fun architectureExtractDatabaseVersion(text: String): String? =
    Regex("""version\s*=\s*(\d+)""").find(text)?.groupValues?.get(1)

fun architectureExtractDocumentedDbVersion(text: String): String? =
    Regex("""DB version\s+(\d+)""").find(text)?.groupValues?.get(1)

fun architectureValidateDocsMatchCode(root: File, violations: MutableList<ArchitectureViolation>) {
    val databaseFile = root.resolve("src/main/java/com/autosecretary/database/AppDatabase.java")
    val claudeFile = root.resolve("CLAUDE.md")
    val databaseReadme = root.resolve("src/main/java/com/autosecretary/database/README.md")
    if (!databaseFile.isFile || !claudeFile.isFile) {
        return
    }
    val codeVersion = architectureExtractDatabaseVersion(databaseFile.readText()) ?: return
    val claudeVersion = architectureExtractDocumentedDbVersion(claudeFile.readText())
    if (claudeVersion != codeVersion) {
        violations.add(ArchitectureViolation(
            "CLAUDE.md",
            "docs-match-code-db-version",
            "CLAUDE.md DB version '$claudeVersion' must match AppDatabase version '$codeVersion'."
        ))
    }
    if (databaseReadme.isFile) {
        val readmeVersion = architectureExtractDocumentedDbVersion(databaseReadme.readText())
        if (readmeVersion != codeVersion) {
            violations.add(ArchitectureViolation(
                "src/main/java/com/autosecretary/database/README.md",
                "docs-match-code-db-version",
                "database README DB version '$readmeVersion' must match AppDatabase version '$codeVersion'."
            ))
        }
    }
}

fun architectureValidateExecutorOwnership(sources: List<ArchitectureSource>, violations: MutableList<ArchitectureViolation>) {
    for (source in sources) {
        if (source.relativePath == "src/main/java/com/autosecretary/app/AppCompositionRoot.java") {
            continue
        }
        if (Regex("""\bExecutors\.new[A-Za-z0-9_]*\s*\(""").containsMatchIn(architectureCodeOnly(source.content))) {
            violations.add(ArchitectureViolation(
                source.relativePath,
                "executor-owner",
                "Executors.new* calls are owned by AppCompositionRoot."
            ))
        }
    }
}

fun architectureValidateApplicationPresenterConvention(sources: List<ArchitectureSource>, violations: MutableList<ArchitectureViolation>) {
    for (source in sources) {
        if (!source.relativePath.contains("/features/") || !source.relativePath.contains("/application/")) {
            continue
        }
        if (source.fileName.endsWith("Presenter.java")) {
            violations.add(ArchitectureViolation(
                source.relativePath,
                "application-no-presenter",
                "Application-layer files must use UseCase/DataService names, not Presenter."
            ))
        }
        val code = architectureCodeOnly(source.content)
        if (architectureImportsOf(source).any { it.substringAfterLast('.').contains("Presenter") }
            || Regex("""\b[A-Za-z0-9_]*Presenter\b""").containsMatchIn(code)
        ) {
            violations.add(ArchitectureViolation(
                source.relativePath,
                "application-no-presenter",
                "Application-layer code must not define or import Presenter types."
            ))
        }
    }
}

fun architectureWriteFixture(root: File, relativePath: String, content: String) {
    val file = root.resolve(relativePath)
    file.parentFile.mkdirs()
    file.writeText(content.trimIndent())
}

fun architectureAssertSelfTest(name: String, expectedRule: String, configure: (File) -> Unit) {
    val root = Files.createTempDirectory("autosecretary-architecture-self-test-$name").toFile()
    configure(root)
    val violations = architectureViolations(root, runSelfTests = false)
    if (violations.none { it.rule == expectedRule }) {
        val body = violations.joinToString("; ") { "${it.rule}:${it.source}" }
        throw GradleException(
            "Architecture self-test '$name' did not produce expected rule '$expectedRule'. Actual: $body"
        )
    }
}

fun architectureRunSelfTests() {
    architectureAssertSelfTest("source-classification", "import-matrix-source-classification") { root ->
        architectureWriteFixture(root, "src/main/java/com/autosecretary/unknown/Unclassified.java", """
            package com.autosecretary.unknown;
            public class Unclassified {}
        """)
    }
    architectureAssertSelfTest("matrix", "import-matrix") { root ->
        architectureWriteFixture(root, "src/main/java/com/autosecretary/app/MainActivity.java", """
            package com.autosecretary.app;
            public class MainActivity {}
        """)
        architectureWriteFixture(root, "src/main/java/com/autosecretary/features/task/ui/BadUi.java", """
            package com.autosecretary.features.task.ui;
            import com.autosecretary.app.MainActivity;
            public class BadUi {
                MainActivity activity;
            }
        """)
    }
    architectureAssertSelfTest("reachability", "unreferenced-class") { root ->
        architectureWriteFixture(root, "src/main/java/com/autosecretary/features/task/domain/DeadDomainType.java", """
            package com.autosecretary.features.task.domain;
            public class DeadDomainType {}
        """)
    }
    architectureAssertSelfTest("db-docs", "docs-match-code-db-version") { root ->
        architectureWriteFixture(root, "src/main/java/com/autosecretary/database/AppDatabase.java", """
            package com.autosecretary.database;
            public class AppDatabase {
                @interface Database { int version(); }
                @Database(version = 7)
                static class Marker {}
            }
        """)
        architectureWriteFixture(root, "CLAUDE.md", "DB version 6")
        architectureWriteFixture(root, "src/main/java/com/autosecretary/database/README.md", "DB version 6")
    }
    architectureAssertSelfTest("executor", "executor-owner") { root ->
        architectureWriteFixture(root, "src/main/java/com/autosecretary/shared/BadExecutor.java", """
            package com.autosecretary.shared;
            import java.util.concurrent.Executors;
            public class BadExecutor {
                Object executor = Executors.newSingleThreadExecutor();
            }
        """)
    }
    architectureAssertSelfTest("presenter", "application-no-presenter") { root ->
        architectureWriteFixture(root, "src/main/java/com/autosecretary/features/task/application/BadPresenter.java", """
            package com.autosecretary.features.task.application;
            public class BadPresenter {}
        """)
    }
}

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

fun architectureViolations(root: File, runSelfTests: Boolean = true): List<ArchitectureViolation> {
    val violations = mutableListOf<ArchitectureViolation>()

    val sources = architectureSources(root)
    val classInfos = sources.mapNotNull(::architectureClassInfo)
    val classesByQualifiedName = classInfos.associateBy(ArchitectureClassInfo::qualifiedName)
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
        val sourceCell = architectureCellOf(source)
        if (source.isProductionJava && sourceCell == null) {
            violations.add(ArchitectureViolation(
                source.relativePath,
                "import-matrix-source-classification",
                "Production source is not classified by the architecture import matrix."
            ))
        }
        if (sourceCell != null) {
            for (importName in imports) {
                val target = architectureProjectImportTarget(importName, classesByQualifiedName)
                if (target != null && !architectureMatrixAllows(source, sourceCell, target)) {
                    violations.add(ArchitectureViolation(
                        source.relativePath,
                        "import-matrix",
                        "Import is not allowed by the architecture matrix: $importName"
                    ))
                }
            }
        }
        if (source.isFeatureJava && source.segments.contains("domain")) {
            val isTaskDomainModel = source.featureName == "task"
                && source.relativePath.contains("/features/task/domain/model/")
            for (importName in imports) {
                val sameFeatureDataPrefix = "com.autosecretary.features.${source.featureName}.data."
                val sameFeatureUiPrefix = "com.autosecretary.features.${source.featureName}.ui."
                val allowedTaskModelRoomImport = isTaskDomainModel
                    && (importName.startsWith("androidx.room.")
                        || importName.startsWith("androidx.annotation."))
                val forbidden = !allowedTaskModelRoomImport
                    && (importName.startsWith("android.")
                    || importName.startsWith("androidx.")
                    || importName.startsWith("com.autosecretary.app.")
                    || importName.startsWith("com.autosecretary.database.")
                    || importName.startsWith(sameFeatureUiPrefix)
                    || importName.contains(".ui.")
                    || (importName.startsWith("com.autosecretary.features.")
                        && importName.contains(".data.")
                        && !importName.startsWith(sameFeatureDataPrefix)))
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

    architectureValidateReachability(sources, classInfos, root, violations)
    architectureValidateDocsMatchCode(root, violations)
    architectureValidateExecutorOwnership(sources, violations)
    architectureValidateApplicationPresenterConvention(sources, violations)
    architectureValidateBuildFileReleaseTasks(root, violations)
    if (runSelfTests) {
        try {
            architectureRunSelfTests()
        } catch (exception: GradleException) {
            violations.add(ArchitectureViolation(
                "build.gradle.kts",
                "architecture-self-test",
                exception.message ?: "Architecture self-test failed."
            ))
        }
    }
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
    testAnnotationProcessor("androidx.room:room-compiler:2.6.1")
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

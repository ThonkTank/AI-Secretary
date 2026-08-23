package de.thonktank.autosecretary;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

public final class TaskEditorGoldenCatalogTest {
    @Test public void scenarioCatalogExactlyMatchesTheOnlyEditorBaselineDirectory()
            throws IOException {
        Set<String> scenarios = TaskEditorGoldenScenario.ALL.stream()
                .map(value -> value.id + ".png")
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Path root = projectPath("src/test/resources/golden/task-editor");
        Path wizard = root.resolve("wizard");
        Set<String> baselines;
        try (java.util.stream.Stream<Path> paths = Files.list(wizard)) {
            baselines = paths.filter(value -> value.getFileName().toString().endsWith(".png"))
                    .map(value -> value.getFileName().toString())
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        }

        assertEquals(scenarios, baselines);
        Path reference = projectPath("src/test/resources/reference/task-editor/variant-2a");
        Set<String> references;
        try (java.util.stream.Stream<Path> paths = Files.list(reference)) {
            references = paths.filter(value -> value.getFileName().toString().endsWith(".png"))
                    .map(value -> value.getFileName().toString())
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        }
        assertEquals(scenarios, references);
        try (java.util.stream.Stream<Path> children = Files.list(root)) {
            assertFalse(children.anyMatch(value -> Files.isDirectory(value)
                    && !value.getFileName().toString().equals("wizard")));
        }
    }

    @Test public void scenarioIdentifiersAreUniqueAndDoNotDriveFixtureDecisions()
            throws IOException {
        Set<String> ids = TaskEditorGoldenScenario.ALL.stream().map(value -> value.id)
                .collect(Collectors.toSet());
        assertEquals(10, ids.size());
        String source = new String(Files.readAllBytes(projectPath(
                "src/test/java/de/thonktank/autosecretary/TaskEditorGoldenScenario.java")),
                java.nio.charset.StandardCharsets.UTF_8);
        assertFalse(source.contains("id.contains("));
        assertFalse(source.contains("id.startsWith("));
        assertFalse(source.contains("id.equals("));
    }

    private static Path projectPath(String relative) {
        Path module = Path.of(relative);
        return Files.exists(module) ? module : Path.of("app").resolve(relative);
    }
}

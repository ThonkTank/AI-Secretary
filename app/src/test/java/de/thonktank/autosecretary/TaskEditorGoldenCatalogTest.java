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
    @Test public void scenarioCatalogsExactlyMatchTheOnlyEditorBaselineDirectories()
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
        Set<String> adaptiveScenarios = TaskEditorAdaptiveGoldenScenario.ALL.stream()
                .map(value -> value.id + ".png")
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Path adaptive = root.resolve("adaptive");
        Set<String> adaptiveBaselines;
        try (java.util.stream.Stream<Path> paths = Files.list(adaptive)) {
            adaptiveBaselines = paths.filter(value -> value.getFileName().toString()
                            .endsWith(".png"))
                    .map(value -> value.getFileName().toString())
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        }
        assertEquals(adaptiveScenarios, adaptiveBaselines);
        Path flow = root.resolve("flow");
        Set<String> flowBaselines;
        try (java.util.stream.Stream<Path> paths = Files.list(flow)) {
            flowBaselines = paths.filter(value -> value.getFileName().toString()
                            .endsWith(".png"))
                    .map(value -> value.getFileName().toString())
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        }
        assertEquals(java.util.Collections.singleton("01-waesche-ablauf.png"), flowBaselines);
        Set<String> allowed = new LinkedHashSet<>();
        allowed.add("wizard"); allowed.add("adaptive"); allowed.add("flow");
        Set<String> actualChildren;
        try (java.util.stream.Stream<Path> children = Files.list(root)) {
            actualChildren = children.filter(Files::isDirectory)
                    .map(value -> value.getFileName().toString())
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        }
        assertEquals(allowed, actualChildren);
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
        assertEquals(5, TaskEditorAdaptiveGoldenScenario.ALL.size());
    }

    private static Path projectPath(String relative) {
        Path module = Path.of(relative);
        return Files.exists(module) ? module : Path.of("app").resolve(relative);
    }
}

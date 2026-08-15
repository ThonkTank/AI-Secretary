package de.thonktank.autosecretary.update;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class UpdateArchitectureBoundaryTest {
    @Test public void updateLayersOnlyKnowAllowedDependencies() throws Exception {
        Path root = updateSources();
        assertNoImports(root.resolve("domain"), List.of(
                "import android.", "import androidx.", "import org.json.",
                ".update.application.", ".update.infrastructure.", ".update.presentation."));
        assertNoImports(root.resolve("application"), List.of(
                "import android.", "import androidx.", "import org.json.",
                ".update.infrastructure.", ".update.presentation."));
        assertNoImports(root.resolve("presentation"), List.of(
                ".update.infrastructure.", ".data.", ".infrastructure.",
                "import android."));
        assertNoImports(root.resolve("infrastructure"), List.of(".update.presentation."));
    }

    @Test public void updateTypesLiveInOneOfTheFourExplicitLayers() throws Exception {
        Path root = updateSources();
        try (Stream<Path> children = Files.list(root)) {
            assertFalse(children.anyMatch(path -> path.getFileName().toString().endsWith(".java")));
        }
        assertTrue(Files.isDirectory(root.resolve("domain")));
        assertTrue(Files.isDirectory(root.resolve("application")));
        assertTrue(Files.isDirectory(root.resolve("infrastructure")));
        assertTrue(Files.isDirectory(root.resolve("presentation")));
    }

    private static void assertNoImports(Path directory, List<String> forbidden)
            throws IOException {
        try (Stream<Path> sources = Files.walk(directory)) {
            for (Path source : sources.filter(path -> path.toString().endsWith(".java"))
                    .collect(Collectors.toList())) {
                String text = new String(Files.readAllBytes(source), StandardCharsets.UTF_8);
                for (String value : forbidden)
                    assertFalse(source + " must not depend on " + value, text.contains(value));
            }
        }
    }

    private static Path updateSources() {
        Path moduleRelative = Path.of("src/main/java/de/thonktank/autosecretary/update");
        if (Files.isDirectory(moduleRelative)) return moduleRelative;
        Path repositoryRelative = Path.of("app").resolve(moduleRelative);
        assertTrue("Update source root not found", Files.isDirectory(repositoryRelative));
        return repositoryRelative;
    }
}

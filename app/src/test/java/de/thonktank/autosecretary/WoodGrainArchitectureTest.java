package de.thonktank.autosecretary;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class WoodGrainArchitectureTest {
    @Test public void drawViewHasNoGeometryOrHierarchyReconstruction() throws Exception {
        String source = new String(Files.readAllBytes(source("WoodGrainView.java")),
                StandardCharsets.UTF_8);

        assertFalse(source.contains("WoodGrainGeometry"));
        assertFalse(source.contains("ViewParent"));
        assertFalse(source.contains("layoutOrigin"));
        assertTrue(source.contains("WoodGrainRenderPipeline.request"));
        assertTrue(source.contains("List<RectF> fadedText"));
    }

    private static Path source(String name) {
        Path module = Path.of("src/main/java/de/thonktank/autosecretary", name);
        if (Files.isRegularFile(module)) return module;
        return Path.of("app/src/main/java/de/thonktank/autosecretary", name);
    }
}

package de.thonktank.autosecretary;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class WoodGrainArchitectureTest {
    @Test public void drawViewHasNoGeometryOrHierarchyReconstruction() throws Exception {
        String source = new String(Files.readAllBytes(source("ui/leaf/WoodGrainView.java")),
                StandardCharsets.UTF_8);

        assertFalse(source.contains("WoodGrainGeometry"));
        assertFalse(source.contains("ViewParent"));
        assertFalse(source.contains("layoutOrigin"));
        assertTrue(source.contains("WoodGrainRenderPipeline.request"));
        assertTrue(source.contains("List<RectF> fadedText"));
    }

    @Test public void leafSurfaceOwnsLayoutGeometryWithoutExternalPostsOrLayerRotation()
            throws Exception {
        String leaf = new String(Files.readAllBytes(sourceIn("ui/leaf/LeafSurface.java")),
                StandardCharsets.UTF_8);
        String header = new String(Files.readAllBytes(source("ui/today/HeaderView.java")),
                StandardCharsets.UTF_8);
        String task = new String(Files.readAllBytes(source("ui/today/TaskLeafView.java")),
                StandardCharsets.UTF_8);
        String focus = new String(Files.readAllBytes(source("ui/today/FocusCardDecoration.java")),
                StandardCharsets.UTF_8);

        assertTrue(leaf.contains("onLayout"));
        assertTrue(leaf.contains("shape.cornerCenter"));
        assertFalse(header.contains("grain.post"));
        assertFalse(task.contains("grain.post"));
        assertFalse(focus.contains("grain.setRotation"));
        assertFalse(focus.contains("surface.setRotation(card"));
    }

    private static Path source(String name) {
        Path module = Path.of("src/main/java/de/thonktank/autosecretary", name);
        if (Files.isRegularFile(module)) return module;
        return Path.of("app/src/main/java/de/thonktank/autosecretary", name);
    }

    private static Path sourceIn(String name) {
        Path module = Path.of("src/main/java/de/thonktank/autosecretary", name);
        if (Files.isRegularFile(module)) return module;
        return Path.of("app/src/main/java/de/thonktank/autosecretary", name);
    }
}

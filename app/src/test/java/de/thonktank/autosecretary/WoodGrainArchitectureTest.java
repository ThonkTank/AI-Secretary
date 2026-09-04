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
        String spec = new String(Files.readAllBytes(sourceIn("ui/leaf/GrainSpec.java")),
                StandardCharsets.UTF_8);
        String occlusion = new String(Files.readAllBytes(sourceIn("ui/leaf/GrainOcclusion.java")),
                StandardCharsets.UTF_8);
        String header = new String(Files.readAllBytes(source("ui/today/HeaderView.java")),
                StandardCharsets.UTF_8);
        String task = new String(Files.readAllBytes(source("ui/today/TaskLeafView.java")),
                StandardCharsets.UTF_8);
        String focus = new String(Files.readAllBytes(source("ui/today/FocusCardDecoration.java")),
                StandardCharsets.UTF_8);

        assertTrue(leaf.contains("onLayout"));
        assertTrue(leaf.contains("shape.cornerCenter"));
        assertTrue(spec.contains("List<GrainOcclusion> occlusions"));
        assertFalse(spec.contains("fadedText"));
        assertFalse(leaf.contains("TextView"));
        assertTrue(occlusion.contains("getLineLeft"));
        assertTrue(occlusion.contains("getLineRight"));
        assertTrue(occlusion.contains("getExtendedPaddingTop"));
        assertTrue(occlusion.contains("getScrollX"));
        assertTrue(occlusion.contains("getEllipsisCount"));
        assertTrue(occlusion.contains("clipToVisibleBounds"));
        assertFalse(header.contains("grain.post"));
        assertFalse(task.contains("grain.post"));
        assertFalse(focus.contains("grain.setRotation"));
        assertFalse(focus.contains("surface.setRotation(card"));
    }

    @Test public void vesselUsesOneClippedCircleWithoutSoftwareShadowOrDegenerateLine()
            throws Exception {
        String vessel = new String(Files.readAllBytes(source("ui/today/XpVesselView.java")),
                StandardCharsets.UTF_8);
        String dimens = new String(Files.readAllBytes(resource("values/dimens.xml")),
                StandardCharsets.UTF_8);

        assertTrue(vessel.contains("canvas.clipPath(innerClip)"));
        assertTrue(vessel.contains("clamped > 0f && clamped < 1f"));
        assertFalse(vessel.contains("setLayerType"));
        assertFalse(vessel.contains("setShadowLayer"));
        assertFalse(vessel.contains("clipRect"));
        assertTrue(dimens.contains("name=\"focus_card_steps_gap\">12dp"));
        assertFalse(dimens.contains("name=\"focus_card_steps_gap\">24dp"));
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

    private static Path resource(String name) {
        Path module = Path.of("src/main/res", name);
        if (Files.isRegularFile(module)) return module;
        return Path.of("app/src/main/res", name);
    }
}

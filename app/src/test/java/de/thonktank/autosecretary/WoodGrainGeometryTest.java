package de.thonktank.autosecretary.ui.leaf;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public final class WoodGrainGeometryTest {
    @Test public void spacingAndVisibleRingCapFollowTheHandoffFormula() {
        assertEquals(7f, WoodGrainGeometry.ringDistance(1f, 0), .001f);
        assertEquals(14.29f, WoodGrainGeometry.ringDistance(1f, 1), .001f);
        WoodGrainGeometry.Shape point = new WoodGrainGeometry.Shape(
                0, 50f, 50f, 0f, 0f, 99);
        assertEquals(5, WoodGrainGeometry.maximumRingCount(100f, 100f, point, 1f));
    }

    @Test public void capsuleSdfTracksCircleAndWideDewEdges() {
        WoodGrainGeometry.Shape circle = new WoodGrainGeometry.Shape(
                0, 20f, 20f, 26f, 26f, 1);
        assertEquals(-13f, WoodGrainGeometry.capsuleDistance(circle, 20f, 20f), .001f);
        assertEquals(0f, WoodGrainGeometry.capsuleDistance(circle, 33f, 20f), .001f);
        WoodGrainGeometry.Shape capsule = new WoodGrainGeometry.Shape(
                1, 30f, 20f, 42f, 26f, 1);
        assertEquals(0f, WoodGrainGeometry.capsuleDistance(capsule, 51f, 20f), .001f);
    }

    @Test public void intersectingSystemsMergeButEnclosureAndSameElementDoNot() {
        WoodGrainGeometry.Shape first = new WoodGrainGeometry.Shape(
                0, 30f, 30f, 10f, 10f, 1);
        WoodGrainGeometry.Shape crossing = new WoodGrainGeometry.Shape(
                1, 50f, 30f, 10f, 10f, 1);
        assertEquals(1, WoodGrainGeometry.groups(100f, 100f, 1f,
                Arrays.asList(first, crossing)).size());

        WoodGrainGeometry.Shape enclosing = new WoodGrainGeometry.Shape(
                0, 30f, 30f, 70f, 70f, 1);
        WoodGrainGeometry.Shape enclosed = new WoodGrainGeometry.Shape(
                1, 31f, 30f, 10f, 10f, 1);
        assertEquals(2, WoodGrainGeometry.groups(100f, 100f, 1f,
                Arrays.asList(enclosing, enclosed)).size());

        WoodGrainGeometry.Shape twoRings = new WoodGrainGeometry.Shape(
                0, 50f, 50f, 10f, 10f, 2);
        assertEquals(2, WoodGrainGeometry.groups(100f, 100f, 1f,
                Collections.singletonList(twoRings)).size());
    }

    @Test public void smoothMinimumIsLocalToTheThreePointFiveDpBlendBand() {
        assertEquals(2f, WoodGrainGeometry.smoothMinimum(2f, 20f, 3.5f), .001f);
        float blended = WoodGrainGeometry.smoothMinimum(2f, 3f, 3.5f);
        assertEquals(true, blended < 2f);
    }
}

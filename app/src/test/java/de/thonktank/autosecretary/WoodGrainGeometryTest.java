package de.thonktank.autosecretary.ui.leaf;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Arrays;

public final class WoodGrainGeometryTest {
    @Test public void spacingAndVisibleRingCapFollowTheHandoffFormula() {
        assertEquals(7f, WoodGrainGeometry.ringDistance(1f, 0), .001f);
        assertEquals(14.29f, WoodGrainGeometry.ringDistance(1f, 1), .001f);
        WoodGrainGeometry.Shape point = new WoodGrainGeometry.Shape(
                50f, 50f, 0f, 0f, 99);
        assertEquals(5, WoodGrainGeometry.maximumRingCount(100f, 100f, point, 1f));
    }

    @Test public void capsuleSdfTracksCircleAndWideDewEdges() {
        WoodGrainGeometry.Shape circle = new WoodGrainGeometry.Shape(
                20f, 20f, 26f, 26f, 1);
        assertEquals(-13f, WoodGrainGeometry.capsuleDistance(circle, 20f, 20f), .001f);
        assertEquals(0f, WoodGrainGeometry.capsuleDistance(circle, 33f, 20f), .001f);
        WoodGrainGeometry.Shape capsule = new WoodGrainGeometry.Shape(
                30f, 20f, 42f, 26f, 1);
        assertEquals(0f, WoodGrainGeometry.capsuleDistance(capsule, 51f, 20f), .001f);
    }

    @Test public void outsideInLayersPairOuterRingsAndNeverFreezeASmallerSystem() {
        WoodGrainGeometry.Shape large = new WoodGrainGeometry.Shape(123, 141, 32, 32, 8);
        WoodGrainGeometry.Shape small = new WoodGrainGeometry.Shape(221, 192, 32, 32, 4);
        java.util.List<WoodGrainGeometry.Group> layers = WoodGrainGeometry.groups(360, 340, 1,
                Arrays.asList(small, large));
        assertEquals(8, layers.size());
        assertEquals(2, layers.get(0).rings.size());
        assertEquals(WoodGrainGeometry.ringDistance(1, 7),
                layers.get(0).rings.get(0).distance, .001f);
        assertEquals(WoodGrainGeometry.ringDistance(1, 3),
                layers.get(0).rings.get(1).distance, .001f);
        assertEquals(7f, layers.get(3).rings.get(1).distance, .001f);
        assertEquals(1, layers.get(4).rings.size());
        assertEquals(7f, layers.get(7).rings.get(0).distance, .001f);
    }

    @Test public void smoothMinimumIsLocalToTheThreePointFiveDpBlendBand() {
        assertEquals(2f, WoodGrainGeometry.smoothMinimum(2f, 20f, 3.5f), .001f);
        float blended = WoodGrainGeometry.smoothMinimum(2f, 3f, 3.5f);
        assertEquals(true, blended < 2f);
    }
}

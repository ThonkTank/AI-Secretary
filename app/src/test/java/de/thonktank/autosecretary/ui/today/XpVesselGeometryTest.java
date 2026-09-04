package de.thonktank.autosecretary.ui.today;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class XpVesselGeometryTest {
    @Test public void fillLevelsShareExactCircleChordGeometry() {
        XpVesselView.FillGeometry empty = XpVesselView.fillGeometry(50f, 50f, 40f, 0f);
        assertEquals(90f, empty.surfaceY, .001f);
        assertFalse(empty.drawSurface);

        XpVesselView.FillGeometry quarter = XpVesselView.fillGeometry(50f, 50f, 40f, .25f);
        assertEquals(70f, quarter.surfaceY, .001f);
        assertEquals(50f - (float) Math.sqrt(1200f), quarter.chordLeft, .001f);
        assertEquals(50f + (float) Math.sqrt(1200f), quarter.chordRight, .001f);
        assertTrue(quarter.drawSurface);

        XpVesselView.FillGeometry half = XpVesselView.fillGeometry(50f, 50f, 40f, .5f);
        assertEquals(50f, half.surfaceY, .001f);
        assertEquals(10f, half.chordLeft, .001f);
        assertEquals(90f, half.chordRight, .001f);
        assertTrue(half.drawSurface);

        XpVesselView.FillGeometry full = XpVesselView.fillGeometry(50f, 50f, 40f, 1f);
        assertEquals(10f, full.surfaceY, .001f);
        assertFalse(full.drawSurface);
    }
}

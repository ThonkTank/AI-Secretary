package de.thonktank.autosecretary.ui.leaf;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class WoodGrainContourTest {
    @Test public void hygieneAndWideDewStressContoursStayNestedWithoutCrossingsOrDuplicates() {
        verify(360, 735, 1, hygiene());
        verify(360, 410, 1, stress());
    }

    @Test public void emptyZeroSeparateEnclosedAndTouchingSystemsRemainValid() {
        verify(200, 200, 1, Collections.emptyList());
        verify(200, 200, 1, Collections.singletonList(shape(100, 100, 26, 26, 0)));
        verify(240, 240, 1, Arrays.asList(shape(120, 120, 70, 70, 3), shape(121, 120, 10, 10, 6)));
        verify(440, 200, 1, Arrays.asList(shape(80, 100, 26, 26, 3), shape(360, 100, 26, 26, 3)));
        verify(200, 160, 1, Arrays.asList(shape(80, 80, 26, 26, 4), shape(120, 80, 26, 26, 4)));
    }

    @Test public void densityAndInputOrderPreserveTheTopology() {
        List<WoodGrainGeometry.Shape> forward = stress();
        List<WoodGrainGeometry.Shape> backward = new ArrayList<>(forward);
        Collections.reverse(backward);
        List<WoodGrainGeometry.Group> first = WoodGrainGeometry.groups(360, 410, 1, forward);
        List<WoodGrainGeometry.Group> second = WoodGrainGeometry.groups(360, 410, 1, backward);
        assertEquals(first.size(), second.size());
        for (int i = 0; i < first.size(); i++) {
            assertEquals(first.get(i).rings.size(), second.get(i).rings.size());
            for (int j = 0; j < first.get(i).rings.size(); j++)
                assertTrue(first.get(i).rings.get(j).shape == second.get(i).rings.get(j).shape);
        }
        List<WoodGrainGeometry.Shape> scaled = new ArrayList<>();
        for (WoodGrainGeometry.Shape a : forward)
            scaled.add(shape(a.cx * 2.75f, a.cy * 2.75f, a.width * 2.75f, a.height * 2.75f, a.level));
        verify(990, 1127.5f, 2.75f, scaled);
    }

    @Test public void fixedDiagonalKeepsNestedSaddlesFromCrossingInsideACell() {
        List<float[]> outer = new ArrayList<>();
        List<float[]> inner = new ArrayList<>();
        WoodGrainContours.trace(new float[][]{{-2, 1}, {1, -2}}, 2, 2, 2,
                (x0, y0, x1, y1) -> outer.add(new float[]{x0, y0, x1, y1}));
        WoodGrainContours.trace(new float[][]{{-1, 2}, {2, -1}}, 2, 2, 2,
                (x0, y0, x1, y1) -> inner.add(new float[]{x0, y0, x1, y1}));
        assertFalse(outer.isEmpty());
        assertFalse(inner.isEmpty());
        for (float[] a : outer) for (float[] b : inner) assertFalse(crosses(a, b));
    }

    static List<WoodGrainGeometry.Shape> hygiene() {
        List<WoodGrainGeometry.Shape> result = new ArrayList<>();
        result.add(shape(290, 60, 66, 66, 14));
        int[] ys = {180, 263, 318, 373, 428, 483, 538, 593};
        int[] levels = {2, 8, 4, 3, 8, 3, 3, 2};
        for (int i = 0; i < ys.length; i++) result.add(shape(53, ys[i], 30, 30, levels[i]));
        return result;
    }

    static List<WoodGrainGeometry.Shape> stress() {
        return Arrays.asList(shape(95, 102, 48, 30, 10), shape(189, 83, 30, 30, 4),
                shape(265, 142, 30, 30, 7), shape(140, 202, 30, 30, 6),
                shape(241, 252, 54, 30, 3), shape(88, 295, 30, 30, 8));
    }

    private static WoodGrainGeometry.Shape shape(float x, float y, float w, float h, int level) {
        return new WoodGrainGeometry.Shape(x, y, w, h, level);
    }

    private static void verify(float width, float height, float density,
                               List<WoodGrainGeometry.Shape> shapes) {
        float step = density * 3;
        int columns = (int) Math.ceil(width / step), rows = (int) Math.ceil(height / step);
        float[][] previous = null;
        Map<Long, List<float[]>> cells = new HashMap<>();
        Set<String> segments = new HashSet<>();
        for (WoodGrainGeometry.Group group : WoodGrainGeometry.groups(width, height, density, shapes)) {
            float[][] field = new float[rows + 1][columns + 1];
            for (int y = 0; y <= rows; y++) for (int x = 0; x <= columns; x++) {
                field[y][x] = WoodGrainGeometry.groupDistance(group,
                        Math.min(width, x * step), Math.min(height, y * step),
                        density * WoodGrainGeometry.SMOOTH_MIN_DP);
                if (previous != null) assertTrue("inner contours must stay inside outer contours",
                        field[y][x] >= previous[y][x] - .0001f * density);
            }
            previous = field;
            WoodGrainContours.trace(field, width, height, step, (x0, y0, x1, y1) -> {
                float[] segment = {x0, y0, x1, y1};
                String a = Float.floatToIntBits(x0) + ":" + Float.floatToIntBits(y0);
                String b = Float.floatToIntBits(x1) + ":" + Float.floatToIntBits(y1);
                assertTrue("no duplicated line", segments.add(a.compareTo(b) < 0 ? a + "/" + b : b + "/" + a));
                int column = (int) ((x0 + x1) / (2 * step));
                int row = (int) ((y0 + y1) / (2 * step));
                long key = ((long) row << 32) | (column & 0xffffffffL);
                List<float[]> neighbors = cells.computeIfAbsent(key, ignored -> new ArrayList<>());
                for (float[] other : neighbors) assertFalse("no crossing lines", crosses(segment, other));
                neighbors.add(segment);
            });
        }
    }

    private static boolean crosses(float[] a, float[] b) {
        return side(a, b[0], b[1]) * side(a, b[2], b[3]) < -1e-8
                && side(b, a[0], a[1]) * side(b, a[2], a[3]) < -1e-8;
    }

    private static double side(float[] a, double x, double y) {
        return (a[2] - (double) a[0]) * (y - a[1]) - (a[3] - (double) a[1]) * (x - a[0]);
    }
}

package de.thonktank.autosecretary.ui.leaf;

/** Extracts contours on the same fixed triangulation for every nested field. */
final class WoodGrainContours {
    interface SegmentConsumer {
        void segment(float x0, float y0, float x1, float y1);
    }

    private WoodGrainContours() { }

    static int trace(float[][] values, float width, float height, float step,
                     SegmentConsumer output) {
        int segments = 0;
        float[] crossings = new float[4];
        for (int row = 0; row < values.length - 1; row++) {
            float top = Math.min(height, row * step);
            float bottom = Math.min(height, (row + 1) * step);
            for (int column = 0; column < values[row].length - 1; column++) {
                float left = Math.min(width, column * step);
                float right = Math.min(width, (column + 1) * step);
                float tl = values[row][column];
                float tr = values[row][column + 1];
                float br = values[row + 1][column + 1];
                float bl = values[row + 1][column];
                // The TL-to-BR diagonal is shared by all layers. Linear interpolation
                // therefore preserves their ordering inside cells, including saddles.
                segments += triangle(left, top, tl, right, top, tr, right, bottom, br,
                        crossings, output);
                segments += triangle(left, top, tl, right, bottom, br, left, bottom, bl,
                        crossings, output);
            }
        }
        return segments;
    }

    private static int triangle(float x0, float y0, float v0,
                                float x1, float y1, float v1,
                                float x2, float y2, float v2,
                                float[] points, SegmentConsumer output) {
        boolean first = v0 < 0f;
        boolean second = v1 < 0f;
        boolean third = v2 < 0f;
        if (first == second && second == third) return 0;
        int count = 0;
        if (first != second) count = crossing(x0, y0, v0, x1, y1, v1, points, count);
        if (second != third) count = crossing(x1, y1, v1, x2, y2, v2, points, count);
        if (third != first) crossing(x2, y2, v2, x0, y0, v0, points, count);
        if (points[0] == points[2] && points[1] == points[3]) return 0;
        output.segment(points[0], points[1], points[2], points[3]);
        return 1;
    }

    private static int crossing(float x0, float y0, float v0,
                                float x1, float y1, float v1,
                                float[] points, int count) {
        // Canonical edge direction gives neighboring cells exactly the same endpoint.
        if (x0 > x1 || x0 == x1 && y0 > y1)
            return crossing(x1, y1, v1, x0, y0, v0, points, count);
        float fraction = Math.max(0f, Math.min(1f, v0 / (v0 - v1)));
        points[count] = x0 + fraction * (x1 - x0);
        points[count + 1] = y0 + fraction * (y1 - y0);
        return count + 2;
    }
}

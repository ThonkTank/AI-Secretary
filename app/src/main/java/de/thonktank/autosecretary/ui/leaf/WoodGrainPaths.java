package de.thonktank.autosecretary.ui.leaf;

import android.graphics.Path;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Joins shared endpoints so a contour needs one move command, not one per segment. */
final class WoodGrainPaths implements WoodGrainContours.SegmentConsumer {
    private static final class Point {
        final float x, y;
        final List<Edge> edges = new ArrayList<>(2);
        Point(float x, float y) { this.x = x; this.y = y; }
        Edge unused() {
            for (Edge edge : edges) if (!edge.used) return edge;
            return null;
        }
    }

    private static final class Edge {
        final Point first, second;
        boolean used;
        Edge(Point first, Point second) { this.first = first; this.second = second; }
    }

    private final Map<Long, Point> points = new LinkedHashMap<>();

    @Override public void segment(float x0, float y0, float x1, float y1) {
        Point first = point(x0, y0), second = point(x1, y1);
        Edge edge = new Edge(first, second);
        first.edges.add(edge);
        second.edges.add(edge);
    }

    Path path() {
        Path path = new Path();
        // Start clipped/open contours at their endpoints before walking closed loops.
        for (Point point : points.values()) if (point.edges.size() == 1) append(path, point);
        for (Point point : points.values()) while (point.unused() != null) append(path, point);
        return path;
    }

    private void append(Path path, Point start) {
        Edge edge = start.unused();
        if (edge == null) return;
        path.moveTo(start.x, start.y);
        Point current = start;
        while (edge != null) {
            edge.used = true;
            current = edge.first == current ? edge.second : edge.first;
            path.lineTo(current.x, current.y);
            edge = current.unused();
        }
    }

    private Point point(float x, float y) {
        long key = ((long) Float.floatToIntBits(x) << 32)
                | (Float.floatToIntBits(y) & 0xffffffffL);
        return points.computeIfAbsent(key, ignored -> new Point(x, y));
    }
}

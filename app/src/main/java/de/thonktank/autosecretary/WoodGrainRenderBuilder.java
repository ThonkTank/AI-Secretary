package de.thonktank.autosecretary;

import android.graphics.Path;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** CPU-heavy SDF and marching-squares builder. Never called by View.onDraw. */
final class WoodGrainRenderBuilder {
    WoodGrainRenderData build(WoodGrainRenderRequest request) {
        List<WoodGrainGeometry.Shape> shapes = shapes(request);
        if (shapes.isEmpty()) return WoodGrainRenderData.EMPTY;
        List<WoodGrainRenderData.Stroke> result = new ArrayList<>();
        List<WoodGrainGeometry.Group> groups = WoodGrainGeometry.groups(
                request.width, request.height, request.density, shapes);
        float area = request.width * request.height;
        float densityArea = request.density * request.density * 40_000f;
        float step = request.density * (area > densityArea ? 4f : 3f);
        float smoothRadius = request.density * WoodGrainGeometry.SMOOTH_MIN_DP;
        for (WoodGrainGeometry.Group group : groups) {
            PathResult path = marchingSquares(request, group, step, smoothRadius);
            if (path.path.isEmpty()) continue;
            float depth = 0f;
            for (WoodGrainGeometry.Ring ring : group.rings) {
                float required = Math.max(1f, WoodGrainGeometry.requiredDistance(
                        request.width, request.height, ring.shape));
                depth = Math.max(depth, Math.min(1f, ring.distance / required));
            }
            result.add(new WoodGrainRenderData.Stroke(path.path,
                    (52f - 34f * depth) / 100f,
                    request.density * (2.2f - .9f * depth), path.segments));
        }
        return new WoodGrainRenderData(result);
    }

    private static List<WoodGrainGeometry.Shape> shapes(WoodGrainRenderRequest request) {
        if (!request.corner) {
            List<WoodGrainGeometry.Shape> result = new ArrayList<>();
            int element = 0;
            for (WoodGrainRenderRequest.Anchor anchor : request.anchors) {
                if (anchor.level <= 0 || anchor.bounds.isEmpty()) continue;
                result.add(new WoodGrainGeometry.Shape(element++, anchor.bounds.centerX(),
                        anchor.bounds.centerY(), anchor.bounds.width(), anchor.bounds.height(),
                        anchor.level));
            }
            return result;
        }
        WoodGrainGeometry.Shape unlimited = new WoodGrainGeometry.Shape(0,
                request.cornerX, request.cornerY,
                0f, 0f, 512);
        int maximum = WoodGrainGeometry.maximumRingCount(request.width, request.height,
                unlimited, request.density);
        return Collections.singletonList(new WoodGrainGeometry.Shape(0,
                unlimited.cx, unlimited.cy, 0f, 0f,
                Math.round(request.cornerRatio * maximum)));
    }

    private static PathResult marchingSquares(WoodGrainRenderRequest request,
                                              WoodGrainGeometry.Group group,
                                              float step, float smoothRadius) {
        int columns = Math.max(1, (int) Math.ceil(request.width / step));
        int rows = Math.max(1, (int) Math.ceil(request.height / step));
        float[][] values = new float[rows + 1][columns + 1];
        for (int row = 0; row <= rows; row++) {
            float y = Math.min(request.height, row * step);
            for (int column = 0; column <= columns; column++) {
                float x = Math.min(request.width, column * step);
                values[row][column] = WoodGrainGeometry.groupDistance(
                        group, x, y, smoothRadius);
            }
        }
        Path path = new Path();
        Point[] crossings = new Point[4];
        int segments = 0;
        for (int row = 0; row < rows; row++) {
            float top = Math.min(request.height, row * step);
            float bottom = Math.min(request.height, (row + 1) * step);
            for (int column = 0; column < columns; column++) {
                float left = Math.min(request.width, column * step);
                float right = Math.min(request.width, (column + 1) * step);
                float topLeft = values[row][column];
                float topRight = values[row][column + 1];
                float bottomRight = values[row + 1][column + 1];
                float bottomLeft = values[row + 1][column];
                int count = 0;
                if (crosses(topLeft, topRight)) crossings[count++] = new Point(
                        interpolate(left, right, topLeft, topRight), top);
                if (crosses(topRight, bottomRight)) crossings[count++] = new Point(
                        right, interpolate(top, bottom, topRight, bottomRight));
                if (crosses(bottomLeft, bottomRight)) crossings[count++] = new Point(
                        interpolate(left, right, bottomLeft, bottomRight), bottom);
                if (crosses(topLeft, bottomLeft)) crossings[count++] = new Point(
                        left, interpolate(top, bottom, topLeft, bottomLeft));
                if (count == 2) {
                    segment(path, crossings[0], crossings[1]);
                    segments++;
                } else if (count == 4) {
                    int bits = (topLeft < 0f ? 1 : 0) | (topRight < 0f ? 2 : 0)
                            | (bottomRight < 0f ? 4 : 0) | (bottomLeft < 0f ? 8 : 0);
                    float center = WoodGrainGeometry.groupDistance(group,
                            (left + right) / 2f, (top + bottom) / 2f, smoothRadius);
                    boolean adjacent = bits == 5 ? center < 0f : center >= 0f;
                    if (adjacent) {
                        segment(path, crossings[0], crossings[1]);
                        segment(path, crossings[2], crossings[3]);
                    } else {
                        segment(path, crossings[0], crossings[3]);
                        segment(path, crossings[1], crossings[2]);
                    }
                    segments += 2;
                }
            }
        }
        return new PathResult(path, segments);
    }

    private static boolean crosses(float first, float second) {
        return first < 0f && second >= 0f || first >= 0f && second < 0f;
    }

    private static float interpolate(float start, float end, float first, float second) {
        float denominator = first - second;
        float fraction = denominator == 0f ? .5f : first / denominator;
        return start + Math.max(0f, Math.min(1f, fraction)) * (end - start);
    }

    private static void segment(Path path, Point first, Point second) {
        path.moveTo(first.x, first.y);
        path.lineTo(second.x, second.y);
    }

    private static final class Point {
        final float x, y;
        Point(float x, float y) { this.x = x; this.y = y; }
    }

    private static final class PathResult {
        final Path path;
        final int segments;
        PathResult(Path path, int segments) { this.path = path; this.segments = segments; }
    }
}

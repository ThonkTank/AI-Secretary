package de.thonktank.autosecretary.ui.leaf;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Pure geometry for the shared wood-grain renderer. Coordinates are pixels. */
final class WoodGrainGeometry {
    static final float SMOOTH_MIN_DP = 3.5f;

    static final class Shape {
        final float cx, cy, width, height;
        final int level;

        Shape(float cx, float cy, float width, float height, int level) {
            this.cx = cx;
            this.cy = cy;
            this.width = Math.max(0f, width);
            this.height = Math.max(0f, height);
            this.level = Math.max(0, level);
        }
    }

    static final class Ring {
        final Shape shape;
        final float distance;

        Ring(Shape shape, float distance) {
            this.shape = shape;
            this.distance = distance;
        }
    }

    static final class Group {
        final List<Ring> rings = new ArrayList<>();
    }

    private WoodGrainGeometry() { }

    static float ringDistance(float density, int index) {
        return density * (7f + 6.9f * index + .39f * index * index);
    }

    static int maximumRingCount(float boxWidth, float boxHeight, Shape shape, float density) {
        float need = requiredDistance(boxWidth, boxHeight, shape);
        int count = 0;
        while (count < 512 && ringDistance(density, count) < need) count++;
        return count;
    }

    static float requiredDistance(float boxWidth, float boxHeight, Shape shape) {
        float halfWidth = shape.width / 2f;
        float halfHeight = shape.height / 2f;
        return Math.max(Math.max(shape.cx - halfWidth,
                        boxWidth - (shape.cx + halfWidth)),
                Math.max(shape.cy - halfHeight,
                        boxHeight - (shape.cy + halfHeight)));
    }

    static List<Group> groups(float boxWidth, float boxHeight, float density,
                              List<Shape> shapes) {
        // One layer per rank from the outside, rather than greedy pairwise grouping.
        // A smaller system stops contributing when it runs out of rings. It must not
        // become a frozen obstacle that compresses the remaining contours around it.
        List<Shape> ordered = new ArrayList<>(shapes);
        ordered.sort(Comparator.comparingDouble((Shape shape) -> shape.cx)
                .thenComparingDouble(shape -> shape.cy)
                .thenComparingDouble(shape -> shape.width)
                .thenComparingDouble(shape -> shape.height)
                .thenComparingInt(shape -> shape.level));
        int[] counts = new int[ordered.size()];
        int maximum = 0;
        for (int index = 0; index < ordered.size(); index++) {
            Shape shape = ordered.get(index);
            counts[index] = Math.min(shape.level,
                    maximumRingCount(boxWidth, boxHeight, shape, density));
            maximum = Math.max(maximum, counts[index]);
        }
        List<Group> groups = new ArrayList<>();
        for (int layer = 0; layer < maximum; layer++) {
            Group group = new Group();
            for (int index = 0; index < ordered.size(); index++) {
                int ring = counts[index] - 1 - layer;
                if (ring >= 0) group.rings.add(new Ring(ordered.get(index),
                        ringDistance(density, ring)));
            }
            groups.add(group);
        }
        return groups;
    }

    static float groupDistance(Group group, float x, float y, float smoothRadius) {
        float result = Float.POSITIVE_INFINITY;
        for (Ring ring : group.rings) {
            float distance = capsuleDistance(ring.shape, x, y) - ring.distance;
            result = Float.isInfinite(result) ? distance
                    : smoothMinimum(result, distance, smoothRadius);
        }
        return result;
    }

    static float capsuleDistance(Shape shape, float x, float y) {
        float radius = Math.min(shape.width, shape.height) / 2f;
        float qx = Math.abs(x - shape.cx) - (shape.width / 2f - radius);
        float qy = Math.abs(y - shape.cy) - (shape.height / 2f - radius);
        float outsideX = Math.max(qx, 0f);
        float outsideY = Math.max(qy, 0f);
        return (float) Math.hypot(outsideX, outsideY)
                + Math.min(Math.max(qx, qy), 0f) - radius;
    }

    static float smoothMinimum(float first, float second, float radius) {
        if (radius <= 0f) return Math.min(first, second);
        float h = Math.max(0f, Math.min(1f,
                .5f + .5f * (second - first) / radius));
        return second + (first - second) * h - radius * h * (1f - h);
    }
}

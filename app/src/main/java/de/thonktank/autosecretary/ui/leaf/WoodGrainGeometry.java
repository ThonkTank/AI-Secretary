package de.thonktank.autosecretary.ui.leaf;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Pure geometry for the shared wood-grain renderer. Coordinates are pixels. */
final class WoodGrainGeometry {
    static final float SMOOTH_MIN_DP = 3.5f;

    enum Relation { SEPARATE, INTERSECTS, ENCLOSES }

    static final class Shape {
        final int element;
        final float cx, cy, width, height;
        final int level;

        Shape(int element, float cx, float cy, float width, float height, int level) {
            this.element = element;
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
        List<Ring> contours = new ArrayList<>();
        for (Shape shape : shapes) {
            int count = Math.min(shape.level,
                    maximumRingCount(boxWidth, boxHeight, shape, density));
            for (int index = 0; index < count; index++)
                contours.add(new Ring(shape, ringDistance(density, index)));
        }
        contours.sort(Comparator.comparingDouble((Ring ring) -> ring.distance).reversed());
        List<Group> groups = new ArrayList<>();
        for (Ring ring : contours) {
            Group destination = null;
            for (Group candidate : groups) {
                boolean sameElement = false;
                boolean intersects = false;
                boolean encloses = false;
                for (Ring member : candidate.rings) {
                    if (member.shape.element == ring.shape.element) {
                        sameElement = true;
                        break;
                    }
                    Relation relation = relation(ring, member);
                    intersects |= relation == Relation.INTERSECTS;
                    encloses |= relation == Relation.ENCLOSES;
                }
                if (!sameElement && intersects && !encloses) {
                    destination = candidate;
                    break;
                }
            }
            if (destination == null) {
                destination = new Group();
                groups.add(destination);
            }
            destination.rings.add(ring);
        }
        return groups;
    }

    static Relation relation(Ring first, Ring second) {
        float dx = second.shape.cx - first.shape.cx;
        float dy = second.shape.cy - first.shape.cy;
        float centerDistance = (float) Math.hypot(dx, dy);
        float firstRadius = first.distance + directionalHalfSize(first.shape, dx, dy);
        float secondRadius = second.distance + directionalHalfSize(second.shape, dx, dy);
        if (centerDistance >= firstRadius + secondRadius) return Relation.SEPARATE;
        if (centerDistance + Math.min(firstRadius, secondRadius)
                <= Math.max(firstRadius, secondRadius)) return Relation.ENCLOSES;
        return Relation.INTERSECTS;
    }

    private static float directionalHalfSize(Shape shape, float dx, float dy) {
        float length = (float) Math.hypot(dx, dy);
        if (length == 0f) return Math.max(shape.width, shape.height) / 2f;
        float nx = dx / length;
        float ny = dy / length;
        float halfWidth = shape.width / 2f;
        float halfHeight = shape.height / 2f;
        return (float) Math.sqrt(halfWidth * halfWidth * nx * nx
                + halfHeight * halfHeight * ny * ny);
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

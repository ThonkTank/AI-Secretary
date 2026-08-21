package de.thonktank.autosecretary.ui.leaf;

import android.content.Context;
import android.graphics.PointF;
import android.graphics.drawable.GradientDrawable;

import java.util.Arrays;

/** Immutable leaf outline and the single source of its four corner radii. */
public final class LeafShape {
    public enum Corner { TOP_LEFT, TOP_RIGHT, BOTTOM_RIGHT, BOTTOM_LEFT }

    public final float topLeftDp;
    public final float topRightDp;
    public final float bottomRightDp;
    public final float bottomLeftDp;

    public LeafShape(float topLeftDp, float topRightDp,
                     float bottomRightDp, float bottomLeftDp) {
        this.topLeftDp = nonNegative(topLeftDp);
        this.topRightDp = nonNegative(topRightDp);
        this.bottomRightDp = nonNegative(bottomRightDp);
        this.bottomLeftDp = nonNegative(bottomLeftDp);
    }

    public float[] radii(float density) {
        float tl = topLeftDp * density;
        float tr = topRightDp * density;
        float br = bottomRightDp * density;
        float bl = bottomLeftDp * density;
        return new float[]{tl, tl, tr, tr, br, br, bl, bl};
    }

    public PointF cornerCenter(Corner corner, float width, float height, float density) {
        float radius = radius(corner) * density;
        switch (corner) {
            case TOP_LEFT: return new PointF(radius, radius);
            case TOP_RIGHT: return new PointF(width - radius, radius);
            case BOTTOM_RIGHT: return new PointF(width - radius, height - radius);
            case BOTTOM_LEFT: return new PointF(radius, height - radius);
            default: throw new IllegalArgumentException("Unknown corner " + corner);
        }
    }

    public GradientDrawable drawable(Context context, int fill, int edge, float strokeDp) {
        float density = context.getResources().getDisplayMetrics().density;
        GradientDrawable result = new GradientDrawable();
        result.setColor(fill);
        result.setCornerRadii(radii(density));
        result.setStroke(Math.max(1, Math.round(strokeDp * density)), edge);
        return result;
    }

    private float radius(Corner corner) {
        switch (corner) {
            case TOP_LEFT: return topLeftDp;
            case TOP_RIGHT: return topRightDp;
            case BOTTOM_RIGHT: return bottomRightDp;
            case BOTTOM_LEFT: return bottomLeftDp;
            default: throw new IllegalArgumentException("Unknown corner " + corner);
        }
    }

    private static float nonNegative(float value) { return Math.max(0f, value); }

    @Override public boolean equals(Object other) {
        if (!(other instanceof LeafShape)) return false;
        LeafShape shape = (LeafShape) other;
        return Arrays.equals(radii(1f), shape.radii(1f));
    }

    @Override public int hashCode() { return Arrays.hashCode(radii(1f)); }
}

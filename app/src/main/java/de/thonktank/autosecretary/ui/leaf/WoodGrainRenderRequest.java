package de.thonktank.autosecretary.ui.leaf;

import de.thonktank.autosecretary.DayPalette;

import android.graphics.RectF;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Immutable geometry input in the renderer's local coordinate system. */
final class WoodGrainRenderRequest {
    static final class Anchor {
        final RectF bounds;
        final int level;

        Anchor(RectF bounds, int level) {
            this.bounds = new RectF(bounds);
            this.level = Math.max(0, level);
        }
    }

    final int width;
    final int height;
    final float density;
    final List<Anchor> anchors;
    final boolean corner;
    final float cornerRatio;
    final float cornerX;
    final float cornerY;
    final String key;

    private WoodGrainRenderRequest(int width, int height, float density,
                                   List<Anchor> anchors, boolean corner,
                                   float cornerRatio, float cornerX, float cornerY) {
        this.width = width;
        this.height = height;
        this.density = density;
        this.anchors = Collections.unmodifiableList(new ArrayList<>(anchors));
        this.corner = corner;
        this.cornerRatio = Math.max(0f, Math.min(1f, cornerRatio));
        this.cornerX = cornerX;
        this.cornerY = cornerY;
        this.key = createKey();
    }

    static WoodGrainRenderRequest anchors(int width, int height, float density,
                                          List<Anchor> anchors) {
        return new WoodGrainRenderRequest(width, height, density, anchors,
                false, 0f, 0f, 0f);
    }

    static WoodGrainRenderRequest corner(int width, int height, float density, float ratio,
                                         float centerX, float centerY) {
        return new WoodGrainRenderRequest(width, height, density,
                Collections.emptyList(), true, ratio, centerX, centerY);
    }

    private String createKey() {
        StringBuilder value = new StringBuilder().append(width).append('x').append(height)
                .append('@').append(String.format(Locale.ROOT, "%.3f", density));
        if (corner) return value.append("|corner,")
                .append(Math.round(cornerRatio * 10_000f)).append(',')
                .append(Math.round(cornerX * 10f)).append(',')
                .append(Math.round(cornerY * 10f)).toString();
        for (Anchor anchor : anchors)
            value.append('|').append(Math.round(anchor.bounds.centerX() * 10f)).append(',')
                    .append(Math.round(anchor.bounds.centerY() * 10f)).append(',')
                    .append(Math.round(anchor.bounds.width() * 10f)).append(',')
                    .append(Math.round(anchor.bounds.height() * 10f)).append(',')
                    .append(anchor.level);
        return value.toString();
    }
}

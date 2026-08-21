package de.thonktank.autosecretary.ui.leaf;

import android.view.View;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable description of grain sources; geometry is resolved by LeafSurface after layout. */
public abstract class GrainSpec {
    final List<View> fadedText;

    private GrainSpec(List<? extends View> fadedText) {
        this.fadedText = immutableViews(fadedText);
    }

    public static Corner corner(LeafShape.Corner corner, float ratio,
                                List<? extends View> fadedText) {
        return new Corner(corner, ratio, fadedText);
    }

    public static Anchors anchors(List<Anchor> anchors, List<? extends View> fadedText) {
        return new Anchors(anchors, fadedText);
    }

    public static Anchor anchor(View target, int level) {
        return new Anchor(target, level, 0f, 0f);
    }

    public static Anchor sizedAnchor(View target, float width, float height, int level) {
        return new Anchor(target, level, width, height);
    }

    public static final class Corner extends GrainSpec {
        final LeafShape.Corner corner;
        final float ratio;

        private Corner(LeafShape.Corner corner, float ratio, List<? extends View> fadedText) {
            super(fadedText);
            if (corner == null) throw new IllegalArgumentException("corner == null");
            this.corner = corner;
            this.ratio = Math.max(0f, Math.min(1f, ratio));
        }
    }

    public static final class Anchors extends GrainSpec {
        final List<Anchor> anchors;

        private Anchors(List<Anchor> anchors, List<? extends View> fadedText) {
            super(fadedText);
            this.anchors = Collections.unmodifiableList(new ArrayList<>(anchors));
        }
    }

    public static final class Anchor {
        final View target;
        final int level;
        final float width;
        final float height;

        private Anchor(View target, int level, float width, float height) {
            if (target == null) throw new IllegalArgumentException("target == null");
            this.target = target;
            this.level = Math.max(0, level);
            this.width = Math.max(0f, width);
            this.height = Math.max(0f, height);
        }
    }

    private static List<View> immutableViews(List<? extends View> values) {
        return Collections.unmodifiableList(new ArrayList<>(values));
    }
}

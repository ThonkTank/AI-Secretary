package de.thonktank.autosecretary.ui.leaf;

import android.graphics.Path;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable paths published by the background geometry pipeline. */
final class WoodGrainRenderData {
    static final WoodGrainRenderData EMPTY = new WoodGrainRenderData(Collections.emptyList());

    static final class Stroke {
        final Path path;
        final float alpha;
        final float width;
        final int segmentCount;

        Stroke(Path path, float alpha, float width, int segmentCount) {
            this.path = path;
            this.alpha = alpha;
            this.width = width;
            this.segmentCount = segmentCount;
        }
    }

    final List<Stroke> strokes;
    final int estimatedBytes;

    WoodGrainRenderData(List<Stroke> strokes) {
        this.strokes = Collections.unmodifiableList(new ArrayList<>(strokes));
        long bytes = 128;
        for (Stroke stroke : strokes) bytes += 96L + stroke.segmentCount * 24L;
        estimatedBytes = (int) Math.min(Integer.MAX_VALUE, bytes);
    }

}

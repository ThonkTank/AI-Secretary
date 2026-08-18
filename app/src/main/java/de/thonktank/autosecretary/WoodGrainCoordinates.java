package de.thonktank.autosecretary;

import android.graphics.RectF;
import android.view.View;
import android.view.ViewParent;

import java.util.ArrayList;
import java.util.List;

/** Captures final rectangles before they cross the renderer boundary. */
final class WoodGrainCoordinates {
    private WoodGrainCoordinates() { }

    static RectF bounds(View renderer, View target) {
        return centeredBounds(renderer, target, target.getWidth(), target.getHeight());
    }

    static RectF centeredBounds(View renderer, View target, float width, float height) {
        float[] rendererLocation = layoutOrigin(renderer);
        float[] targetLocation = layoutOrigin(target);
        float centerX = targetLocation[0] - rendererLocation[0] + target.getWidth() / 2f;
        float centerY = targetLocation[1] - rendererLocation[1] + target.getHeight() / 2f;
        return new RectF(centerX - width / 2f, centerY - height / 2f,
                centerX + width / 2f, centerY + height / 2f);
    }

    static List<RectF> visibleBounds(View renderer, List<? extends View> views) {
        List<RectF> result = new ArrayList<>();
        for (View view : views)
            if (view != null && view.isShown() && view.getWidth() > 0 && view.getHeight() > 0)
                result.add(bounds(renderer, view));
        return result;
    }

    private static float[] layoutOrigin(View view) {
        float x = 0f;
        float y = 0f;
        View current = view;
        while (current != null) {
            x += current.getLeft();
            y += current.getTop();
            ViewParent parent = current.getParent();
            if (!(parent instanceof View)) break;
            View parentView = (View) parent;
            x -= parentView.getScrollX();
            y -= parentView.getScrollY();
            current = parentView;
        }
        return new float[]{x, y};
    }
}

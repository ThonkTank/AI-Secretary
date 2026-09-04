package de.thonktank.autosecretary.ui.leaf;

import android.graphics.RectF;
import android.text.Layout;
import android.view.Gravity;
import android.view.View;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Resolves the visible geometry that locally softens wood grain. */
public interface GrainOcclusion {
    List<RectF> resolve(LeafSurface surface);

    static GrainOcclusion text(TextView text) {
        if (text == null) throw new IllegalArgumentException("text == null");
        return surface -> textLines(surface, text);
    }

    static GrainOcclusion bounds(View view) {
        if (view == null) throw new IllegalArgumentException("view == null");
        return surface -> {
            if (!visible(view)) return Collections.emptyList();
            RectF bounds = surface.localBounds(view);
            if (!surface.clipToVisibleBounds(bounds, view)) return Collections.emptyList();
            return Collections.singletonList(bounds);
        };
    }

    private static List<RectF> textLines(LeafSurface surface, TextView text) {
        Layout layout = text.getLayout();
        if (!visible(text) || layout == null || layout.getLineCount() == 0)
            return Collections.emptyList();
        RectF viewBounds = surface.localBounds(text);
        float originX = viewBounds.left + text.getCompoundPaddingLeft() - text.getScrollX();
        float originY = viewBounds.top + text.getExtendedPaddingTop()
                + verticalOffset(text, layout) - text.getScrollY();
        CharSequence rendered = layout.getText();
        List<RectF> result = new ArrayList<>();
        for (int line = 0; line < layout.getLineCount(); line++) {
            int start = layout.getLineStart(line);
            int end = layout.getLineVisibleEnd(line);
            if (layout.getEllipsisCount(line) == 0 && blank(rendered, start, end)) continue;
            float left = originX + Math.min(layout.getLineLeft(line), layout.getLineRight(line));
            float right = originX + Math.max(layout.getLineLeft(line), layout.getLineRight(line));
            if (right <= left) continue;
            RectF bounds = new RectF(left, originY + layout.getLineTop(line),
                    right, originY + layout.getLineBottom(line));
            if (bounds.intersect(viewBounds)
                    && surface.clipToVisibleBounds(bounds, text)) result.add(bounds);
        }
        return Collections.unmodifiableList(result);
    }

    private static float verticalOffset(TextView text, Layout layout) {
        int available = text.getHeight() - text.getExtendedPaddingTop()
                - text.getExtendedPaddingBottom();
        int extra = Math.max(0, available - layout.getHeight());
        int gravity = text.getGravity() & Gravity.VERTICAL_GRAVITY_MASK;
        if (gravity == Gravity.BOTTOM) return extra;
        if (gravity == Gravity.CENTER_VERTICAL) return extra / 2f;
        return 0f;
    }

    private static boolean blank(CharSequence text, int start, int end) {
        for (int index = Math.max(0, start); index < Math.min(text.length(), end); index++) {
            char value = text.charAt(index);
            if (!Character.isWhitespace(value) && !Character.isSpaceChar(value)
                    && value != '\u200b') return false;
        }
        return true;
    }

    private static boolean visible(View view) {
        return view.isShown() && view.getWidth() > 0 && view.getHeight() > 0;
    }
}

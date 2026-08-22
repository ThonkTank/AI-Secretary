package de.thonktank.autosecretary.presentation.alltasks;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextPaint;
import android.text.style.ReplacementSpan;

import androidx.annotation.NonNull;

import de.thonktank.autosecretary.DayPalette;
import de.thonktank.autosecretary.UiStyle;

import java.util.Locale;

/** Applies the characterized rounded search marker without owning any row hierarchy. */
final class AllTasksSearchHighlighter {
    private final UiStyle style;

    AllTasksSearchHighlighter(UiStyle style) { this.style = style; }

    CharSequence highlight(String value, String needle, DayPalette palette) {
        if (needle == null || needle.isEmpty()) return value;
        SpannableString result = new SpannableString(value);
        String haystack = value.toLowerCase(Locale.GERMAN);
        int from = 0;
        while (from < haystack.length()) {
            int start = haystack.indexOf(needle, from);
            if (start < 0) break;
            int end = start + needle.length();
            result.setSpan(new RoundedHighlightSpan(UiStyle.alpha(palette.light, .34f),
                            style.dp(3), style.dp(1)), start, end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            from = end;
        }
        return result;
    }

    private static final class RoundedHighlightSpan extends ReplacementSpan {
        private final int color;
        private final float radius;
        private final float padding;

        RoundedHighlightSpan(int color, float radius, float padding) {
            this.color = color;
            this.radius = radius;
            this.padding = padding;
        }

        @Override public int getSize(@NonNull Paint paint, CharSequence text, int start, int end,
                                     Paint.FontMetricsInt metrics) {
            return Math.round(paint.measureText(text, start, end) + padding * 2);
        }

        @Override public void draw(@NonNull Canvas canvas, CharSequence text, int start, int end,
                                   float x, int top, int y, int bottom,
                                   @NonNull Paint paint) {
            int oldColor = paint.getColor();
            float width = paint.measureText(text, start, end);
            paint.setColor(color);
            canvas.drawRoundRect(new RectF(x, top, x + width + padding * 2, bottom),
                    radius, radius, paint);
            paint.setColor(oldColor);
            canvas.drawText(text, start, end, x + padding, y, paint);
        }
    }
}

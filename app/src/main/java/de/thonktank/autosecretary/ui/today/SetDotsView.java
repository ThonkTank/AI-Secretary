package de.thonktank.autosecretary.ui.today;

import de.thonktank.autosecretary.*;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.animation.DecelerateInterpolator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Quiet, non-scrolling progress display for set-based repetition steps. */
public final class SetDotsView extends View {
    static final int MAX_DOTS_PER_ROW = 20;
    private final UiStyle style;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private String stepId;
    private int slots;
    private List<Integer> repetitions = Collections.emptyList();
    private int selected = -1;
    private DayPalette palette;
    private ValueAnimator animator;
    private int animatedSlot = -1;
    private float fillProgress = 1f;

    public SetDotsView(Context context) {
        super(context);
        style = new UiStyle(context);
        setId(R.id.set_dots);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        setFocusable(false);
    }

    public void bind(String boundStepId, int slotCount, List<Integer> values,
                     int selectedIndex, DayPalette colors) {
        int previousCount = stepId != null && stepId.equals(boundStepId)
                ? repetitions.size() : 0;
        stepId = boundStepId;
        slots = Math.max(0, slotCount);
        repetitions = Collections.unmodifiableList(new ArrayList<>(values));
        selected = selectedIndex;
        palette = colors;
        setContentDescription(description());
        if (repetitions.size() > previousCount) animateFill(repetitions.size() - 1);
        else {
            stopAnimation();
            animatedSlot = -1;
            fillProgress = 1f;
            invalidate();
        }
        requestLayout();
    }

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int desiredWidth = style.dp(220);
        int desiredHeight = style.dp(Math.max(24, rowCount() * 20 + 4));
        setMeasuredDimension(resolveSize(desiredWidth, widthMeasureSpec),
                resolveSize(desiredHeight, heightMeasureSpec));
    }

    @Override protected void onDraw(Canvas canvas) {
        if (palette == null || slots == 0) return;
        int rows = rowCount();
        float rowHeight = getHeight() / (float) rows;
        for (int row = 0; row < rows; row++) {
            int first = row * MAX_DOTS_PER_ROW;
            int count = Math.min(MAX_DOTS_PER_ROW, slots - first);
            float diameter = style.dp(8);
            float currentDiameter = style.dp(13);
            float widest = Math.max(diameter, currentDiameter);
            float maximumPitch = style.dp(15);
            float pitch = count <= 1 ? 0f : Math.min(maximumPitch,
                    Math.max(diameter, (getWidth() - widest) / (count - 1f)));
            float used = widest + pitch * Math.max(0, count - 1);
            float start = (getWidth() - used) / 2f + widest / 2f;
            float centerY = rowHeight * row + rowHeight / 2f;
            for (int offset = 0; offset < count; offset++) {
                int index = first + offset;
                float centerX = start + pitch * offset;
                drawDot(canvas, index, centerX, centerY);
            }
        }
    }

    private void drawDot(Canvas canvas, int index, float centerX, float centerY) {
        boolean complete = index < repetitions.size();
        boolean current = index == repetitions.size() && index < slots;
        float progress = index == animatedSlot ? fillProgress : 1f;
        paint.setStrokeWidth(style.dp(2));
        if (complete) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(palette.accent);
            canvas.drawCircle(centerX, centerY, style.dp(4) * progress, paint);
        } else if (current) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(palette.leaf1);
            canvas.drawCircle(centerX, centerY, style.dp(6.5f), paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setColor(palette.accent);
            canvas.drawCircle(centerX, centerY, style.dp(5.5f), paint);
        } else {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(UiStyle.alpha(palette.dot, .38f));
            canvas.drawCircle(centerX, centerY, style.dp(4), paint);
        }
        if (index == selected) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(style.dp(1.5f));
            paint.setColor(palette.accent);
            RectF ring = new RectF(centerX - style.dp(7), centerY - style.dp(7),
                    centerX + style.dp(7), centerY + style.dp(7));
            canvas.drawOval(ring, paint);
        }
    }

    @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClassName(android.widget.ProgressBar.class.getName());
        if (slots > 0) info.setRangeInfo(AccessibilityNodeInfo.RangeInfo.obtain(
                AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_INT, 0, slots,
                Math.min(slots, repetitions.size())));
    }

    int rowCount() {
        return Math.max(1, (slots + MAX_DOTS_PER_ROW - 1) / MAX_DOTS_PER_ROW);
    }

    private String description() {
        if (slots <= 0) return "";
        int done = Math.min(slots, repetitions.size());
        StringBuilder value = new StringBuilder(getContext().getString(
                R.string.content_sets_progress, done, slots));
        if (done < slots) value.append(", ").append(getContext().getString(
                R.string.content_set_current, done + 1));
        if (selected >= 0 && selected < repetitions.size()) value.append(", ").append(
                getContext().getString(R.string.content_set_selected,
                        selected + 1, repetitions.get(selected)));
        return value.toString();
    }

    private void animateFill(int index) {
        stopAnimation();
        animatedSlot = index;
        fillProgress = 0f;
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(palette.motion.dewDurationMs);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(value -> {
            fillProgress = (float) value.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    private void stopAnimation() {
        if (animator != null) animator.cancel();
        animator = null;
    }

    @Override protected void onDetachedFromWindow() {
        stopAnimation();
        super.onDetachedFromWindow();
    }
}

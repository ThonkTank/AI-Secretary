package de.thonktank.autosecretary;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.animation.DecelerateInterpolator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.IntConsumer;

/** Compact, horizontally scrollable set bars exposed as one accessibility group. */
public final class SetBarsView extends View {
    private static final int ACTION_EDIT_BASE = 0x01020000;
    private final UiStyle style;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private String stepId;
    private int slots;
    private List<Integer> actual = Collections.emptyList();
    private int selected = -1;
    private DayPalette palette;
    private IntConsumer listener = ignored -> { };
    private ValueAnimator animator;
    private int animatedSlot = -1;
    private float fillProgress = 1f;

    public SetBarsView(Context context) {
        super(context);
        style = new UiStyle(context);
        setId(R.id.set_bars);
        setFocusable(true);
        setClickable(true);
        setMinimumHeight(style.dp(44));
    }

    public void bind(String boundStepId, int slotCount, List<Integer> values,
                     int selectedIndex, DayPalette colors, IntConsumer edits) {
        int previousCount = stepId != null && stepId.equals(boundStepId) ? actual.size() : 0;
        stepId = boundStepId;
        slots = slotCount;
        actual = Collections.unmodifiableList(new ArrayList<>(values));
        selected = selectedIndex;
        palette = colors;
        listener = edits == null ? ignored -> { } : edits;
        setEnabled(!actual.isEmpty());
        setContentDescription(description());
        if (actual.size() > previousCount) animateFill(actual.size() - 1);
        else { animatedSlot = -1; fillProgress = 1f; invalidate(); }
        requestLayout();
    }

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int desiredWidth = style.dp(Math.max(1, slots) * 30 - 8);
        setMeasuredDimension(resolveSize(desiredWidth, widthMeasureSpec),
                resolveSize(style.dp(44), heightMeasureSpec));
    }

    @Override protected void onDraw(Canvas canvas) {
        if (palette == null) return;
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(style.serif);
        paint.setTextSize(style.dp(13));
        float textY = style.dp(14);
        float barY = style.dp(31);
        for (int index = 0; index < slots; index++) {
            float left = style.dp(index * 30);
            float center = left + style.dp(11);
            boolean saved = index < actual.size();
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(saved ? palette.ink2 : palette.done);
            canvas.drawText(saved ? String.valueOf(actual.get(index)) : "–",
                    center, textY, paint);
            float fraction = index == animatedSlot ? fillProgress : 1f;
            paint.setColor(saved ? palette.accent : UiStyle.alpha(palette.dot, .4f));
            RectF bar = new RectF(left, barY,
                    left + style.dp(22) * fraction, barY + style.dp(5));
            canvas.drawRoundRect(bar, style.dp(3), style.dp(3), paint);
            if (index == selected) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(style.dp(1.5f));
                paint.setColor(palette.accent);
                RectF outline = new RectF(left - style.dp(2), barY - style.dp(2),
                        left + style.dp(24), barY + style.dp(7));
                canvas.drawRoundRect(outline, style.dp(4), style.dp(4), paint);
            }
        }
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled()) return false;
        if (event.getActionMasked() == MotionEvent.ACTION_UP) {
            int index = Math.max(0, Math.min(slots - 1,
                    Math.round((event.getX() - style.dp(11)) / style.dp(30f))));
            if (index < actual.size()) {
                listener.accept(index);
                performClick();
            }
            return true;
        }
        return event.getActionMasked() == MotionEvent.ACTION_DOWN || super.onTouchEvent(event);
    }

    @Override public boolean performClick() {
        super.performClick();
        return true;
    }

    @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClassName(View.class.getName());
        info.setContentDescription(description());
        for (int index = 0; index < actual.size(); index++)
            info.addAction(new AccessibilityNodeInfo.AccessibilityAction(
                    ACTION_EDIT_BASE + index,
                    getContext().getString(R.string.content_edit_set, index + 1)));
    }

    @Override public boolean performAccessibilityAction(int action, Bundle arguments) {
        int index = action - ACTION_EDIT_BASE;
        if (index >= 0 && index < actual.size()) {
            listener.accept(index);
            return true;
        }
        return super.performAccessibilityAction(action, arguments);
    }

    private String description() {
        if (slots <= 0) return "";
        StringBuilder value = new StringBuilder();
        for (int index = 0; index < slots; index++) {
            if (value.length() > 0) value.append(", ");
            if (index < actual.size()) value.append(getContext().getString(
                    R.string.content_set_value, index + 1, actual.get(index)));
            else value.append(getContext().getString(R.string.content_set_open, index + 1));
        }
        return value.toString();
    }

    private void animateFill(int index) {
        if (animator != null) animator.cancel();
        animatedSlot = index;
        fillProgress = 0f;
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(palette.motion.stateChangeDurationMs);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(value -> {
            fillProgress = (float) value.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    @Override protected void onDetachedFromWindow() {
        if (animator != null) animator.cancel();
        animator = null;
        super.onDetachedFromWindow();
    }
}

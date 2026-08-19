package de.thonktank.autosecretary;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.customview.widget.ExploreByTouchHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.IntConsumer;

/** Compact, horizontally scrollable set bars with one virtual action node per saved set. */
public final class SetBarsView extends View {
    private final UiStyle style;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final SetAccessibilityHelper accessibility;
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
        // The host routes keyboard focus; only its virtual saved-set nodes are actions.
        setClickable(false);
        setMinimumHeight(style.dp(44));
        accessibility = new SetAccessibilityHelper(this);
        ViewCompat.setAccessibilityDelegate(this, accessibility);
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
        accessibility.invalidateRoot();
        if (actual.size() > previousCount) animateFill(actual.size() - 1);
        else {
            stopAnimation();
            animatedSlot = -1;
            fillProgress = 1f;
            invalidate();
        }
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

    @Override public boolean dispatchHoverEvent(MotionEvent event) {
        return accessibility.dispatchHoverEvent(event) || super.dispatchHoverEvent(event);
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        return accessibility.dispatchKeyEvent(event) || super.dispatchKeyEvent(event);
    }

    @Override protected void onFocusChanged(boolean gainFocus, int direction,
                                             Rect previouslyFocusedRect) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect);
        accessibility.onFocusChanged(gainFocus, direction, previouslyFocusedRect);
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

    private final class SetAccessibilityHelper extends ExploreByTouchHelper {
        SetAccessibilityHelper(View host) { super(host); }

        @Override protected int getVirtualViewAt(float x, float y) {
            if (y < 0 || y > getHeight() || actual.isEmpty()) return INVALID_ID;
            int index = Math.max(0, Math.min(slots - 1,
                    Math.round((x - style.dp(11)) / style.dp(30f))));
            return index < interactiveSetCount() ? index : INVALID_ID;
        }

        @Override protected void getVisibleVirtualViews(List<Integer> virtualViewIds) {
            for (int index = 0; index < interactiveSetCount(); index++)
                virtualViewIds.add(index);
        }

        @SuppressWarnings("deprecation") // ExploreByTouchHelper 1.1 requires parent bounds.
        @Override protected void onPopulateNodeForVirtualView(
                int virtualViewId, AccessibilityNodeInfoCompat node) {
            int number = virtualViewId + 1;
            node.setClassName(android.widget.Button.class.getName());
            node.setContentDescription(getContext().getString(R.string.content_set_value,
                    number, actual.get(virtualViewId)) + ", "
                    + getContext().getString(R.string.content_edit_set, number));
            node.setBoundsInParent(touchBounds(virtualViewId));
            node.setClickable(true);
            node.setFocusable(true);
            node.setEnabled(isEnabled());
            node.setSelected(virtualViewId == selected);
            node.addAction(AccessibilityNodeInfoCompat.ACTION_CLICK);
        }

        @Override protected boolean onPerformActionForVirtualView(
                int virtualViewId, int action, Bundle arguments) {
            if (action != AccessibilityNodeInfoCompat.ACTION_CLICK
                    || !isEnabled() || virtualViewId < 0
                    || virtualViewId >= interactiveSetCount()) return false;
            listener.accept(virtualViewId);
            sendEventForVirtualView(virtualViewId,
                    android.view.accessibility.AccessibilityEvent.TYPE_VIEW_CLICKED);
            return true;
        }

        private Rect touchBounds(int index) {
            int center = style.dp(index * 30 + 11);
            int half = style.dp(22);
            return new Rect(center - half, 0, center + half, style.dp(44));
        }

        private int interactiveSetCount() {
            return Math.min(actual.size(), Math.max(0, slots));
        }
    }

}

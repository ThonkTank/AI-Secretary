package de.thonktank.autosecretary.ui.today;

import de.thonktank.autosecretary.*;

import de.thonktank.autosecretary.ui.leaf.WoodGrainView;
import de.thonktank.autosecretary.ui.leaf.GrainOcclusion;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Arrays;
import java.util.List;
import java.util.function.IntConsumer;

import de.thonktank.autosecretary.domain.model.RepetitionProgress;

/** Minus/value/plus control that owns repeat-on-hold gesture state. */
public final class RepStepperView extends LinearLayout {
    private static final long REPEAT_MS = 300L;

    private final UiStyle style;
    private final TextView minusVisual;
    private final TextView value;
    private final TextView plusVisual;
    private final FrameLayout minus;
    private final FrameLayout plus;
    private final RepeatTouchListener minusRepeat;
    private final RepeatTouchListener plusRepeat;
    private IntConsumer listener = ignored -> { };

    public RepStepperView(Context context) {
        super(context);
        style = new UiStyle(context);
        setGravity(Gravity.CENTER_VERTICAL);
        minusVisual = visual("−");
        plusVisual = visual("＋");
        minus = target(minusVisual, R.id.rep_stepper_decrement,
                R.string.content_repetition_less);
        plus = target(plusVisual, R.id.rep_stepper_increment,
                R.string.content_repetition_more);
        minusRepeat = new RepeatTouchListener(minus, -1);
        plusRepeat = new RepeatTouchListener(plus, 1);
        minus.setOnTouchListener(minusRepeat);
        plus.setOnTouchListener(plusRepeat);
        addView(minus, new LayoutParams(style.dp(44), style.dp(44)));
        value = style.serif("0", 26, 0, false, 400);
        value.setId(R.id.rep_stepper_value);
        value.setGravity(Gravity.CENTER);
        value.setMinWidth(style.dp(34));
        LayoutParams number = new LayoutParams(-2, style.dp(44));
        number.setMargins(style.dp(5), 0, style.dp(5), 0);
        addView(value, number);
        addView(plus, new LayoutParams(style.dp(44), style.dp(44)));
    }

    public void bind(int current, DayPalette palette, IntConsumer changes) {
        listener = changes == null ? ignored -> { } : changes;
        value.setText(String.valueOf(current));
        value.setTextColor(palette.ink);
        minusVisual.setTextColor(palette.ink2);
        plusVisual.setTextColor(palette.ink2);
        bindCircle(minusVisual, palette);
        bindCircle(plusVisual, palette);
        minus.setEnabled(current > 0);
        plus.setEnabled(current < RepetitionProgress.MAX_INPUT);
        minus.setAlpha(current > 0 ? 1f : .45f);
        plus.setAlpha(current < RepetitionProgress.MAX_INPUT ? 1f : .45f);
        WoodGrainView.applyTextHalo(value, palette.leaf1);
    }

    List<GrainOcclusion> grainOcclusions() {
        return Arrays.asList(GrainOcclusion.text(minusVisual), GrainOcclusion.text(value),
                GrainOcclusion.text(plusVisual));
    }

    private TextView visual(String text) {
        TextView visual = style.sans(text, 18, 0, false);
        visual.setGravity(Gravity.CENTER);
        return visual;
    }

    private FrameLayout target(TextView visual, int id, int description) {
        FrameLayout target = new FrameLayout(getContext());
        target.setId(id);
        target.setContentDescription(getContext().getString(description));
        AccessibilityRoles.button(target);
        FrameLayout.LayoutParams visualParams = new FrameLayout.LayoutParams(
                style.dp(34), style.dp(34), Gravity.CENTER);
        target.addView(visual, visualParams);
        return target;
    }

    private void bindCircle(TextView visual, DayPalette palette) {
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(Color.TRANSPARENT);
        circle.setStroke(style.dp(1), palette.dot);
        visual.setBackground(circle);
    }

    private final class RepeatTouchListener implements OnTouchListener {
        private final Handler handler = new Handler(Looper.getMainLooper());
        private final View target;
        private final int delta;
        private boolean repeated;
        private final Runnable repeat = new Runnable() {
            @Override public void run() {
                if (!target.isPressed() || !target.isEnabled()) return;
                repeated = true;
                listener.accept(delta);
                handler.postDelayed(this, REPEAT_MS);
            }
        };

        RepeatTouchListener(View target, int delta) {
            this.target = target;
            this.delta = delta;
            target.setOnClickListener(view -> listener.accept(delta));
        }

        @Override public boolean onTouch(View view, MotionEvent event) {
            if (!view.isEnabled()) return false;
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                repeated = false;
                view.setPressed(true);
                handler.postDelayed(repeat, REPEAT_MS);
                return true;
            }
            if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                handler.removeCallbacks(repeat);
                view.setPressed(false);
                if (!repeated) view.performClick();
                return true;
            }
            if (event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                handler.removeCallbacks(repeat);
                view.setPressed(false);
                return true;
            }
            return true;
        }

        void cancel() {
            handler.removeCallbacks(repeat);
            repeated = false;
            target.setPressed(false);
        }
    }

    @Override protected void onDetachedFromWindow() {
        minusRepeat.cancel();
        plusRepeat.cancel();
        super.onDetachedFromWindow();
    }
}

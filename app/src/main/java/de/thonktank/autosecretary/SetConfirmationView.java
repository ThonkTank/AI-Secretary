package de.thonktank.autosecretary;

import android.content.Context;
import android.content.res.ColorStateList;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** Leaf prompt for confirming one actual set without turning the dashboard into an editor. */
public final class SetConfirmationView extends FrameLayout {
    public interface Listener {
        void onConfirm(String stepId, int repetitions);
        void onFinish(String stepId);
        default void onEditProgress(String stepId, List<Integer> repetitions) { }
        void onDismiss();
    }

    private final UiStyle style;
    private final TaskStepSnapshot step;
    private final DayPalette palette;
    private final Listener listener;
    private final EditText repetitions;
    private final TextView inlineError;
    private final LinearLayout card;

    public SetConfirmationView(Context context, TaskStepSnapshot step, DayPalette palette,
                               Listener listener) {
        super(context);
        this.style = new UiStyle(context); this.step = step; this.palette = palette;
        this.listener = listener;
        setBackgroundColor(0x88060c08); setClickable(true); setFocusable(true);
        card = new LinearLayout(context); card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(style.dp(28), style.dp(26), style.dp(28), style.dp(26));
        card.setBackground(new LeafShapeDrawable(palette.leaf1, palette.leaf1Edge, style.dp(1),
                style.dp(10), style.dp(64), style.dp(10), style.dp(64)));
        style.shadow(card, palette, 20, 1f);
        card.addView(style.serif(step.label, 19, palette.accent, true, 300));
        card.addView(style.serif(step.done ? context.getString(R.string.set_progress_edit_title)
                : context.getString(R.string.set_title, step.nextSetNumber(),
                        step.plannedSets), 29, palette.ink, false, 200), margin(6));
        addProgress();
        repetitions = new EditText(context);
        repetitions.setText(step.done ? join(step.actualRepetitions)
                : String.valueOf(step.plannedReps)); repetitions.setTextSize(23);
        repetitions.setTypeface(style.serif); repetitions.setTextColor(palette.ink);
        repetitions.setSingleLine(true); repetitions.setInputType(step.done
                ? InputType.TYPE_CLASS_TEXT : InputType.TYPE_CLASS_NUMBER);
        repetitions.setBackgroundTintList(ColorStateList.valueOf(palette.accent));
        repetitions.setSelection(repetitions.length());
        LinearLayout value = new LinearLayout(context); value.setOrientation(LinearLayout.VERTICAL);
        value.addView(repetitions, new LinearLayout.LayoutParams(style.dp(150), style.dp(48)));
        value.addView(style.sans(context.getString(step.done ? R.string.set_edit_hint
                        : R.string.amount_reps_unit), 14,
                palette.hint, false));
        inlineError = style.sans(context.getString(R.string.err_set_zero), 14,
                palette.bad, true);
        inlineError.setVisibility(GONE);
        value.addView(inlineError);
        repetitions.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence value, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence value, int start, int before, int count) {
                if (positive(value)) inlineError.setVisibility(GONE);
            }
            @Override public void afterTextChanged(Editable value) { }
        });
        card.addView(value, margin(16));
        if (!step.note.isEmpty()) card.addView(style.sans(step.note, 15, palette.ink2, false), margin(14));
        addActions();
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(-1, -2);
        params.leftMargin = style.dp(60); params.rightMargin = style.dp(22);
        params.topMargin = style.dp(280); addView(card, params);
    }

    public boolean handleBack() { listener.onDismiss(); return true; }

    private void addProgress() {
        LinearLayout row = new LinearLayout(getContext()); row.setGravity(Gravity.CENTER_VERTICAL);
        for (int i = 0; i < step.plannedSets; i++) {
            TextView bar = new TextView(getContext());
            android.graphics.drawable.GradientDrawable bg = style.pill(
                    i < step.actualRepetitions.size() ? palette.accent
                            : UiStyle.alpha(palette.dot, .4f), 3);
            bar.setBackground(bg);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(style.dp(22), style.dp(5));
            if (i > 0) params.setMargins(style.dp(8), 0, 0, 0); row.addView(bar, params);
        }
        TextView label = style.serif(getContext().getString(R.string.set_progress,
                step.actualRepetitions.size(), step.plannedSets), 14, palette.muted, true, 300);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(-2, -2);
        labelParams.setMargins(style.dp(12), 0, 0, 0); row.addView(label, labelParams);
        card.addView(row, margin(8));
    }

    private void addActions() {
        LinearLayout actions = new LinearLayout(getContext()); actions.setGravity(Gravity.CENTER_VERTICAL);
        TextView confirm = style.primaryButton(getContext().getString(step.done
                ? R.string.set_progress_save : R.string.set_confirm), palette);
        confirm.setOnClickListener(view -> { if (step.done) edit(); else confirm(); }); actions.addView(confirm,
                new LinearLayout.LayoutParams(-2, style.dp(52)));
        TextView finish = style.sans(getContext().getString(R.string.set_finish), 17,
                palette.ink2, false); finish.setGravity(Gravity.CENTER); finish.setMinHeight(style.dp(48));
        finish.setOnClickListener(view -> listener.onFinish(step.id));
        LinearLayout.LayoutParams finishParams = new LinearLayout.LayoutParams(-2, style.dp(52));
        finishParams.setMargins(style.dp(16), 0, 0, 0);
        if (!step.done) actions.addView(finish, finishParams);
        card.addView(actions, margin(20));
    }

    private void confirm() {
        int value;
        try { value = Integer.parseInt(repetitions.getText().toString()); }
        catch (NumberFormatException error) { value = 0; }
        if (value <= 0) {
            inlineError.setVisibility(VISIBLE);
            repetitions.requestFocus(); return;
        }
        listener.onConfirm(step.id, value);
    }

    private void edit() {
        String raw = repetitions.getText().toString().trim();
        List<Integer> values = new ArrayList<>();
        if (!raw.isEmpty()) for (String part : raw.split("[,; ]+")) {
            try {
                int value = Integer.parseInt(part);
                if (value <= 0) throw new NumberFormatException();
                values.add(value);
            } catch (NumberFormatException error) {
                inlineError.setVisibility(VISIBLE); return;
            }
        }
        if (values.size() > step.plannedSets) { inlineError.setVisibility(VISIBLE); return; }
        listener.onEditProgress(step.id, values);
    }

    private static String join(List<Integer> values) {
        StringBuilder result = new StringBuilder();
        for (Integer value : values) {
            if (result.length() > 0) result.append(", ");
            result.append(value);
        }
        return result.toString();
    }

    private static boolean positive(CharSequence value) {
        try { return Integer.parseInt(value.toString()) > 0; }
        catch (NumberFormatException error) { return false; }
    }

    private LinearLayout.LayoutParams margin(int topDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = style.dp(topDp); return params;
    }
}

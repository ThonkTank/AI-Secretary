package de.thonktank.autosecretary;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import de.thonktank.autosecretary.domain.model.Recurrence;
import de.thonktank.autosecretary.domain.model.StepAmount;
import de.thonktank.autosecretary.domain.model.StepAmountKind;
import de.thonktank.autosecretary.editor.TaskEditorStateReducer;
import de.thonktank.autosecretary.presentation.AndroidUiTextProvider;
import de.thonktank.autosecretary.presentation.StepTextFormatter;

/** Self-contained step-list and step-detail editor embedded by {@link TaskEditorView}. */
final class TaskStepsEditorView extends LinearLayout {
    interface Listener { void onStateChanged(EditorUiState state, boolean rerender); }

    private final UiStyle style;
    private final Listener listener;
    private final StepTextFormatter formatter;
    private EditorUiState state;
    private final DayPalette palette;

    TaskStepsEditorView(Context context, UiStyle style, EditorUiState state,
                        DayPalette palette, Listener listener) {
        super(context);
        this.style = style;
        this.state = state;
        this.palette = palette;
        this.listener = listener;
        this.formatter = new StepTextFormatter(new AndroidUiTextProvider(context));
        setOrientation(VERTICAL);
        setClipChildren(false);
        setClipToPadding(false);
        render();
    }

    private void render() {
        addView(style.serif(getContext().getString(R.string.field_steps_label), 17,
                palette.muted, true, 300), params(-1, -2, 0, 22, 0, 0));
        for (int i = 0; i < state.stepStates.size(); i++) {
            EditorStepState step = state.stepStates.get(i);
            if (step.id.equals(state.expandedStepId)) addExpandedStep(i, step);
            else addCollapsedStep(i, step);
        }
        TextView add = style.sans("＋  " + getContext().getString(R.string.step_add), 15,
                palette.ink2, false);
        add.setGravity(Gravity.CENTER_VERTICAL);
        add.setMinHeight(style.dp(52));
        add.setPadding(style.dp(16), 0, style.dp(16), 0);
        add.setBackground(style.dashed(palette));
        add.setContentDescription(getContext().getString(R.string.step_add));
        add.setOnClickListener(view -> apply(TaskEditorStateReducer.addStep(state), true));
        addView(add, params(-1, -2, 0, 14, 0, 0));
    }

    private void addCollapsedStep(int index, EditorStepState step) {
        LinearLayout row = new LinearLayout(getContext());
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(style.dp(16), style.dp(13), style.dp(10), style.dp(13));
        boolean error = state.errors.contains(TaskEditorValidator.STEP_PREFIX + step.id);
        int edge = error ? palette.bad : palette.leaf2Edge;
        row.setBackground(new LeafShapeDrawable(error ? UiStyle.alpha(palette.bad, .10f)
                : palette.leaf2, edge, style.dp(error ? 2 : 1),
                style.dp(index % 2 == 0 ? 56 : 8), style.dp(index % 2 == 0 ? 8 : 56),
                style.dp(index % 2 == 0 ? 56 : 8), style.dp(index % 2 == 0 ? 8 : 56)));
        row.setRotation(index % 2 == 0 ? -.8f : .9f);
        style.shadow(row, palette, 8, .7f);
        row.addView(style.serif(String.valueOf(index + 1), 16, palette.muted, true, 300),
                new LinearLayout.LayoutParams(style.dp(18), -2));
        LinearLayout words = new LinearLayout(getContext());
        words.setOrientation(VERTICAL);
        words.addView(style.serif(step.text.isEmpty()
                ? getContext().getString(R.string.step_name_hint) : step.text,
                19, error ? palette.bad : palette.ink, false, 300));
        String meta = meta(step);
        if (!meta.isEmpty()) words.addView(style.serif(meta, 14, palette.muted, true, 300));
        if (!step.note.isEmpty()) {
            TextView note = style.sans(step.note, 14, palette.hint, false);
            note.setMaxLines(2);
            note.setEllipsize(android.text.TextUtils.TruncateAt.END);
            words.addView(note);
        }
        row.addView(words, new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(moveButton("↑", () -> moveStep(index, index - 1),
                () -> moveStep(index, 0)));
        row.addView(moveButton("↓", () -> moveStep(index, index + 1),
                () -> moveStep(index, state.stepStates.size() - 1)));
        TextView menu = style.sans("⋮", 20, palette.dot, false);
        menu.setGravity(Gravity.CENTER);
        menu.setMinWidth(style.dp(34));
        menu.setMinHeight(style.dp(48));
        menu.setContentDescription(getContext().getString(R.string.step_menu_edit));
        menu.setOnClickListener(view -> expandStep(step.id));
        row.addView(menu);
        row.setOnClickListener(view -> expandStep(step.id));
        addView(row, params(-1, -2, 0, 14, 0, 0));
        if (error) addView(errorView(R.string.err_step_empty));
    }

    private TextView moveButton(String text, Runnable click, Runnable longClick) {
        TextView view = style.sans(text, 15, palette.dot, false);
        view.setGravity(Gravity.CENTER);
        view.setMinWidth(style.dp(28));
        view.setMinHeight(style.dp(48));
        view.setContentDescription(text.equals("↑") ? "nach oben" : "nach unten");
        view.setOnClickListener(ignored -> click.run());
        view.setOnLongClickListener(ignored -> { longClick.run(); return true; });
        return view;
    }

    private void addExpandedStep(int index, EditorStepState step) {
        LinearLayout card = new LinearLayout(getContext());
        card.setOrientation(VERTICAL);
        card.setPadding(style.dp(20), style.dp(18), style.dp(20), style.dp(18));
        card.setBackground(new LeafShapeDrawable(palette.leaf1, palette.accent, style.dp(1),
                style.dp(56), style.dp(8), style.dp(56), style.dp(8)));
        style.shadow(card, palette, 12, .8f);
        card.addView(style.serif(getContext().getString(R.string.step_marker, index + 1),
                16, palette.accent, true, 300));
        EditText name = compactField(card, R.string.step_name_label, R.string.step_name_hint,
                step.text, value -> updateStep(index, currentStep(index).withText(value), false));
        name.setSingleLine(true);
        if (state.recurrence != Recurrence.ONCE) {
            addCardLabel(card, R.string.step_days_label);
            card.addView(dayPicker(step.weekdayMask,
                    mask -> updateStep(index, currentStep(index).withWeekdayMask(mask), true)));
        }
        addCardLabel(card, R.string.step_amount_label);
        EditorFlowLayout amounts = new EditorFlowLayout(getContext());
        addAmountChip(amounts, R.string.amount_none, StepAmountKind.NONE, step, index);
        addAmountChip(amounts, R.string.amount_sets_reps, StepAmountKind.SETS_REPS, step, index);
        addAmountChip(amounts, R.string.amount_reps, StepAmountKind.REPS, step, index);
        addAmountChip(amounts, R.string.amount_duration, StepAmountKind.DURATION, step, index);
        card.addView(amounts);
        addAmountInputs(card, step, index);
        compactField(card, R.string.step_note_label, R.string.field_note_hint, step.note,
                value -> updateStep(index, currentStep(index).withNote(value), false)).setMinLines(2);
        if (state.errors.contains(TaskEditorValidator.AMOUNT_PREFIX + step.id))
            card.addView(errorView(R.string.err_amount_zero));
        LinearLayout actions = new LinearLayout(getContext());
        actions.setGravity(Gravity.CENTER_VERTICAL);
        TextView done = outlineButton(R.string.step_apply, palette.ink2, palette.dot);
        done.setOnClickListener(view -> expandStep(null));
        actions.addView(done);
        TextView remove = outlineButton(R.string.step_remove, palette.bad,
                UiStyle.alpha(palette.bad, .34f));
        remove.setOnClickListener(view ->
                apply(TaskEditorStateReducer.removeStep(state, index), true));
        LinearLayout.LayoutParams removeParams = new LinearLayout.LayoutParams(-2, style.dp(46));
        removeParams.setMargins(style.dp(16), 0, 0, 0);
        actions.addView(remove, removeParams);
        card.addView(actions, params(-1, -2, 0, 16, 0, 0));
        addView(card, params(-1, -2, 0, 14, 0, 0));
    }

    private void addAmountChip(EditorFlowLayout row, int label, StepAmountKind kind,
                               EditorStepState step, int index) {
        addChip(row, label, step.amount.kind() == kind, () -> updateStep(index,
                step.withAmount(selectedAmount(kind, step.amount)), true));
    }

    private void addAmountInputs(LinearLayout card, EditorStepState step, int index) {
        if (step.amount instanceof StepAmount.None) return;
        LinearLayout row = new LinearLayout(getContext());
        row.setGravity(Gravity.BOTTOM);
        if (step.amount instanceof StepAmount.SetsReps) {
            StepAmount.SetsReps amount = (StepAmount.SetsReps) step.amount;
            row.addView(numberInput(amount.sets, R.string.amount_sets_unit,
                    value -> { EditorStepState current = currentStep(index);
                        StepAmount.SetsReps currentAmount = (StepAmount.SetsReps) current.amount;
                        updateStep(index, current.withAmount(StepAmount.setsReps(
                                value == null ? 0 : value, currentAmount.repetitions)), false); }),
                    new LinearLayout.LayoutParams(0, -2, 1));
            TextView multiply = style.serif("×", 22, palette.muted, false, 300);
            multiply.setGravity(Gravity.CENTER);
            row.addView(multiply, new LinearLayout.LayoutParams(style.dp(34), style.dp(58)));
            row.addView(numberInput(amount.repetitions, R.string.amount_reps_unit,
                    value -> { EditorStepState current = currentStep(index);
                        StepAmount.SetsReps currentAmount = (StepAmount.SetsReps) current.amount;
                        updateStep(index, current.withAmount(StepAmount.setsReps(
                                currentAmount.sets, value == null ? 0 : value)), false); }),
                    new LinearLayout.LayoutParams(0, -2, 1));
        } else if (step.amount instanceof StepAmount.Repetitions) {
            row.addView(numberInput(((StepAmount.Repetitions) step.amount).repetitions,
                    R.string.amount_reps_unit, value -> { EditorStepState current = currentStep(index);
                        updateStep(index, current.withAmount(StepAmount.repetitions(
                                value == null ? 0 : value)), false); }),
                    new LinearLayout.LayoutParams(0, -2, 1));
        } else {
            row.addView(numberInput(((StepAmount.Duration) step.amount).seconds,
                    R.string.amount_seconds_unit, value -> { EditorStepState current = currentStep(index);
                        updateStep(index, current.withAmount(StepAmount.duration(
                                value == null ? 0 : value)), false); }),
                    new LinearLayout.LayoutParams(0, -2, 1));
        }
        card.addView(row, params(-1, -2, 0, 12, 0, 0));
    }

    private static StepAmount selectedAmount(StepAmountKind kind, StepAmount previous) {
        if (kind == StepAmountKind.SETS_REPS) return previous instanceof StepAmount.SetsReps
                ? previous : StepAmount.setsReps(3, 12);
        if (kind == StepAmountKind.REPS) return previous instanceof StepAmount.Repetitions
                ? previous : StepAmount.repetitions(12);
        if (kind == StepAmountKind.DURATION) return previous instanceof StepAmount.Duration
                ? previous : StepAmount.duration(45);
        return StepAmount.none();
    }

    private EditText compactField(LinearLayout parent, int label, int hint, String value,
                                  StringListener listener) {
        LinearLayout wrapper = new LinearLayout(getContext());
        wrapper.setOrientation(VERTICAL);
        wrapper.addView(style.serif(getContext().getString(label), 17, palette.muted, true, 300));
        EditText input = new EditText(getContext());
        input.setHint(hint);
        input.setText(value);
        input.setTextSize(17);
        input.setTextColor(palette.ink);
        input.setHintTextColor(palette.dot);
        input.setTypeface(style.sans);
        input.setPadding(0, style.dp(2), 0, style.dp(6));
        input.setBackgroundTintList(ColorStateList.valueOf(palette.accent));
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setSelection(input.length());
        input.addTextChangedListener(watcher(listener));
        wrapper.addView(input);
        parent.addView(wrapper, params(-1, -2, 0, 10, 0, 0));
        return input;
    }

    private LinearLayout numberInput(Integer value, int unit, IntegerListener listener) {
        LinearLayout wrapper = new LinearLayout(getContext());
        wrapper.setOrientation(VERTICAL);
        EditText input = new EditText(getContext());
        input.setText(value == null || value <= 0 ? "" : String.valueOf(value));
        input.setTextSize(23);
        input.setTypeface(style.serif);
        input.setTextColor(palette.ink);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setBackgroundTintList(ColorStateList.valueOf(palette.accent));
        input.addTextChangedListener(watcher(text -> listener.accept(parseInteger(text))));
        wrapper.addView(input, new LinearLayout.LayoutParams(-1, style.dp(45)));
        wrapper.addView(style.sans(getContext().getString(unit), 14, palette.hint, false));
        return wrapper;
    }

    private LinearLayout dayPicker(int mask, IntListener listener) {
        LinearLayout row = new LinearLayout(getContext());
        row.setGravity(Gravity.CENTER_VERTICAL);
        int[] labels = {R.string.day_mon, R.string.day_tue, R.string.day_wed,
                R.string.day_thu, R.string.day_fri, R.string.day_sat, R.string.day_sun};
        for (int i = 0; i < labels.length; i++) {
            final int bit = 1 << i;
            boolean selected = (mask & bit) != 0;
            TextView day = style.sans(getContext().getString(labels[i]), 14,
                    selected ? palette.accentText : palette.ink, selected);
            day.setGravity(Gravity.CENTER);
            day.setMinWidth(style.dp(38));
            day.setMinHeight(style.dp(48));
            GradientDrawable circle = style.pill(selected ? palette.accent : Color.TRANSPARENT, 19);
            if (!selected) circle.setStroke(style.dp(1), palette.dot);
            day.setBackground(circle);
            day.setOnClickListener(view -> listener.accept(mask ^ bit));
            row.addView(day, new LinearLayout.LayoutParams(0, style.dp(48), 1));
        }
        return row;
    }

    private void addChip(EditorFlowLayout row, int label, boolean selected, Runnable action) {
        String text = getContext().getString(label);
        TextView chip = style.sans(text, 15, selected ? palette.accentText : palette.ink, selected);
        chip.setGravity(Gravity.CENTER);
        chip.setMinHeight(style.dp(48));
        chip.setPadding(style.dp(16), 0, style.dp(16), 0);
        GradientDrawable background = style.pill(selected ? palette.accent
                : Color.TRANSPARENT, 24);
        if (!selected) background.setStroke(style.dp(1), palette.dot);
        chip.setBackground(background);
        chip.setOnClickListener(view -> action.run());
        chip.setContentDescription(text + (selected ? ", ausgewählt" : ""));
        row.addView(chip, new ViewGroup.LayoutParams(-2, style.dp(48)));
    }

    private TextView outlineButton(int label, int textColor, int edge) {
        TextView button = style.sans(getContext().getString(label), 16, textColor, false);
        button.setGravity(Gravity.CENTER);
        button.setPadding(style.dp(20), 0, style.dp(20), 0);
        GradientDrawable background = style.pill(Color.TRANSPARENT, 23);
        background.setStroke(style.dp(1), edge);
        button.setBackground(background);
        return button;
    }

    private void addCardLabel(LinearLayout card, int text) {
        card.addView(style.serif(getContext().getString(text), 16, palette.muted, true, 300),
                params(-1, -2, 0, 14, 0, 8));
    }

    private TextView errorView(int resource) {
        TextView error = style.serif(getContext().getString(resource), 14,
                palette.bad, true, 300);
        error.setPadding(style.dp(12), style.dp(9), style.dp(12), style.dp(9));
        GradientDrawable background = style.pill(UiStyle.alpha(palette.bad, .10f), 10);
        background.setStroke(style.dp(1), UiStyle.alpha(palette.bad, .34f));
        error.setBackground(background);
        error.setLayoutParams(params(-1, -2, 0, 7, 0, 0));
        return error;
    }

    private String meta(EditorStepState step) {
        List<String> values = new ArrayList<>();
        if (step.weekdayMask != 0) {
            String[] days = {"Mo", "Di", "Mi", "Do", "Fr", "Sa", "So"};
            List<String> selected = new ArrayList<>();
            for (int i = 0; i < days.length; i++)
                if ((step.weekdayMask & 1 << i) != 0) selected.add(days[i]);
            values.add(android.text.TextUtils.join(" · ", selected));
        }
        String amount = formatter.format(step.amount, "");
        if (!amount.isEmpty()) values.add(amount);
        return android.text.TextUtils.join(" · ", values);
    }

    private void updateStep(int index, EditorStepState step, boolean rerender) {
        apply(TaskEditorStateReducer.updateStep(state, index, step), rerender);
    }

    private EditorStepState currentStep(int index) { return state.stepStates.get(index); }

    private void moveStep(int from, int to) {
        EditorUiState next = TaskEditorStateReducer.moveStep(state, from, to);
        if (next != state) apply(next, true);
    }

    private void expandStep(String id) {
        apply(TaskEditorStateReducer.expandStep(state, id), true);
    }

    private void apply(EditorUiState next, boolean rerender) {
        state = next;
        listener.onStateChanged(next, rerender);
    }

    private TextWatcher watcher(StringListener listener) {
        return new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                listener.accept(s.toString());
            }
            @Override public void afterTextChanged(Editable s) { }
        };
    }

    private static Integer parseInteger(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        try { return Integer.parseInt(value.trim()); }
        catch (NumberFormatException error) { return 0; }
    }

    private static LayoutParams params(int width, int height, int left, int top,
                                       int right, int bottom) {
        LayoutParams params = new LayoutParams(width, height);
        params.setMargins(left, top, right, bottom);
        return params;
    }

    private interface StringListener { void accept(String value); }
    private interface IntegerListener { void accept(Integer value); }
    private interface IntListener { void accept(int value); }
}

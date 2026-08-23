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

/** Wizard page for the step list and the uncounted step-detail sub-page. */
final class TaskStepsEditorView extends LinearLayout {
    interface Listener { void onStateChanged(EditorUiState state, boolean rerender); }

    private final UiStyle style;
    private final Listener listener;
    private final StepTextFormatter formatter;
    private final DayPalette palette;
    private EditorUiState state;

    TaskStepsEditorView(Context context, UiStyle style, EditorUiState state,
                        DayPalette palette, Listener listener) {
        super(context);
        this.style = style;
        this.state = state;
        this.palette = palette;
        this.listener = listener;
        formatter = new StepTextFormatter(new AndroidUiTextProvider(context));
        setOrientation(VERTICAL);
        setClipChildren(false);
        setClipToPadding(false);
        render();
    }

    private void render() {
        if (state.expandedStepId == null) renderList();
        else renderDetail();
    }

    private void renderList() {
        addView(question(R.string.editor_frage_schritte));
        for (int index = 0; index < state.stepStates.size(); index++)
            addCollapsedStep(index, state.stepStates.get(index));
        TextView add = style.sans("＋  " + getContext().getString(R.string.step_add), 15,
                palette.ink2, false);
        add.setGravity(Gravity.CENTER_VERTICAL);
        add.setMinHeight(style.dp(52));
        add.setPadding(style.dp(16), 0, style.dp(16), 0);
        add.setBackground(style.dashed(palette));
        add.setContentDescription(getContext().getString(R.string.step_add));
        add.setOnClickListener(view -> apply(TaskEditorStateReducer.addStep(state), true));
        addView(add, params(-1, -2, 0, 18, 0, 0));
    }

    private void renderDetail() {
        int index = expandedIndex();
        if (index < 0) return;
        EditorStepState step = state.stepStates.get(index);
        addView(question(R.string.editor_frage_schritt));
        EditText name = input(R.string.step_name_hint, step.text, false,
                value -> updateStep(index, currentStep(index).withText(value), false));
        name.setSingleLine(true);
        addView(name, params(-1, style.dp(48), 0, 22, 0, 0));
        if (state.errors.contains(TaskEditorValidator.STEP_PREFIX + step.id))
            addView(errorView(R.string.err_step_empty));

        if (state.recurrence != Recurrence.ONCE) addDaySchedule(index, step);

        addLabel(R.string.step_amount_label, 24, 10);
        EditorFlowLayout amounts = new EditorFlowLayout(getContext());
        addAmountChip(amounts, R.string.amount_none, StepAmountKind.NONE, step, index);
        addAmountChip(amounts, R.string.amount_sets_reps, StepAmountKind.SETS_REPS, step, index);
        addAmountChip(amounts, R.string.amount_reps, StepAmountKind.REPS, step, index);
        addAmountChip(amounts, R.string.amount_duration, StepAmountKind.DURATION, step, index);
        addView(amounts);
        addAmountInputs(step, index);
        if (state.errors.contains(TaskEditorValidator.AMOUNT_PREFIX + step.id))
            addView(errorView(R.string.err_amount_zero));

        addLabel(R.string.step_note_label, 24, 4);
        EditText note = input(R.string.field_note_hint, step.note, false,
                value -> updateStep(index, currentStep(index).withNote(value), false));
        note.setSingleLine(true);
        addView(note, new LayoutParams(-1, style.dp(48)));
    }

    private void addDaySchedule(int index, EditorStepState step) {
        addLabel(R.string.editor_label_tage_frage, 24, 10);
        EditorFlowLayout choices = new EditorFlowLayout(getContext());
        addChip(choices, R.string.editor_tage_immer,
                step.weekdayMask == 0 && step.intervalDays == 0,
                () -> updateStep(index, currentStep(index).withIntervalDays(0), true));
        addChip(choices, R.string.editor_tage_feste, step.weekdayMask != 0,
                () -> updateStep(index, currentStep(index).withWeekdayMask(
                        currentStep(index).weekdayMask == 0 ? 1 : currentStep(index).weekdayMask),
                        true));
        addChip(choices, R.string.editor_tage_intervall, step.intervalDays != 0,
                () -> updateStep(index, currentStep(index).withIntervalDays(
                        currentStep(index).intervalDays < 2 ? 2
                                : currentStep(index).intervalDays), true));
        addView(choices);
        if (step.weekdayMask != 0)
            addView(dayPicker(step.weekdayMask,
                    mask -> updateStep(index, currentStep(index).withWeekdayMask(mask), true)),
                    params(-1, style.dp(48), 0, 14, 0, 0));
        if (step.intervalDays != 0) {
            LinearLayout interval = new LinearLayout(getContext());
            interval.setGravity(Gravity.CENTER_VERTICAL);
            EditText number = numberField(step.intervalDays,
                    value -> updateStep(index, currentStep(index).withIntervalDays(
                            value == null ? 0 : value), false));
            interval.addView(number, new LinearLayout.LayoutParams(style.dp(96), style.dp(48)));
            TextView unit = style.serif(getContext().getString(R.string.editor_interval_unit),
                    17, palette.muted, true, 300);
            LinearLayout.LayoutParams unitParams = new LinearLayout.LayoutParams(-2, -2);
            unitParams.setMargins(style.dp(12), 0, 0, 0);
            interval.addView(unit, unitParams);
            addView(interval, params(-1, -2, 0, 14, 0, 0));
            if (state.errors.contains(TaskEditorValidator.STEP_INTERVAL_PREFIX + step.id))
                addView(errorView(R.string.err_interval_zero));
        }
    }

    private void addCollapsedStep(int index, EditorStepState step) {
        LinearLayout row = new LinearLayout(getContext());
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(style.dp(16), style.dp(12), style.dp(8), style.dp(12));
        boolean error = state.errors.contains(TaskEditorValidator.STEP_PREFIX + step.id)
                || state.errors.contains(TaskEditorValidator.AMOUNT_PREFIX + step.id)
                || state.errors.contains(TaskEditorValidator.STEP_INTERVAL_PREFIX + step.id);
        row.setBackground(new LeafShapeDrawable(error ? UiStyle.alpha(palette.bad, .10f)
                : palette.leaf2, error ? palette.bad : palette.leaf2Edge,
                style.dp(error ? 2 : 1), style.dp(index % 2 == 0 ? 56 : 8),
                style.dp(index % 2 == 0 ? 8 : 56), style.dp(index % 2 == 0 ? 56 : 8),
                style.dp(index % 2 == 0 ? 8 : 56)));
        row.setRotation(index % 2 == 0 ? -.8f : .9f);
        style.shadow(row, palette, 7, .7f);
        row.addView(style.serif(String.valueOf(index + 1), 16, palette.muted, true, 300),
                new LinearLayout.LayoutParams(style.dp(22), -2));
        LinearLayout words = new LinearLayout(getContext());
        words.setOrientation(VERTICAL);
        words.addView(style.serif(step.text.isEmpty()
                ? getContext().getString(R.string.step_name_hint) : step.text,
                19, error ? palette.bad : palette.ink, false, 400));
        String meta = meta(step);
        if (!meta.isEmpty()) words.addView(style.serif(meta, 14, palette.muted, true, 300));
        row.addView(words, new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(moveButton("↑", () -> moveStep(index, index - 1),
                () -> moveStep(index, 0)));
        row.addView(moveButton("↓", () -> moveStep(index, index + 1),
                () -> moveStep(index, state.stepStates.size() - 1)));
        row.setOnClickListener(view -> expandStep(step.id));
        addView(row, params(-1, -2, 0, 14, 0, 0));
    }

    private TextView question(int resource) {
        return style.serif(getContext().getString(resource), 30, palette.ink, false, 200);
    }

    private void addLabel(int resource, int top, int bottom) {
        addView(style.serif(getContext().getString(resource), 17, palette.muted, true, 300),
                params(-1, -2, 0, top, 0, bottom));
    }

    private void addAmountChip(EditorFlowLayout row, int label, StepAmountKind kind,
                               EditorStepState step, int index) {
        addChip(row, label, step.amount.kind() == kind, () -> updateStep(index,
                currentStep(index).withAmount(selectedAmount(kind, currentStep(index).amount)),
                true));
    }

    private void addAmountInputs(EditorStepState step, int index) {
        if (step.amount instanceof StepAmount.None) return;
        LinearLayout row = new LinearLayout(getContext());
        row.setGravity(Gravity.BOTTOM);
        if (step.amount instanceof StepAmount.SetsReps) {
            StepAmount.SetsReps amount = (StepAmount.SetsReps) step.amount;
            row.addView(numberInput(amount.sets, R.string.amount_sets_unit,
                    value -> updateSets(index, value, true)), new LayoutParams(0, -2, 1));
            TextView multiply = style.serif("×", 22, palette.muted, false, 400);
            multiply.setGravity(Gravity.CENTER);
            row.addView(multiply, new LayoutParams(style.dp(34), style.dp(58)));
            row.addView(numberInput(amount.repetitions, R.string.amount_reps_unit,
                    value -> updateSets(index, value, false)), new LayoutParams(0, -2, 1));
        } else if (step.amount instanceof StepAmount.Repetitions) {
            row.addView(numberInput(((StepAmount.Repetitions) step.amount).repetitions,
                    R.string.amount_reps_unit, value -> updateStep(index,
                            currentStep(index).withAmount(StepAmount.repetitions(orZero(value))),
                            false)), new LayoutParams(0, -2, 1));
        } else {
            row.addView(numberInput(((StepAmount.Duration) step.amount).seconds,
                    R.string.amount_seconds_unit, value -> updateStep(index,
                            currentStep(index).withAmount(StepAmount.duration(orZero(value))),
                            false)), new LayoutParams(0, -2, 1));
        }
        addView(row, params(-1, -2, 0, 12, 0, 0));
    }

    private void updateSets(int index, Integer value, boolean sets) {
        EditorStepState current = currentStep(index);
        StepAmount.SetsReps amount = (StepAmount.SetsReps) current.amount;
        updateStep(index, current.withAmount(StepAmount.setsReps(
                sets ? orZero(value) : amount.sets,
                sets ? amount.repetitions : orZero(value))), false);
    }

    private static int orZero(Integer value) { return value == null ? 0 : value; }

    private static StepAmount selectedAmount(StepAmountKind kind, StepAmount previous) {
        if (kind == StepAmountKind.SETS_REPS) return previous instanceof StepAmount.SetsReps
                ? previous : StepAmount.setsReps(3, 12);
        if (kind == StepAmountKind.REPS) return previous instanceof StepAmount.Repetitions
                ? previous : StepAmount.repetitions(12);
        if (kind == StepAmountKind.DURATION) return previous instanceof StepAmount.Duration
                ? previous : StepAmount.duration(45);
        return StepAmount.none();
    }

    private EditText input(int hint, String value, boolean multiline, StringListener listener) {
        EditText input = new EditText(getContext());
        input.setHint(hint);
        input.setText(value);
        input.setTextSize(17);
        input.setTextColor(palette.ink);
        input.setHintTextColor(palette.dot);
        input.setTypeface(style.sans);
        input.setPadding(0, style.dp(2), 0, style.dp(6));
        input.setBackgroundTintList(ColorStateList.valueOf(palette.accent));
        input.setInputType(InputType.TYPE_CLASS_TEXT
                | (multiline ? InputType.TYPE_TEXT_FLAG_MULTI_LINE : 0));
        input.setSelection(input.length());
        input.addTextChangedListener(watcher(listener));
        return input;
    }

    private EditText numberField(Integer value, IntegerListener listener) {
        EditText input = new EditText(getContext());
        input.setText(value == null || value <= 0 ? "" : String.valueOf(value));
        input.setTextSize(23);
        input.setTypeface(style.serif);
        input.setTextColor(palette.ink);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setBackgroundTintList(ColorStateList.valueOf(palette.accent));
        input.addTextChangedListener(watcher(text -> listener.accept(parseInteger(text))));
        return input;
    }

    private LinearLayout numberInput(Integer value, int unit, IntegerListener listener) {
        LinearLayout wrapper = new LinearLayout(getContext());
        wrapper.setOrientation(VERTICAL);
        wrapper.addView(numberField(value, listener), new LayoutParams(-1, style.dp(45)));
        wrapper.addView(style.sans(getContext().getString(unit), 14, palette.hint, false));
        return wrapper;
    }

    private LinearLayout dayPicker(int mask, IntListener listener) {
        LinearLayout row = new LinearLayout(getContext());
        int[] labels = {R.string.day_mon, R.string.day_tue, R.string.day_wed,
                R.string.day_thu, R.string.day_fri, R.string.day_sat, R.string.day_sun};
        for (int index = 0; index < labels.length; index++) {
            final int bit = 1 << index;
            boolean selected = (mask & bit) != 0;
            TextView day = style.sans(getContext().getString(labels[index]), 14,
                    selected ? palette.accentText : palette.ink, selected);
            day.setGravity(Gravity.CENTER);
            day.setMinHeight(style.dp(48));
            GradientDrawable background = style.pill(selected ? palette.accent
                    : Color.TRANSPARENT, 19);
            if (!selected) background.setStroke(style.dp(1), palette.dot);
            day.setBackground(background);
            day.setOnClickListener(view -> listener.accept(mask ^ bit));
            LayoutParams dayParams = new LayoutParams(0, style.dp(48), 1);
            if (index > 0) dayParams.setMargins(style.dp(8), 0, 0, 0);
            row.addView(day, dayParams);
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

    private TextView moveButton(String text, Runnable click, Runnable longClick) {
        TextView view = style.sans(text, 15, palette.dot, false);
        view.setGravity(Gravity.CENTER);
        view.setMinWidth(style.dp(48));
        view.setMinHeight(style.dp(48));
        view.setContentDescription(text.equals("↑") ? "nach oben" : "nach unten");
        view.setOnClickListener(ignored -> click.run());
        view.setOnLongClickListener(ignored -> { longClick.run(); return true; });
        return view;
    }

    private TextView errorView(int resource) {
        TextView error = style.serif(getContext().getString(resource), 14, palette.bad, true, 300);
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
            for (int index = 0; index < days.length; index++)
                if ((step.weekdayMask & 1 << index) != 0) selected.add(days[index]);
            values.add(android.text.TextUtils.join(" · ", selected));
        } else if (step.intervalDays != 0) {
            values.add("alle " + step.intervalDays + " Tage");
        }
        String amount = formatter.format(step.amount, "");
        if (!amount.isEmpty()) values.add(amount);
        return android.text.TextUtils.join(" · ", values);
    }

    private int expandedIndex() {
        for (int index = 0; index < state.stepStates.size(); index++)
            if (state.stepStates.get(index).id.equals(state.expandedStepId)) return index;
        return -1;
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

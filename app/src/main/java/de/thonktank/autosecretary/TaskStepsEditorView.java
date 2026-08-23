package de.thonktank.autosecretary;

import android.content.Context;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import de.thonktank.autosecretary.domain.model.Recurrence;
import de.thonktank.autosecretary.domain.model.StepAmount;
import de.thonktank.autosecretary.domain.model.StepAmountKind;
import de.thonktank.autosecretary.editor.TaskEditorStateReducer;
import de.thonktank.autosecretary.presentation.AndroidUiTextProvider;
import de.thonktank.autosecretary.presentation.TaskEditorTextFormatter;

/** Wizard page for the step list and the uncounted step-detail sub-page. */
final class TaskStepsEditorView extends LinearLayout {
    interface Listener { void onStateChanged(EditorUiState state, boolean rerender); }

    private final UiStyle style;
    private final Listener listener;
    private final TaskEditorTextFormatter formatter;
    private final TaskEditorControlFactory controls;
    private final DayPalette palette;
    private EditorUiState state;

    TaskStepsEditorView(Context context, UiStyle style, EditorUiState state,
                        DayPalette palette, Listener listener) {
        super(context);
        this.style = style;
        this.state = state;
        this.palette = palette;
        this.listener = listener;
        formatter = new TaskEditorTextFormatter(new AndroidUiTextProvider(context));
        controls = new TaskEditorControlFactory(context, style, palette);
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
        TextView add = style.sans(getContext().getString(R.string.editor_step_add_label,
                getContext().getString(R.string.step_add)), 15,
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
        name.setTag(focusTag("title", step.id));
        name.setSingleLine(true);
        addView(name, params(-1, style.dp(48), 0, 22, 0, 0));
        if (hasIssue(ValidationIssue.Field.STEP_TITLE, step.id))
            addView(errorView(R.string.err_step_empty));

        if (state.recurrence != Recurrence.ONCE) addDaySchedule(index, step);

        addLabel(R.string.step_amount_label, 24, 10);
        EditorFlowLayout amounts = controls.flow();
        addAmountChip(amounts, R.string.amount_none, StepAmountKind.NONE, step, index);
        addAmountChip(amounts, R.string.amount_sets_reps, StepAmountKind.SETS_REPS, step, index);
        addAmountChip(amounts, R.string.amount_reps, StepAmountKind.REPS, step, index);
        addAmountChip(amounts, R.string.amount_duration, StepAmountKind.DURATION, step, index);
        addView(amounts);
        addAmountInputs(step, index);
        if (hasIssue(ValidationIssue.Field.STEP_AMOUNT, step.id))
            addView(errorView(R.string.err_amount_zero));

        addLabel(R.string.step_note_label, 24, 4);
        EditText note = input(R.string.field_note_hint, step.note, false,
                value -> updateStep(index, currentStep(index).withNote(value), false));
        note.setTag(focusTag("note", step.id));
        note.setSingleLine(true);
        addView(note, new LayoutParams(-1, style.dp(48)));
    }

    private void addDaySchedule(int index, EditorStepState step) {
        addLabel(R.string.editor_label_tage_frage, 24, 10);
        EditorFlowLayout choices = controls.flow();
        addChip(choices, R.string.editor_tage_immer,
                step.cadenceMode == StepCadenceMode.ALWAYS,
                () -> updateStep(index, currentStep(index).withCadenceMode(
                        StepCadenceMode.ALWAYS), true));
        addChip(choices, R.string.editor_tage_feste,
                step.cadenceMode == StepCadenceMode.WEEKDAYS,
                () -> updateStep(index, currentStep(index).withCadenceMode(
                        StepCadenceMode.WEEKDAYS), true));
        addChip(choices, R.string.editor_tage_intervall,
                step.cadenceMode == StepCadenceMode.INTERVAL,
                () -> updateStep(index, currentStep(index).withCadenceMode(
                        StepCadenceMode.INTERVAL), true));
        addView(choices);
        if (step.cadenceMode == StepCadenceMode.WEEKDAYS)
            addView(dayPicker(step.weekdayMask,
                    mask -> {
                        if (mask != 0)
                            updateStep(index, currentStep(index).withWeekdayMask(mask), true);
                    }),
                    params(-1, style.dp(48), 0, 14, 0, 0));
        if (step.cadenceMode == StepCadenceMode.INTERVAL) {
            LinearLayout interval = new LinearLayout(getContext());
            interval.setGravity(Gravity.CENTER_VERTICAL);
            EditText number = numberField(step.intervalDays,
                    value -> updateStep(index, currentStep(index).withIntervalDays(value), false));
            number.setTag(focusTag("interval", step.id));
            interval.addView(number, new LinearLayout.LayoutParams(style.dp(96), style.dp(48)));
            TextView unit = style.serif(getContext().getString(R.string.editor_interval_unit),
                    17, palette.muted, true, 300);
            LinearLayout.LayoutParams unitParams = new LinearLayout.LayoutParams(-2, -2);
            unitParams.setMargins(style.dp(12), 0, 0, 0);
            interval.addView(unit, unitParams);
            addView(interval, params(-1, -2, 0, 14, 0, 0));
            if (hasIssue(ValidationIssue.Field.STEP_INTERVAL, step.id))
                addView(errorView(R.string.err_interval_zero));
        }
    }

    private void addCollapsedStep(int index, EditorStepState step) {
        LinearLayout row = new LinearLayout(getContext());
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(style.dp(16), style.dp(12), style.dp(8), style.dp(12));
        boolean error = hasIssue(ValidationIssue.Field.STEP_TITLE, step.id)
                || hasIssue(ValidationIssue.Field.STEP_AMOUNT, step.id)
                || hasIssue(ValidationIssue.Field.STEP_INTERVAL, step.id);
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
        row.addView(moveButton(R.string.editor_move_up_symbol, R.string.a11y_editor_move_up,
                () -> moveStep(index, index - 1),
                () -> moveStep(index, 0)));
        row.addView(moveButton(R.string.editor_move_down_symbol, R.string.a11y_editor_move_down,
                () -> moveStep(index, index + 1),
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
                    value -> updateSets(index, value, true), focusTag("sets", step.id)),
                    new LayoutParams(0, -2, 1));
            TextView multiply = style.serif(getContext().getString(R.string.editor_multiply),
                    22, palette.muted, false, 400);
            multiply.setGravity(Gravity.CENTER);
            row.addView(multiply, new LayoutParams(style.dp(34), style.dp(58)));
            row.addView(numberInput(amount.repetitions, R.string.amount_reps_unit,
                    value -> updateSets(index, value, false), focusTag("repetitions", step.id)),
                    new LayoutParams(0, -2, 1));
        } else if (step.amount instanceof StepAmount.Repetitions) {
            row.addView(numberInput(((StepAmount.Repetitions) step.amount).repetitions,
                    R.string.amount_reps_unit, value -> updateStep(index,
                            currentStep(index).withAmount(StepAmount.repetitions(orZero(value))),
                            false), focusTag("repetitions", step.id)),
                    new LayoutParams(0, -2, 1));
        } else {
            row.addView(numberInput(((StepAmount.Duration) step.amount).seconds,
                    R.string.amount_seconds_unit, value -> updateStep(index,
                            currentStep(index).withAmount(StepAmount.duration(orZero(value))),
                            false), focusTag("duration", step.id)),
                    new LayoutParams(0, -2, 1));
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

    private EditText input(int hint, String value, boolean multiline,
                           TaskEditorControlFactory.StringListener listener) {
        return controls.input(hint, value, multiline, 17, false, listener);
    }

    private EditText numberField(Integer value,
                                 TaskEditorControlFactory.IntegerListener listener) {
        return controls.numberField(value, listener);
    }

    private LinearLayout numberInput(Integer value, int unit,
                                     TaskEditorControlFactory.IntegerListener listener,
                                     String focusTag) {
        return controls.numberInput(value, unit, listener, focusTag);
    }

    private LinearLayout dayPicker(int mask, TaskEditorControlFactory.IntListener listener) {
        return controls.dayPicker(mask, listener);
    }

    private void addChip(EditorFlowLayout row, int label, boolean selected, Runnable action) {
        controls.addChip(row, label, selected, action);
    }

    private TextView moveButton(int symbol, int contentDescription, Runnable click,
                                Runnable longClick) {
        TextView view = style.sans(getContext().getString(symbol), 15, palette.dot, false);
        view.setGravity(Gravity.CENTER);
        view.setMinWidth(style.dp(48));
        view.setMinHeight(style.dp(48));
        view.setContentDescription(getContext().getString(contentDescription));
        view.setOnClickListener(ignored -> click.run());
        view.setOnLongClickListener(ignored -> { longClick.run(); return true; });
        return view;
    }

    private TextView errorView(int resource) {
        return controls.errorView(resource);
    }

    private String meta(EditorStepState step) {
        return formatter.stepMeta(step);
    }

    private int expandedIndex() {
        for (int index = 0; index < state.stepStates.size(); index++)
            if (state.stepStates.get(index).id.equals(state.expandedStepId)) return index;
        return -1;
    }
    private boolean hasIssue(ValidationIssue.Field field, String stepId) {
        return state.issues.contains(ValidationIssue.step(field, stepId));
    }
    private static String focusTag(String field, String stepId) {
        return "step:" + stepId + ':' + field;
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

    private static LayoutParams params(int width, int height, int left, int top,
                                       int right, int bottom) {
        LayoutParams params = new LayoutParams(width, height);
        params.setMargins(left, top, right, bottom);
        return params;
    }
}

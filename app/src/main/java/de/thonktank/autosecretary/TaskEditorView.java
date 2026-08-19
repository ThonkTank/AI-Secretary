package de.thonktank.autosecretary;

import android.app.DatePickerDialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import de.thonktank.autosecretary.domain.model.Recurrence;
import de.thonktank.autosecretary.domain.model.StepAmountKind;
import de.thonktank.autosecretary.domain.model.StepAmount;
import de.thonktank.autosecretary.domain.model.TaskBoundKind;
import de.thonktank.autosecretary.domain.model.TaskSlot;
import de.thonktank.autosecretary.domain.model.TimeOfDay;

/** Full-screen, state-driven editor mounted above the dashboard. */
public final class TaskEditorView extends FrameLayout {
    public interface Listener {
        void onDraftChanged(EditorUiState draft);
        void onSave(EditorUiState draft);
        void onDelete(String taskId);
        void onDismiss();
    }

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.", Locale.GERMANY);
    private final UiStyle style;
    private final Listener listener;
    private final TaskEditorValidator validator = new TaskEditorValidator();
    private final LinearLayout leaf;
    private final ScrollView scroll;
    private final TextView cancel;
    private final TextView contextLabel;
    private final Button save;
    private final TextView discard;
    private final TextView delete;
    private final FrameLayout promptHost;
    private EditorUiState state;
    private EditorUiState lastEmitted;
    private DayPalette palette;
    private LocalDate today;

    public TaskEditorView(Context context, Listener listener) {
        super(context);
        this.listener = listener;
        this.style = new UiStyle(context);
        LayoutInflater.from(context).inflate(R.layout.task_editor_view, this, true);
        leaf = findViewById(R.id.task_editor_leaf);
        scroll = findViewById(R.id.task_editor_scroll);
        cancel = findViewById(R.id.task_editor_cancel);
        contextLabel = findViewById(R.id.task_editor_context);
        save = findViewById(R.id.task_editor_save);
        discard = findViewById(R.id.task_editor_discard);
        delete = findViewById(R.id.task_editor_delete);
        promptHost = findViewById(R.id.task_editor_prompt_host);
        cancel.setOnClickListener(view -> requestClose());
        discard.setOnClickListener(view -> requestClose());
        save.setOnClickListener(view -> requestSave());
        delete.setOnClickListener(view -> showPrompt(EditorUiState.Prompt.DELETE));
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    }

    public void bind(EditorUiState value, DayPalette palette, LocalDate today) {
        if (value == lastEmitted && this.palette == palette
                && this.today != null && this.today.equals(today)) return;
        this.state = value;
        this.palette = palette;
        this.today = today;
        lastEmitted = null;
        render();
    }

    public boolean handleBack() {
        if (state == null || !state.open) return false;
        if (state.prompt != EditorUiState.Prompt.NONE) {
            apply(state.withFeedback(state.errors, EditorUiState.Prompt.NONE,
                    state.storageError), true);
        } else requestClose();
        return true;
    }

    private void render() {
        if (state.loading) return;
        setBackgroundColor(Color.TRANSPARENT);
        cancel.setTextColor(palette.muted);
        discard.setTextColor(palette.ink2);
        delete.setTextColor(palette.bad);
        contextLabel.setText(state.taskId == null ? R.string.editor_ctx_new : R.string.editor_ctx_edit);
        contextLabel.setTextColor(palette.muted);
        delete.setVisibility(state.taskId == null ? GONE : VISIBLE);
        save.setText(state.saving ? getContext().getString(R.string.update_busy)
                : getContext().getString(R.string.action_save));
        save.setEnabled(!state.saving && !state.title.trim().isEmpty());
        save.setAlpha(save.isEnabled() ? 1f : .48f);
        save.setTextColor(palette.accentText);
        save.setBackground(style.pill(palette.accent, 26));
        leaf.setBackground(new LeafShapeDrawable(palette.leaf1, palette.leaf1Edge,
                style.dp(1), style.dp(10), style.dp(64), style.dp(10), style.dp(64)));
        leaf.setRotation(-.7f);
        style.shadow(leaf, palette, 14, 1f);
        leaf.removeAllViews();

        TextView marker = style.serif(getContext().getString(R.string.editor_marker), 19,
                palette.accent, true, 300);
        leaf.addView(marker, params(-1, -2, 0, 0, 0, 10));
        addTitleField();
        addTaskNote();
        addDuration();
        addRhythm();
        if (state.recurrence == Recurrence.WEEKDAYS) addTaskWeekdays();
        if (state.recurrence == Recurrence.INTERVAL) addInterval();
        if (state.recurrence != Recurrence.ONCE) addTimes();
        addBoundOrDeadline();
        addSteps();
        addHint();
        if (!state.storageError.isEmpty()) addError(state.storageError);
        renderPrompt();
    }

    private void addTitleField() {
        EditText title = field(R.string.field_title_label, R.string.field_title_hint,
                state.title, false, value -> apply(draft(value, state.estimatedMinutes,
                state.recurrence, state.intervalDays, state.weekdayMask, state.timeOfDayMask,
                state.boundKind, state.boundUntilOn, state.boundWeeks, state.remainingCount,
                state.deadlineOn, state.note, state.stepStates, state.expandedStepId), false));
        title.setSingleLine(true); title.setMaxLines(1); title.setImeOptions(EditorInfo.IME_ACTION_NEXT);
        if (state.errors.contains(TaskEditorValidator.TITLE))
            addError(state.title.trim().length() > 120 ? R.string.err_title_long
                    : R.string.err_title_empty);
    }

    private void addTaskNote() {
        EditText note = field(R.string.field_note_label, R.string.field_note_hint, state.note,
                true, value -> apply(draft(state.title, state.estimatedMinutes,
                state.recurrence, state.intervalDays, state.weekdayMask, state.timeOfDayMask,
                state.boundKind, state.boundUntilOn, state.boundWeeks, state.remainingCount,
                state.deadlineOn, value, state.stepStates, state.expandedStepId), false));
        note.setMinLines(state.note.isEmpty() ? 1 : 2);
    }

    private void addDuration() {
        addSectionLabel(R.string.field_duration_label);
        EditorFlowLayout row = flow();
        addChip(row, R.string.duration_15, Integer.valueOf(15).equals(state.estimatedMinutes),
                false, () -> setDuration(15));
        addChip(row, R.string.duration_30, Integer.valueOf(30).equals(state.estimatedMinutes),
                false, () -> setDuration(30));
        addChip(row, R.string.duration_45, Integer.valueOf(45).equals(state.estimatedMinutes),
                false, () -> setDuration(45));
        addChip(row, R.string.duration_60, Integer.valueOf(60).equals(state.estimatedMinutes),
                false, () -> setDuration(60));
        boolean custom = state.estimatedMinutes != null && state.estimatedMinutes != 15
                && state.estimatedMinutes != 30 && state.estimatedMinutes != 45
                && state.estimatedMinutes != 60;
        addChip(row, R.string.duration_custom, custom, true,
                () -> setDuration(state.estimatedMinutes == null ? 0 : state.estimatedMinutes));
        leaf.addView(row, params(-1, -2, 0, 10, 0, 0));
        if (custom) addNumberField(state.estimatedMinutes, R.string.duration_custom_unit,
                value -> setDuration(value));
        if (state.errors.contains(TaskEditorValidator.DURATION)) addError(R.string.err_duration_zero);
    }

    private void setDuration(Integer value) {
        apply(draft(state.title, value, state.recurrence, state.intervalDays, state.weekdayMask,
                state.timeOfDayMask, state.boundKind, state.boundUntilOn, state.boundWeeks,
                state.remainingCount, state.deadlineOn, state.note, state.stepStates,
                state.expandedStepId), true);
    }

    private void addRhythm() {
        addSectionLabel(R.string.field_rhythm_label);
        EditorFlowLayout row = flow();
        addChip(row, R.string.rhythm_once, state.recurrence == Recurrence.ONCE, false,
                () -> setRecurrence(Recurrence.ONCE));
        addChip(row, R.string.rhythm_daily, state.recurrence == Recurrence.DAILY, false,
                () -> setRecurrence(Recurrence.DAILY));
        addChip(row, R.string.rhythm_weekdays, state.recurrence == Recurrence.WEEKDAYS, false,
                () -> setRecurrence(Recurrence.WEEKDAYS));
        addChip(row, R.string.rhythm_every_n, state.recurrence == Recurrence.INTERVAL, false,
                () -> setRecurrence(Recurrence.INTERVAL));
        leaf.addView(row, params(-1, -2, 0, 10, 0, 0));
    }

    private void setRecurrence(Recurrence recurrence) {
        int times = recurrence == Recurrence.ONCE ? 0
                : state.timeOfDayMask == 0 ? TimeOfDay.fromSlot(state.slot).bit
                : state.timeOfDayMask;
        int weekdays = recurrence == Recurrence.WEEKDAYS ? state.weekdayMask : 0;
        apply(draft(state.title, state.estimatedMinutes, recurrence, state.intervalDays,
                weekdays, times, TaskBoundKind.FOREVER, null, null, null,
                recurrence == Recurrence.ONCE ? state.deadlineOn : null, state.note,
                state.stepStates, state.expandedStepId), true);
    }

    private void addTaskWeekdays() {
        LinearLayout row = dayPicker(state.weekdayMask, mask -> apply(draft(state.title,
                state.estimatedMinutes, state.recurrence, state.intervalDays, mask,
                state.timeOfDayMask, state.boundKind, state.boundUntilOn, state.boundWeeks,
                state.remainingCount, state.deadlineOn, state.note, state.stepStates,
                state.expandedStepId), true));
        leaf.addView(row, params(-1, -2, 0, 10, 0, 0));
        if (state.errors.contains(TaskEditorValidator.WEEKDAYS)) addError(R.string.err_weekdays_empty);
    }

    private void addInterval() {
        addSectionLabel(R.string.rhythm_every_n);
        EditorFlowLayout row = flow();
        for (int days : new int[]{2, 3, 7})
            addChip(row, getContext().getString(R.string.rhythm_every_n_value, days),
                    state.intervalDays == days, false, () -> setInterval(days));
        addChip(row, R.string.rhythm_every_n, state.intervalDays != 2
                && state.intervalDays != 3 && state.intervalDays != 7, true,
                () -> setInterval(state.intervalDays));
        leaf.addView(row, params(-1, -2, 0, 10, 0, 0));
        if (state.intervalDays != 2 && state.intervalDays != 3 && state.intervalDays != 7)
            addNumberField(state.intervalDays, R.string.rhythm_every_n,
                    value -> setInterval(value == null ? 0 : value));
        if (state.errors.contains(TaskEditorValidator.INTERVAL)) addError(R.string.err_interval_zero);
    }

    private void setInterval(int value) {
        apply(draft(state.title, state.estimatedMinutes, state.recurrence, value,
                state.weekdayMask, state.timeOfDayMask, state.boundKind, state.boundUntilOn,
                state.boundWeeks, state.remainingCount, state.deadlineOn, state.note,
                state.stepStates, state.expandedStepId), true);
    }

    private void addTimes() {
        addSectionLabel(R.string.field_timeofday_label);
        EditorFlowLayout row = flow();
        int[] labels = {R.string.tod_morning, R.string.tod_noon,
                R.string.tod_evening, R.string.tod_night};
        for (int i = 0; i < TimeOfDay.values().length; i++) {
            TimeOfDay value = TimeOfDay.values()[i];
            addChip(row, labels[i], (state.timeOfDayMask & value.bit) != 0, false,
                    () -> toggleTime(value));
        }
        leaf.addView(row, params(-1, -2, 0, 10, 0, 0));
        if (state.errors.contains(TaskEditorValidator.TIMES)) addError(R.string.err_timeofday_empty);
    }

    private void toggleTime(TimeOfDay value) {
        int times = state.timeOfDayMask ^ value.bit;
        TaskSlot slot = TimeOfDay.earliestSlot(times, state.slot);
        apply(state.draft(state.title, slot, state.estimatedMinutes, state.recurrence,
                state.intervalDays, state.weekdayMask, times, state.boundKind,
                state.boundUntilOn, state.boundWeeks, state.remainingCount, state.deadlineOn,
                state.note, state.stepStates, state.expandedStepId, state.nextDraftIdentity), true);
    }

    private void addBoundOrDeadline() {
        addSectionLabel(state.recurrence == Recurrence.ONCE
                ? R.string.field_deadline_label : R.string.field_bound_label);
        EditorFlowLayout row = flow();
        if (state.recurrence == Recurrence.ONCE) {
            addChip(row, R.string.deadline_none, state.deadlineOn == null, false,
                    () -> setDeadline(null));
            addChip(row, R.string.deadline_today, today.equals(state.deadlineOn), false,
                    () -> setDeadline(today));
            addChip(row, R.string.deadline_tomorrow, today.plusDays(1).equals(state.deadlineOn),
                    false, () -> setDeadline(today.plusDays(1)));
            addChip(row, R.string.deadline_date, state.deadlineOn != null
                    && !today.equals(state.deadlineOn) && !today.plusDays(1).equals(state.deadlineOn),
                    true, () -> pickDate(state.deadlineOn, this::setDeadline));
        } else {
            addChip(row, R.string.bound_forever, state.boundKind == TaskBoundKind.FOREVER,
                    false, () -> setBound(TaskBoundKind.FOREVER));
            addChip(row, R.string.bound_until, state.boundKind == TaskBoundKind.UNTIL_DATE,
                    false, () -> setBound(TaskBoundKind.UNTIL_DATE));
            addChip(row, R.string.bound_weeks, state.boundKind == TaskBoundKind.FOR_WEEKS,
                    true, () -> setBound(TaskBoundKind.FOR_WEEKS));
            addChip(row, R.string.bound_times, state.boundKind == TaskBoundKind.N_TIMES,
                    true, () -> setBound(TaskBoundKind.N_TIMES));
        }
        leaf.addView(row, params(-1, -2, 0, 10, 0, 0));
        addBoundValue();
        if (state.errors.contains(TaskEditorValidator.BOUND)) addError(R.string.err_until_past);
    }

    private void setDeadline(LocalDate date) {
        apply(draft(state.title, state.estimatedMinutes, state.recurrence, state.intervalDays,
                state.weekdayMask, state.timeOfDayMask, state.boundKind, state.boundUntilOn,
                state.boundWeeks, state.remainingCount, date, state.note, state.stepStates,
                state.expandedStepId), true);
    }

    private void setBound(TaskBoundKind kind) {
        LocalDate until = null; Integer weeks = null; Integer count = null;
        if (kind == TaskBoundKind.UNTIL_DATE) until = state.boundUntilOn == null ? today : state.boundUntilOn;
        if (kind == TaskBoundKind.FOR_WEEKS) {
            weeks = state.boundWeeks == null ? 2 : state.boundWeeks;
            until = firstScheduledDate().plusWeeks(weeks);
        }
        if (kind == TaskBoundKind.N_TIMES) count = state.remainingCount == null ? 10 : state.remainingCount;
        apply(draft(state.title, state.estimatedMinutes, state.recurrence, state.intervalDays,
                state.weekdayMask, state.timeOfDayMask, kind, until, weeks, count, null,
                state.note, state.stepStates, state.expandedStepId), true);
    }

    private void addBoundValue() {
        if (state.recurrence == Recurrence.ONCE) {
            if (state.deadlineOn != null && !today.equals(state.deadlineOn)
                    && !today.plusDays(1).equals(state.deadlineOn))
                addValueLeaf(getContext().getString(R.string.bound_until_value,
                        DATE.format(state.deadlineOn)), () -> pickDate(state.deadlineOn, this::setDeadline));
            return;
        }
        if (state.boundKind == TaskBoundKind.UNTIL_DATE && state.boundUntilOn != null)
            addValueLeaf(getContext().getString(R.string.bound_until_value,
                    DATE.format(state.boundUntilOn)), () -> pickDate(state.boundUntilOn,
                    value -> apply(draft(state.title, state.estimatedMinutes, state.recurrence,
                            state.intervalDays, state.weekdayMask, state.timeOfDayMask,
                            state.boundKind, value, null, null, null, state.note,
                            state.stepStates, state.expandedStepId), true)));
        else if (state.boundKind == TaskBoundKind.FOR_WEEKS) {
            addNumberField(state.boundWeeks, R.string.bound_weeks, value -> {
                int weeks = value == null ? 0 : value;
                apply(draft(state.title, state.estimatedMinutes, state.recurrence,
                        state.intervalDays, state.weekdayMask, state.timeOfDayMask,
                        state.boundKind, firstScheduledDate().plusWeeks(Math.max(0, weeks)), weeks, null,
                        null, state.note, state.stepStates, state.expandedStepId), true);
            });
            if (state.boundWeeks != null && state.boundUntilOn != null)
                addValueLeaf(getContext().getString(R.string.bound_weeks_value,
                        state.boundWeeks, DATE.format(state.boundUntilOn)), null);
        } else if (state.boundKind == TaskBoundKind.N_TIMES) {
            addNumberField(state.remainingCount, R.string.bound_times, value -> apply(draft(
                    state.title, state.estimatedMinutes, state.recurrence, state.intervalDays,
                    state.weekdayMask, state.timeOfDayMask, state.boundKind, null, null,
                    value, null, state.note, state.stepStates, state.expandedStepId), true));
            if (state.remainingCount != null) addValueLeaf(getContext().getString(
                    R.string.bound_times_value, state.remainingCount), null);
        }
    }

    private void addSteps() {
        addSectionLabel(R.string.field_steps_label);
        for (int i = 0; i < state.stepStates.size(); i++) {
            EditorStepState step = state.stepStates.get(i);
            if (step.id.equals(state.expandedStepId)) addExpandedStep(i, step);
            else addCollapsedStep(i, step);
        }
        TextView add = style.sans("＋  " + getContext().getString(R.string.step_add), 15,
                palette.ink2, false);
        add.setGravity(Gravity.CENTER_VERTICAL); add.setMinHeight(style.dp(52));
        add.setPadding(style.dp(16), 0, style.dp(16), 0); add.setBackground(style.dashed(palette));
        add.setContentDescription(getContext().getString(R.string.step_add));
        add.setOnClickListener(view -> addStep());
        leaf.addView(add, params(-1, -2, 0, 14, 0, 0));
    }

    private void addCollapsedStep(int index, EditorStepState step) {
        LinearLayout row = new LinearLayout(getContext()); row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(style.dp(16), style.dp(13), style.dp(10), style.dp(13));
        boolean error = state.errors.contains(TaskEditorValidator.STEP_PREFIX + step.id);
        int edge = error ? palette.bad : palette.leaf2Edge;
        row.setBackground(new LeafShapeDrawable(error ? UiStyle.alpha(palette.bad, .10f) : palette.leaf2,
                edge, style.dp(error ? 2 : 1), style.dp(index % 2 == 0 ? 56 : 8),
                style.dp(index % 2 == 0 ? 8 : 56), style.dp(index % 2 == 0 ? 56 : 8),
                style.dp(index % 2 == 0 ? 8 : 56)));
        row.setRotation(index % 2 == 0 ? -.8f : .9f); style.shadow(row, palette, 8, .7f);
        TextView number = style.serif(String.valueOf(index + 1), 16, palette.muted, true, 300);
        row.addView(number, new LinearLayout.LayoutParams(style.dp(18), -2));
        LinearLayout words = new LinearLayout(getContext()); words.setOrientation(LinearLayout.VERTICAL);
        TextView name = style.serif(step.text.isEmpty() ? getContext().getString(R.string.step_name_hint)
                : step.text, 19, error ? palette.bad : palette.ink, false, 300);
        words.addView(name);
        String meta = meta(step);
        if (!meta.isEmpty()) words.addView(style.serif(meta, 14, palette.muted, true, 300));
        if (!step.note.isEmpty()) {
            TextView note = style.sans(step.note, 14, palette.hint, false);
            note.setMaxLines(2); note.setEllipsize(android.text.TextUtils.TruncateAt.END);
            words.addView(note);
        }
        row.addView(words, new LinearLayout.LayoutParams(0, -2, 1));
        TextView up = moveButton("↑", () -> moveStep(index, index - 1), () -> moveStep(index, 0));
        TextView down = moveButton("↓", () -> moveStep(index, index + 1),
                () -> moveStep(index, state.stepStates.size() - 1));
        row.addView(up); row.addView(down);
        TextView menu = style.sans("⋮", 20, palette.dot, false);
        menu.setGravity(Gravity.CENTER); menu.setMinWidth(style.dp(34)); menu.setMinHeight(style.dp(48));
        menu.setContentDescription(getContext().getString(R.string.step_menu_edit));
        menu.setOnClickListener(view -> expandStep(step.id)); row.addView(menu);
        row.setOnClickListener(view -> expandStep(step.id));
        leaf.addView(row, params(-1, -2, 0, 14, 0, 0));
        if (error) addError(R.string.err_step_empty);
    }

    private TextView moveButton(String text, Runnable click, Runnable longClick) {
        TextView view = style.sans(text, 15, palette.dot, false);
        view.setGravity(Gravity.CENTER); view.setMinWidth(style.dp(28)); view.setMinHeight(style.dp(48));
        view.setContentDescription(text.equals("↑") ? "nach oben" : "nach unten");
        view.setOnClickListener(ignored -> click.run());
        view.setOnLongClickListener(ignored -> { longClick.run(); return true; });
        return view;
    }

    private void addExpandedStep(int index, EditorStepState step) {
        LinearLayout card = new LinearLayout(getContext()); card.setOrientation(LinearLayout.VERTICAL);
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
        EditorFlowLayout amounts = flow();
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
        LinearLayout actions = new LinearLayout(getContext()); actions.setGravity(Gravity.CENTER_VERTICAL);
        TextView apply = outlineButton(R.string.step_apply, palette.ink2, palette.dot);
        apply.setOnClickListener(view -> expandStep(null)); actions.addView(apply);
        TextView remove = outlineButton(R.string.step_remove, palette.bad,
                UiStyle.alpha(palette.bad, .34f));
        remove.setOnClickListener(view -> removeStep(index));
        LinearLayout.LayoutParams removeParams = new LinearLayout.LayoutParams(-2, style.dp(46));
        removeParams.setMargins(style.dp(16), 0, 0, 0); actions.addView(remove, removeParams);
        card.addView(actions, params(-1, -2, 0, 16, 0, 0));
        leaf.addView(card, params(-1, -2, 0, 14, 0, 0));
    }

    private void addAmountChip(EditorFlowLayout row, int label, StepAmountKind kind,
                               EditorStepState step, int index) {
        addChip(row, label, step.amount.kind() == kind, false,
                () -> updateStep(index, step.withAmount(selectedAmount(kind, step.amount)), true));
    }

    private void addAmountInputs(LinearLayout card, EditorStepState step, int index) {
        if (step.amount instanceof StepAmount.None) return;
        LinearLayout row = new LinearLayout(getContext()); row.setGravity(Gravity.BOTTOM);
        if (step.amount instanceof StepAmount.SetsReps) {
            StepAmount.SetsReps amount = (StepAmount.SetsReps) step.amount;
            row.addView(numberInput(amount.sets, R.string.amount_sets_unit,
                    value -> { EditorStepState current = currentStep(index);
                        StepAmount.SetsReps currentAmount = (StepAmount.SetsReps) current.amount;
                        updateStep(index, current.withAmount(StepAmount.setsReps(
                                value == null ? 0 : value, currentAmount.repetitions)), false); }),
                    new LinearLayout.LayoutParams(0, -2, 1));
            TextView multiply = style.serif("×", 22, palette.muted, false, 300);
            multiply.setGravity(Gravity.CENTER); row.addView(multiply,
                    new LinearLayout.LayoutParams(style.dp(34), style.dp(58)));
            row.addView(numberInput(amount.repetitions, R.string.amount_reps_unit,
                    value -> { EditorStepState current = currentStep(index);
                        StepAmount.SetsReps currentAmount = (StepAmount.SetsReps) current.amount;
                        updateStep(index, current.withAmount(StepAmount.setsReps(
                                currentAmount.sets, value == null ? 0 : value)), false); }),
                    new LinearLayout.LayoutParams(0, -2, 1));
        } else if (step.amount instanceof StepAmount.Repetitions) {
            row.addView(numberInput(((StepAmount.Repetitions) step.amount).repetitions,
                    R.string.amount_reps_unit,
                    value -> { EditorStepState current = currentStep(index);
                        updateStep(index, current.withAmount(StepAmount.repetitions(
                                value == null ? 0 : value)), false); }),
                    new LinearLayout.LayoutParams(0, -2, 1));
        } else {
            row.addView(numberInput(((StepAmount.Duration) step.amount).seconds,
                    R.string.amount_seconds_unit,
                    value -> { EditorStepState current = currentStep(index);
                        updateStep(index, current.withAmount(StepAmount.duration(
                                value == null ? 0 : value)), false); }),
                    new LinearLayout.LayoutParams(0, -2, 1));
        }
        card.addView(row, params(-1, -2, 0, 12, 0, 0));
    }

    private static StepAmount selectedAmount(StepAmountKind kind, StepAmount previous) {
        if (kind == StepAmountKind.SETS_REPS) {
            if (previous instanceof StepAmount.SetsReps) return previous;
            return StepAmount.setsReps(3, 12);
        }
        if (kind == StepAmountKind.REPS) {
            if (previous instanceof StepAmount.Repetitions) return previous;
            return StepAmount.repetitions(12);
        }
        if (kind == StepAmountKind.DURATION) {
            if (previous instanceof StepAmount.Duration) return previous;
            return StepAmount.duration(45);
        }
        return StepAmount.none();
    }

    private LinearLayout numberInput(Integer value, int unit, IntegerListener listener) {
        LinearLayout wrapper = new LinearLayout(getContext()); wrapper.setOrientation(LinearLayout.VERTICAL);
        EditText input = new EditText(getContext());
        input.setText(value == null || value <= 0 ? "" : String.valueOf(value));
        input.setTextSize(23); input.setTypeface(style.serif); input.setTextColor(palette.ink);
        input.setSingleLine(true); input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setBackgroundTintList(ColorStateList.valueOf(palette.accent));
        input.addTextChangedListener(watcher(text -> listener.accept(parseInteger(text))));
        wrapper.addView(input, new LinearLayout.LayoutParams(-1, style.dp(45)));
        wrapper.addView(style.sans(getContext().getString(unit), 14, palette.hint, false));
        return wrapper;
    }

    private void addStep() {
        List<EditorStepState> steps = new ArrayList<>(state.stepStates);
        EditorStepState added = EditorStepState.blank(state.nextDraftIdentity);
        steps.add(added);
        apply(state.draft(state.title, state.slot, state.estimatedMinutes, state.recurrence,
                state.intervalDays, state.weekdayMask, state.timeOfDayMask, state.boundKind,
                state.boundUntilOn, state.boundWeeks, state.remainingCount, state.deadlineOn,
                state.note, steps, added.id, state.nextDraftIdentity + 1), true);
    }

    private void updateStep(int index, EditorStepState step, boolean render) {
        List<EditorStepState> steps = new ArrayList<>(state.stepStates); steps.set(index, step);
        apply(state.draft(state.title, state.slot, state.estimatedMinutes, state.recurrence,
                state.intervalDays, state.weekdayMask, state.timeOfDayMask, state.boundKind,
                state.boundUntilOn, state.boundWeeks, state.remainingCount, state.deadlineOn,
                state.note, steps, state.expandedStepId, state.nextDraftIdentity), render);
    }

    private EditorStepState currentStep(int index) {
        return state.stepStates.get(index);
    }

    private LocalDate firstScheduledDate() {
        if (state.recurrence == Recurrence.WEEKDAYS && state.weekdayMask == 0) return today;
        return ScheduleCalculator.firstDue(state.recurrence, state.weekdayMask, today);
    }

    private void removeStep(int index) {
        List<EditorStepState> steps = new ArrayList<>(state.stepStates); steps.remove(index);
        apply(state.draft(state.title, state.slot, state.estimatedMinutes, state.recurrence,
                state.intervalDays, state.weekdayMask, state.timeOfDayMask, state.boundKind,
                state.boundUntilOn, state.boundWeeks, state.remainingCount, state.deadlineOn,
                state.note, steps, null, state.nextDraftIdentity), true);
    }

    private void moveStep(int from, int to) {
        if (to < 0 || to >= state.stepStates.size() || from == to) return;
        List<EditorStepState> steps = new ArrayList<>(state.stepStates);
        EditorStepState moved = steps.remove(from); steps.add(to, moved);
        apply(state.draft(state.title, state.slot, state.estimatedMinutes, state.recurrence,
                state.intervalDays, state.weekdayMask, state.timeOfDayMask, state.boundKind,
                state.boundUntilOn, state.boundWeeks, state.remainingCount, state.deadlineOn,
                state.note, steps, state.expandedStepId, state.nextDraftIdentity), true);
        View row = leaf.getChildAt(Math.min(leaf.getChildCount() - 1, to));
        if (row != null && android.animation.ValueAnimator.areAnimatorsEnabled()) {
            row.animate().alpha(.65f).setDuration(120).withEndAction(() ->
                    row.animate().alpha(1f).setDuration(880)).start();
        }
    }

    private void expandStep(String id) {
        apply(state.draft(state.title, state.slot, state.estimatedMinutes, state.recurrence,
                state.intervalDays, state.weekdayMask, state.timeOfDayMask, state.boundKind,
                state.boundUntilOn, state.boundWeeks, state.remainingCount, state.deadlineOn,
                state.note, state.stepStates, id, state.nextDraftIdentity), true);
    }

    private void addHint() {
        int res = state.stepStates.isEmpty() ? R.string.hint_once
                : state.recurrence == Recurrence.WEEKDAYS ? R.string.hint_weekdays
                : state.recurrence == Recurrence.INTERVAL ? R.string.hint_every_n
                : R.string.hint_steps;
        String value = res == R.string.hint_every_n
                ? getContext().getString(res, state.intervalDays) : getContext().getString(res);
        TextView hint = style.serif(value, 15, palette.muted, true, 300);
        hint.setLineSpacing(0, 1.5f); leaf.addView(hint, params(-1, -2, 0, 15, 0, 0));
    }

    private void requestSave() {
        Set<String> errors = validator.errors(state, today);
        if (!errors.isEmpty()) {
            apply(state.withFeedback(errors, EditorUiState.Prompt.NONE, ""), true);
            return;
        }
        listener.onSave(state);
    }

    private void requestClose() {
        if (state.dirty) showPrompt(EditorUiState.Prompt.DISCARD);
        else listener.onDismiss();
    }

    private void showPrompt(EditorUiState.Prompt prompt) {
        if (prompt == EditorUiState.Prompt.DELETE && state.taskId == null) return;
        apply(state.withFeedback(state.errors, prompt, state.storageError), true);
    }

    private void renderPrompt() {
        promptHost.removeAllViews();
        if (state.prompt == EditorUiState.Prompt.NONE) { promptHost.setVisibility(GONE); return; }
        promptHost.setVisibility(VISIBLE); promptHost.setBackgroundColor(0x88060c08);
        LinearLayout card = new LinearLayout(getContext()); card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(style.dp(28), style.dp(26), style.dp(28), style.dp(26));
        card.setBackground(new LeafShapeDrawable(palette.leaf1, palette.leaf1Edge, style.dp(1),
                style.dp(10), style.dp(64), style.dp(10), style.dp(64)));
        boolean deleting = state.prompt == EditorUiState.Prompt.DELETE;
        card.addView(style.serif(getContext().getString(deleting ? R.string.ask_delete_kicker
                : R.string.ask_discard_kicker), 19, palette.accent, true, 300));
        String title = deleting ? getContext().getString(R.string.ask_delete_title,
                state.title.trim().isEmpty() ? getContext().getString(R.string.editor_ctx_new) : state.title)
                : getContext().getString(R.string.ask_discard_title);
        card.addView(style.serif(title, 29, palette.ink, false, 200), params(-1, -2, 0, 6, 0, 0));
        int body = deleting ? state.stepStates.isEmpty() ? R.string.ask_delete_body
                : R.string.ask_delete_body_steps : state.stepStates.isEmpty()
                ? R.string.ask_discard_body : R.string.ask_discard_body_steps;
        String bodyText = state.stepStates.isEmpty() ? getContext().getString(body)
                : getContext().getString(body, state.stepStates.size());
        card.addView(style.sans(bodyText, 16, palette.ink2, false), params(-1, -2, 0, 8, 0, 0));
        LinearLayout actions = new LinearLayout(getContext()); actions.setGravity(Gravity.CENTER_VERTICAL);
        TextView confirm = style.primaryButton(getContext().getString(deleting
                ? R.string.ask_delete_confirm : R.string.ask_discard_confirm), palette);
        if (deleting) confirm.setBackground(style.pill(palette.bad, 26));
        confirm.setOnClickListener(view -> {
            if (deleting) listener.onDelete(state.taskId); else listener.onDismiss();
        }); actions.addView(confirm, new LinearLayout.LayoutParams(-2, style.dp(52)));
        TextView keep = style.sans(getContext().getString(deleting ? R.string.ask_delete_keep
                : R.string.ask_discard_keep), 17, palette.ink2, false);
        keep.setGravity(Gravity.CENTER); keep.setMinHeight(style.dp(48));
        keep.setOnClickListener(view -> apply(state.withFeedback(state.errors,
                EditorUiState.Prompt.NONE, state.storageError), true));
        LinearLayout.LayoutParams keepParams = new LinearLayout.LayoutParams(-2, style.dp(52));
        keepParams.setMargins(style.dp(16), 0, 0, 0); actions.addView(keep, keepParams);
        card.addView(actions, params(-1, -2, 0, 20, 0, 0));
        FrameLayout.LayoutParams cardParams = new FrameLayout.LayoutParams(-1, -2);
        cardParams.gravity = Gravity.TOP; cardParams.leftMargin = style.dp(60);
        cardParams.rightMargin = style.dp(22); cardParams.topMargin = style.dp(250);
        promptHost.addView(card, cardParams);
    }

    private EditText field(int label, int hint, String value, boolean multiline,
                           StringListener listener) {
        LinearLayout wrapper = new LinearLayout(getContext()); wrapper.setOrientation(LinearLayout.VERTICAL);
        TextView labelView = style.serif(getContext().getString(label), 17, palette.muted, true, 300);
        wrapper.addView(labelView);
        EditText input = styledInput(hint, value, multiline);
        input.addTextChangedListener(watcher(listener)); wrapper.addView(input);
        leaf.addView(wrapper, params(-1, -2, 0, 12, 0, 0));
        return input;
    }

    private EditText compactField(LinearLayout parent, int label, int hint, String value,
                                  StringListener listener) {
        LinearLayout wrapper = new LinearLayout(getContext()); wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.addView(style.serif(getContext().getString(label), 17, palette.muted, true, 300));
        EditText input = styledInput(hint, value, true); input.addTextChangedListener(watcher(listener));
        wrapper.addView(input); parent.addView(wrapper, params(-1, -2, 0, 10, 0, 0)); return input;
    }

    private EditText styledInput(int hint, String value, boolean multiline) {
        EditText input = new EditText(getContext()); input.setHint(hint); input.setText(value);
        input.setTextSize(multiline ? 17 : 25); input.setTextColor(palette.ink);
        input.setHintTextColor(palette.dot); input.setTypeface(multiline ? style.sans : style.serif);
        input.setPadding(0, style.dp(2), 0, style.dp(6));
        input.setBackgroundTintList(ColorStateList.valueOf(palette.accent));
        input.setInputType(multiline ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setSelection(input.length()); return input;
    }

    private void addSectionLabel(int text) {
        leaf.addView(style.serif(getContext().getString(text), 17, palette.muted, true, 300),
                params(-1, -2, 0, 22, 0, 0));
    }
    private void addCardLabel(LinearLayout card, int text) {
        card.addView(style.serif(getContext().getString(text), 16, palette.muted, true, 300),
                params(-1, -2, 0, 14, 0, 8));
    }

    private EditorFlowLayout flow() { return new EditorFlowLayout(getContext()); }

    private void addChip(EditorFlowLayout row, int label, boolean selected, boolean dashed,
                         Runnable action) {
        addChip(row, getContext().getString(label), selected, dashed, action);
    }
    private void addChip(EditorFlowLayout row, String text, boolean selected, boolean dashed,
                         Runnable action) {
        TextView chip = style.sans(text, 15, selected ? palette.accentText
                : dashed ? palette.muted : palette.ink, selected);
        chip.setGravity(Gravity.CENTER); chip.setMinHeight(style.dp(48));
        chip.setPadding(style.dp(16), 0, style.dp(16), 0);
        GradientDrawable background = style.pill(selected ? palette.accent : Color.TRANSPARENT, 24);
        if (!selected) background.setStroke(style.dp(1), palette.dot,
                dashed ? style.dp(5) : 0, dashed ? style.dp(4) : 0);
        chip.setBackground(background); chip.setOnClickListener(view -> action.run());
        chip.setContentDescription(text + (selected ? ", ausgewählt" : ""));
        row.addView(chip, new ViewGroup.LayoutParams(-2, style.dp(48)));
    }

    private LinearLayout dayPicker(int mask, IntListener listener) {
        LinearLayout row = new LinearLayout(getContext()); row.setGravity(Gravity.CENTER_VERTICAL);
        int[] labels = {R.string.day_mon, R.string.day_tue, R.string.day_wed,
                R.string.day_thu, R.string.day_fri, R.string.day_sat, R.string.day_sun};
        for (int i = 0; i < labels.length; i++) {
            final int bit = 1 << i; boolean selected = (mask & bit) != 0;
            TextView day = style.sans(getContext().getString(labels[i]), 14,
                    selected ? palette.accentText : palette.ink, selected);
            day.setGravity(Gravity.CENTER); day.setMinWidth(style.dp(38)); day.setMinHeight(style.dp(48));
            GradientDrawable circle = style.pill(selected ? palette.accent : Color.TRANSPARENT, 19);
            if (!selected) circle.setStroke(style.dp(1), palette.dot); day.setBackground(circle);
            day.setOnClickListener(view -> listener.accept(mask ^ bit)); row.addView(day,
                    new LinearLayout.LayoutParams(0, style.dp(48), 1));
        }
        return row;
    }

    private void addNumberField(Integer value, int unit, IntegerListener listener) {
        LinearLayout wrapper = numberInput(value, unit, listener);
        leaf.addView(wrapper, params(-1, -2, 0, 8, 0, 0));
    }

    private void addValueLeaf(String value, Runnable action) {
        LinearLayout row = new LinearLayout(getContext()); row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(style.dp(18), style.dp(12), style.dp(18), style.dp(12));
        row.setBackground(new LeafShapeDrawable(palette.leaf2, palette.leaf2Edge, style.dp(1),
                style.dp(8), style.dp(42), style.dp(8), style.dp(42)));
        row.addView(style.serif(value, 18, palette.ink2, false, 300),
                new LinearLayout.LayoutParams(0, -2, 1));
        if (action != null) {
            TextView change = style.serif(getContext().getString(R.string.value_change), 14,
                    palette.accent, true, 300); row.addView(change); row.setOnClickListener(view -> action.run());
        }
        leaf.addView(row, params(-1, -2, 0, 8, 0, 0));
    }

    private TextView outlineButton(int label, int textColor, int edge) {
        TextView button = style.sans(getContext().getString(label), 16, textColor, false);
        button.setGravity(Gravity.CENTER); button.setPadding(style.dp(20), 0, style.dp(20), 0);
        GradientDrawable bg = style.pill(Color.TRANSPARENT, 23); bg.setStroke(style.dp(1), edge);
        button.setBackground(bg); return button;
    }

    private void addError(int resource) { leaf.addView(errorView(resource)); }
    private void addError(String value) { leaf.addView(errorView(value)); }
    private TextView errorView(int resource) { return errorView(getContext().getString(resource)); }
    private TextView errorView(String value) {
        TextView error = style.serif(value, 14, palette.bad, true, 300);
        error.setPadding(style.dp(12), style.dp(9), style.dp(12), style.dp(9));
        GradientDrawable bg = style.pill(UiStyle.alpha(palette.bad, .10f), 10);
        bg.setStroke(style.dp(1), UiStyle.alpha(palette.bad, .34f)); error.setBackground(bg);
        LinearLayout.LayoutParams params = params(-1, -2, 0, 7, 0, 0); error.setLayoutParams(params);
        return error;
    }

    private String meta(EditorStepState step) {
        List<String> values = new ArrayList<>();
        if (step.weekdayMask != 0) {
            String[] days = {"Mo", "Di", "Mi", "Do", "Fr", "Sa", "So"};
            List<String> selected = new ArrayList<>();
            for (int i = 0; i < 7; i++) if ((step.weekdayMask & 1 << i) != 0) selected.add(days[i]);
            values.add(android.text.TextUtils.join(" · ", selected));
        }
        if (step.amount instanceof StepAmount.SetsReps) {
            StepAmount.SetsReps amount = (StepAmount.SetsReps) step.amount;
            values.add(amount.sets + " × " + amount.repetitions);
        } else if (step.amount instanceof StepAmount.Repetitions) {
            values.add(((StepAmount.Repetitions) step.amount).repetitions + " ×");
        } else if (step.amount instanceof StepAmount.Duration) {
            values.add(((StepAmount.Duration) step.amount).seconds + " Sek");
        }
        return android.text.TextUtils.join(" · ", values);
    }

    private void pickDate(LocalDate initial, DateListener listener) {
        LocalDate value = initial == null ? today : initial;
        new DatePickerDialog(getContext(), (picker, year, month, day) ->
                listener.accept(LocalDate.of(year, month + 1, day)), value.getYear(),
                value.getMonthValue() - 1, value.getDayOfMonth()).show();
    }

    private EditorUiState draft(String title, Integer estimated, Recurrence recurrence,
                                int interval, int weekdays, int times, TaskBoundKind bound,
                                LocalDate until, Integer weeks, Integer count, LocalDate deadline,
                                String note, List<EditorStepState> steps, String expanded) {
        return state.draft(title, state.slot, estimated, recurrence, interval, weekdays, times,
                bound, until, weeks, count, deadline, note, steps, expanded,
                state.nextDraftIdentity);
    }

    private void apply(EditorUiState next, boolean rerender) {
        state = next; lastEmitted = next; listener.onDraftChanged(next);
        if (rerender) render();
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

    private static LinearLayout.LayoutParams params(int width, int height, int left, int top,
                                                     int right, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, height);
        params.setMargins(left, top, right, bottom); return params;
    }

    private interface StringListener { void accept(String value); }
    private interface IntegerListener { void accept(Integer value); }
    private interface IntListener { void accept(int value); }
    private interface DateListener { void accept(LocalDate value); }
}

package de.thonktank.autosecretary;

import android.animation.ValueAnimator;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.Selection;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.animation.PathInterpolator;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import de.thonktank.autosecretary.domain.model.Recurrence;
import de.thonktank.autosecretary.domain.model.TaskBoundKind;
import de.thonktank.autosecretary.domain.model.TaskSlot;
import de.thonktank.autosecretary.domain.model.TimeOfDay;
import de.thonktank.autosecretary.editor.TaskEditorStateReducer;

/** Full-screen state-driven four-page task wizard mounted above the dashboard. */
public final class TaskEditorView extends FrameLayout {
    public interface Listener {
        void onDraftChanged(EditorUiState draft);
        void onSave(EditorUiState draft);
        void onDelete(String taskId);
        void onDismiss();
    }

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern(
            "dd.MM.", Locale.GERMANY);
    private static final int PAGE_COUNT = 4;
    private final UiStyle style;
    private final Listener listener;
    private final TaskEditorValidator validator = new TaskEditorValidator();
    private final LinearLayout leaf;
    private final ScrollView scroll;
    private final TextView cancel;
    private final TextView contextLabel;
    private final Button primary;
    private final TextView secondary;
    private final TextView destructive;
    private final LinearLayout progress;
    private EditorUiState state;
    private EditorUiState lastEmitted;
    private DayPalette palette;
    private LocalDate today;
    private AlertDialog prompt;
    private EditorUiState.Prompt shownPrompt = EditorUiState.Prompt.NONE;
    private int pendingDirection;
    private Object pendingFocusTag;
    private int pendingSelection = -1;
    private Integer pendingScrollY;

    public TaskEditorView(Context context, Listener listener) {
        super(context);
        this.listener = listener;
        style = new UiStyle(context);
        LayoutInflater.from(context).inflate(R.layout.task_editor_view, this, true);
        leaf = findViewById(R.id.task_editor_leaf);
        scroll = findViewById(R.id.task_editor_scroll);
        cancel = findViewById(R.id.task_editor_cancel);
        contextLabel = findViewById(R.id.task_editor_context);
        primary = findViewById(R.id.task_editor_save);
        secondary = findViewById(R.id.task_editor_discard);
        destructive = findViewById(R.id.task_editor_delete);
        progress = findViewById(R.id.task_editor_progress);
        findViewById(R.id.task_editor_prompt_host).setVisibility(GONE);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        scroll.setOverScrollMode(OVER_SCROLL_IF_CONTENT_SCROLLS);
    }

    public void bind(EditorUiState value, DayPalette palette, LocalDate today) {
        if (value == lastEmitted && this.palette == palette
                && this.today != null && this.today.equals(today)) return;
        state = value;
        this.palette = palette;
        this.today = today;
        lastEmitted = null;
        render();
    }

    public boolean handleBack() {
        if (state == null || !state.open) return false;
        if (state.prompt != EditorUiState.Prompt.NONE) closePrompt();
        else if (state.expandedStepId != null) closeStepDetail();
        else if (state.returnToSummary) navigate(EditorUiState.Page.SUMMARY, false, -1);
        else if (state.page == EditorUiState.Page.TITLE) requestClose();
        else if (state.page == EditorUiState.Page.SUMMARY && state.taskId != null) requestClose();
        else navigate(previous(state.page), false, -1);
        return true;
    }

    private void render() {
        if (state.loading) return;
        setBackgroundColor(Color.TRANSPARENT);
        boolean detail = state.expandedStepId != null;
        cancel.setText(detail ? R.string.editor_back_steps : R.string.editor_cancel);
        cancel.setTextColor(palette.muted);
        cancel.setOnClickListener(view -> { if (detail) closeStepDetail(); else requestClose(); });
        contextLabel.setText(detail ? getContext().getString(R.string.step_marker,
                expandedIndex() + 1) : getContext().getString(state.taskId == null
                ? R.string.editor_ctx_new : R.string.editor_ctx_edit));
        contextLabel.setTextColor(palette.muted);

        configureFooter(detail);
        configureLeaf(state.page == EditorUiState.Page.SUMMARY && !detail);
        leaf.removeAllViews();
        if (detail || state.page == EditorUiState.Page.STEPS) renderSteps();
        else if (state.page == EditorUiState.Page.TITLE) renderTitle();
        else if (state.page == EditorUiState.Page.SCHEDULE) renderSchedule();
        else renderSummary();
        if (!state.storageError.isEmpty()) leaf.addView(errorView(state.storageError),
                params(-1, -2, 0, 12, 0, 0));
        restoreViewportAfterRender();
        animatePage();
        renderPrompt();
    }

    private void configureFooter(boolean detail) {
        primary.setText(state.saving ? getContext().getString(R.string.update_busy)
                : getContext().getString(detail ? R.string.step_apply
                : state.page == EditorUiState.Page.SUMMARY ? R.string.action_save
                : R.string.editor_next));
        primary.setTextColor(palette.accentText);
        primary.setBackground(style.pill(palette.accent, 26));
        primary.setEnabled(!state.saving && !hasVisibleBlockingIssue(detail));
        primary.setAlpha(primary.isEnabled() ? 1f : .48f);
        primary.setOnClickListener(view -> {
            if (detail) applyStepDetail();
            else if (state.page == EditorUiState.Page.SUMMARY) requestSave();
            else advance();
        });

        secondary.setTextColor(palette.ink2);
        if (detail || state.page == EditorUiState.Page.TITLE) secondary.setVisibility(GONE);
        else {
            secondary.setVisibility(VISIBLE);
            secondary.setText(state.page == EditorUiState.Page.SUMMARY
                    ? R.string.action_discard : R.string.editor_back);
            secondary.setOnClickListener(view -> {
                if (state.page == EditorUiState.Page.SUMMARY) requestClose();
                else if (state.returnToSummary) navigate(EditorUiState.Page.SUMMARY, false, -1);
                else navigate(previous(state.page), false, -1);
            });
        }

        destructive.setTextColor(palette.bad);
        if (detail) {
            destructive.setVisibility(VISIBLE);
            destructive.setText(R.string.step_remove);
            destructive.setOnClickListener(view -> removeExpandedStep());
        } else if (state.page == EditorUiState.Page.SUMMARY && state.taskId != null) {
            destructive.setVisibility(VISIBLE);
            destructive.setText(R.string.action_delete);
            destructive.setOnClickListener(view -> showPrompt(EditorUiState.Prompt.DELETE));
        } else destructive.setVisibility(GONE);
        renderProgress(detail);
    }

    private void configureLeaf(boolean summary) {
        leaf.setPadding(style.dp(summary ? 0 : 26), style.dp(summary ? 0 : 26),
                style.dp(summary ? 0 : 26), style.dp(summary ? 0 : 30));
        if (summary) {
            leaf.setBackgroundColor(Color.TRANSPARENT);
            leaf.setRotation(0);
            leaf.setElevation(0);
        } else {
            leaf.setBackground(new LeafShapeDrawable(palette.leaf1, palette.leaf1Edge,
                    style.dp(1), style.dp(10), style.dp(64), style.dp(10), style.dp(64)));
            leaf.setRotation(-.7f);
            style.shadow(leaf, palette, 14, 1f);
        }
    }

    private void renderTitle() {
        addQuestion(R.string.editor_frage_titel);
        EditText title = input(R.string.field_title_hint, state.title, false, 25, true,
                value -> apply(draft(value, state.estimatedMinutes, state.recurrence,
                        state.intervalDays, state.weekdayMask, state.timeOfDayMask,
                        state.boundKind, state.boundUntilOn, state.boundWeeks,
                        state.remainingCount, state.deadlineOn, state.note, state.stepStates,
                        state.expandedStepId), false));
        title.setTag("task:title");
        title.setFilters(new InputFilter[]{new InputFilter.LengthFilter(120)});
        title.setImeOptions(EditorInfo.IME_ACTION_NEXT);
        if (hasIssue(ValidationIssue.Field.TITLE))
            title.setBackgroundTintList(ColorStateList.valueOf(palette.bad));
        leaf.addView(title, params(-1, style.dp(52), 0, 20, 0, 0));
        if (hasIssue(ValidationIssue.Field.TITLE))
            leaf.addView(errorView(state.title.trim().isEmpty()
                    ? R.string.err_title_empty : R.string.err_title_long));

        addLabel(R.string.field_note_label, 26, 4);
        EditText note = input(R.string.field_note_hint, state.note, true, 17, false,
                value -> apply(draft(state.title, state.estimatedMinutes, state.recurrence,
                        state.intervalDays, state.weekdayMask, state.timeOfDayMask,
                        state.boundKind, state.boundUntilOn, state.boundWeeks,
                        state.remainingCount, state.deadlineOn, value, state.stepStates,
                        state.expandedStepId), false));
        note.setTag("task:note");
        note.setMinHeight(style.dp(56));
        leaf.addView(note);
        addBoundOrDeadline();
    }

    private void renderSchedule() {
        addQuestion(R.string.editor_frage_rhythmus);
        EditorFlowLayout rhythm = flow();
        addChip(rhythm, R.string.rhythm_once, state.recurrence == Recurrence.ONCE,
                () -> setRecurrence(Recurrence.ONCE));
        addChip(rhythm, R.string.rhythm_daily, state.recurrence == Recurrence.DAILY,
                () -> setRecurrence(Recurrence.DAILY));
        addChip(rhythm, R.string.rhythm_weekdays, state.recurrence == Recurrence.WEEKDAYS,
                () -> setRecurrence(Recurrence.WEEKDAYS));
        addChip(rhythm, R.string.rhythm_every_n, state.recurrence == Recurrence.INTERVAL,
                () -> setRecurrence(Recurrence.INTERVAL));
        leaf.addView(rhythm, params(-1, -2, 0, 24, 0, 0));
        if (state.recurrence == Recurrence.WEEKDAYS) {
            leaf.addView(dayPicker(state.weekdayMask, mask -> apply(draft(state.title,
                    state.estimatedMinutes, state.recurrence, state.intervalDays, mask,
                    state.timeOfDayMask, state.boundKind, state.boundUntilOn, state.boundWeeks,
                    state.remainingCount, state.deadlineOn, state.note, state.stepStates,
                    state.expandedStepId), true)), params(-1, style.dp(48), 0, 14, 0, 0));
            if (hasIssue(ValidationIssue.Field.WEEKDAYS))
                leaf.addView(errorView(R.string.err_weekdays_empty));
        } else if (state.recurrence == Recurrence.INTERVAL) {
            LinearLayout interval = new LinearLayout(getContext());
            interval.setGravity(Gravity.CENTER_VERTICAL);
            EditText number = numberField(state.intervalDays,
                    value -> setInterval(value == null ? 0 : value));
            number.setTag("task:interval");
            interval.addView(number, new LinearLayout.LayoutParams(style.dp(96), style.dp(48)));
            TextView unit = style.serif(getContext().getString(R.string.editor_interval_unit),
                    17, palette.muted, true, 300);
            LinearLayout.LayoutParams unitParams = new LinearLayout.LayoutParams(-2, -2);
            unitParams.setMargins(style.dp(12), 0, 0, 0);
            interval.addView(unit, unitParams);
            leaf.addView(interval, params(-1, -2, 0, 14, 0, 0));
            if (hasIssue(ValidationIssue.Field.INTERVAL))
                leaf.addView(errorView(R.string.err_interval_zero));
        }
        if (state.recurrence != Recurrence.ONCE) addTimes();
        addDuration();
    }

    private void renderSteps() {
        leaf.addView(new TaskStepsEditorView(getContext(), style, state, palette,
                this::apply), new LinearLayout.LayoutParams(-1, -2));
    }

    private void renderSummary() {
        LinearLayout hero = new LinearLayout(getContext());
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(style.dp(26), style.dp(24), style.dp(26), style.dp(24));
        hero.setBackground(new LeafShapeDrawable(palette.leaf1, palette.leaf1Edge, style.dp(1),
                style.dp(10), style.dp(64), style.dp(10), style.dp(64)));
        hero.setRotation(-.7f);
        style.shadow(hero, palette, 14, 1f);
        hero.addView(style.serif(state.title, 30, palette.ink, false, 200));
        hero.addView(style.serif(summaryLine(), 16, palette.muted, true, 300),
                params(-1, -2, 0, 8, 0, 0));
        hero.setContentDescription(getContext().getString(R.string.field_title_label) + ", "
                + state.title + ", " + getContext().getString(R.string.editor_change));
        hero.setOnClickListener(view -> navigate(EditorUiState.Page.TITLE, true, -1));
        leaf.addView(hero, params(-1, -2, 0, 0, 0, 9));
        addSummaryRow(R.string.field_rhythm_label, rhythmSummary(), EditorUiState.Page.SCHEDULE, 0);
        addSummaryRow(R.string.field_timeofday_label, timeSummary(), EditorUiState.Page.SCHEDULE, 1);
        addSummaryRow(R.string.field_duration_label, durationSummary(), EditorUiState.Page.SCHEDULE, 2);
        addSummaryRow(state.recurrence == Recurrence.ONCE ? R.string.editor_label_deadline
                : R.string.field_bound_label, boundSummary(), EditorUiState.Page.TITLE, 3);
        addSummaryRow(R.string.field_steps_label, stepsSummary(), EditorUiState.Page.STEPS, 4);
        addSummaryRow(R.string.field_note_label, state.note.isEmpty() ? "—" : state.note,
                EditorUiState.Page.TITLE, 5);
    }

    private void addSummaryRow(int label, String value, EditorUiState.Page target, int index) {
        LinearLayout row = new LinearLayout(getContext());
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(style.dp(16), style.dp(9), style.dp(14), style.dp(9));
        boolean optional = index == 5;
        row.setBackground(new LeafShapeDrawable(optional ? palette.leaf3 : palette.leaf2,
                optional ? palette.leaf3Edge : palette.leaf2Edge, style.dp(1),
                style.dp(index % 2 == 0 ? 8 : 56), style.dp(index % 2 == 0 ? 56 : 8),
                style.dp(index % 2 == 0 ? 8 : 56), style.dp(index % 2 == 0 ? 56 : 8)));
        float[] rotations = {1.1f, -1.5f, .8f, -.7f, 1.4f, -1f};
        row.setRotation(rotations[index]);
        LinearLayout words = new LinearLayout(getContext());
        words.setOrientation(LinearLayout.VERTICAL);
        words.addView(style.serif(getContext().getString(label), 14, palette.muted, true, 300));
        TextView valueView = style.serif(value, 18, palette.ink2, false, 400);
        valueView.setSingleLine(true);
        valueView.setEllipsize(TextUtils.TruncateAt.END);
        words.addView(valueView);
        row.addView(words, new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(style.serif(getContext().getString(R.string.editor_change), 14,
                palette.accent, true, 300));
        row.setOnClickListener(view -> navigate(target, true, 1));
        leaf.addView(row, params(-1, -2, 0, 0, 0, 9));
    }

    private void addBoundOrDeadline() {
        addLabel(state.recurrence == Recurrence.ONCE ? R.string.editor_label_deadline
                : R.string.field_bound_label, 26, 10);
        EditorFlowLayout row = flow();
        if (state.recurrence == Recurrence.ONCE) {
            addChip(row, R.string.deadline_none, state.deadlineOn == null,
                    () -> setDeadline(null));
            addChip(row, R.string.deadline_today, today.equals(state.deadlineOn),
                    () -> setDeadline(today));
            addChip(row, R.string.deadline_tomorrow, today.plusDays(1).equals(state.deadlineOn),
                    () -> setDeadline(today.plusDays(1)));
            addChip(row, R.string.deadline_date, state.deadlineOn != null
                    && !today.equals(state.deadlineOn) && !today.plusDays(1).equals(state.deadlineOn),
                    () -> pickDate(state.deadlineOn, this::setDeadline));
        } else {
            addChip(row, R.string.bound_forever, state.boundKind == TaskBoundKind.FOREVER,
                    () -> setBound(TaskBoundKind.FOREVER));
            addChip(row, R.string.bound_until, state.boundKind == TaskBoundKind.UNTIL_DATE,
                    () -> setBound(TaskBoundKind.UNTIL_DATE));
            addChip(row, R.string.bound_weeks, state.boundKind == TaskBoundKind.FOR_WEEKS,
                    () -> setBound(TaskBoundKind.FOR_WEEKS));
            addChip(row, R.string.bound_times, state.boundKind == TaskBoundKind.N_TIMES,
                    () -> setBound(TaskBoundKind.N_TIMES));
        }
        leaf.addView(row);
        addBoundValue();
        if (hasIssue(ValidationIssue.Field.BOUND))
            leaf.addView(errorView(R.string.err_until_past));
    }

    private void addBoundValue() {
        if (state.recurrence == Recurrence.ONCE) {
            if (state.deadlineOn != null) addValueLeaf(getContext().getString(
                    R.string.bound_until_value, DATE.format(state.deadlineOn)),
                    () -> pickDate(state.deadlineOn, this::setDeadline));
            return;
        }
        if (state.boundKind == TaskBoundKind.UNTIL_DATE) {
            LocalDate value = state.boundUntilOn == null ? today : state.boundUntilOn;
            addValueLeaf(getContext().getString(R.string.bound_until_value, DATE.format(value)),
                    () -> pickDate(value, date -> applyBound(date, null, null)));
        } else if (state.boundKind == TaskBoundKind.FOR_WEEKS) {
            int weeks = state.boundWeeks == null || state.boundWeeks < 1 ? 1 : state.boundWeeks;
            LocalDate until = today.plusWeeks(weeks);
            addValueLeaf(getContext().getString(R.string.bound_weeks_value, weeks,
                    DATE.format(until)), () -> showNumberPicker(weeks,
                    value -> applyBound(today.plusWeeks(value), value, null)));
        } else if (state.boundKind == TaskBoundKind.N_TIMES) {
            int count = state.remainingCount == null || state.remainingCount < 1
                    ? 1 : state.remainingCount;
            addValueLeaf(getContext().getString(R.string.bound_times_value, count),
                    () -> showNumberPicker(count, value -> applyBound(null, null, value)));
        }
    }

    private void addTimes() {
        addLabel(R.string.field_timeofday_label, 24, 10);
        EditorFlowLayout row = flow();
        int[] labels = {R.string.tod_morning, R.string.tod_noon,
                R.string.tod_evening, R.string.tod_night};
        for (int index = 0; index < TimeOfDay.values().length; index++) {
            TimeOfDay time = TimeOfDay.values()[index];
            addChip(row, labels[index], (state.timeOfDayMask & time.bit) != 0,
                    () -> toggleTime(time));
        }
        leaf.addView(row);
    }

    private void addDuration() {
        addLabel(R.string.field_duration_label, 24, 10);
        EditorFlowLayout row = flow();
        int[] values = {15, 30, 45, 60};
        int[] labels = {R.string.duration_15, R.string.duration_30,
                R.string.duration_45, R.string.duration_60};
        for (int index = 0; index < values.length; index++) {
            int value = values[index];
            addChip(row, labels[index], Integer.valueOf(value).equals(state.estimatedMinutes),
                    () -> setDuration(value));
        }
        boolean custom = state.estimatedMinutes != null && state.estimatedMinutes != 15
                && state.estimatedMinutes != 30 && state.estimatedMinutes != 45
                && state.estimatedMinutes != 60;
        addChip(row, R.string.duration_custom, custom,
                () -> setDuration(state.estimatedMinutes == null ? 20 : state.estimatedMinutes));
        leaf.addView(row);
        if (custom) leaf.addView(numberInput(state.estimatedMinutes,
                R.string.duration_custom_unit, value -> {
                    if (value != null && value > 0) setDuration(value);
                }, "task:duration"), params(-1, -2, 0, 10, 0, 0));
    }

    private void setRecurrence(Recurrence recurrence) {
        int times = recurrence == Recurrence.ONCE ? 0 : state.timeOfDayMask == 0
                ? TimeOfDay.fromSlot(state.slot).bit : state.timeOfDayMask;
        int weekdays = recurrence == Recurrence.WEEKDAYS
                ? state.weekdayMask == 0 ? 1 : state.weekdayMask : 0;
        LocalDate deadline = recurrence == Recurrence.ONCE
                && state.boundKind == TaskBoundKind.UNTIL_DATE ? state.boundUntilOn
                : recurrence == Recurrence.ONCE ? state.deadlineOn : null;
        apply(draft(state.title, state.estimatedMinutes, recurrence,
                recurrence == Recurrence.INTERVAL ? Math.max(2, state.intervalDays) : 1,
                weekdays, times, TaskBoundKind.FOREVER, null, null, null, deadline,
                state.note, state.stepStates, state.expandedStepId), true);
    }

    private void setDuration(Integer value) {
        apply(draft(state.title, value, state.recurrence, state.intervalDays, state.weekdayMask,
                state.timeOfDayMask, state.boundKind, state.boundUntilOn, state.boundWeeks,
                state.remainingCount, state.deadlineOn, state.note, state.stepStates,
                state.expandedStepId), true);
    }
    private void setInterval(int value) {
        apply(draft(state.title, state.estimatedMinutes, state.recurrence, value,
                state.weekdayMask, state.timeOfDayMask, state.boundKind, state.boundUntilOn,
                state.boundWeeks, state.remainingCount, state.deadlineOn, state.note,
                state.stepStates, state.expandedStepId), false);
    }
    private void toggleTime(TimeOfDay value) {
        int times = state.timeOfDayMask ^ value.bit;
        if (times == 0) return;
        TaskSlot slot = TimeOfDay.earliestSlot(times, state.slot);
        apply(state.draft(state.title, slot, state.estimatedMinutes, state.recurrence,
                state.intervalDays, state.weekdayMask, times, state.boundKind,
                state.boundUntilOn, state.boundWeeks, state.remainingCount, state.deadlineOn,
                state.note, state.stepStates, state.expandedStepId, state.nextDraftIdentity), true);
    }
    private void setDeadline(LocalDate date) {
        apply(draft(state.title, state.estimatedMinutes, state.recurrence, state.intervalDays,
                state.weekdayMask, state.timeOfDayMask, state.boundKind, state.boundUntilOn,
                state.boundWeeks, state.remainingCount, date, state.note, state.stepStates,
                state.expandedStepId), true);
    }
    private void setBound(TaskBoundKind kind) {
        apply(draft(state.title, state.estimatedMinutes, state.recurrence, state.intervalDays,
                state.weekdayMask, state.timeOfDayMask, kind,
                kind == TaskBoundKind.UNTIL_DATE ? today
                        : kind == TaskBoundKind.FOR_WEEKS ? today.plusWeeks(1) : null,
                kind == TaskBoundKind.FOR_WEEKS ? 1 : null,
                kind == TaskBoundKind.N_TIMES ? 1 : null, null, state.note,
                state.stepStates, state.expandedStepId), true);
    }
    private void applyBound(LocalDate until, Integer weeks, Integer count) {
        apply(draft(state.title, state.estimatedMinutes, state.recurrence, state.intervalDays,
                state.weekdayMask, state.timeOfDayMask, state.boundKind, until, weeks, count,
                null, state.note, state.stepStates, state.expandedStepId), true);
    }

    private void advance() {
        Set<ValidationIssue> all = validator.issues(state, today);
        Set<ValidationIssue> issues = issuesForPage(all, state.page, false);
        if (!issues.isEmpty()) {
            apply(state.withValidationAttempt(state.page, null, all), true);
            return;
        }
        EditorUiState.Page next = state.returnToSummary ? EditorUiState.Page.SUMMARY
                : next(state.page);
        navigate(next, false, 1);
    }

    private void applyStepDetail() {
        Set<ValidationIssue> all = validator.issues(state, today);
        Set<ValidationIssue> issues = issuesForPage(all, EditorUiState.Page.STEPS, true);
        if (!issues.isEmpty()) {
            apply(state.withValidationAttempt(EditorUiState.Page.STEPS,
                    state.expandedStepId, all), true);
            return;
        }
        closeStepDetail();
    }

    private void requestSave() {
        Set<ValidationIssue> issues = validator.issues(state, today);
        if (!issues.isEmpty()) {
            EditorUiState.Page target = firstIssuePage(issues);
            EditorUiState next = state.withAllValidationAttempted(issues)
                    .withPage(target, true);
            String stepId = firstStepIssue(issues);
            if (stepId != null) next = next.withExpandedStep(stepId);
            apply(next, true);
            return;
        }
        listener.onSave(state);
    }

    private Set<ValidationIssue> issuesForPage(Set<ValidationIssue> all,
                                               EditorUiState.Page page,
                                               boolean currentStepOnly) {
        Set<ValidationIssue> result = new LinkedHashSet<>();
        for (ValidationIssue issue : all)
            if (issue.belongsTo(page) && (!currentStepOnly
                    || issue.belongsToStep(state.expandedStepId))) result.add(issue);
        return Collections.unmodifiableSet(result);
    }

    private boolean hasVisibleBlockingIssue(boolean detail) {
        if (state.issues.isEmpty()) return false;
        return !issuesForPage(state.issues, state.page, detail).isEmpty();
    }

    private EditorUiState.Page firstIssuePage(Set<ValidationIssue> issues) {
        for (ValidationIssue issue : issues)
            if (issue.belongsTo(EditorUiState.Page.TITLE)) return EditorUiState.Page.TITLE;
        for (ValidationIssue issue : issues)
            if (issue.belongsTo(EditorUiState.Page.SCHEDULE)) return EditorUiState.Page.SCHEDULE;
        return EditorUiState.Page.STEPS;
    }

    private static String firstStepIssue(Set<ValidationIssue> issues) {
        for (ValidationIssue issue : issues) if (issue.stepId != null) return issue.stepId;
        return null;
    }

    private void navigate(EditorUiState.Page page, boolean returnToSummary, int direction) {
        pendingDirection = direction;
        apply(state.withPage(page, returnToSummary).withExpandedStep(null), true);
    }
    private void closeStepDetail() {
        pendingDirection = -1;
        apply(TaskEditorStateReducer.expandStep(state, null), true);
    }
    private void removeExpandedStep() {
        int index = expandedIndex();
        if (index >= 0) apply(TaskEditorStateReducer.removeStep(state, index), true);
    }
    private int expandedIndex() {
        if (state == null || state.expandedStepId == null) return -1;
        for (int index = 0; index < state.stepStates.size(); index++)
            if (state.stepStates.get(index).id.equals(state.expandedStepId)) return index;
        return -1;
    }

    private void requestClose() {
        if (state.dirty) showPrompt(EditorUiState.Prompt.DISCARD);
        else listener.onDismiss();
    }
    private void showPrompt(EditorUiState.Prompt value) {
        if (value == EditorUiState.Prompt.DELETE && state.taskId == null) return;
        apply(state.withFeedback(state.issues, value, state.storageError), true);
    }
    private void closePrompt() {
        apply(state.withFeedback(state.issues, EditorUiState.Prompt.NONE,
                state.storageError), true);
    }

    private void renderPrompt() {
        if (state.prompt == EditorUiState.Prompt.NONE) {
            if (prompt != null) {
                prompt.setOnDismissListener(null);
                prompt.dismiss();
                prompt = null;
            }
            shownPrompt = EditorUiState.Prompt.NONE;
            return;
        }
        if (prompt != null && shownPrompt == state.prompt && prompt.isShowing()) return;
        if (prompt != null) prompt.dismiss();
        boolean deleting = state.prompt == EditorUiState.Prompt.DELETE;
        LinearLayout card = new LinearLayout(getContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(style.dp(28), style.dp(26), style.dp(28), style.dp(26));
        card.setBackground(new LeafShapeDrawable(palette.leaf1, palette.leaf1Edge, style.dp(1),
                style.dp(10), style.dp(64), style.dp(10), style.dp(64)));
        card.addView(style.serif(getContext().getString(deleting ? R.string.ask_delete_kicker
                : R.string.ask_discard_kicker), 19, deleting ? palette.bad : palette.accent,
                true, 300));
        String title = deleting ? getContext().getString(R.string.ask_delete_title, state.title)
                : getContext().getString(R.string.ask_discard_title);
        card.addView(style.serif(title, 29, palette.ink, false, 200),
                params(-1, -2, 0, 6, 0, 0));
        int body = deleting ? state.stepStates.isEmpty() ? R.string.ask_delete_body
                : R.string.ask_delete_body_steps : state.stepStates.isEmpty()
                ? R.string.ask_discard_body : R.string.ask_discard_body_steps;
        String bodyText = state.stepStates.isEmpty() ? getContext().getString(body)
                : getContext().getString(body, state.stepStates.size());
        card.addView(style.sans(bodyText, 16, palette.ink2, false),
                params(-1, -2, 0, 8, 0, 0));
        LinearLayout actions = new LinearLayout(getContext());
        actions.setGravity(Gravity.CENTER_VERTICAL);
        TextView confirm = style.primaryButton(getContext().getString(deleting
                ? R.string.ask_delete_confirm : R.string.ask_discard_confirm), palette);
        if (deleting) confirm.setBackground(style.pill(palette.bad, 26));
        confirm.setOnClickListener(view -> {
            if (deleting) listener.onDelete(state.taskId); else listener.onDismiss();
        });
        actions.addView(confirm, new LinearLayout.LayoutParams(-2, style.dp(52)));
        TextView keep = style.sans(getContext().getString(deleting ? R.string.ask_delete_keep
                : R.string.ask_discard_keep), 17, palette.ink2, false);
        keep.setGravity(Gravity.CENTER);
        keep.setMinHeight(style.dp(48));
        keep.setOnClickListener(view -> closePrompt());
        LinearLayout.LayoutParams keepParams = new LinearLayout.LayoutParams(-2, style.dp(48));
        keepParams.setMargins(style.dp(16), 0, 0, 0);
        actions.addView(keep, keepParams);
        card.addView(actions, params(-1, -2, 0, 20, 0, 0));
        FrameLayout wrapper = new FrameLayout(getContext());
        wrapper.setPadding(style.dp(60), 0, style.dp(22), 0);
        FrameLayout.LayoutParams cardParams = new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER);
        wrapper.addView(card, cardParams);
        prompt = new AlertDialog.Builder(getContext()).setView(wrapper).create();
        prompt.setCanceledOnTouchOutside(false);
        prompt.setOnCancelListener(dialog -> closePrompt());
        shownPrompt = state.prompt;
        prompt.show();
        Window window = prompt.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(-1, -2);
            window.setGravity(Gravity.TOP);
            WindowManager.LayoutParams attributes = window.getAttributes();
            attributes.dimAmount = .53f;
            attributes.y = style.dp(250);
            window.setAttributes(attributes);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        }
    }

    @Override protected void onDetachedFromWindow() {
        if (prompt != null) {
            prompt.setOnDismissListener(null);
            prompt.dismiss();
            prompt = null;
        }
        super.onDetachedFromWindow();
    }

    private void renderProgress(boolean detail) {
        progress.removeAllViews();
        boolean hidden = detail || state.page == EditorUiState.Page.SUMMARY;
        progress.setVisibility(hidden ? GONE : VISIBLE);
        if (hidden) return;
        int current = state.page.ordinal();
        for (int index = 0; index < PAGE_COUNT; index++) {
            View bar = new View(getContext());
            bar.setBackground(style.pill(index <= current ? palette.light :
                    UiStyle.alpha(palette.dot, .5f), 3));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    style.dp(30), style.dp(4));
            if (index > 0) params.setMargins(style.dp(5), 0, 0, 0);
            progress.addView(bar, params);
        }
    }

    private void animatePage() {
        if (pendingDirection == 0) return;
        int direction = pendingDirection;
        pendingDirection = 0;
        if (!ValueAnimator.areAnimatorsEnabled()) {
            leaf.setAlpha(1f);
            leaf.setTranslationX(0);
            return;
        }
        leaf.setAlpha(0f);
        leaf.setTranslationX(style.dp(18) * direction);
        leaf.animate().alpha(1f).translationX(0).setDuration(240L)
                .setInterpolator(new PathInterpolator(.2f, .7f, .3f, 1f)).start();
    }

    private void addQuestion(int resource) {
        leaf.addView(style.serif(getContext().getString(resource), 30, palette.ink,
                false, 200));
    }
    private void addLabel(int resource, int top, int bottom) {
        leaf.addView(style.serif(getContext().getString(resource), 17, palette.muted,
                true, 300), params(-1, -2, 0, top, 0, bottom));
    }
    private EditText input(int hint, String value, boolean multiline, float size,
                           boolean serif, StringListener listener) {
        EditText input = new EditText(getContext());
        input.setHint(hint);
        input.setText(value);
        input.setTextSize(size);
        input.setTextColor(palette.ink);
        input.setHintTextColor(palette.dot);
        input.setTypeface(serif ? style.serif : style.sans);
        input.setPadding(0, style.dp(2), 0, style.dp(6));
        input.setBackgroundTintList(ColorStateList.valueOf(palette.accent));
        input.setInputType(InputType.TYPE_CLASS_TEXT
                | (multiline ? InputType.TYPE_TEXT_FLAG_MULTI_LINE : 0));
        input.setSingleLine(!multiline);
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
    private LinearLayout numberInput(Integer value, int unit, IntegerListener listener,
                                     String focusTag) {
        LinearLayout wrapper = new LinearLayout(getContext());
        wrapper.setOrientation(LinearLayout.VERTICAL);
        EditText number = numberField(value, listener);
        number.setTag(focusTag);
        wrapper.addView(number, new LinearLayout.LayoutParams(-1, style.dp(45)));
        wrapper.addView(style.sans(getContext().getString(unit), 14, palette.hint, false));
        return wrapper;
    }
    private EditorFlowLayout flow() { return new EditorFlowLayout(getContext()); }
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
            LinearLayout.LayoutParams dayParams = new LinearLayout.LayoutParams(
                    0, style.dp(48), 1);
            if (index > 0) dayParams.setMargins(style.dp(8), 0, 0, 0);
            row.addView(day, dayParams);
        }
        return row;
    }
    private void addValueLeaf(String value, Runnable action) {
        LinearLayout row = new LinearLayout(getContext());
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(style.dp(18), style.dp(12), style.dp(18), style.dp(12));
        row.setBackground(new LeafShapeDrawable(palette.leaf2, palette.leaf2Edge, style.dp(1),
                style.dp(8), style.dp(42), style.dp(8), style.dp(42)));
        row.addView(style.serif(value, 18, palette.ink2, false, 400),
                new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(style.serif(getContext().getString(R.string.editor_change), 14,
                palette.accent, true, 300));
        row.setOnClickListener(view -> action.run());
        leaf.addView(row, params(-1, -2, 0, 10, 0, 0));
    }
    private TextView errorView(int resource) { return errorView(getContext().getString(resource)); }
    private TextView errorView(String value) {
        TextView error = style.serif(value, 14, palette.bad, true, 300);
        error.setPadding(style.dp(12), style.dp(9), style.dp(12), style.dp(9));
        GradientDrawable background = style.pill(UiStyle.alpha(palette.bad, .10f), 10);
        background.setStroke(style.dp(1), UiStyle.alpha(palette.bad, .34f));
        error.setBackground(background);
        error.setLayoutParams(params(-1, -2, 0, 7, 0, 0));
        return error;
    }
    private void pickDate(LocalDate initial, DateListener listener) {
        LocalDate value = initial == null || initial.isBefore(today) ? today : initial;
        DatePickerDialog dialog = new DatePickerDialog(getContext(), (view, year, month, day) ->
                listener.accept(LocalDate.of(year, month + 1, day)), value.getYear(),
                value.getMonthValue() - 1, value.getDayOfMonth());
        dialog.getDatePicker().setMinDate(java.sql.Date.valueOf(today.toString()).getTime());
        dialog.show();
    }
    private void showNumberPicker(int initial, IntListener listener) {
        EditText input = numberField(Math.max(1, initial), value -> { });
        new AlertDialog.Builder(getContext()).setView(input)
                .setPositiveButton(R.string.step_apply, (dialog, which) -> {
                    Integer value = parseInteger(input.getText().toString());
                    listener.accept(value == null || value < 1 ? 1 : value);
                }).setNegativeButton(R.string.ask_delete_keep, null).show();
    }

    private String summaryLine() {
        List<String> values = new ArrayList<>();
        String rhythm = rhythmSummary();
        if (state.recurrence == Recurrence.WEEKDAYS && state.weekdayMask == 31) rhythm = "Mo–Fr";
        values.add(state.recurrence == Recurrence.ONCE ? rhythm : rhythm + " " + timeSummary());
        if (state.estimatedMinutes != null) values.add(getContext().getString(
                R.string.editor_summary_duration, state.estimatedMinutes));
        values.add(getContext().getString(R.string.editor_summary_steps, state.stepStates.size()));
        return TextUtils.join(" · ", values);
    }
    private String rhythmSummary() {
        if (state.recurrence == Recurrence.ONCE) return getContext().getString(R.string.rhythm_once);
        if (state.recurrence == Recurrence.DAILY) return getContext().getString(R.string.rhythm_daily);
        if (state.recurrence == Recurrence.INTERVAL)
            return getContext().getString(R.string.rhythm_every_n_value, state.intervalDays);
        return days(state.weekdayMask);
    }
    private String timeSummary() {
        if (state.recurrence == Recurrence.ONCE) return "—";
        String[] names = {getContext().getString(R.string.tod_morning),
                getContext().getString(R.string.tod_noon),
                getContext().getString(R.string.tod_evening),
                getContext().getString(R.string.tod_night)};
        List<String> values = new ArrayList<>();
        for (int index = 0; index < TimeOfDay.values().length; index++)
            if ((state.timeOfDayMask & TimeOfDay.values()[index].bit) != 0) values.add(names[index]);
        return TextUtils.join(" · ", values);
    }
    private String durationSummary() {
        return state.estimatedMinutes == null ? "—" : state.estimatedMinutes + " Minuten";
    }
    private String boundSummary() {
        if (state.recurrence == Recurrence.ONCE) return state.deadlineOn == null
                ? getContext().getString(R.string.deadline_none)
                : getContext().getString(R.string.bound_until_value, DATE.format(state.deadlineOn));
        if (state.boundKind == TaskBoundKind.FOREVER)
            return getContext().getString(R.string.bound_forever);
        if (state.boundKind == TaskBoundKind.UNTIL_DATE)
            return getContext().getString(R.string.bound_until_value, DATE.format(state.boundUntilOn));
        if (state.boundKind == TaskBoundKind.FOR_WEEKS)
            return getContext().getString(R.string.bound_weeks_value, state.boundWeeks,
                    DATE.format(state.boundUntilOn));
        return getContext().getString(R.string.bound_times_value, state.remainingCount);
    }
    private String stepsSummary() {
        if (state.stepStates.isEmpty()) return "—";
        List<String> values = new ArrayList<>();
        for (EditorStepState step : state.stepStates) values.add(step.text);
        return TextUtils.join(" · ", values);
    }
    private static String days(int mask) {
        String[] names = {"Mo", "Di", "Mi", "Do", "Fr", "Sa", "So"};
        List<String> values = new ArrayList<>();
        for (int index = 0; index < names.length; index++)
            if ((mask & 1 << index) != 0) values.add(names[index]);
        return TextUtils.join(" · ", values);
    }

    private EditorUiState draft(String title, Integer estimated, Recurrence recurrence,
                                int interval, int weekdays, int times, TaskBoundKind bound,
                                LocalDate until, Integer weeks, Integer count,
                                LocalDate deadline, String note, List<EditorStepState> steps,
                                String expanded) {
        return state.draft(title, state.slot, estimated, recurrence, interval, weekdays, times,
                bound, until, weeks, count, deadline, note, steps, expanded,
                state.nextDraftIdentity);
    }
    private void apply(EditorUiState next, boolean rerender) {
        EditorUiState validated = liveValidated(next);
        boolean issueChanged = !state.issues.equals(validated.issues);
        if (issueChanged && !rerender) captureViewportForNextRender();
        state = validated;
        if (rerender || issueChanged) render();
        lastEmitted = validated;
        listener.onDraftChanged(validated);
    }

    private EditorUiState liveValidated(EditorUiState value) {
        if (value.attemptedPages.isEmpty() && value.attemptedStepIds.isEmpty()) return value;
        Set<ValidationIssue> visible = new LinkedHashSet<>();
        for (ValidationIssue issue : validator.issues(value, today)) {
            if ((issue.stepId == null && value.attemptedPages.contains(issue.field.page))
                    || (issue.stepId != null && (value.attemptedPages.contains(
                    EditorUiState.Page.STEPS)
                    || value.attemptedStepIds.contains(issue.stepId)))) visible.add(issue);
        }
        return value.withFeedback(visible, value.prompt, value.storageError);
    }

    private void captureViewportForNextRender() {
        View focused = findFocus();
        if (focused instanceof EditText && focused.getTag() != null) {
            pendingFocusTag = focused.getTag();
            pendingSelection = ((EditText) focused).getSelectionStart();
        }
        pendingScrollY = scroll.getScrollY();
    }

    private void restoreViewportAfterRender() {
        Object focusTag = pendingFocusTag;
        int selection = pendingSelection;
        Integer scrollY = pendingScrollY;
        pendingFocusTag = null; pendingSelection = -1; pendingScrollY = null;
        scroll.post(() -> {
            scroll.scrollTo(0, scrollY == null ? 0 : scrollY);
            if (focusTag == null) return;
            View focused = findViewWithTag(focusTag);
            if (!(focused instanceof EditText)) return;
            EditText input = (EditText) focused;
            input.requestFocus();
            input.setSelection(Math.max(0, Math.min(selection, input.length())));
        });
    }

    private boolean hasIssue(ValidationIssue.Field field) {
        return state.issues.contains(ValidationIssue.task(field));
    }
    private TextWatcher watcher(StringListener listener) {
        return new TextWatcher() {
            private int selection;
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                selection = start + count;
            }
            @Override public void afterTextChanged(Editable s) {
                Selection.setSelection(s, Math.max(0, Math.min(selection, s.length())));
                listener.accept(s.toString());
            }
        };
    }
    private static Integer parseInteger(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        try { return Integer.parseInt(value.trim()); }
        catch (NumberFormatException error) { return 0; }
    }
    private static EditorUiState.Page next(EditorUiState.Page page) {
        if (page == EditorUiState.Page.TITLE) return EditorUiState.Page.SCHEDULE;
        if (page == EditorUiState.Page.SCHEDULE) return EditorUiState.Page.STEPS;
        return EditorUiState.Page.SUMMARY;
    }
    private static EditorUiState.Page previous(EditorUiState.Page page) {
        if (page == EditorUiState.Page.SUMMARY) return EditorUiState.Page.STEPS;
        if (page == EditorUiState.Page.STEPS) return EditorUiState.Page.SCHEDULE;
        return EditorUiState.Page.TITLE;
    }
    private static LinearLayout.LayoutParams params(int width, int height, int left, int top,
                                                     int right, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, height);
        params.setMargins(left, top, right, bottom);
        return params;
    }
    private interface StringListener { void accept(String value); }
    private interface IntegerListener { void accept(Integer value); }
    private interface IntListener { void accept(int value); }
    private interface DateListener { void accept(LocalDate value); }
}

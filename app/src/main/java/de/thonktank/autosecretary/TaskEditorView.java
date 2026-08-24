package de.thonktank.autosecretary;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.text.InputFilter;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Space;
import android.widget.TextView;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import de.thonktank.autosecretary.domain.model.Recurrence;
import de.thonktank.autosecretary.domain.model.TaskBoundKind;
import de.thonktank.autosecretary.domain.model.TimeOfDay;
import de.thonktank.autosecretary.editor.TaskEditorStateReducer;
import de.thonktank.autosecretary.presentation.AndroidUiTextProvider;
import de.thonktank.autosecretary.presentation.TaskEditorTextFormatter;

/** Full-screen state-driven four-page task wizard mounted above the dashboard. */
public final class TaskEditorView extends FrameLayout {
    public interface Listener {
        void onDraftChanged(EditorUiState draft);
        void onSave(EditorUiState draft);
        void onDelete(String taskId);
        void onDismiss();
    }

    private static final int PAGE_COUNT = 4;
    static final String DEPENDENT_TAG = "task-editor:dependent";
    private final UiStyle style;
    private final TaskEditorLayoutPolicy layout;
    private final Listener listener;
    private final TaskEditorValidator validator = new TaskEditorValidator();
    private final TaskEditorTextFormatter formatter;
    private final LinearLayout leaf;
    private final ScrollView scroll;
    private final LinearLayout actions;
    private final TextView cancel;
    private final TextView contextLabel;
    private final Button primary;
    private final TextView secondary;
    private final TextView destructive;
    private final LinearLayout progress;
    private LinearLayout compactSecondActionRow;
    private EditorUiState state;
    private EditorUiState lastEmitted;
    private DayPalette palette;
    private TaskEditorControlFactory controls;
    private LocalDate today;
    private AlertDialog prompt;
    private AlertDialog closingPrompt;
    private EditorUiState.Prompt shownPrompt = EditorUiState.Prompt.NONE;
    private int pendingDirection;
    private Object pendingFocusTag;
    private int pendingSelection = -1;
    private Integer pendingScrollY;
    private boolean renderedOnce;
    private boolean pendingDependentEnter;
    private int dependentTransitionGeneration;

    public TaskEditorView(Context context, Listener listener) {
        super(context);
        this.listener = listener;
        style = new UiStyle(context);
        layout = TaskEditorLayoutPolicy.from(context.getResources());
        formatter = new TaskEditorTextFormatter(new AndroidUiTextProvider(context));
        LayoutInflater.from(context).inflate(R.layout.task_editor_view, this, true);
        leaf = findViewById(R.id.task_editor_leaf);
        scroll = findViewById(R.id.task_editor_scroll);
        actions = findViewById(R.id.task_editor_actions);
        cancel = findViewById(R.id.task_editor_cancel);
        contextLabel = findViewById(R.id.task_editor_context);
        primary = findViewById(R.id.task_editor_save);
        secondary = findViewById(R.id.task_editor_discard);
        destructive = findViewById(R.id.task_editor_delete);
        progress = findViewById(R.id.task_editor_progress);
        applyAdaptiveLayout();
        findViewById(R.id.task_editor_prompt_host).setVisibility(GONE);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        scroll.setOverScrollMode(OVER_SCROLL_IF_CONTENT_SCROLLS);
    }

    public void bind(EditorUiState value, DayPalette palette, LocalDate today) {
        if (value == lastEmitted && this.palette == palette
                && this.today != null && this.today.equals(today)) return;
        prepareViewport(state, value);
        dependentTransitionGeneration++;
        View dependent = findViewWithTag(DEPENDENT_TAG);
        if (dependent != null) TaskEditorMotion.cancel(dependent);
        state = value;
        traceState("bind", value);
        this.palette = palette;
        controls = new TaskEditorControlFactory(getContext(), style, palette);
        this.today = today;
        lastEmitted = null;
        render();
    }

    private void applyAdaptiveLayout() {
        if (!layout.compact) return;
        View header = findViewById(R.id.task_editor_header);
        header.setPadding(style.dp(layout.pageStartDp), header.getPaddingTop(),
                style.dp(layout.pageEndDp), header.getPaddingBottom());
        scroll.setPadding(style.dp(layout.pageStartDp), scroll.getPaddingTop(),
                style.dp(layout.pageEndDp), scroll.getPaddingBottom());
        ViewGroup.LayoutParams actionParams = actions.getLayoutParams();
        actionParams.height = style.dp(layout.footerHeightDp);
        actions.setLayoutParams(actionParams);
        actions.setOrientation(LinearLayout.VERTICAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        actions.setPadding(style.dp(layout.pageStartDp), style.dp(4),
                style.dp(layout.pageEndDp), style.dp(4));

        actions.removeAllViews();
        LinearLayout primaryRow = new LinearLayout(getContext());
        primaryRow.setGravity(Gravity.CENTER_VERTICAL);
        primaryRow.addView(primary, new LinearLayout.LayoutParams(-2, style.dp(52)));
        LinearLayout.LayoutParams secondaryParams = new LinearLayout.LayoutParams(
                -2, style.dp(48));
        secondaryParams.setMargins(style.dp(18), 0, 0, 0);
        primaryRow.addView(secondary, secondaryParams);
        primaryRow.addView(new Space(getContext()), new LinearLayout.LayoutParams(0, 1, 1));
        actions.addView(primaryRow, new LinearLayout.LayoutParams(-1, style.dp(56)));

        compactSecondActionRow = new LinearLayout(getContext());
        compactSecondActionRow.setGravity(Gravity.CENTER_VERTICAL);
        compactSecondActionRow.addView(destructive,
                new LinearLayout.LayoutParams(-2, style.dp(48)));
        compactSecondActionRow.addView(new Space(getContext()),
                new LinearLayout.LayoutParams(0, 1, 1));
        compactSecondActionRow.addView(progress,
                new LinearLayout.LayoutParams(-2, style.dp(48)));
        actions.addView(compactSecondActionRow,
                new LinearLayout.LayoutParams(-1, style.dp(48)));
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
        cancel.setMinWidth(style.dp(48));
        cancel.setBackground(controls.transparentRipple(24));
        AccessibilityRoles.button(cancel);
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
        configureAccessibilityOrder();
        restoreViewportAfterRender();
        animatePage();
        renderPrompt();
        if (pendingDependentEnter) {
            pendingDependentEnter = false;
            View dependent = findViewWithTag(DEPENDENT_TAG);
            if (dependent != null) TaskEditorMotion.enter(dependent, palette, 6f, style);
        }
        renderedOnce = true;
    }

    private void configureFooter(boolean detail) {
        primary.setText(state.saving ? getContext().getString(R.string.update_busy)
                : getContext().getString(detail ? R.string.step_apply
                : state.page == EditorUiState.Page.SUMMARY ? R.string.action_save
                : R.string.editor_next));
        primary.setTextColor(palette.accentText);
        primary.setBackground(controls.pillRipple(palette.accent, 26));
        AccessibilityRoles.button(primary);
        primary.setEnabled(!state.saving && !hasVisibleBlockingIssue(detail));
        primary.setAlpha(primary.isEnabled() ? 1f : .48f);
        primary.setOnClickListener(view -> {
            if (detail) applyStepDetail();
            else if (state.page == EditorUiState.Page.SUMMARY) requestSave();
            else advance();
        });

        secondary.setTextColor(palette.ink2);
        secondary.setMinWidth(style.dp(48));
        secondary.setBackground(controls.transparentRipple(24));
        AccessibilityRoles.button(secondary);
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
        destructive.setMinWidth(style.dp(48));
        destructive.setBackground(controls.transparentRipple(24));
        AccessibilityRoles.button(destructive);
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
        if (compactSecondActionRow != null) compactSecondActionRow.setVisibility(
                destructive.getVisibility() == VISIBLE || progress.getVisibility() == VISIBLE
                        ? VISIBLE : INVISIBLE);
    }

    private void configureLeaf(boolean summary) {
        leaf.setPadding(style.dp(summary ? 0 : layout.leafHorizontalPaddingDp),
                style.dp(summary ? 0 : layout.leafTopPaddingDp),
                style.dp(summary ? 0 : layout.leafHorizontalPaddingDp),
                style.dp(summary ? 0 : layout.leafBottomPaddingDp));
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
                value -> apply(TaskEditorStateReducer.updateTitle(state, value), false));
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
                value -> apply(TaskEditorStateReducer.updateNote(state, value), false));
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
            LinearLayout dependent = dependentContainer();
            dependent.addView(dayPicker(state.weekdayMask, mask -> apply(
                    TaskEditorStateReducer.updateWeekdays(state, mask), true)),
                    new LinearLayout.LayoutParams(-1, -2));
            if (hasIssue(ValidationIssue.Field.WEEKDAYS))
                dependent.addView(errorView(R.string.err_weekdays_empty));
            leaf.addView(dependent, params(-1, -2, 0, 14, 0, 0));
        } else if (state.recurrence == Recurrence.INTERVAL) {
            LinearLayout dependent = dependentContainer();
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
            dependent.addView(interval);
            if (hasIssue(ValidationIssue.Field.INTERVAL))
                dependent.addView(errorView(R.string.err_interval_zero));
            leaf.addView(dependent, params(-1, -2, 0, 14, 0, 0));
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
        controls.applyLeafPressState(hero, 10, 64, 10, 64);
        hero.setRotation(-.7f);
        style.shadow(hero, palette, 14, 1f);
        hero.addView(style.serif(state.title, 30, palette.ink, false, 200));
        hero.addView(style.serif(formatter.summaryLine(state), 16, palette.muted, true, 300),
                params(-1, -2, 0, 8, 0, 0));
        hero.setContentDescription(getContext().getString(R.string.a11y_editor_summary_row,
                getContext().getString(R.string.field_title_label), state.title,
                getContext().getString(R.string.editor_change)));
        hero.setMinimumHeight(style.dp(48));
        AccessibilityRoles.button(hero);
        hero.setOnClickListener(view -> navigate(EditorUiState.Page.TITLE, true, -1));
        leaf.addView(hero, params(-1, -2, 0, 0, 0, 9));
        addSummaryRow(R.string.field_rhythm_label, formatter.rhythm(state), EditorUiState.Page.SCHEDULE, 0);
        addSummaryRow(R.string.field_timeofday_label, formatter.time(state), EditorUiState.Page.SCHEDULE, 1);
        addSummaryRow(R.string.field_duration_label, formatter.duration(state), EditorUiState.Page.SCHEDULE, 2);
        addSummaryRow(state.recurrence == Recurrence.ONCE ? R.string.editor_label_deadline
                : R.string.field_bound_label, formatter.bound(state), EditorUiState.Page.TITLE, 3);
        addSummaryRow(R.string.field_steps_label, formatter.steps(state), EditorUiState.Page.STEPS, 4);
        addSummaryRow(R.string.field_note_label, state.note.isEmpty() ? formatter.empty() : state.note,
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
        controls.applyLeafPressState(row, index % 2 == 0 ? 8 : 56,
                index % 2 == 0 ? 56 : 8, index % 2 == 0 ? 8 : 56,
                index % 2 == 0 ? 56 : 8);
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
        row.setMinimumHeight(style.dp(48));
        row.setContentDescription(getContext().getString(R.string.a11y_editor_summary_row,
                getContext().getString(label), value,
                getContext().getString(R.string.editor_change)));
        AccessibilityRoles.button(row);
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
                    R.string.bound_until_value, formatter.date(state.deadlineOn)),
                    () -> pickDate(state.deadlineOn, this::setDeadline));
            return;
        }
        if (state.boundKind == TaskBoundKind.UNTIL_DATE) {
            LocalDate value = state.boundUntilOn == null ? today : state.boundUntilOn;
            addValueLeaf(getContext().getString(R.string.bound_until_value, formatter.date(value)),
                    () -> pickDate(value, date -> applyBound(date, null, null)));
        } else if (state.boundKind == TaskBoundKind.FOR_WEEKS) {
            int weeks = state.boundWeeks == null || state.boundWeeks < 1 ? 1 : state.boundWeeks;
            LocalDate until = today.plusWeeks(weeks);
            addValueLeaf(getContext().getString(R.string.bound_weeks_value, weeks,
                    formatter.date(until)), () -> showNumberPicker(weeks,
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
        apply(TaskEditorStateReducer.updateRecurrence(state, recurrence), true);
    }

    private void setDuration(Integer value) {
        apply(TaskEditorStateReducer.updateDuration(state, value), true);
    }
    private void setInterval(int value) {
        apply(TaskEditorStateReducer.updateInterval(state, value), false);
    }
    private void toggleTime(TimeOfDay value) {
        apply(TaskEditorStateReducer.toggleTime(state, value), true);
    }
    private void setDeadline(LocalDate date) {
        apply(TaskEditorStateReducer.updateDeadline(state, date), true);
    }
    private void setBound(TaskBoundKind kind) {
        apply(TaskEditorStateReducer.updateBoundKind(state, kind, today), true);
    }
    private void applyBound(LocalDate until, Integer weeks, Integer count) {
        apply(TaskEditorStateReducer.updateBound(state, until, weeks, count), true);
    }

    private void advance() {
        Set<ValidationIssue> all = validator.issues(state, today);
        EditorUiState next = TaskEditorStateReducer.advance(state, all);
        if (next.page != state.page) pendingDirection = 1;
        apply(next, true);
    }

    private void applyStepDetail() {
        Set<ValidationIssue> all = validator.issues(state, today);
        EditorUiState next = TaskEditorStateReducer.applyStepDetail(state, all);
        if (next.expandedStepId == null) pendingDirection = -1;
        apply(next, true);
    }

    private void requestSave() {
        Set<ValidationIssue> issues = validator.issues(state, today);
        if (!issues.isEmpty()) {
            EditorUiState next = TaskEditorStateReducer.routeValidationFailure(state, issues);
            apply(next, true);
            return;
        }
        listener.onSave(state);
    }

    private boolean hasVisibleBlockingIssue(boolean detail) {
        return TaskEditorStateReducer.hasVisibleBlockingIssue(state, detail);
    }

    private void navigate(EditorUiState.Page page, boolean returnToSummary, int direction) {
        pendingDirection = direction;
        apply(TaskEditorStateReducer.navigate(state, page, returnToSummary), true);
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
        apply(TaskEditorStateReducer.feedback(state, state.issues, value,
                state.storageError), true);
    }
    private void closePrompt() {
        apply(TaskEditorStateReducer.feedback(state, state.issues,
                EditorUiState.Prompt.NONE, state.storageError), true);
    }

    private void renderPrompt() {
        if (state.prompt == EditorUiState.Prompt.NONE) {
            if (prompt != null) {
                AlertDialog closing = prompt;
                prompt = null;
                closingPrompt = closing;
                closing.setOnDismissListener(null);
                closing.setOnKeyListener((dialog, keyCode, event) -> {
                    if (keyCode != KeyEvent.KEYCODE_BACK) return false;
                    if (event.getAction() == KeyEvent.ACTION_UP) handleBack();
                    return true;
                });
                View decor = closing.getWindow() == null ? null
                        : closing.getWindow().getDecorView();
                Runnable dismiss = () -> {
                    if (closing.isShowing()) closing.dismiss();
                    if (closingPrompt == closing) closingPrompt = null;
                };
                if (decor == null || !renderedOnce) dismiss.run();
                else TaskEditorMotion.fadeOut(decor, palette, dismiss);
            }
            shownPrompt = EditorUiState.Prompt.NONE;
            return;
        }
        if (prompt != null && shownPrompt == state.prompt && prompt.isShowing()) return;
        if (prompt != null) prompt.dismiss();
        dismissClosingPrompt();
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
        confirm.setBackground(style.pill(deleting ? palette.bad : palette.accent, 26));
        controls.applyPillPressState(confirm, 26);
        AccessibilityRoles.button(confirm);
        confirm.setOnClickListener(view -> {
            if (deleting) listener.onDelete(state.taskId); else listener.onDismiss();
        });
        actions.addView(confirm, new LinearLayout.LayoutParams(-2, style.dp(52)));
        TextView keep = style.sans(getContext().getString(deleting ? R.string.ask_delete_keep
                : R.string.ask_discard_keep), 17, palette.ink2, false);
        keep.setGravity(Gravity.CENTER);
        keep.setMinWidth(style.dp(48));
        keep.setMinHeight(style.dp(48));
        keep.setBackground(controls.transparentRipple(24));
        AccessibilityRoles.button(keep);
        keep.setOnClickListener(view -> closePrompt());
        LinearLayout.LayoutParams keepParams = new LinearLayout.LayoutParams(-2, style.dp(48));
        keepParams.setMargins(style.dp(16), 0, 0, 0);
        actions.addView(keep, keepParams);
        card.addView(actions, params(-1, -2, 0, 20, 0, 0));
        FrameLayout wrapper = new FrameLayout(getContext());
        wrapper.setPadding(style.dp(layout.pageStartDp), 0, style.dp(layout.pageEndDp), 0);
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
            attributes.y = style.dp(layout.promptTopDp(screenHeightDp(), 0));
            window.setAttributes(attributes);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            card.post(() -> positionPrompt(window, card));
            if (renderedOnce) TaskEditorMotion.fadeIn(window.getDecorView(), palette);
            else TaskEditorMotion.settle(window.getDecorView());
        }
    }

    private void positionPrompt(Window window, View card) {
        if (window == null || !card.isAttachedToWindow()) return;
        int cardHeightDp = Math.round(card.getHeight()
                / getResources().getDisplayMetrics().density);
        WindowManager.LayoutParams attributes = window.getAttributes();
        attributes.y = style.dp(layout.promptTopDp(screenHeightDp(), cardHeightDp));
        window.setAttributes(attributes);
    }

    private int screenHeightDp() {
        int configured = getResources().getConfiguration().screenHeightDp;
        if (configured > 0) return configured;
        return Math.round(getResources().getDisplayMetrics().heightPixels
                / getResources().getDisplayMetrics().density);
    }

    @Override protected void onDetachedFromWindow() {
        if (prompt != null) {
            prompt.setOnDismissListener(null);
            prompt.dismiss();
            prompt = null;
        }
        dismissClosingPrompt();
        super.onDetachedFromWindow();
    }

    private void dismissClosingPrompt() {
        if (closingPrompt == null) return;
        AlertDialog closing = closingPrompt;
        closingPrompt = null;
        Window window = closing.getWindow();
        if (window != null) TaskEditorMotion.cancel(window.getDecorView());
        closing.setOnDismissListener(null);
        if (closing.isShowing()) closing.dismiss();
    }

    AlertDialog promptForTest() { return prompt; }

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

    private void configureAccessibilityOrder() {
        List<View> order = new ArrayList<>();
        order.add(cancel);
        order.add(contextLabel);
        collectAccessibilityOrder(leaf, order);
        order.add(primary);
        if (secondary.getVisibility() == VISIBLE) order.add(secondary);
        if (destructive.getVisibility() == VISIBLE) order.add(destructive);
        View previous = null;
        View previousFocusable = null;
        for (View view : order) {
            if (view.getVisibility() != VISIBLE) continue;
            if (view.getId() == NO_ID) view.setId(View.generateViewId());
            view.setAccessibilityTraversalAfter(previous == null ? NO_ID : previous.getId());
            previous = view;
            if (!view.isFocusable()) continue;
            if (previousFocusable != null)
                previousFocusable.setNextFocusForwardId(view.getId());
            previousFocusable = view;
        }
        if (previousFocusable != null) previousFocusable.setNextFocusForwardId(NO_ID);
    }

    private static void collectAccessibilityOrder(View view, List<View> output) {
        if (view.getVisibility() != VISIBLE) return;
        boolean describedGroup = view instanceof ViewGroup
                && view.getContentDescription() != null
                && view.getContentDescription().length() > 0;
        if (view instanceof TextView || view.isClickable() || describedGroup) output.add(view);
        if (!(view instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) view;
        for (int index = 0; index < group.getChildCount(); index++) {
            View child = group.getChildAt(index);
            if (!describedGroup || child.isClickable())
                collectAccessibilityOrder(child, output);
        }
    }

    private void animatePage() {
        if (pendingDirection == 0) return;
        int direction = pendingDirection;
        pendingDirection = 0;
        if (!TaskEditorMotion.enabled()) {
            TaskEditorMotion.settle(leaf);
            traceMotion("page-settled", direction);
            return;
        }
        traceMotion("page-start", direction);
        leaf.setAlpha(0f);
        leaf.setTranslationX(style.dp(18) * direction);
        leaf.animate().alpha(1f).translationX(0)
                .setDuration(TaskEditorMotion.duration(palette))
                .setInterpolator(TaskEditorMotion.interpolator())
                .withEndAction(() -> traceMotion("page-end", direction)).start();
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
                           boolean serif, TaskEditorControlFactory.StringListener listener) {
        return controls.input(hint, value, multiline, size, serif, listener);
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
    private EditorFlowLayout flow() { return controls.flow(); }
    private void addChip(EditorFlowLayout row, int label, boolean selected, Runnable action) {
        controls.addChip(row, label, selected, action);
    }
    private LinearLayout dayPicker(int mask, TaskEditorControlFactory.IntListener listener) {
        return controls.dayPicker(mask, listener);
    }
    private void addValueLeaf(String value, Runnable action) {
        LinearLayout row = new LinearLayout(getContext());
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(style.dp(18), style.dp(12), style.dp(18), style.dp(12));
        row.setBackground(new LeafShapeDrawable(palette.leaf2, palette.leaf2Edge, style.dp(1),
                style.dp(8), style.dp(42), style.dp(8), style.dp(42)));
        controls.applyLeafPressState(row, 8, 42, 8, 42);
        row.addView(style.serif(value, 18, palette.ink2, false, 400),
                new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(style.serif(getContext().getString(R.string.editor_change), 14,
                palette.accent, true, 300));
        row.setContentDescription(getContext().getString(R.string.a11y_editor_value_row,
                value, getContext().getString(R.string.editor_change)));
        AccessibilityRoles.button(row, style.dp(48));
        row.setOnClickListener(view -> action.run());
        leaf.addView(row, params(-1, -2, 0, 10, 0, 0));
        AccessibilityRoles.installExpandedTouchTarget(row, style.dp(48));
    }
    private TextView errorView(int resource) { return controls.errorView(resource); }
    private TextView errorView(String value) { return controls.errorView(value); }
    private void pickDate(LocalDate initial, DateListener listener) {
        LocalDate value = initial == null || initial.isBefore(today) ? today : initial;
        DatePickerDialog dialog = new DatePickerDialog(getContext(), (view, year, month, day) ->
                listener.accept(LocalDate.of(year, month + 1, day)), value.getYear(),
                value.getMonthValue() - 1, value.getDayOfMonth());
        dialog.getDatePicker().setMinDate(java.sql.Date.valueOf(today.toString()).getTime());
        dialog.show();
    }
    private void showNumberPicker(int initial, TaskEditorControlFactory.IntListener listener) {
        EditText input = numberField(Math.max(1, initial), value -> { });
        new AlertDialog.Builder(getContext()).setView(input)
                .setPositiveButton(R.string.step_apply, (dialog, which) -> {
                    Integer value = TaskEditorControlFactory.parseInteger(
                            input.getText().toString());
                    listener.accept(value == null || value < 1 ? 1 : value);
                }).setNegativeButton(R.string.ask_delete_keep, null).show();
    }

    private void apply(EditorUiState next, boolean rerender) {
        EditorUiState validated = liveValidated(next);
        boolean issueChanged = !state.issues.equals(validated.issues);
        boolean mustRender = rerender || issueChanged;
        EditorUiState previous = state;
        if (mustRender) prepareViewport(previous, validated);
        if (rerender && dependentUiChanged(previous, validated)
                && animateDependentExit(validated)) return;
        state = validated;
        traceState("state", validated);
        if (rerender && dependentKey(validated) != 0 && dependentKey(previous) == 0)
            pendingDependentEnter = true;
        if (mustRender) render();
        lastEmitted = validated;
        listener.onDraftChanged(validated);
    }

    private static void traceState(String kind, EditorUiState value) {
        if (!PresentationTrace.enabled()) return;
        if (value == null) {
            PresentationTrace.emit("editor", kind, "state=null");
            return;
        }
        PresentationTrace.emit("editor", kind, "page=" + value.page
                + " prompt=" + value.prompt + " dirty=" + value.dirty
                + " saving=" + value.saving + " open=" + value.open
                + " detail=" + (value.expandedStepId != null));
    }

    private static void traceMotion(String kind, int direction) {
        if (PresentationTrace.enabled()) PresentationTrace.emit("editor-motion", kind,
                "direction=" + direction);
    }

    private boolean animateDependentExit(EditorUiState validated) {
        View dependent = findViewWithTag(DEPENDENT_TAG);
        if (dependent == null || !TaskEditorMotion.enabled()) return false;
        int generation = ++dependentTransitionGeneration;
        state = validated;
        lastEmitted = validated;
        listener.onDraftChanged(validated);
        TaskEditorMotion.fadeOut(dependent, palette, () -> {
            if (generation != dependentTransitionGeneration) return;
            pendingDependentEnter = dependentKey(validated) != 0;
            render();
        });
        return true;
    }

    private static boolean dependentUiChanged(EditorUiState previous, EditorUiState next) {
        return previous != null && next != null
                && previous.page == next.page
                && Objects.equals(previous.expandedStepId, next.expandedStepId)
                && dependentKey(previous) != dependentKey(next);
    }

    private static int dependentKey(EditorUiState value) {
        if (value == null) return 0;
        if (value.expandedStepId != null) {
            for (EditorStepState step : value.stepStates) {
                if (!step.id.equals(value.expandedStepId)) continue;
                if (step.cadenceMode == StepCadenceMode.WEEKDAYS) return 11;
                if (step.cadenceMode == StepCadenceMode.INTERVAL) return 12;
                return 0;
            }
        }
        if (value.page != EditorUiState.Page.SCHEDULE) return 0;
        if (value.recurrence == Recurrence.WEEKDAYS) return 1;
        if (value.recurrence == Recurrence.INTERVAL) return 2;
        return 0;
    }

    private LinearLayout dependentContainer() {
        LinearLayout container = new LinearLayout(getContext());
        container.setOrientation(LinearLayout.VERTICAL);
        container.setTag(DEPENDENT_TAG);
        return container;
    }

    private EditorUiState liveValidated(EditorUiState value) {
        return TaskEditorStateReducer.liveValidation(value, validator.issues(value, today));
    }

    private void captureViewportForNextRender() {
        View focused = findFocus();
        if (focused instanceof EditText && focused.getTag() != null) {
            pendingFocusTag = focused.getTag();
            pendingSelection = ((EditText) focused).getSelectionStart();
        }
        pendingScrollY = scroll.getScrollY();
    }

    private void prepareViewport(EditorUiState previous, EditorUiState next) {
        if (previous != null && previous.page == next.page
                && Objects.equals(previous.expandedStepId, next.expandedStepId)) {
            captureViewportForNextRender();
            return;
        }
        pendingFocusTag = null;
        pendingSelection = -1;
        pendingScrollY = 0;
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
    private interface DateListener { void accept(LocalDate value); }
}

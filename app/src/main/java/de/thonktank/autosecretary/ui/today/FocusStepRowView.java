package de.thonktank.autosecretary.ui.today;

import de.thonktank.autosecretary.*;

import android.content.Context;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import de.thonktank.autosecretary.presentation.today.RepetitionProgressUiModel;
import de.thonktank.autosecretary.presentation.today.FocusStepUiModel;
import de.thonktank.autosecretary.presentation.today.FocusStepRowUiModel;
import de.thonktank.autosecretary.presentation.today.StepExecutionUiAction;
import de.thonktank.autosecretary.presentation.today.TodayAction;
import de.thonktank.autosecretary.presentation.today.TodayActionSink;
import de.thonktank.autosecretary.domain.model.ResistanceLoad;
import de.thonktank.autosecretary.ui.leaf.GrainSpec;
import de.thonktank.autosecretary.ui.leaf.GrainOcclusion;
import de.thonktank.autosecretary.ui.leaf.WoodGrainView;
import de.thonktank.autosecretary.timer.TimerManager;
import de.thonktank.autosecretary.timer.TimerSession;

/** Modular focus-card row for the running step and compact following steps. */
public final class FocusStepRowView extends LinearLayout {
    private final UiStyle style;
    private final View topLine;
    private final View bottomLine;
    private final LinearLayout body;
    private final LinearLayout header;
    private final DewDotView reward;
    private final TextView title;
    private final TextView amount;
    private final TextView menu;
    private final TextView note;
    private final LinearLayout controls;
    private final RepStepperView singleStepper;
    private final LinearLayout progressHeader;
    private final TextView progressPosition;
    private final TextView progressDone;
    private final SetDotsView dots;
    private final EditorFlowLayout values;
    private final TextLinkView repetitionsValue;
    private final TextLinkView loadValue;
    private final TextLinkView rirValue;
    private final TextLinkView safety;
    private final InlineValueEditorView inlineEditor;
    private final LinearLayout.LayoutParams controlsParams;
    private final TrainingAssistantPanelView assistantPanel;
    private final LinearLayout timerControls;
    private final TextView timerLabel;
    private final TextLinkView timerPrimary;
    private final TextLinkView timerSecondary;
    private String lastAnimatedTimerId;
    private int grainLevel;
    private String boundStepId;
    private boolean boundExpanded;
    private boolean boundQuestionOpen;
    private int boundResultCount = -1;
    private String boundMode;
    private EditorKind activeEditor = EditorKind.NONE;
    private FocusStepUiModel boundStep;
    private RepetitionInputState boundInput;
    private DayPalette boundPalette;
    private TodayActionSink boundEvents;

    private enum EditorKind { NONE, REPETITIONS, LOAD, RIR, ANSWER }

    interface ReorderAction {
        boolean perform(String stepId, int actionId);
    }

    public FocusStepRowView(Context context) {
        super(context);
        style = new UiStyle(context);
        setOrientation(VERTICAL);
        topLine = new View(context);
        addView(topLine, new LayoutParams(-1, style.dp(1)));
        body = new LinearLayout(context);
        body.setOrientation(VERTICAL);
        addView(body, new LayoutParams(-1, -2));
        bottomLine = new View(context);
        addView(bottomLine, new LayoutParams(-1, style.dp(1)));

        header = new LinearLayout(context);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setMinimumHeight(style.dp(48));
        reward = new DewDotView(context);
        header.addView(reward, new LinearLayout.LayoutParams(style.dp(48), style.dp(48)));
        title = style.sans("", 19, 0, false);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, -2, 1);
        titleParams.setMargins(style.dp(4), 0, style.dp(8), 0);
        header.addView(title, titleParams);
        amount = style.sans("", 15, 0, false);
        amount.setSingleLine(true);
        amount.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        header.addView(amount, new LinearLayout.LayoutParams(-2, -2));
        menu = style.sans("⋮", 24, 0, true);
        menu.setGravity(Gravity.CENTER);
        menu.setMinWidth(style.dp(48));
        menu.setMinHeight(style.dp(48));
        AccessibilityRoles.button(menu);
        header.addView(menu, new LinearLayout.LayoutParams(style.dp(48), style.dp(48)));
        body.addView(header, new LinearLayout.LayoutParams(-1, -2));

        note = style.sans("", 15, 0, false);
        note.setMaxLines(2);
        note.setEllipsize(TextUtils.TruncateAt.END);
        note.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams noteParams = new LinearLayout.LayoutParams(-1, -2);
        noteParams.setMargins(style.dp(52), style.dp(-4), 0, 0);
        body.addView(note, noteParams);

        controls = new LinearLayout(context);
        controls.setOrientation(VERTICAL);
        singleStepper = new RepStepperView(context);
        controls.addView(singleStepper, new LinearLayout.LayoutParams(-2, style.dp(44)));
        progressHeader = new LinearLayout(context);
        progressHeader.setGravity(Gravity.CENTER_VERTICAL);
        progressHeader.setPadding(0, 0, style.dp(16), 0);
        progressPosition = style.sans("", 15, 0, false);
        progressDone = style.sans("", 15, 0, true);
        progressDone.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        progressHeader.addView(progressPosition, new LinearLayout.LayoutParams(0, -2, 1));
        progressHeader.addView(progressDone, new LinearLayout.LayoutParams(-2, -2));
        controls.addView(progressHeader, new LinearLayout.LayoutParams(-1, -2));
        dots = new SetDotsView(context);
        controls.addView(dots, new LinearLayout.LayoutParams(-1, -2));
        values = new EditorFlowLayout(context);
        values.setId(R.id.training_values);
        repetitionsValue = valueLink();
        repetitionsValue.setId(R.id.training_repetitions_value);
        loadValue = valueLink();
        loadValue.setId(R.id.training_load_value_today);
        rirValue = valueLink();
        rirValue.setId(R.id.training_rir_value_today);
        safety = valueLink();
        safety.setId(R.id.training_safety_value);
        values.addView(repetitionsValue,
                new ViewGroup.LayoutParams(-2, style.dp(44)));
        values.addView(loadValue, new ViewGroup.LayoutParams(-2, style.dp(44)));
        values.addView(rirValue, new ViewGroup.LayoutParams(-2, style.dp(44)));
        values.addView(safety, new ViewGroup.LayoutParams(-2, style.dp(44)));
        controls.addView(values, new LinearLayout.LayoutParams(-1, -2));
        inlineEditor = new InlineValueEditorView(context);
        inlineEditor.setId(R.id.training_inline_editor);
        controls.addView(inlineEditor, new LinearLayout.LayoutParams(-1, -2));
        controlsParams = new LinearLayout.LayoutParams(-1, -2);
        controlsParams.setMargins(style.dp(52), style.dp(10), 0, 0);
        body.addView(controls, controlsParams);

        assistantPanel = new TrainingAssistantPanelView(context, body);

        timerControls = new LinearLayout(context);
        timerControls.setGravity(Gravity.CENTER_VERTICAL);
        timerLabel = style.sans("", 18, 0, true);
        timerLabel.setGravity(Gravity.CENTER_VERTICAL);
        timerControls.addView(timerLabel, new LinearLayout.LayoutParams(0, style.dp(44), 1));
        timerPrimary = new TextLinkView(context);
        AccessibilityRoles.button(timerPrimary);
        timerControls.addView(timerPrimary, new LinearLayout.LayoutParams(-2, style.dp(44)));
        timerSecondary = new TextLinkView(context);
        AccessibilityRoles.button(timerSecondary);
        LinearLayout.LayoutParams secondaryParams = new LinearLayout.LayoutParams(
                -2, style.dp(44));
        secondaryParams.leftMargin = style.dp(12);
        timerControls.addView(timerSecondary, secondaryParams);
        LinearLayout.LayoutParams timerParams = new LinearLayout.LayoutParams(-1, style.dp(44));
        timerParams.setMargins(style.dp(52), style.dp(8), 0, 0);
        body.addView(timerControls, timerParams);
    }

    public void bind(FocusStepRowUiModel row, DayPalette palette,
                     RepetitionInputState input, TimerManager.Snapshot timers,
                     TodayActionSink events) {
        FocusStepUiModel step = row.step;
        boolean active = row.expanded();
        int resultCount = step.repetitionProgress == null
                ? -1 : step.repetitionProgress.repetitions.size();
        String mode = step.repetitionProgress == null ? "none"
                : step.repetitionProgress.kind.name() + ':'
                + step.repetitionProgress.plannedLoad.mode.name();
        boolean questionOpen = active && step.trainingPrompt != null;
        boolean resetTransient = !step.id.equals(boundStepId) || !active
                || boundExpanded != active
                || boundQuestionOpen && !questionOpen
                || !mode.equals(boundMode)
                || boundResultCount >= 0 && resultCount != boundResultCount;
        if (resetTransient) {
            activeEditor = EditorKind.NONE;
            assistantPanel.resetTransientState();
        }
        boundStepId = step.id;
        boundExpanded = active;
        boundQuestionOpen = questionOpen;
        boundResultCount = resultCount;
        boundMode = mode;
        boundStep = step;
        boundInput = input;
        boundPalette = palette;
        boundEvents = events;
        bindSurface(step, active, palette);
        bindText(step, active, palette);
        TimerSession timer = timers.forStep(step.id);
        boolean restBlocks = timer != null && timer.kind == TimerSession.Kind.REST
                && (timer.state == TimerSession.State.RUNNING
                || timer.state == TimerSession.State.PAUSED);
        bindRepetition(row, step, input, palette, events, restBlocks);
        bindAssistant(step, active, palette, events);
        bindAction(row.action, step, input, events, restBlocks);
        bindTimer(step, timer, timers.elapsedRealtime, palette, events);
    }

    private void bindSurface(FocusStepUiModel step, boolean active, DayPalette palette) {
        topLine.setVisibility(active ? VISIBLE : GONE);
        bottomLine.setVisibility(active ? VISIBLE : GONE);
        int divider = UiStyle.alpha(palette.dot, .45f);
        topLine.setBackgroundColor(divider);
        bottomLine.setBackgroundColor(divider);
        body.setPadding(0, active ? style.dp(11) : 0,
                0, active ? style.dp(13) : 0);
        reward.bind(false, false, palette, step.reward.resultXp);
        grainLevel = step.grainLevel;
        reward.setActiveOutline(active);
    }

    private void bindText(FocusStepUiModel step, boolean active, DayPalette palette) {
        title.setText(step.title);
        title.setTextColor(palette.ink);
        String trailing = step.contextLabel.isEmpty() ? step.amountLabel : step.contextLabel;
        amount.setText(trailing);
        amount.setTextColor(step.contextLabel.isEmpty() ? palette.muted : palette.accent);
        amount.setVisibility(!trailing.isEmpty()
                && (!active || !step.contextLabel.isEmpty()) ? VISIBLE : GONE);
        menu.setTextColor(palette.muted);
        menu.setContentDescription(getContext().getString(
                R.string.content_step_actions, step.title));
        note.setText(step.note);
        note.setTextColor(palette.hint);
        note.setVisibility(step.note.isEmpty() ? GONE : VISIBLE);
        WoodGrainView.applyTextHalo(title, palette.leaf1);
        WoodGrainView.applyTextHalo(amount, palette.leaf1);
        WoodGrainView.applyTextHalo(note, palette.leaf1);
    }

    private void bindAssistant(FocusStepUiModel step, boolean active, DayPalette palette,
                               TodayActionSink events) {
        assistantPanel.bind(step.id, step.trainingPrompt, active,
                activeEditor == EditorKind.ANSWER, palette, this::toggleAnswer,
                action -> events.emit(TodayAction.trainingAssistant(action)));
    }

    private void bindRepetition(FocusStepRowUiModel row, FocusStepUiModel step,
                                RepetitionInputState input, DayPalette palette,
                                TodayActionSink events, boolean restBlocks) {
        RepetitionProgressUiModel progress = step.repetitionProgress;
        boolean editsRepetitions = row.action.kind
                == StepExecutionUiAction.Kind.SUBMIT_REPETITION;
        controls.setVisibility(editsRepetitions && !restBlocks ? VISIBLE : GONE);
        if (editsRepetitions) {
            int editingIndex = input.editingIndexFor(step);
            boolean sets = progress.showsBars();
            singleStepper.setVisibility(sets ? GONE : VISIBLE);
            singleStepper.bind(sets ? 0 : input.valueFor(step), palette, sets ? null
                    : delta -> events.emit(TodayAction.adjustRepetition(step.id, delta)));
            progressHeader.setVisibility(sets ? VISIBLE : GONE);
            dots.setVisibility(sets ? VISIBLE : GONE);
            if (sets) {
                int completed = progress.repetitions.size();
                int position = editingIndex >= 0 ? editingIndex + 1 : progress.nextSlotNumber();
                progressPosition.setText(getContext().getString(
                        R.string.training_set_position, position, progress.slotCount));
                progressDone.setText(getContext().getString(
                        R.string.training_sets_done, completed, progress.slotCount));
                progressPosition.setTextColor(palette.ink2);
                progressDone.setTextColor(palette.ink);
                WoodGrainView.applyTextHalo(progressPosition, palette.leaf1);
                WoodGrainView.applyTextHalo(progressDone, palette.leaf1);
                dots.bind(step.id, progress.slotCount, progress.repetitions,
                        editingIndex, palette);
                bindValueControls(step, progress, input, palette, events);
                bindInlineEditor(step, progress, input, palette, events);
            } else {
                dots.bind(step.id, 0, java.util.Collections.emptyList(), -1, palette);
                values.setVisibility(GONE);
                inlineEditor.setVisibility(GONE);
            }
        } else {
            singleStepper.setVisibility(GONE);
            singleStepper.bind(0, palette, null);
            progressHeader.setVisibility(GONE);
            dots.setVisibility(GONE);
            values.setVisibility(GONE);
            inlineEditor.setVisibility(GONE);
            dots.bind(step.id, 0, java.util.Collections.emptyList(), -1, palette);
        }
    }

    private void bindAction(StepExecutionUiAction action, FocusStepUiModel step,
                            RepetitionInputState input, TodayActionSink events,
                            boolean restBlocks) {
        boolean hasMenu = step.repetitionProgress != null && !step.isDone();
        menu.setVisibility(hasMenu ? VISIBLE : GONE);
        menu.setOnClickListener(hasMenu ? view -> showStepMenu(view, step, events) : null);
        reward.setContentDescription(null);
        RepetitionProgressUiModel progress = step.repetitionProgress;
        if (action.kind == StepExecutionUiAction.Kind.SUBMIT_REPETITION && progress != null) {
            int current = input.valueFor(step);
            int editingIndex = input.editingIndexFor(step);
            reward.setContentDescription(progress.kind == RepetitionProgressUiModel.Kind.SINGLE
                    ? getContext().getString(R.string.content_confirm_repetitions, current)
                    : getContext().getString(editingIndex >= 0
                            ? R.string.content_update_set : R.string.content_confirm_set,
                    editingIndex >= 0 ? editingIndex + 1 : progress.nextSlotNumber(), current));
        } else if (action.kind == StepExecutionUiAction.Kind.TOGGLE) {
            reward.setContentDescription(getContext().getString(
                    R.string.content_complete_step, step.title, step.reward.resultXp));
        } else if (action.kind == StepExecutionUiAction.Kind.TOGGLE_WITH_DELAY) {
            reward.setContentDescription(getContext().getString(
                    R.string.content_complete_step_with_delay, step.title,
                    step.reward.resultXp));
        } else if (action.kind
                == StepExecutionUiAction.Kind.ADVANCE_PLANNED_REPETITIONS) {
            StringBuilder description = new StringBuilder(step.title);
            if (!step.amountLabel.isEmpty()) description.append(", ").append(step.amountLabel);
            if (!step.note.isEmpty()) description.append(", ").append(step.note);
            if (progress != null) description.append(", ").append(getContext().getString(
                    R.string.content_advance_planned_repetitions,
                    progress.plannedRepetitions, step.reward.resultXp));
            else description.append(", ").append(step.reward.resultXp).append(" XP, ")
                    .append(getContext().getString(R.string.action_complete));
            reward.setContentDescription(description.toString());
        }
        reward.setOnClickListener(action.kind == StepExecutionUiAction.Kind.NONE
                || restBlocks
                ? null : view -> emitExecution(action, events));
        reward.setActionEnabled(action.kind != StepExecutionUiAction.Kind.NONE
                && !restBlocks);
    }

    private void bindValueControls(FocusStepUiModel step, RepetitionProgressUiModel progress,
                                   RepetitionInputState input, DayPalette palette,
                                   TodayActionSink events) {
        values.setVisibility(VISIBLE);
        bindValueLink(repetitionsValue,
                getContext().getString(R.string.training_repetitions_value,
                        input.valueFor(step)), EditorKind.REPETITIONS, true, palette);
        boolean training = progress.detailedTraining();
        loadValue.setVisibility(training ? VISIBLE : GONE);
        rirValue.setVisibility(training ? VISIBLE : GONE);
        safety.setVisibility(training ? VISIBLE : GONE);
        if (!training) return;
        ResistanceLoad load = input.loadFor(step);
        int rir = input.rirFor(step);
        boolean flagged = input.safetyFor(step);
        boolean adjustable = load.adjustable();
        bindValueLink(loadValue, formatLoad(load), EditorKind.LOAD, adjustable, palette);
        bindValueLink(rirValue, getContext().getString(R.string.training_rir_value, rir),
                EditorKind.RIR, true, palette);
        safety.setText(flagged ? R.string.training_safety_flagged : R.string.training_safety_ok);
        safety.bind(flagged ? palette.bad : palette.hint, palette.dot);
        safety.setClickable(true);
        safety.setFocusable(true);
        safety.setContentDescription(safety.getText());
        safety.setOnClickListener(view -> events.emit(TodayAction.toggleTrainingSafety(step.id)));
    }

    private void bindInlineEditor(FocusStepUiModel step, RepetitionProgressUiModel progress,
                                  RepetitionInputState input, DayPalette palette,
                                  TodayActionSink events) {
        inlineEditor.setVisibility(activeEditor == EditorKind.REPETITIONS
                || activeEditor == EditorKind.LOAD || activeEditor == EditorKind.RIR
                ? VISIBLE : GONE);
        if (activeEditor == EditorKind.REPETITIONS) {
            int current = input.valueFor(step);
            inlineEditor.bind(getContext().getString(R.string.training_edit_repetitions),
                    getContext().getString(R.string.training_repetitions_value, current),
                    current > 0, current < 999, palette,
                    () -> events.emit(TodayAction.adjustRepetition(step.id, -1)),
                    () -> events.emit(TodayAction.adjustRepetition(step.id, 1)));
        } else if (activeEditor == EditorKind.LOAD && progress.detailedTraining()) {
            ResistanceLoad load = input.loadFor(step);
            long delta = load.unit == ResistanceLoad.Unit.LB ? 5_000 : 1_000;
            long amount = load.milliUnits == null ? 0L : load.milliUnits;
            inlineEditor.bind(getContext().getString(R.string.training_edit_load),
                    formatLoad(load), amount > 0, load.adjustable(), palette,
                    () -> events.emit(TodayAction.adjustTrainingLoad(step.id, (int) -delta)),
                    () -> events.emit(TodayAction.adjustTrainingLoad(step.id, (int) delta)));
        } else if (activeEditor == EditorKind.RIR && progress.detailedTraining()) {
            int rir = input.rirFor(step);
            inlineEditor.bind(getContext().getString(R.string.training_edit_rir),
                    getContext().getString(R.string.training_rir_value, rir),
                    rir > 0, rir < 5, palette,
                    () -> events.emit(TodayAction.adjustTrainingRir(step.id, -1)),
                    () -> events.emit(TodayAction.adjustTrainingRir(step.id, 1)));
        }
    }

    private TextLinkView valueLink() {
        TextLinkView view = new TextLinkView(getContext());
        view.setTextSize(16);
        AccessibilityRoles.button(view);
        return view;
    }

    private void bindValueLink(TextLinkView view, String text, EditorKind kind,
                               boolean editable, DayPalette palette) {
        view.setText(text);
        view.setContentDescription(text);
        view.bind(activeEditor == kind ? palette.ink : editable ? palette.accent : palette.ink2,
                editable ? activeEditor == kind ? palette.accent : palette.dot : palette.leaf1);
        view.setOnClickListener(editable ? ignored -> selectEditor(kind) : null);
        view.setClickable(editable);
        view.setFocusable(editable);
        WoodGrainView.applyTextHalo(view, palette.leaf1);
    }

    private void selectEditor(EditorKind kind) {
        activeEditor = activeEditor == kind ? EditorKind.NONE : kind;
        refreshTransientViews();
    }

    private void toggleAnswer() {
        activeEditor = activeEditor == EditorKind.ANSWER ? EditorKind.NONE : EditorKind.ANSWER;
        refreshTransientViews();
    }

    private void refreshTransientViews() {
        if (boundStep == null || boundInput == null || boundPalette == null
                || boundEvents == null || !boundExpanded) return;
        RepetitionProgressUiModel progress = boundStep.repetitionProgress;
        if (progress != null) {
            bindValueControls(boundStep, progress, boundInput, boundPalette, boundEvents);
            bindInlineEditor(boundStep, progress, boundInput, boundPalette, boundEvents);
        }
        bindAssistant(boundStep, true, boundPalette, boundEvents);
        requestLayout();
    }

    private String formatLoad(ResistanceLoad load) {
        if (load.mode == ResistanceLoad.Mode.BODYWEIGHT)
            return getContext().getString(R.string.training_load_bodyweight_short);
        double value = (load.milliUnits == null ? 0L : load.milliUnits) / 1000d;
        String unit = load.unit == ResistanceLoad.Unit.LB ? "lb" : "kg";
        String prefix = load.mode == ResistanceLoad.Mode.BODYWEIGHT_PLUS ? "+"
                : load.mode == ResistanceLoad.Mode.ASSISTED_BODYWEIGHT ? "−" : "";
        return prefix + String.format(java.util.Locale.getDefault(), "%.1f %s", value, unit);
    }

    private void bindTimer(FocusStepUiModel step, TimerSession timer, long elapsedRealtime,
                           DayPalette palette, TodayActionSink events) {
        boolean durationAvailable = timer == null && step.durationSeconds > 0;
        timerControls.setVisibility(durationAvailable || timer != null ? VISIBLE : GONE);
        timerLabel.setText("");
        timerPrimary.setText("");
        timerPrimary.setVisibility(GONE);
        timerPrimary.setOnClickListener(null);
        timerSecondary.setText("");
        timerSecondary.setVisibility(GONE);
        timerSecondary.setOnClickListener(null);
        timerLabel.setTextColor(palette.ink);
        timerPrimary.bind(palette.hint, palette.dot);
        timerSecondary.bind(palette.hint, palette.dot);
        WoodGrainView.applyTextHalo(timerLabel, palette.leaf1);
        if (timerControls.getVisibility() == GONE) {
            body.animate().cancel();
            body.setScaleX(1f);
            body.setScaleY(1f);
            return;
        }
        if (durationAvailable) {
            timerLabel.setText(formatSeconds(step.durationSeconds));
            timerPrimary.setVisibility(VISIBLE);
            timerPrimary.setText(R.string.timer_start);
            timerPrimary.setOnClickListener(view -> events.emit(TodayAction.startDurationTimer(
                    step.id, step.title, step.durationSeconds)));
            timerSecondary.setVisibility(GONE);
            return;
        }
        timerSecondary.setVisibility(VISIBLE);
        if (timer.state == TimerSession.State.FINISHED) {
            timerLabel.setText(timer.kind == TimerSession.Kind.REST
                    ? R.string.rest_timer_finished : R.string.duration_timer_finished);
            timerPrimary.setVisibility(GONE);
            if (timer.kind == TimerSession.Kind.REST) {
                timerSecondary.setVisibility(GONE);
            } else {
                timerSecondary.setText(R.string.timer_reset);
                timerSecondary.setOnClickListener(view ->
                        events.emit(TodayAction.resetTimer(timer.id)));
            }
            animateTimerFinished(timer, events);
            return;
        }
        timerPrimary.setVisibility(VISIBLE);
        timerLabel.setText(formatMillis(timer.remainingAt(elapsedRealtime)));
        timerPrimary.setText(timer.state == TimerSession.State.RUNNING
                ? R.string.timer_pause : R.string.timer_resume);
        timerPrimary.setOnClickListener(view -> events.emit(
                timer.state == TimerSession.State.RUNNING
                        ? TodayAction.pauseTimer(timer.id) : TodayAction.resumeTimer(timer.id)));
        timerSecondary.setText(timer.kind == TimerSession.Kind.REST
                ? R.string.timer_skip_rest : R.string.timer_reset);
        timerSecondary.setOnClickListener(view -> events.emit(TodayAction.resetTimer(timer.id)));
    }

    private void animateTimerFinished(TimerSession timer, TodayActionSink events) {
        if (timer.completionObserved || timer.id.equals(lastAnimatedTimerId)) return;
        lastAnimatedTimerId = timer.id;
        post(() -> body.animate().cancel());
        post(() -> body.animate().scaleX(1.025f).scaleY(1.025f).setDuration(160)
                .withEndAction(() -> body.animate().scaleX(1f).scaleY(1f).setDuration(220).start())
                .start());
        events.emit(TodayAction.observeTimer(timer.id));
    }

    private static String formatMillis(long millis) {
        return formatSeconds((int) Math.ceil(millis / 1000d));
    }

    private static String formatSeconds(int totalSeconds) {
        int seconds = Math.max(0, totalSeconds);
        return String.format(java.util.Locale.getDefault(), "%d:%02d", seconds / 60,
                seconds % 60);
    }

    private void emitExecution(StepExecutionUiAction action, TodayActionSink events) {
        switch (action.kind) {
            case TOGGLE:
                events.emit(TodayAction.toggleStep(action.stepId));
                return;
            case TOGGLE_WITH_DELAY:
                FlowDurationDialog.show(getContext(), getContext().getString(
                                R.string.flow_delay_prompt_title), action.proposedDelayMillis,
                        delay -> events.emit(TodayAction.toggleStep(action.stepId, delay)));
                return;
            case SUBMIT_REPETITION:
                events.emit(TodayAction.submitRepetition(action.stepId));
                return;
            case ADVANCE_PLANNED_REPETITIONS:
                events.emit(TodayAction.advanceStep(action.stepId));
                return;
            case NONE:
                return;
        }
        throw new AssertionError("Unhandled step action " + action.kind);
    }

    private void showStepMenu(View anchor, FocusStepUiModel step, TodayActionSink events) {
        PopupMenu popup = new PopupMenu(getContext(), anchor);
        int editBase = 1_000;
        if (step.repetitionProgress != null) {
            for (int index = 0; index < step.repetitionProgress.repetitions.size(); index++) {
                popup.getMenu().add(0, editBase + index, index,
                        getContext().getString(R.string.content_edit_set, index + 1));
            }
        }
        int finishId = 1;
        int finishOrder = step.repetitionProgress == null ? 0
                : step.repetitionProgress.repetitions.size();
        popup.getMenu().add(0, finishId, finishOrder, R.string.action_finish_today);
        popup.setOnMenuItemClickListener(item -> {
            if (item.getItemId() >= editBase) {
                activeEditor = EditorKind.REPETITIONS;
                events.emit(TodayAction.editRepetition(step.id,
                        item.getItemId() - editBase));
                return true;
            }
            events.emit(TodayAction.finishStep(step.id));
            return true;
        });
        popup.show();
    }

    void bindInteractions(String stepId, String stepTitle, OnClickListener titleClick,
                          OnLongClickListener longClick, boolean canMoveUp,
                          boolean canMoveDown, boolean canSelect, ReorderAction action) {
        title.setClickable(titleClick != null);
        title.setOnClickListener(titleClick);
        title.setLongClickable(longClick != null);
        body.setLongClickable(longClick != null);
        body.setOnLongClickListener(longClick);
        title.setOnLongClickListener(longClick);
        bindAccessibility(stepId, stepTitle, canMoveUp, canMoveDown, canSelect, action);
    }

    private void bindAccessibility(String stepId, String title, boolean canMoveUp,
                                   boolean canMoveDown, boolean canSelect,
                                   ReorderAction action) {
        body.setFocusable(true);
        body.setContentDescription(getContext().getString(R.string.a11y_today_step_row, title));
        body.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override public void onInitializeAccessibilityNodeInfo(
                    View host, AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                if (canSelect) info.addAction(accessibilityAction(
                        R.id.action_today_step_select, R.string.a11y_today_step_select));
                if (canMoveUp) {
                    info.addAction(accessibilityAction(R.id.action_today_step_up,
                            R.string.a11y_step_up));
                    info.addAction(accessibilityAction(R.id.action_today_step_front,
                            R.string.a11y_today_step_front));
                }
                if (canMoveDown) {
                    info.addAction(accessibilityAction(R.id.action_today_step_down,
                            R.string.a11y_step_down));
                    info.addAction(accessibilityAction(R.id.action_today_step_back,
                            R.string.a11y_today_step_back));
                }
            }

            @Override public boolean performAccessibilityAction(View host, int actionId,
                                                                android.os.Bundle arguments) {
                return action.perform(stepId, actionId)
                        || super.performAccessibilityAction(host, actionId, arguments);
            }
        });
    }

    private AccessibilityNodeInfo.AccessibilityAction accessibilityAction(int id, int label) {
        return new AccessibilityNodeInfo.AccessibilityAction(id,
                getContext().getString(label));
    }

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int desiredStart = width < style.dp(250) ? 0 : style.dp(52);
        if (controlsParams.leftMargin != desiredStart) controlsParams.leftMargin = desiredStart;
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    public View rewardAnchor() { return reward; }

    public List<GrainOcclusion> grainOcclusions() {
        List<GrainOcclusion> views = new ArrayList<>();
        views.add(GrainOcclusion.text(title));
        if (amount.getVisibility() == VISIBLE) views.add(GrainOcclusion.text(amount));
        if (note.getVisibility() == VISIBLE) views.add(GrainOcclusion.text(note));
        if (controls.getVisibility() == VISIBLE) {
            if (singleStepper.getVisibility() == VISIBLE)
                views.addAll(singleStepper.grainOcclusions());
            if (progressHeader.getVisibility() == VISIBLE) {
                views.add(GrainOcclusion.text(progressPosition));
                views.add(GrainOcclusion.text(progressDone));
            }
            if (dots.getVisibility() == VISIBLE) views.add(GrainOcclusion.bounds(dots));
            if (repetitionsValue.getVisibility() == VISIBLE)
                views.add(GrainOcclusion.text(repetitionsValue));
            if (loadValue.getVisibility() == VISIBLE)
                views.add(GrainOcclusion.text(loadValue));
            if (rirValue.getVisibility() == VISIBLE)
                views.add(GrainOcclusion.text(rirValue));
            if (safety.getVisibility() == VISIBLE)
                views.add(GrainOcclusion.text(safety));
            if (inlineEditor.getVisibility() == VISIBLE)
                views.add(GrainOcclusion.bounds(inlineEditor));
        }
        if (timerControls.getVisibility() == VISIBLE) {
            views.add(GrainOcclusion.text(timerLabel));
            if (timerPrimary.getVisibility() == VISIBLE)
                views.add(GrainOcclusion.text(timerPrimary));
            if (timerSecondary.getVisibility() == VISIBLE)
                views.add(GrainOcclusion.text(timerSecondary));
        }
        return views;
    }

    void appendGrainAnchor(List<GrainSpec.Anchor> anchors) {
        anchors.add(GrainSpec.sizedAnchor(reward, reward.grainWidth(),
                reward.grainHeight(), grainLevel));
    }

}

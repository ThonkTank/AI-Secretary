package de.thonktank.autosecretary.ui.today;

import de.thonktank.autosecretary.*;

import android.content.Context;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.HorizontalScrollView;
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
    private final RepStepperView stepper;
    private final HorizontalScrollView barsScroll;
    private final SetBarsView bars;
    private final LinearLayout.LayoutParams controlsParams;
    private final HorizontalScrollView trainingScroll;
    private final LinearLayout trainingControls;
    private final TextLinkView loadMinus;
    private final TextView loadLabel;
    private final TextLinkView loadPlus;
    private final TextLinkView rirMinus;
    private final TextView rirLabel;
    private final TextLinkView rirPlus;
    private final TextLinkView safety;
    private final TrainingAssistantPanelView assistantPanel;
    private final LinearLayout timerControls;
    private final TextView timerLabel;
    private final TextLinkView timerPrimary;
    private final TextLinkView timerSecondary;
    private String lastAnimatedTimerId;
    private int grainLevel;

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
        controls.setGravity(Gravity.CENTER_VERTICAL);
        stepper = new RepStepperView(context);
        controls.addView(stepper, new LinearLayout.LayoutParams(-2, style.dp(44)));
        barsScroll = new HorizontalScrollView(context);
        barsScroll.setHorizontalScrollBarEnabled(false);
        barsScroll.setFillViewport(false);
        bars = new SetBarsView(context);
        barsScroll.addView(bars, new HorizontalScrollView.LayoutParams(-2, style.dp(44)));
        LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(0, style.dp(44), 1);
        barParams.setMargins(style.dp(14), 0, 0, 0);
        controls.addView(barsScroll, barParams);
        controlsParams = new LinearLayout.LayoutParams(-1, style.dp(44));
        controlsParams.setMargins(style.dp(52), style.dp(10), 0, 0);
        body.addView(controls, controlsParams);

        trainingScroll = new HorizontalScrollView(context);
        trainingScroll.setHorizontalScrollBarEnabled(false);
        trainingScroll.setFillViewport(false);
        trainingControls = new LinearLayout(context);
        trainingControls.setGravity(Gravity.CENTER_VERTICAL);
        loadMinus = trainingLink("−");
        loadLabel = style.sans("", 15, 0, true);
        loadLabel.setGravity(Gravity.CENTER);
        loadPlus = trainingLink("+");
        rirMinus = trainingLink("−");
        rirLabel = style.sans("", 15, 0, true);
        rirLabel.setGravity(Gravity.CENTER);
        rirPlus = trainingLink("+");
        safety = trainingLink(context.getString(R.string.training_safety_ok));
        trainingControls.addView(loadMinus, compactControl());
        trainingControls.addView(loadLabel, new LinearLayout.LayoutParams(style.dp(84), style.dp(44)));
        trainingControls.addView(loadPlus, compactControl());
        LinearLayout.LayoutParams rirMinusParams = compactControl();
        rirMinusParams.leftMargin = style.dp(10);
        trainingControls.addView(rirMinus, rirMinusParams);
        trainingControls.addView(rirLabel, new LinearLayout.LayoutParams(style.dp(58), style.dp(44)));
        trainingControls.addView(rirPlus, compactControl());
        LinearLayout.LayoutParams safetyParams = new LinearLayout.LayoutParams(-2, style.dp(44));
        safetyParams.leftMargin = style.dp(10);
        trainingControls.addView(safety, safetyParams);
        trainingScroll.addView(trainingControls,
                new HorizontalScrollView.LayoutParams(-2, style.dp(44)));
        LinearLayout.LayoutParams trainingParams = new LinearLayout.LayoutParams(-1, style.dp(44));
        trainingParams.setMargins(style.dp(52), style.dp(5), 0, 0);
        body.addView(trainingScroll, trainingParams);

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
        bindSurface(step, active, palette);
        bindText(step, active, palette);
        bindAssistant(step, active, palette, events);
        TimerSession timer = timers.forStep(step.id);
        boolean restBlocks = timer != null && timer.kind == TimerSession.Kind.REST
                && (timer.state == TimerSession.State.RUNNING
                || timer.state == TimerSession.State.PAUSED);
        bindRepetition(row, step, input, palette, events, restBlocks);
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
        amount.setText(step.amountLabel);
        amount.setTextColor(palette.muted);
        amount.setVisibility(!active && !step.amountLabel.isEmpty() ? VISIBLE : GONE);
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
        assistantPanel.bind(step.trainingContext, active, palette,
                action -> events.emit(TodayAction.trainingAssistant(action)));
    }

    private void bindRepetition(FocusStepRowUiModel row, FocusStepUiModel step,
                                RepetitionInputState input, DayPalette palette,
                                TodayActionSink events, boolean restBlocks) {
        RepetitionProgressUiModel progress = step.repetitionProgress;
        boolean editsRepetitions = row.action.kind
                == StepExecutionUiAction.Kind.SUBMIT_REPETITION;
        controls.setVisibility(editsRepetitions && !restBlocks ? VISIBLE : GONE);
        barsScroll.setVisibility(GONE);
        trainingScroll.setVisibility(GONE);
        if (editsRepetitions) {
            int current = input.valueFor(step);
            int editingIndex = input.editingIndexFor(step);
            stepper.bind(current, palette,
                    delta -> events.emit(TodayAction.adjustRepetition(step.id, delta)));
            barsScroll.setVisibility(progress.showsBars() ? VISIBLE : GONE);
            if (progress.showsBars()) bars.bind(step.id, progress.slotCount,
                    progress.repetitions, editingIndex, palette,
                    index -> events.emit(TodayAction.editRepetition(step.id, index)));
            bindTrainingInputs(step, progress, input, palette, events);
        } else {
            stepper.bind(0, palette, null);
            bars.bind(step.id, 0, java.util.Collections.emptyList(), -1, palette, null);
            bindTrainingInputs(step, null, input, palette, events);
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

    private void bindTrainingInputs(FocusStepUiModel step, RepetitionProgressUiModel progress,
                                    RepetitionInputState input, DayPalette palette,
                                    TodayActionSink events) {
        boolean visible = progress != null && progress.detailedTraining();
        trainingScroll.setVisibility(visible ? VISIBLE : GONE);
        bindTrainingLink(loadMinus, palette);
        bindTrainingLink(loadPlus, palette);
        bindTrainingLink(rirMinus, palette);
        bindTrainingLink(rirPlus, palette);
        bindTrainingLink(safety, palette);
        loadMinus.setOnClickListener(null);
        loadPlus.setOnClickListener(null);
        rirMinus.setOnClickListener(null);
        rirPlus.setOnClickListener(null);
        safety.setOnClickListener(null);
        if (!visible) {
            loadLabel.setText("");
            rirLabel.setText("");
            safety.setText("");
            loadMinus.setVisibility(GONE);
            loadPlus.setVisibility(GONE);
            rirMinus.setVisibility(GONE);
            rirPlus.setVisibility(GONE);
            safety.setVisibility(GONE);
            return;
        }
        ResistanceLoad load = input.loadFor(step);
        int rir = input.rirFor(step);
        boolean flagged = input.safetyFor(step);
        loadLabel.setText(formatLoad(load));
        loadLabel.setTextColor(palette.ink);
        rirLabel.setText(getContext().getString(R.string.training_rir_value, rir));
        rirLabel.setTextColor(palette.ink);
        int delta = load.unit == ResistanceLoad.Unit.LB ? 5_000 : 1_000;
        boolean adjustable = load.adjustable();
        loadMinus.setVisibility(adjustable ? VISIBLE : GONE);
        loadPlus.setVisibility(adjustable ? VISIBLE : GONE);
        rirMinus.setVisibility(VISIBLE);
        rirPlus.setVisibility(VISIBLE);
        safety.setVisibility(VISIBLE);
        loadMinus.setOnClickListener(adjustable ? view -> events.emit(
                TodayAction.adjustTrainingLoad(step.id, -delta)) : null);
        loadPlus.setOnClickListener(adjustable ? view -> events.emit(
                TodayAction.adjustTrainingLoad(step.id, delta)) : null);
        rirMinus.setOnClickListener(rir > 0 ? view -> events.emit(
                TodayAction.adjustTrainingRir(step.id, -1)) : null);
        rirPlus.setOnClickListener(rir < 5 ? view -> events.emit(
                TodayAction.adjustTrainingRir(step.id, 1)) : null);
        safety.setText(flagged ? R.string.training_safety_flagged : R.string.training_safety_ok);
        safety.setTextColor(flagged ? palette.bad : palette.hint);
        safety.setOnClickListener(view -> events.emit(TodayAction.toggleTrainingSafety(step.id)));
    }

    private TextLinkView trainingLink(String text) {
        TextLinkView view = new TextLinkView(getContext());
        view.setText(text);
        view.setGravity(Gravity.CENTER);
        AccessibilityRoles.button(view);
        return view;
    }

    private LinearLayout.LayoutParams compactControl() {
        return new LinearLayout.LayoutParams(style.dp(36), style.dp(44));
    }

    private static void bindTrainingLink(TextLinkView view, DayPalette palette) {
        view.bind(palette.hint, palette.dot);
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
        popup.getMenu().add(R.string.action_finish_today);
        popup.setOnMenuItemClickListener(item -> {
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
            views.addAll(stepper.grainOcclusions());
            if (barsScroll.getVisibility() == VISIBLE)
                views.add(GrainOcclusion.bounds(bars));
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

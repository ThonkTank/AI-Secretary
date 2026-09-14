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
    private final LinearLayout titleArea;
    private final TextView amount;
    private final TextView menu;
    private final TextView note;
    private final LinearLayout controls;
    private final RepStepperView stepper;
    private final android.widget.HorizontalScrollView barsScroll;
    private final SetBarsView bars;
    private final LinearLayout.LayoutParams controlsParams;
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
        titleArea = new LinearLayout(context);
        titleArea.setOrientation(VERTICAL);
        titleArea.addView(title, new LinearLayout.LayoutParams(-1, -2));
        header.addView(titleArea, titleParams);
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
        barsScroll = new android.widget.HorizontalScrollView(context);
        barsScroll.setHorizontalScrollBarEnabled(false);
        barsScroll.setFillViewport(false);
        bars = new SetBarsView(context);
        barsScroll.addView(bars, new android.widget.HorizontalScrollView.LayoutParams(-2, style.dp(44)));
        LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(0, style.dp(44), 1);
        barParams.setMargins(style.dp(14), 0, 0, 0);
        controls.addView(barsScroll, barParams);
        controlsParams = new LinearLayout.LayoutParams(-1, style.dp(44));
        controlsParams.setMargins(style.dp(52), style.dp(10), 0, 0);
        body.addView(controls, controlsParams);

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
        boolean flow = step.activeAction.isFlowExecution();
        android.view.ViewGroup amountParent = flow ? titleArea : header;
        if (amount.getParent() != amountParent) {
            ((android.view.ViewGroup) amount.getParent()).removeView(amount);
            if (flow) titleArea.addView(amount, new LinearLayout.LayoutParams(-2, -2));
            else header.addView(amount, header.indexOfChild(menu), new LinearLayout.LayoutParams(-2, -2));
        }
        title.setSingleLine(!flow);
        title.setMaxLines(flow ? Integer.MAX_VALUE : 1);
        title.setEllipsize(flow ? null : TextUtils.TruncateAt.END);
        title.setText(step.title);
        title.setTextColor(palette.ink);
        amount.setText(step.amountLabel);
        amount.setTextColor(palette.muted);
        amount.setVisibility((flow || !active) && !step.amountLabel.isEmpty() ? VISIBLE : GONE);
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



    private void bindRepetition(FocusStepRowUiModel row, FocusStepUiModel step,
                                RepetitionInputState input, DayPalette palette,
                                TodayActionSink events, boolean restBlocks) {
        RepetitionProgressUiModel progress = step.repetitionProgress;
        boolean editable = row.action.kind == StepExecutionUiAction.Kind.SUBMIT_REPETITION;
        controls.setVisibility(editable && !restBlocks ? VISIBLE : GONE);
        stepper.bind(editable ? input.valueFor(step) : 0, palette, editable
                ? delta -> events.emit(TodayAction.adjustRepetition(step.id, delta)) : null);
        boolean sets = editable && progress.showsBars();
        barsScroll.setVisibility(sets ? VISIBLE : GONE);
        bars.setVisibility(sets ? VISIBLE : GONE);
        bars.bind(step.id, sets ? progress.slotCount : 0,
                sets ? progress.repetitions : java.util.Collections.emptyList(),
                sets ? input.editingIndexFor(step) : -1, palette,
                sets ? index -> events.emit(TodayAction.editRepetition(step.id, index)) : null);
    }

    private void bindAction(StepExecutionUiAction action, FocusStepUiModel step,
                            RepetitionInputState input, TodayActionSink events,
                            boolean restBlocks) {
        menu.setVisibility(step.noteTargetId == null ? GONE : VISIBLE);
        menu.setOnClickListener(step.noteTargetId == null ? null : view -> showStepMenu(view, step, events));
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
        } else if (action.kind == StepExecutionUiAction.Kind.TOGGLE
                || action.kind == StepExecutionUiAction.Kind.TOGGLE_FLOW_RUN_STEP
                || action.kind == StepExecutionUiAction.Kind.START_FLOW_CANDIDATE
                || action.kind == StepExecutionUiAction.Kind.COLLECT_FLOW) {
            reward.setContentDescription(getContext().getString(
                    R.string.content_complete_step, step.title, step.reward.resultXp));
        } else if (action.kind == StepExecutionUiAction.Kind.TOGGLE_WITH_DELAY
                || action.kind == StepExecutionUiAction.Kind.TOGGLE_FLOW_RUN_STEP_WITH_DELAY
                || action.kind == StepExecutionUiAction.Kind.START_FLOW_CANDIDATE_WITH_DELAY) {
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
            case TOGGLE_FLOW_RUN_STEP:
                events.emit(TodayAction.completeFlowStep(action.stepId)); return;
            case COLLECT_FLOW:
                events.emit(TodayAction.collectFlow(action.stepId)); return;
            case TOGGLE_WITH_DELAY:
                FlowDurationDialog.show(getContext(), getContext().getString(
                                R.string.flow_delay_prompt_title), action.proposedDelayMillis,
                        delay -> events.emit(TodayAction.toggleStep(action.stepId, delay)));
                return;
            case TOGGLE_FLOW_RUN_STEP_WITH_DELAY:
                FlowDurationDialog.show(getContext(), getContext().getString(R.string.flow_delay_prompt_title),
                        action.proposedDelayMillis, delay -> events.emit(TodayAction.completeFlowStep(action.stepId, delay)));
                return;
            case START_FLOW_CANDIDATE:
                events.emit(TodayAction.startFlowCandidate(action.stepId));
                return;
            case START_FLOW_CANDIDATE_WITH_DELAY:
                FlowDurationDialog.show(getContext(), getContext().getString(
                                R.string.flow_delay_prompt_title), action.proposedDelayMillis,
                        delay -> events.emit(TodayAction.startFlowCandidate(
                                action.stepId, delay)));
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
        if (step.repetitionProgress != null && !step.isDone()) {
            for (int index = 0; index < step.repetitionProgress.repetitions.size(); index++) {
                popup.getMenu().add(0, editBase + index, index + 1,
                        getContext().getString(R.string.content_edit_set, index + 1));
            }
        }
        int finishId = 1;
        int finishOrder = step.repetitionProgress == null ? 0
                : step.repetitionProgress.repetitions.size();
        if (step.repetitionProgress != null && !step.isDone())
            popup.getMenu().add(0, finishId, finishOrder + 1, R.string.action_finish_today);
        int noteId = 2;
        popup.getMenu().add(0, noteId, 0, R.string.action_edit_step_note);
        popup.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == noteId) {
                events.emit(TodayAction.editStepNote(step.noteTargetId));
                return true;
            }
            if (item.getItemId() >= editBase) {
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
            views.addAll(stepper.grainOcclusions());
            if (barsScroll.getVisibility() == VISIBLE) views.add(GrainOcclusion.bounds(barsScroll));
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

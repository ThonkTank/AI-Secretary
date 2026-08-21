package de.thonktank.autosecretary;

import android.content.Context;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import de.thonktank.autosecretary.presentation.RepetitionProgressUiModel;
import de.thonktank.autosecretary.presentation.FocusStepUiModel;
import de.thonktank.autosecretary.presentation.today.StepExecutionUiAction;
import de.thonktank.autosecretary.presentation.today.TodayAction;
import de.thonktank.autosecretary.presentation.today.TodayActionSink;
import de.thonktank.autosecretary.ui.leaf.GrainSpec;

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
    private final TextView note;
    private final LinearLayout controls;
    private final RepStepperView stepper;
    private final HorizontalScrollView barsScroll;
    private final SetBarsView bars;
    private final LinearLayout.LayoutParams controlsParams;
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
    }

    public void bind(FocusStepUiModel step, boolean active, DayPalette palette,
                     RepetitionInputState input, TodayActionSink events) {
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
        title.setText(step.title);
        title.setTextColor(palette.ink);
        amount.setText(step.amountLabel);
        amount.setTextColor(palette.muted);
        amount.setVisibility(!active && !step.amountLabel.isEmpty() ? VISIBLE : GONE);
        note.setText(step.note);
        note.setTextColor(palette.hint);
        note.setVisibility(step.note.isEmpty() ? GONE : VISIBLE);
        WoodGrainView.applyTextHalo(title, palette.leaf1);
        WoodGrainView.applyTextHalo(amount, palette.leaf1);
        WoodGrainView.applyTextHalo(note, palette.leaf1);

        RepetitionProgressUiModel progress = step.repetitionProgress;
        boolean editsRepetitions = step.executionAction.kind
                == StepExecutionUiAction.Kind.SUBMIT_REPETITION;
        controls.setVisibility(editsRepetitions ? VISIBLE : GONE);
        if (editsRepetitions) {
            int current = input.valueFor(step);
            int editingIndex = input.editingIndexFor(step);
            stepper.bind(current, palette,
                    delta -> events.emit(TodayAction.adjustRepetition(step.id, delta)));
            barsScroll.setVisibility(progress.showsBars() ? VISIBLE : GONE);
            if (progress.showsBars()) bars.bind(step.id, progress.slotCount,
                    progress.actualRepetitions, editingIndex, palette,
                    index -> events.emit(TodayAction.editRepetition(step.id, index)));
            reward.setContentDescription(progress.kind == RepetitionProgressUiModel.Kind.SINGLE
                    ? getContext().getString(R.string.content_confirm_repetitions, current)
                    : getContext().getString(editingIndex >= 0
                            ? R.string.content_update_set : R.string.content_confirm_set,
                    editingIndex >= 0 ? editingIndex + 1 : progress.nextSlotNumber(), current));
        } else if (step.executionAction.kind == StepExecutionUiAction.Kind.TOGGLE) {
            reward.setContentDescription(getContext().getString(
                    R.string.content_complete_step, step.title, step.reward.resultXp));
        } else if (step.executionAction.kind
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
        reward.setOnClickListener(step.executionAction.kind == StepExecutionUiAction.Kind.NONE
                ? null : view -> emitExecution(step.executionAction, events));
        reward.setActionEnabled(step.executionAction.kind != StepExecutionUiAction.Kind.NONE);
    }

    private static void emitExecution(StepExecutionUiAction action, TodayActionSink events) {
        switch (action.kind) {
            case TOGGLE:
                events.emit(TodayAction.toggleStep(action.stepId));
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

    void setOnStepLongClickListener(OnLongClickListener listener) {
        body.setOnLongClickListener(listener);
    }

    void configureReorderAccessibility(String stepId, String title, boolean canMoveUp,
                                       boolean canMoveDown, ReorderAction action) {
        body.setFocusable(true);
        body.setContentDescription(getContext().getString(R.string.a11y_today_step_row, title));
        body.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override public void onInitializeAccessibilityNodeInfo(
                    View host, AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
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

    public List<View> grainTextViews() {
        List<View> views = new ArrayList<>();
        views.add(title);
        if (amount.getVisibility() == VISIBLE) views.add(amount);
        if (note.getVisibility() == VISIBLE) views.add(note);
        if (controls.getVisibility() == VISIBLE) {
            views.addAll(stepper.grainTextViews());
            if (barsScroll.getVisibility() == VISIBLE) views.add(bars);
        }
        return views;
    }

    void appendGrainAnchor(List<GrainSpec.Anchor> anchors) {
        anchors.add(GrainSpec.sizedAnchor(reward, reward.grainWidth(),
                reward.grainHeight(), grainLevel));
    }

}

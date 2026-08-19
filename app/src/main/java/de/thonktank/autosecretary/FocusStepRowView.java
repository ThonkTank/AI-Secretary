package de.thonktank.autosecretary;

import android.content.Context;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import de.thonktank.autosecretary.presentation.RepetitionProgressUiModel;
import de.thonktank.autosecretary.presentation.TaskStepUiModel;

/** Modular focus-card row for the running step and compact following steps. */
public final class FocusStepRowView extends LinearLayout {
    public interface Actions {
        void onToggleStep(String stepId);
        void onConfirmRepetitions(String stepId, int repetitions);
        void onEditRepetitions(String stepId, List<Integer> repetitions);
        void onRepetitionInputStateChanged(RepetitionInputState state);
    }

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

    public void bind(TaskStepUiModel step, boolean active, DayPalette palette,
                     RepetitionInputState input, Actions actions) {
        topLine.setVisibility(active ? VISIBLE : GONE);
        bottomLine.setVisibility(active ? VISIBLE : GONE);
        int divider = UiStyle.alpha(palette.dot, .45f);
        topLine.setBackgroundColor(divider);
        bottomLine.setBackgroundColor(divider);
        body.setPadding(0, active ? style.dp(11) : 0,
                0, active ? style.dp(13) : 0);
        reward.bind(false, false, palette, step.claimableXp);
        reward.setActiveOutline(active);
        reward.setActionEnabled(active);
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
        controls.setVisibility(active && progress != null ? VISIBLE : GONE);
        if (active && progress != null) {
            int current = input.valueFor(step);
            int editingIndex = input.editingIndexFor(step);
            stepper.bind(current, palette,
                    delta -> actions.onRepetitionInputStateChanged(input.adjust(step, delta)));
            barsScroll.setVisibility(progress.showsBars() ? VISIBLE : GONE);
            if (progress.showsBars()) bars.bind(step.id, progress.slotCount,
                    progress.actualRepetitions, editingIndex, palette,
                    index -> actions.onRepetitionInputStateChanged(input.edit(step, index)));
            reward.setContentDescription(progress.kind == RepetitionProgressUiModel.Kind.SINGLE
                    ? getContext().getString(R.string.content_confirm_repetitions, current)
                    : getContext().getString(editingIndex >= 0
                            ? R.string.content_update_set : R.string.content_confirm_set,
                    editingIndex >= 0 ? editingIndex + 1 : progress.nextSlotNumber(), current));
            reward.setOnClickListener(view -> commit(step, input, actions));
        } else if (active) {
            reward.setContentDescription(getContext().getString(
                    R.string.content_complete_step, step.title, step.claimableXp));
            reward.setOnClickListener(view -> actions.onToggleStep(step.id));
        } else {
            StringBuilder description = new StringBuilder(step.title);
            if (!step.amountLabel.isEmpty()) description.append(", ").append(step.amountLabel);
            if (!step.note.isEmpty()) description.append(", ").append(step.note);
            description.append(", ").append(step.claimableXp).append(" XP");
            reward.setContentDescription(description.toString());
            reward.setOnClickListener(null);
        }
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

    CharSequence renderedTitle() { return title.getText(); }
    CharSequence renderedSubtitle() { return note.getText(); }
    boolean editorVisible() { return controls.getVisibility() == VISIBLE; }

    private static void commit(TaskStepUiModel step, RepetitionInputState input,
                               Actions actions) {
        int value = input.valueFor(step);
        int editingIndex = input.editingIndexFor(step);
        actions.onRepetitionInputStateChanged(RepetitionInputState.idle());
        if (editingIndex < 0) {
            actions.onConfirmRepetitions(step.id, value);
            return;
        }
        List<Integer> values = new ArrayList<>(
                step.repetitionProgress.actualRepetitions);
        values.set(editingIndex, value);
        actions.onEditRepetitions(step.id, values);
    }
}

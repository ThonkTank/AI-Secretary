package de.thonktank.autosecretary.ui.today;

import de.thonktank.autosecretary.*;

import de.thonktank.autosecretary.presentation.today.FocusTaskUiModel;
import de.thonktank.autosecretary.presentation.today.TodayAction;
import de.thonktank.autosecretary.presentation.today.TodayActionSink;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import de.thonktank.autosecretary.presentation.today.FocusStepUiModel;
import de.thonktank.autosecretary.ui.leaf.GrainSpec;
import de.thonktank.autosecretary.ui.leaf.GrainOcclusion;
import de.thonktank.autosecretary.ui.leaf.WoodGrainView;

/** Content-only focus card. Decoration and transition effects live outside this view. */
public final class FocusCardView extends ViewGroup {
    private final UiStyle style;
    private final FrameLayout titleRow;
    private final TextView title;
    private final TextView backlog;
    private final TextView placement;
    private final XpVesselView ring;
    private final DewDotView taskDew;
    private final FocusStepListLayout steps;
    private final LinearLayout actions;
    private final TextView primary;
    private final TextLinkView later;
    private final android.widget.ScrollView waiting;
    private final LinearLayout waitRows;
    private int maximumContentHeight = Integer.MAX_VALUE;
    private boolean reorderingSteps;

    FocusCardView(Context context, TodayActionSink events,
                  EdgeAutoScroller.ScrollHost scrollHost) {
        super(context);
        style = new UiStyle(context);
        setPadding(style.dimen(R.dimen.focus_card_padding_start),
                style.dimen(R.dimen.focus_card_padding_vertical),
                style.dimen(R.dimen.focus_card_padding_end),
                style.dimen(R.dimen.focus_card_padding_vertical));
        setBackgroundColor(Color.TRANSPARENT);

        titleRow = new FrameLayout(context);
        LinearLayout titleBlock = new LinearLayout(context);
        titleBlock.setOrientation(LinearLayout.VERTICAL);
        title = style.serif("", 37, 0, false, 200);
        title.setLineSpacing(0, 1.04f);
        title.setLetterSpacing(-.02f);
        title.setTextSize(30);
        title.setPadding(0, 0, style.dp(82), 0);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-1, -2);
        titleParams.topMargin = style.dp(4);
        titleBlock.addView(title, titleParams);
        placement = style.sans("", 14, 0, false);
        titleBlock.addView(placement, new LinearLayout.LayoutParams(-1, -2));
        backlog = style.sans("", 14, 0, true);
        titleBlock.addView(backlog, new LinearLayout.LayoutParams(-1, -2));
        titleRow.addView(titleBlock, new FrameLayout.LayoutParams(-1, -2));
        ring = new XpVesselView(context);
        titleRow.addView(ring, new FrameLayout.LayoutParams(style.dp(68), style.dp(68),
                Gravity.TOP | Gravity.END));
        taskDew = new DewDotView(context);
        taskDew.setVisibility(GONE);
        titleRow.addView(taskDew, new FrameLayout.LayoutParams(style.dp(48), style.dp(48),
                Gravity.TOP | Gravity.END));
        addView(titleRow, new MarginLayoutParams(-1, -2));

        steps = new FocusStepListLayout(context, events, scrollHost);
        steps.setReorderModeListener(active -> {
            reorderingSteps = active;
            requestLayout();
        });
        MarginLayoutParams stepsParams = new MarginLayoutParams(-1, -2);
        stepsParams.topMargin = style.dimen(R.dimen.focus_card_steps_gap);
        addView(steps, stepsParams);

        waiting = new android.widget.ScrollView(context);
        waitRows = new LinearLayout(context);
        waitRows.setOrientation(LinearLayout.VERTICAL);
        waiting.addView(waitRows, new android.widget.ScrollView.LayoutParams(-1, -2));
        waiting.setVisibility(GONE);
        addView(waiting, new MarginLayoutParams(-1, -2));

        actions = new LinearLayout(context);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        MarginLayoutParams actionParams = new MarginLayoutParams(-1, -2);
        actionParams.topMargin = style.dimen(R.dimen.focus_card_actions_gap);
        addView(actions, actionParams);
        primary = new TextView(context);
        primary.setGravity(Gravity.CENTER);
        primary.setMinHeight(style.dp(52));
        primary.setPadding(style.dp(28), 0, style.dp(28), 0);
        primary.setTypeface(style.sansBold);
        primary.setTextSize(17);
        AccessibilityRoles.button(primary);
        actions.addView(primary, new LinearLayout.LayoutParams(-2, style.dp(52)));
        later = new TextLinkView(context);
        AccessibilityRoles.button(later);
        later.setText(R.string.action_later);
        LinearLayout.LayoutParams laterParams = new LinearLayout.LayoutParams(-2, style.dp(52));
        laterParams.leftMargin = style.dp(18);
        actions.addView(later, laterParams);
    }

    void bind(FocusCardUiModel model, TodayActionSink events, Runnable onDefer) {
        FocusTaskUiModel task = model.task;
        boolean compact = task.ongoing && task.steps.isEmpty();
        setPadding(style.dimen(R.dimen.focus_card_padding_start),
                style.dimen(R.dimen.focus_card_padding_vertical),
                style.dimen(R.dimen.focus_card_padding_end),
                style.dimen(compact ? R.dimen.focus_card_compact_padding_bottom
                        : R.dimen.focus_card_padding_vertical));
        MarginLayoutParams actionParams = (MarginLayoutParams) actions.getLayoutParams();
        actionParams.topMargin = style.dimen(compact
                ? R.dimen.focus_card_compact_actions_gap : R.dimen.focus_card_actions_gap);

        placement.setText(task.placementLabel);
        placement.setTextColor(model.palette.hint);
        placement.setVisibility(task.placementLabel.isEmpty() ? GONE : VISIBLE);
        placement.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        title.setText(task.title());
        title.setTextColor(model.palette.ink);
        backlog.setText(task.backlogCount == 0 ? "" : getContext().getString(
                R.string.today_backlog_count, task.backlogCount));
        backlog.setTextColor(model.palette.hint);
        backlog.setVisibility(task.backlogCount == 0 ? GONE : VISIBLE);
        WoodGrainView.applyTextHalo(title, model.palette.leaf1);
        boolean flow = task.actionTarget.item.kind == de.thonktank.autosecretary.presentation.today.TodayItemTarget.Kind.FLOW_TASK_SHEET;
        title.setPadding(0, 0, flow ? 0 : style.dp(82), 0);
        boolean vessel = !task.steps.isEmpty() && !flow;
        ring.setVisibility(vessel ? VISIBLE : GONE);
        taskDew.setVisibility(vessel || flow ? GONE : VISIBLE);
        if (vessel) {
            ring.setPalette(model.palette);
            ring.bind(task.vessel);
            ring.setOnClickListener(task.harvestReady
                    ? view -> events.emit(TodayAction.harvest(task.itemId())) : null);
        } else {
            taskDew.bind(false, false, model.palette, task.reward.resultXp);
            taskDew.setContentDescription(getContext().getString(
                    R.string.content_complete_task, task.title(), task.reward.resultXp));
            taskDew.setOnClickListener(view -> events.emit(task.terminalCondition()
                    ? TodayAction.requestClose(task.taskId(), task.title())
                    : TodayAction.completeOccurrence(task.itemId())));
        }

        steps.bind(model);
        waitRows.removeAllViews();
        waiting.setVisibility(task.waits.isEmpty() ? GONE : VISIBLE);
        for (de.thonktank.autosecretary.presentation.today.FlowWaitUiModel wait : task.waits) {
            TextView row = style.sans("", 13, model.palette.hint, false);
            String status = wait.elapsed ? "Wartezeit verlängern" : wait.readyAtEpochMillis == null ? "wartet auf Kapazität"
                    : de.thonktank.autosecretary.presentation.mobile.RemainingDurationFormatterKt.remainingDurationText(
                            wait.readyAtEpochMillis, System.currentTimeMillis());
            row.setText(wait.title + " · " + status);
            row.setPadding(0, style.dp(6), 0, style.dp(6));
            row.setMinHeight(style.dp(48));
            if (wait.readyAtEpochMillis != null || wait.elapsed) {
                AccessibilityRoles.button(row);
                row.setContentDescription(row.getText() + ", Wartezeit ändern");
                row.setOnClickListener(view -> FlowDurationDialog.show(getContext(),
                        getContext().getString(R.string.flow_adjust_prompt_title),
                        wait.readyAtEpochMillis == null ? 0 : Math.max(0, wait.readyAtEpochMillis - System.currentTimeMillis()),
                        delay -> events.emit(TodayAction.adjustFlowWait(wait.runId, wait.waitId,
                                System.currentTimeMillis() + delay))));
            }
            waitRows.addView(row, new LinearLayout.LayoutParams(-1, -2));
        }
        primary.setText(R.string.action_complete_rest);
        primary.setTextColor(model.palette.accentText);
        primary.setBackground(style.pill(model.palette.accent, 26));
        style.shadow(primary, model.palette, 5, .7f);
        primary.setOnClickListener(view -> events.emit(
                TodayAction.completeRemaining(task.itemId())));
        primary.setVisibility(vessel && task.remainingSteps > 0 && task.allowBulkComplete
                ? VISIBLE : GONE);
        later.setVisibility(task.allowDefer ? VISIBLE : GONE);
        later.bind(model.palette.hint, model.palette.dot);
        later.setOnClickListener(view -> {
            onDefer.run();
            events.emit(TodayAction.defer(task.actionTarget));
        });
        requestLayout();
    }

    void setMaximumContentHeight(int maximumHeight) {
        int value = Math.max(0, maximumHeight);
        if (maximumContentHeight == value) return;
        maximumContentHeight = value;
        requestLayout();
    }

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int innerWidth = Math.max(0, width - getPaddingLeft() - getPaddingRight());
        int childWidth = MeasureSpec.makeMeasureSpec(innerWidth, MeasureSpec.EXACTLY);
        int naturalHeight = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
        titleRow.measure(childWidth, naturalHeight);
        actions.measure(childWidth, naturalHeight);
        if (waiting.getVisibility() != GONE)
            waiting.measure(childWidth, MeasureSpec.makeMeasureSpec(style.dp(104), MeasureSpec.AT_MOST));

        int fixedHeight = getPaddingTop() + getPaddingBottom()
                + titleRow.getMeasuredHeight() + childExtentWithoutHeight(actions)
                + (waiting.getVisibility() == GONE ? 0 : extent(waiting));
        int listBudget = reorderingSteps ? Integer.MAX_VALUE
                : Math.max(0, maximumContentHeight - fixedHeight - topMargin(steps));
        if (steps.getVisibility() != GONE)
            steps.measure(childWidth, MeasureSpec.makeMeasureSpec(listBudget, MeasureSpec.AT_MOST));
        int desiredHeight = fixedHeight;
        if (steps.getVisibility() != GONE) desiredHeight += extent(steps);
        int constrainedHeight = reorderingSteps ? desiredHeight
                : Math.min(desiredHeight, maximumContentHeight);
        setMeasuredDimension(resolveSize(width, widthMeasureSpec),
                resolveSize(constrainedHeight, heightMeasureSpec));
    }

    @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int y = getPaddingTop();
        y = layoutChild(titleRow, y);
        if (steps.getVisibility() != GONE) y = layoutChild(steps, y);
        if (waiting.getVisibility() != GONE) y = layoutChild(waiting, y);
        layoutChild(actions, y);
    }

    private int layoutChild(View child, int y) {
        MarginLayoutParams params = (MarginLayoutParams) child.getLayoutParams();
        int childTop = y + params.topMargin;
        child.layout(getPaddingLeft() + params.leftMargin, childTop,
                getWidth() - getPaddingRight() - params.rightMargin,
                childTop + child.getMeasuredHeight());
        return childTop + child.getMeasuredHeight() + params.bottomMargin;
    }

    private int childExtentWithoutHeight(View child) {
        MarginLayoutParams params = (MarginLayoutParams) child.getLayoutParams();
        return params.topMargin + child.getMeasuredHeight() + params.bottomMargin;
    }

    private int extent(View child) {
        return topMargin(child) + child.getMeasuredHeight()
                + ((MarginLayoutParams) child.getLayoutParams()).bottomMargin;
    }

    private int topMargin(View child) {
        return ((MarginLayoutParams) child.getLayoutParams()).topMargin;
    }

    void registerRewardAnchors(RewardAnchorRegistry registry, FocusTaskUiModel task) {
        if (ring.getVisibility() == VISIBLE)
            registry.register(new RewardAnchorKey(RewardAnchorKey.Kind.VESSEL,
                    task.itemId()), ring);
        else if (taskDew.getVisibility() == VISIBLE) registry.register(new RewardAnchorKey(task.terminalCondition()
                        ? RewardAnchorKey.Kind.TASK : RewardAnchorKey.Kind.OCCURRENCE,
                task.terminalCondition() ? task.taskId() : task.itemId()), taskDew);
        steps.registerRewardAnchors(registry);
        registry.register(new RewardAnchorKey(RewardAnchorKey.Kind.REST,
                task.itemId()), primary);
    }

    View mainRewardAnchor() { return steps.getVisibility() == VISIBLE ? ring : taskDew; }

    GrainSpec grainSpec(FocusTaskUiModel task) {
        List<GrainSpec.Anchor> anchors = new ArrayList<>();
        if (mainRewardAnchor().getVisibility() == VISIBLE) anchors.add(grainAnchor(mainRewardAnchor(), task.grainLevel));
        anchors.addAll(steps.grainAnchors());
        return GrainSpec.anchors(anchors, grainOcclusions());
    }

    List<GrainOcclusion> grainOcclusions() {
        List<GrainOcclusion> faded = new ArrayList<>();
        faded.add(GrainOcclusion.text(title));
        faded.addAll(steps.grainOcclusions());
        for (int i = 0; i < waitRows.getChildCount(); i++) faded.add(GrainOcclusion.text((TextView) waitRows.getChildAt(i)));
        if (primary.getVisibility() == VISIBLE) faded.add(GrainOcclusion.text(primary));
        if (later.getVisibility() == VISIBLE) faded.add(GrainOcclusion.text(later));
        return faded;
    }

    private GrainSpec.Anchor grainAnchor(View anchor, int level) {
        if (anchor instanceof DewDotView)
            return GrainSpec.sizedAnchor(anchor, ((DewDotView) anchor).grainWidth(),
                    ((DewDotView) anchor).grainHeight(), level);
        return GrainSpec.anchor(anchor, level);
    }

    @Override protected LayoutParams generateDefaultLayoutParams() {
        return new MarginLayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
    }

    @Override protected LayoutParams generateLayoutParams(LayoutParams params) {
        return new MarginLayoutParams(params);
    }

    @Override public LayoutParams generateLayoutParams(android.util.AttributeSet attrs) {
        return new MarginLayoutParams(getContext(), attrs);
    }

    @Override protected boolean checkLayoutParams(LayoutParams params) {
        return params instanceof MarginLayoutParams;
    }
}

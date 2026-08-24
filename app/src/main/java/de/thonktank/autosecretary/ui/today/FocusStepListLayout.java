package de.thonktank.autosecretary.ui.today;

import de.thonktank.autosecretary.*;

import android.content.ClipData;
import android.content.Context;
import android.view.DragEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import de.thonktank.autosecretary.presentation.today.FocusStepUiModel;
import de.thonktank.autosecretary.presentation.today.FocusStepStatus;
import de.thonktank.autosecretary.presentation.today.TodayAction;
import de.thonktank.autosecretary.presentation.today.TodayActionSink;
import de.thonktank.autosecretary.presentation.today.TodayFeatureState;
import de.thonktank.autosecretary.ui.leaf.GrainSpec;
import de.thonktank.autosecretary.ui.leaf.WoodGrainView;

/**
 * Rendering-only vertical step list. The coordinator owns canonical, preview and persistence
 * state; this view only lays out rows and translates gestures into {@link TodayAction}s.
 */
public final class FocusStepListLayout extends ViewGroup {
    private final UiStyle style;
    private final TextView doneStatus;
    private final TextView moreStatus;
    private final List<FocusStepRowView> rows = new ArrayList<>();
    private final List<String> rowIds = new ArrayList<>();
    private final TodayActionSink actions;
    private final EdgeAutoScroller autoScroller;
    private DayPalette palette;
    private int maximumFollowing;
    private int visibleFollowing;
    private TodayFeatureState.Reorder reorder;
    private ReorderModeListener reorderModeListener = active -> { };

    interface ReorderModeListener { void onReorderModeChanged(boolean active); }

    FocusStepListLayout(Context context, TodayActionSink actions,
                        EdgeAutoScroller.ScrollHost scrollHost) {
        super(context);
        if (actions == null) throw new IllegalArgumentException("Today actions are required");
        this.actions = actions;
        style = new UiStyle(context);
        autoScroller = scrollHost == null ? null : new EdgeAutoScroller(scrollHost,
                () -> android.os.SystemClock.uptimeMillis(), style.dp(56), style.dp(360));
        doneStatus = style.serif("", 15, 0, true, 400);
        addView(doneStatus, new MarginLayoutParams(-1, -2));
        moreStatus = style.serif("", 15, 0, true, 400);
        MarginLayoutParams moreParams = new MarginLayoutParams(-1, -2);
        moreParams.leftMargin = style.dimen(R.dimen.focus_step_more_start);
        moreParams.topMargin = style.dimen(R.dimen.focus_step_more_gap);
        addView(moreStatus, moreParams);
        setOnDragListener(this::onStepDrag);
    }

    void setReorderModeListener(ReorderModeListener listener) {
        reorderModeListener = listener == null ? active -> { } : listener;
    }

    void bind(FocusCardUiModel model) {
        palette = model.palette;
        reorder = model.reorder;
        List<FocusStepUiModel> openSteps = new ArrayList<>();
        int doneCount = 0;
        for (FocusStepUiModel step : model.task.steps) {
            if (step.isDone()) doneCount++;
            else openSteps.add(step);
        }
        rowIds.clear();
        for (FocusStepUiModel step : openSteps) rowIds.add(step.id);
        doneStatus.setText(doneCount == 0 ? "" : getResources().getQuantityString(
                R.plurals.focus_steps_done, doneCount, doneCount));
        doneStatus.setTextColor(model.palette.done);
        doneStatus.setVisibility(doneCount == 0 ? GONE : VISIBLE);
        WoodGrainView.applyTextHalo(doneStatus, model.palette.leaf1);

        while (rows.size() < openSteps.size()) {
            FocusStepRowView row = new FocusStepRowView(getContext());
            addView(row, getChildCount() - 1, new MarginLayoutParams(-1, -2));
            rows.add(row);
        }
        for (int index = 0; index < rows.size(); index++) {
            FocusStepRowView row = rows.get(index);
            if (index >= openSteps.size()) {
                row.setVisibility(GONE);
                continue;
            }
            FocusStepUiModel step = openSteps.get(index);
            row.bind(step, step.status == FocusStepStatus.ACTIVE, model.palette,
                    model.repetitionInput, actions);
            row.setOnStepLongClickListener(view -> beginReorder(row, step.id));
            final int renderedIndex = index;
            row.configureReorderAccessibility(step.id, step.title, index > 0,
                    index < openSteps.size() - 1,
                    (stepId, actionId) -> accessibilityMove(stepId, renderedIndex, actionId));
        }
        int availableFollowing = Math.max(0, openSteps.size() - 1);
        maximumFollowing = model.stepLimit.automatic() ? availableFollowing
                : Math.min(model.stepLimit.maximumFollowingSteps, availableFollowing);
        applyVisibleFollowing(isReordering() ? availableFollowing : maximumFollowing);
        reorderModeListener.onReorderModeChanged(isReordering());
        setVisibility(model.task.steps.isEmpty() ? GONE : VISIBLE);
        requestLayout();
        if (PresentationTrace.enabled()) PresentationTrace.emit("today-steps", "bind",
                "rows=" + rowIds.size() + " reorder="
                        + (reorder == null ? "none" : reorder.phase));
    }

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int childWidth = Math.max(0, width - getPaddingLeft() - getPaddingRight());
        int exactChildWidth = MeasureSpec.makeMeasureSpec(childWidth, MeasureSpec.EXACTLY);
        int unlimitedHeight = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);

        int required = 0;
        if (doneStatus.getVisibility() != GONE) {
            doneStatus.measure(exactChildWidth, unlimitedHeight);
            required += extent(doneStatus);
        }
        int[] followingHeights = new int[Math.max(0, rowIds.size() - 1)];
        for (int index = 0; index < rowIds.size(); index++) {
            FocusStepRowView row = rows.get(index);
            updateRowMargin(row, index);
            row.measure(exactChildWidth, unlimitedHeight);
            if (index == 0) required += extent(row);
            else followingHeights[index - 1] = extent(row);
        }

        setMoreText(followingHeights.length);
        moreStatus.measure(exactChildWidth, unlimitedHeight);
        int moreExtent = extent(moreStatus);
        int available = MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED
                ? Integer.MAX_VALUE : MeasureSpec.getSize(heightMeasureSpec);
        int chosen = isReordering() ? followingHeights.length
                : FocusStepLayoutPolicy.visibleFollowing(available, required,
                        followingHeights, moreExtent, maximumFollowing);
        applyVisibleFollowing(chosen);
        if (moreStatus.getVisibility() == VISIBLE)
            moreStatus.measure(exactChildWidth, unlimitedHeight);

        int height = required;
        for (int index = 0; index < chosen; index++) height += followingHeights[index];
        if (moreStatus.getVisibility() == VISIBLE) height += extent(moreStatus);
        setMeasuredDimension(resolveSize(width, widthMeasureSpec),
                resolveSize(height + getPaddingTop() + getPaddingBottom(), heightMeasureSpec));
    }

    @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int y = getPaddingTop();
        for (int index = 0; index < getChildCount(); index++) {
            View child = getChildAt(index);
            if (child.getVisibility() == GONE) continue;
            MarginLayoutParams params = (MarginLayoutParams) child.getLayoutParams();
            y += params.topMargin;
            child.layout(getPaddingLeft() + params.leftMargin, y,
                    getWidth() - getPaddingRight() - params.rightMargin,
                    y + child.getMeasuredHeight());
            y += child.getMeasuredHeight() + params.bottomMargin;
        }
    }

    private void updateRowMargin(FocusStepRowView row, int index) {
        MarginLayoutParams params = (MarginLayoutParams) row.getLayoutParams();
        params.topMargin = index == 0
                ? (doneStatus.getVisibility() == VISIBLE
                        ? style.dimen(R.dimen.focus_step_done_gap) : 0)
                : style.dimen(index == 1 ? R.dimen.focus_step_first_following_gap
                        : R.dimen.focus_step_following_gap);
    }

    private int extent(View child) {
        MarginLayoutParams params = (MarginLayoutParams) child.getLayoutParams();
        return params.topMargin + child.getMeasuredHeight() + params.bottomMargin;
    }

    private void applyVisibleFollowing(int following) {
        visibleFollowing = Math.max(0, Math.min(following, Math.max(0, rowIds.size() - 1)));
        for (int index = 0; index < rows.size(); index++)
            rows.get(index).setVisibility(index < rowIds.size()
                    && (index == 0 || index <= visibleFollowing) ? VISIBLE : GONE);
        setMoreText(Math.max(0, rowIds.size() - 1 - visibleFollowing));
    }

    private void setMoreText(int hidden) {
        if (isReordering()) hidden = 0;
        moreStatus.setText(hidden == 0 ? "" : getResources().getQuantityString(
                R.plurals.focus_steps_more, hidden, hidden));
        if (palette != null) {
            moreStatus.setTextColor(palette.muted);
            WoodGrainView.applyTextHalo(moreStatus, palette.leaf1);
        }
        moreStatus.setVisibility(hidden == 0 ? GONE : VISIBLE);
    }

    private boolean beginReorder(FocusStepRowView row, String stepId) {
        if (rowIds.size() < 2) return false;
        emit(TodayAction.beginReorder(stepId, rowIds));
        ClipData data = ClipData.newPlainText("today-step", stepId);
        boolean started = row.startDragAndDrop(data, new View.DragShadowBuilder(row), stepId, 0);
        if (!started) emit(TodayAction.cancelReorder(stepId));
        return started;
    }

    private boolean onStepDrag(View view, DragEvent event) {
        String stepId = dragStepId(event);
        if (stepId == null) return false;
        if (event.getAction() == DragEvent.ACTION_DRAG_LOCATION) {
            emitPreview(stepId, targetIndex(event.getY()));
            if (autoScroller != null) autoScroller.update(event.getY(), getHeight());
            return true;
        }
        if (event.getAction() == DragEvent.ACTION_DROP) {
            emit(TodayAction.dropReorder(stepId, beforeStepId(stepId)));
            if (autoScroller != null) autoScroller.stop();
            return true;
        }
        if (event.getAction() == DragEvent.ACTION_DRAG_ENDED) {
            if (autoScroller != null) autoScroller.stop();
            if (!event.getResult()) emit(TodayAction.cancelReorder(stepId));
            return true;
        }
        return true;
    }

    private void emitPreview(String stepId, int target) {
        List<String> preview = movedOrder(rowIds, stepId, target);
        if (!preview.equals(rowIds)) emit(TodayAction.previewReorder(stepId, preview));
    }

    private int targetIndex(float y) {
        int target = Math.max(0, rowIds.size() - 1);
        for (int index = 0; index < rowIds.size(); index++) {
            FocusStepRowView row = rows.get(index);
            if (y < row.getTop() + row.getHeight() / 2f) return index;
        }
        return target;
    }

    private String beforeStepId(String movingStepId) {
        int index = rowIds.indexOf(movingStepId);
        return index >= 0 && index + 1 < rowIds.size() ? rowIds.get(index + 1) : null;
    }

    private boolean accessibilityMove(String stepId, int index, int actionId) {
        int target;
        if (actionId == R.id.action_today_step_up && index > 0) target = index - 1;
        else if (actionId == R.id.action_today_step_down && index < rowIds.size() - 1)
            target = index + 1;
        else if (actionId == R.id.action_today_step_front && index > 0) target = 0;
        else if (actionId == R.id.action_today_step_back && index < rowIds.size() - 1)
            target = rowIds.size() - 1;
        else return false;
        List<String> preview = movedOrder(rowIds, stepId, target);
        emit(TodayAction.beginReorder(stepId, rowIds));
        emit(TodayAction.previewReorder(stepId, preview));
        int movedIndex = preview.indexOf(stepId);
        String before = movedIndex + 1 < preview.size() ? preview.get(movedIndex + 1) : null;
        emit(TodayAction.dropReorder(stepId, before));
        announceForAccessibility(getContext().getString(R.string.a11y_today_step_moved));
        return true;
    }

    private void emit(TodayAction action) {
        if (PresentationTrace.enabled())
            PresentationTrace.emit("today-steps", "action", action.kind.name());
        actions.emit(action);
    }

    private static List<String> movedOrder(List<String> source, String stepId, int target) {
        List<String> result = new ArrayList<>(source);
        int current = result.indexOf(stepId);
        if (current < 0 || target < 0 || target >= result.size()) return result;
        result.remove(current);
        result.add(Math.min(target, result.size()), stepId);
        return result;
    }

    private static String dragStepId(DragEvent event) {
        return event.getLocalState() instanceof String ? (String) event.getLocalState() : null;
    }

    private boolean isReordering() {
        return reorder != null && reorder.phase != TodayFeatureState.Reorder.Phase.IDLE;
    }

    void registerRewardAnchors(RewardAnchorRegistry registry) {
        for (int index = 0; index < rowIds.size() && index < rows.size(); index++)
            registry.register(new RewardAnchorKey(RewardAnchorKey.Kind.STEP,
                    rowIds.get(index)), rows.get(index).rewardAnchor());
    }

    List<GrainSpec.Anchor> grainAnchors() {
        List<GrainSpec.Anchor> anchors = new ArrayList<>();
        for (int index = 0; index < rowIds.size() && index < rows.size(); index++)
            if (rows.get(index).getVisibility() == VISIBLE)
                rows.get(index).appendGrainAnchor(anchors);
        return Collections.unmodifiableList(anchors);
    }

    List<View> grainTextViews() {
        List<View> views = new ArrayList<>();
        if (doneStatus.getVisibility() == VISIBLE) views.add(doneStatus);
        for (int index = 0; index < rowIds.size() && index < rows.size(); index++)
            if (rows.get(index).getVisibility() == VISIBLE)
                views.addAll(rows.get(index).grainTextViews());
        if (moreStatus.getVisibility() == VISIBLE) views.add(moreStatus);
        return views;
    }

    @Override protected void onDetachedFromWindow() {
        if (autoScroller != null) autoScroller.stop();
        super.onDetachedFromWindow();
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

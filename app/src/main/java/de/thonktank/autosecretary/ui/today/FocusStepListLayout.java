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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import de.thonktank.autosecretary.presentation.today.FocusStepRowMode;
import de.thonktank.autosecretary.presentation.today.FocusStepRowUiModel;
import de.thonktank.autosecretary.presentation.today.TodayAction;
import de.thonktank.autosecretary.presentation.today.TodayActionSink;
import de.thonktank.autosecretary.presentation.today.TodayFeatureState;
import de.thonktank.autosecretary.ui.leaf.GrainSpec;
import de.thonktank.autosecretary.ui.leaf.GrainOcclusion;
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
    private final Map<String, FocusStepRowView> rowCache = new LinkedHashMap<>();
    private final List<String> rowIds = new ArrayList<>();
    private final List<String> reorderIds = new ArrayList<>();
    private final TodayActionSink actions;
    private final EdgeAutoScroller autoScroller;
    private DayPalette palette;
    private int maximumFollowing;
    private int visibleFollowing;
    private TodayFeatureState.Reorder reorder;
    private String occurrenceId;
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
        if (!model.steps.occurrenceId.equals(occurrenceId)) {
            for (FocusStepRowView row : rowCache.values()) removeView(row);
            rowCache.clear();
            rows.clear();
            occurrenceId = model.steps.occurrenceId;
        }
        rowIds.clear();
        reorderIds.clear();
        for (FocusStepRowUiModel row : model.steps.rows) {
            rowIds.add(row.id());
            if (row.mode != FocusStepRowMode.ASSISTANT) reorderIds.add(row.id());
        }
        doneStatus.setText(model.steps.doneCount == 0 ? "" : getResources().getQuantityString(
                R.plurals.focus_steps_done, model.steps.doneCount, model.steps.doneCount));
        doneStatus.setTextColor(model.palette.done);
        doneStatus.setVisibility(model.steps.doneCount == 0 ? GONE : VISIBLE);
        WoodGrainView.applyTextHalo(doneStatus, model.palette.leaf1);

        Map<String, FocusStepRowView> retained = new LinkedHashMap<>();
        rows.clear();
        for (int index = 0; index < model.steps.rows.size(); index++) {
            FocusStepRowUiModel projected = model.steps.rows.get(index);
            String key = rowKey(model.steps.occurrenceId, projected.id());
            FocusStepRowView row = rowCache.get(key);
            if (row == null) row = new FocusStepRowView(getContext());
            final FocusStepRowView boundRow = row;
            retained.put(key, row);
            rows.add(row);
            if (row.getParent() == null)
                addView(row, getChildCount() - 1, new MarginLayoutParams(-1, -2));
            row.bind(projected, model.palette,
                    model.repetitionInput, model.timers, actions);
            boolean reorderable = projected.mode != FocusStepRowMode.ASSISTANT;
            final int renderedIndex = index;
            row.bindInteractions(projected.id(), projected.step.title,
                    projected.mode == FocusStepRowMode.COMPACT
                            ? view -> emit(TodayAction.selectStep(projected.id())) : null,
                    reorderable ? view -> beginReorder(boundRow, projected.id()) : null,
                    reorderable && index > 0,
                    reorderable && index < reorderIds.size() - 1,
                    projected.mode == FocusStepRowMode.COMPACT,
                    (stepId, actionId) -> accessibilityMove(stepId, renderedIndex, actionId));
        }
        for (Map.Entry<String, FocusStepRowView> entry : rowCache.entrySet())
            if (!retained.containsKey(entry.getKey())) removeView(entry.getValue());
        rowCache.clear();
        rowCache.putAll(retained);
        for (FocusStepRowView row : rows) bringChildToFront(row);
        bringChildToFront(moreStatus);
        int availableFollowing = Math.max(0, model.steps.rows.size() - 1);
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
        if (reorderIds.size() < 2) return false;
        emit(TodayAction.beginReorder(stepId, reorderIds));
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
        List<String> preview = movedOrder(reorderIds, stepId, target);
        if (!preview.equals(reorderIds)) emit(TodayAction.previewReorder(stepId, preview));
    }

    private int targetIndex(float y) {
        int target = Math.max(0, reorderIds.size() - 1);
        for (int index = 0; index < reorderIds.size(); index++) {
            FocusStepRowView row = rows.get(index);
            if (y < row.getTop() + row.getHeight() / 2f) return index;
        }
        return target;
    }

    private String beforeStepId(String movingStepId) {
        int index = reorderIds.indexOf(movingStepId);
        return index >= 0 && index + 1 < reorderIds.size() ? reorderIds.get(index + 1) : null;
    }

    private boolean accessibilityMove(String stepId, int index, int actionId) {
        if (actionId == R.id.action_today_step_select) {
            emit(TodayAction.selectStep(stepId));
            announceForAccessibility(getContext().getString(R.string.a11y_today_step_selected));
            return true;
        }
        int target;
        if (actionId == R.id.action_today_step_up && index > 0) target = index - 1;
        else if (actionId == R.id.action_today_step_down && index < reorderIds.size() - 1)
            target = index + 1;
        else if (actionId == R.id.action_today_step_front && index > 0) target = 0;
        else if (actionId == R.id.action_today_step_back && index < reorderIds.size() - 1)
            target = reorderIds.size() - 1;
        else return false;
        List<String> preview = movedOrder(reorderIds, stepId, target);
        emit(TodayAction.beginReorder(stepId, reorderIds));
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

    private static String rowKey(String occurrenceId, String stepId) {
        return occurrenceId + '\u0000' + stepId;
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

    List<GrainOcclusion> grainOcclusions() {
        List<GrainOcclusion> views = new ArrayList<>();
        if (doneStatus.getVisibility() == VISIBLE)
            views.add(GrainOcclusion.text(doneStatus));
        for (int index = 0; index < rowIds.size() && index < rows.size(); index++)
            if (rows.get(index).getVisibility() == VISIBLE)
                views.addAll(rows.get(index).grainOcclusions());
        if (moreStatus.getVisibility() == VISIBLE)
            views.add(GrainOcclusion.text(moreStatus));
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

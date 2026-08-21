package de.thonktank.autosecretary;

import de.thonktank.autosecretary.presentation.today.FocusCardUiModel;

import android.content.Context;
import android.content.ClipData;
import android.os.Bundle;
import android.view.DragEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import de.thonktank.autosecretary.data.preferences.FocusStepLimit;
import de.thonktank.autosecretary.presentation.FocusStepUiModel;

/**
 * Vertical focus-step layout with an explicit height-budget contract.
 *
 * <p>The active row is mandatory. Following rows are admitted in source order up to both the
 * preference limit and the height supplied through {@code AT_MOST}. A summary row consumes part
 * of that same budget whenever rows remain hidden. Children are measured once per pass; content
 * binding and reward registration never depend on the resulting geometry.</p>
 */
final class FocusStepListLayout extends ViewGroup {
    private final UiStyle style;
    private final TextView doneStatus;
    private final TextView moreStatus;
    private final List<FocusStepRowView> rows = new ArrayList<>();
    private final List<FocusStepUiModel> openSteps = new ArrayList<>();
    private DayPalette palette;
    private int maximumFollowing;
    private int visibleFollowing;
    private boolean reordering;
    private boolean dropped;
    private String draggingStepId;
    private FocusCardUiModel boundModel;
    private DashboardEventSink boundEvents;
    private ReorderModeListener reorderModeListener = active -> { };

    interface ReorderModeListener { void onReorderModeChanged(boolean active); }

    FocusStepListLayout(Context context) {
        super(context);
        style = new UiStyle(context);
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

    void bind(FocusCardUiModel model, DashboardEventSink events) {
        boundModel = model;
        boundEvents = events;
        palette = model.palette;
        openSteps.clear();
        int doneCount = 0;
        for (FocusStepUiModel step : model.task.steps) {
            if (step.done) doneCount++;
            else openSteps.add(step);
        }
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
            row.bind(step, index == 0, model.palette, model.repetitionInput, events);
            row.setOnStepLongClickListener(view -> beginReorder(row, step.id));
            configureAccessibility(row, step, index);
        }
        int availableFollowing = Math.max(0, openSteps.size() - 1);
        maximumFollowing = model.stepLimit.automatic() ? availableFollowing
                : Math.min(model.stepLimit.maximumFollowingSteps, availableFollowing);
        applyVisibleFollowing(maximumFollowing);
        setVisibility(model.task.steps.isEmpty() ? GONE : VISIBLE);
        requestLayout();
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
        int[] followingHeights = new int[Math.max(0, openSteps.size() - 1)];
        for (int index = 0; index < openSteps.size(); index++) {
            FocusStepRowView row = rows.get(index);
            updateRowMargin(row, index);
            row.measure(exactChildWidth, unlimitedHeight);
            if (index == 0) required += extent(row);
            else followingHeights[index - 1] = extent(row);
        }

        // Measure a real summary even when AUTO could show every row. A smaller viewport may
        // still need that summary, so an empty/GONE label must not contribute a false zero.
        setMoreText(followingHeights.length);
        moreStatus.measure(exactChildWidth, unlimitedHeight);
        int moreExtent = extent(moreStatus);
        int available = MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED
                ? Integer.MAX_VALUE : MeasureSpec.getSize(heightMeasureSpec);
        int chosen = reordering ? followingHeights.length
                : FocusStepLayoutPolicy.visibleFollowing(available, required,
                        followingHeights, moreExtent, maximumFollowing);
        applyVisibleFollowing(chosen);
        if (moreStatus.getVisibility() == VISIBLE) moreStatus.measure(exactChildWidth,
                unlimitedHeight);

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
        visibleFollowing = Math.max(0,
                Math.min(following, Math.max(0, openSteps.size() - 1)));
        for (int index = 0; index < rows.size(); index++)
            rows.get(index).setVisibility(index < openSteps.size()
                    && (index == 0 || index <= visibleFollowing) ? VISIBLE : GONE);
        setMoreText(Math.max(0, openSteps.size() - 1 - visibleFollowing));
    }

    private void setMoreText(int hidden) {
        if (reordering) hidden = 0;
        moreStatus.setText(hidden == 0 ? "" : getResources().getQuantityString(
                R.plurals.focus_steps_more, hidden, hidden));
        if (palette != null) {
            moreStatus.setTextColor(palette.muted);
            WoodGrainView.applyTextHalo(moreStatus, palette.leaf1);
        }
        moreStatus.setVisibility(hidden == 0 ? GONE : VISIBLE);
    }

    List<FocusStepUiModel> openSteps() { return openSteps; }

    boolean reordering() { return reordering; }

    private boolean beginReorder(FocusStepRowView row, String stepId) {
        if (openSteps.size() < 2) return false;
        enterReorder(stepId);
        ClipData data = ClipData.newPlainText("today-step", stepId);
        boolean started = row.startDragAndDrop(data, new View.DragShadowBuilder(row), row, 0);
        if (!started) finishReorder();
        return started;
    }

    private void enterReorder(String stepId) {
        reordering = true;
        dropped = false;
        draggingStepId = stepId;
        applyVisibleFollowing(Math.max(0, openSteps.size() - 1));
        reorderModeListener.onReorderModeChanged(true);
        requestLayout();
    }

    private boolean onStepDrag(View view, DragEvent event) {
        if (!reordering) return false;
        if (event.getAction() == DragEvent.ACTION_DRAG_LOCATION) {
            moveDraggedRow(targetIndex(event.getY()));
            autoScroll(event.getY());
            return true;
        }
        if (event.getAction() == DragEvent.ACTION_DROP) {
            persistDrop();
            return true;
        }
        if (event.getAction() == DragEvent.ACTION_DRAG_ENDED) {
            finishReorder();
            return true;
        }
        return true;
    }

    private void persistDrop() {
        if (dropped) return;
        dropped = true;
        int index = indexOfStep(draggingStepId);
        if (index < 0 || boundEvents == null) return;
        String before = index + 1 < openSteps.size()
                ? openSteps.get(index + 1).id : null;
        boundEvents.emit(DashboardEvent.moveTodayStep(draggingStepId, before));
    }

    private int targetIndex(float y) {
        int target = Math.max(0, openSteps.size() - 1);
        for (int index = 0; index < openSteps.size(); index++) {
            FocusStepRowView row = rows.get(index);
            if (y < row.getTop() + row.getHeight() / 2f) return index;
        }
        return target;
    }

    private void moveDraggedRow(int target) {
        int source = indexOfStep(draggingStepId);
        if (source < 0 || target < 0 || target >= openSteps.size() || source == target) return;
        FocusStepUiModel model = openSteps.remove(source);
        FocusStepRowView row = rows.remove(source);
        openSteps.add(target, model);
        rows.add(target, row);
        MarginLayoutParams params = (MarginLayoutParams) row.getLayoutParams();
        removeView(row);
        addView(row, 1 + target, params);
        requestLayout();
    }

    private int indexOfStep(String stepId) {
        for (int index = 0; index < openSteps.size(); index++)
            if (openSteps.get(index).id.equals(stepId)) return index;
        return -1;
    }

    private void autoScroll(float y) {
        ScrollView scroll = ancestorScrollView();
        if (scroll == null) return;
        int edge = style.dp(56);
        if (y < edge) scroll.scrollBy(0, -style.dp(18));
        else if (y > getHeight() - edge) scroll.scrollBy(0, style.dp(18));
    }

    private ScrollView ancestorScrollView() {
        android.view.ViewParent parent = getParent();
        while (parent instanceof View) {
            if (parent instanceof ScrollView) return (ScrollView) parent;
            parent = parent.getParent();
        }
        return null;
    }

    private void finishReorder() {
        reordering = false;
        draggingStepId = null;
        reorderModeListener.onReorderModeChanged(false);
        // The view-model refreshes the canonical order after a successful write. Restoring the
        // bound model here also prevents a failed write from leaving an optimistic order behind.
        if (boundModel != null && boundEvents != null) bind(boundModel, boundEvents);
        else requestLayout();
    }

    private void configureAccessibility(FocusStepRowView row, FocusStepUiModel step,
                                        int index) {
        View body = row.stepBody();
        body.setFocusable(true);
        body.setContentDescription(getContext().getString(
                R.string.a11y_today_step_row, step.title));
        body.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override public void onInitializeAccessibilityNodeInfo(
                    View host, AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                int current = indexOfStep(step.id);
                if (current > 0) {
                    info.addAction(action(R.id.action_today_step_up,
                            R.string.a11y_step_up));
                    info.addAction(action(R.id.action_today_step_front,
                            R.string.a11y_today_step_front));
                }
                if (current >= 0 && current < openSteps.size() - 1) {
                    info.addAction(action(R.id.action_today_step_down,
                            R.string.a11y_step_down));
                    info.addAction(action(R.id.action_today_step_back,
                            R.string.a11y_today_step_back));
                }
            }

            @Override public boolean performAccessibilityAction(View host, int action,
                                                                Bundle arguments) {
                int current = indexOfStep(step.id);
                if (current < 0) return false;
                if (action == R.id.action_today_step_up && current > 0)
                    return emitMove(step.id, openSteps.get(current - 1).id);
                if (action == R.id.action_today_step_down
                        && current < openSteps.size() - 1)
                    return emitMove(step.id, current + 2 < openSteps.size()
                            ? openSteps.get(current + 2).id : null);
                if (action == R.id.action_today_step_front && current > 0)
                    return emitMove(step.id, openSteps.get(0).id);
                if (action == R.id.action_today_step_back
                        && current < openSteps.size() - 1)
                    return emitMove(step.id, null);
                return super.performAccessibilityAction(host, action, arguments);
            }
        });
    }

    private AccessibilityNodeInfo.AccessibilityAction action(int id, int label) {
        return new AccessibilityNodeInfo.AccessibilityAction(id,
                getContext().getString(label));
    }

    private boolean emitMove(String stepId, String beforeStepId) {
        if (boundEvents == null) return false;
        boundEvents.emit(DashboardEvent.moveTodayStep(stepId, beforeStepId));
        announceForAccessibility(getContext().getString(R.string.a11y_today_step_moved));
        return true;
    }

    List<FocusStepRowView> visibleRows() {
        List<FocusStepRowView> visible = new ArrayList<>();
        for (int index = 0; index < openSteps.size() && index < rows.size(); index++)
            if (rows.get(index).getVisibility() == VISIBLE) visible.add(rows.get(index));
        return visible;
    }

    void registerRewardAnchors(RewardAnchorRegistry registry) {
        for (int index = 0; index < openSteps.size() && index < rows.size(); index++)
            registry.register(new RewardAnchorKey(RewardAnchorKey.Kind.STEP,
                    openSteps.get(index).id), rows.get(index).rewardAnchor());
    }

    List<View> grainTextViews() {
        List<View> views = new ArrayList<>();
        if (doneStatus.getVisibility() == VISIBLE) views.add(doneStatus);
        for (FocusStepRowView row : visibleRows()) views.addAll(row.grainTextViews());
        if (moreStatus.getVisibility() == VISIBLE) views.add(moreStatus);
        return views;
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

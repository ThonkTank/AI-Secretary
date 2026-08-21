package de.thonktank.autosecretary;

import de.thonktank.autosecretary.presentation.today.FocusCardUiModel;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
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
    }

    void bind(FocusCardUiModel model, DashboardEventSink events) {
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
        int chosen = FocusStepLayoutPolicy.visibleFollowing(available, required,
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
        moreStatus.setText(hidden == 0 ? "" : getResources().getQuantityString(
                R.plurals.focus_steps_more, hidden, hidden));
        if (palette != null) {
            moreStatus.setTextColor(palette.muted);
            WoodGrainView.applyTextHalo(moreStatus, palette.leaf1);
        }
        moreStatus.setVisibility(hidden == 0 ? GONE : VISIBLE);
    }

    List<FocusStepUiModel> openSteps() { return openSteps; }

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

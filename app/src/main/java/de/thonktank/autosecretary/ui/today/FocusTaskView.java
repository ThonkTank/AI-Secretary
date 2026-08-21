package de.thonktank.autosecretary.ui.today;

import de.thonktank.autosecretary.*;

import de.thonktank.autosecretary.presentation.today.FocusTaskUiModel;
import de.thonktank.autosecretary.presentation.today.TodayActionSink;
import de.thonktank.autosecretary.presentation.today.TodayFeatureState;
import de.thonktank.autosecretary.presentation.today.TodayUiModel;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;

import de.thonktank.autosecretary.data.preferences.FocusStepLimit;
import de.thonktank.autosecretary.ui.leaf.LeafSurface;

/** Composition shell for focus content, paper decoration and transition effects. */
public final class FocusTaskView extends FrameLayout {
    private final UiStyle style;
    private final FocusCardView card;
    private final LeafSurface surface;
    private final FocusCardDecoration decoration;
    private final FocusCardAnimationController animations;
    private final LayoutParams cardParams;
    private String boundTaskId;
    private boolean deferPending;
    private TodayActionSink actions = action -> { };

    public FocusTaskView(Context context) {
        this(context, new RewardAnchorRegistry());
    }

    public FocusTaskView(Context context, RewardAnchorRegistry rewardAnchors) {
        this(context, rewardAnchors, null);
    }

    public FocusTaskView(Context context, RewardAnchorRegistry rewardAnchors,
                         EdgeAutoScroller.ScrollHost scrollHost) {
        super(context);
        style = new UiStyle(context);
        setClipChildren(false);
        card = new FocusCardView(context, action -> actions.emit(action), scrollHost);
        decoration = new FocusCardDecoration(context, this, rewardAnchors, card);
        surface = decoration.surface();
        cardParams = new LayoutParams(-1, -2);
        cardParams.topMargin = style.dimen(R.dimen.focus_card_top);
        surface.setLayoutParams(cardParams);
        animations = new FocusCardAnimationController(context, this, surface);
    }

    public void bind(FocusTaskUiModel task, boolean stacked,
                     DayPalette palette, TodayActionSink events) {
        bind(task, stacked, palette, FocusStepLimit.AUTO,
                RepetitionInputState.idle(), events);
    }

    public void bind(FocusTaskUiModel task, boolean stacked,
                     DayPalette palette, FocusStepLimit stepLimit,
                     RepetitionInputState inputState, TodayActionSink events) {
        bind(task, stacked, palette, stepLimit, inputState,
                idleReorder(task), events);
    }

    public void bind(FocusTaskUiModel task, boolean stacked,
                     DayPalette palette, FocusStepLimit stepLimit,
                     RepetitionInputState inputState, TodayFeatureState.Reorder reorder,
                     TodayActionSink events) {
        actions = events == null ? action -> { } : events;
        boolean focusChanged = boundTaskId != null && !boundTaskId.equals(task.taskId());
        boundTaskId = task.taskId();
        boolean compact = task.ongoing && task.steps.isEmpty();
        setMinimumHeight(compact ? style.dimen(R.dimen.focus_card_compact_min_height)
                : task.steps.isEmpty() ? style.dimen(R.dimen.focus_card_empty_min_height) : 0);
        cardParams.topMargin = compact ? 0 : style.dimen(R.dimen.focus_card_top);
        surface.setLayoutParams(cardParams);
        surface.setTranslationY(0f);
        surface.setAlpha(1f);
        FocusCardUiModel model = new FocusCardUiModel(task, palette,
                stepLimit, inputState, reorder);
        card.bind(model, actions, () -> deferPending = true);
        decoration.bind(task, stacked, compact, palette, card);
        if (focusChanged) {
            boolean deferred = deferPending;
            deferPending = false;
            animations.focusChanged(palette, deferred);
        }
    }

    private static TodayFeatureState.Reorder idleReorder(FocusTaskUiModel task) {
        TodayUiModel today = new TodayUiModel(
                new de.thonktank.autosecretary.domain.model.XpProgress(0), task,
                java.util.Collections.emptyList(), java.util.Collections.emptyList());
        return TodayFeatureState.idle(today).reorder;
    }

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int safeHeight = safeCardHeight(heightMeasureSpec);
        card.setMaximumContentHeight(Math.max(0, safeHeight - cardParams.topMargin));
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    private int safeCardHeight(int heightMeasureSpec) {
        int mode = MeasureSpec.getMode(heightMeasureSpec);
        int specified = MeasureSpec.getSize(heightMeasureSpec);
        if (mode != MeasureSpec.UNSPECIFIED && specified > 0) return specified;
        android.view.ViewParent ancestor = getParent();
        while (ancestor instanceof View) {
            View view = (View) ancestor;
            if (view instanceof android.widget.ScrollView && view.getMeasuredHeight() > 0)
                return Math.max(0, view.getMeasuredHeight()
                        - style.dimen(R.dimen.content_top));
            ancestor = view.getParent();
        }
        int screenHeightDp = getResources().getConfiguration().screenHeightDp;
        if (screenHeightDp <= 0) return Integer.MAX_VALUE;
        return Math.max(0, style.dp(screenHeightDp)
                - style.dimen(R.dimen.header_height)
                - style.dimen(R.dimen.footer_height)
                - style.dimen(R.dimen.content_top));
    }

    @Override protected void onDetachedFromWindow() {
        animations.cancel();
        super.onDetachedFromWindow();
    }

}

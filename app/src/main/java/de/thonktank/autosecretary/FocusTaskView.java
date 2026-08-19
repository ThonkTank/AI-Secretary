package de.thonktank.autosecretary;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;

import de.thonktank.autosecretary.data.preferences.FocusStepLimit;

/** Composition shell for focus content, paper decoration and transition effects. */
public final class FocusTaskView extends FrameLayout {
    private final UiStyle style;
    private final FocusCardView card;
    private final FocusCardDecoration decoration;
    private final FocusCardAnimationController animations;
    private final LayoutParams cardParams;
    private String boundTaskId;
    private boolean deferPending;

    public FocusTaskView(Context context) {
        this(context, new RewardAnchorRegistry());
    }

    public FocusTaskView(Context context, RewardAnchorRegistry rewardAnchors) {
        super(context);
        style = new UiStyle(context);
        setClipChildren(false);
        decoration = new FocusCardDecoration(context, this, rewardAnchors);
        card = new FocusCardView(context);
        cardParams = new LayoutParams(-1, -2);
        cardParams.topMargin = style.dimen(R.dimen.focus_card_top);
        addView(card, cardParams);
        animations = new FocusCardAnimationController(context, this, card);
    }

    public void bind(TaskSnapshot task, boolean stacked, boolean allowDefer,
                     DayPalette palette, DashboardEventSink events) {
        bind(task, stacked, allowDefer, palette, FocusStepLimit.AUTO,
                RepetitionInputState.idle(), events);
    }

    public void bind(TaskSnapshot task, boolean stacked, boolean allowDefer,
                     DayPalette palette, FocusStepLimit stepLimit,
                     RepetitionInputState inputState, DashboardEventSink events) {
        boolean focusChanged = boundTaskId != null && !boundTaskId.equals(task.taskId);
        boundTaskId = task.taskId;
        boolean compact = task.ongoing && task.steps.isEmpty();
        setMinimumHeight(compact ? style.dimen(R.dimen.focus_card_compact_min_height)
                : task.steps.isEmpty() ? style.dimen(R.dimen.focus_card_empty_min_height) : 0);
        cardParams.topMargin = compact ? 0 : style.dimen(R.dimen.focus_card_top);
        card.setLayoutParams(cardParams);
        card.setTranslationY(0f);
        card.setAlpha(1f);
        FocusCardUiModel model = new FocusCardUiModel(task, allowDefer, palette,
                stepLimit, inputState);
        card.bind(model, events, () -> deferPending = true);
        decoration.bind(task, stacked, compact, palette, card);
        if (focusChanged) {
            boolean deferred = deferPending;
            deferPending = false;
            animations.focusChanged(palette, deferred);
        }
    }

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int safeHeight = safeCardHeight(heightMeasureSpec);
        card.setMaximumContentHeight(Math.max(0, safeHeight - cardParams.topMargin));
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        decoration.layoutTo(card);
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

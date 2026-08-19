package de.thonktank.autosecretary;

import de.thonktank.autosecretary.presentation.TaskStepUiModel;
import de.thonktank.autosecretary.data.preferences.FocusStepLimit;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;

import java.util.ArrayList;
import java.util.List;

public final class FocusTaskView extends FrameLayout {
    public interface TaskActions {
        void onComplete(TaskSnapshot task);
        void onCompleteRemaining(TaskSnapshot task);
        void onHarvest(TaskSnapshot task);
        void onDefer(TaskSnapshot task);
    }

    private final UiStyle style;
    private final RewardAnchorRegistry rewardAnchors;
    private final View back;
    private final View middle;
    private final LinearLayout card;
    private final View cardSurface;
    private final WoodGrainView grain;
    private final LayoutParams cardParams;
    private final LinearLayout titleBlock;
    private final TextView marker;
    private final TextView title;
    private final TextView softTime;
    private final XpVesselView ring;
    private final DewDotView taskDew;
    private final LinearLayout steps;
    private final TextView doneStatus;
    private final TextView moreStatus;
    private final List<FocusStepRowView> stepRows = new ArrayList<>();
    private final List<TaskStepUiModel> openSteps = new ArrayList<>();
    private final LinearLayout actions;
    private final LinearLayout.LayoutParams actionParams;
    private final TextView primary;
    private final TextLinkView later;
    private final View glint;
    private final View afterglow;
    private String boundTaskId;
    private boolean deferPending;
    private FocusStepLimit boundStepLimit = FocusStepLimit.AUTO;
    private DayPalette boundPalette;
    private int requestedFollowingSteps;
    private int visibleFollowingSteps;
    private boolean stepVisibilityNeedsMeasure;
    private int lastMeasureWidth = -1;
    private int lastSafeHeight = -1;
    private float lastFontScale = -1f;

    public FocusTaskView(Context context) {
        this(context, new RewardAnchorRegistry());
    }

    public FocusTaskView(Context context, RewardAnchorRegistry rewardAnchors) {
        super(context);
        this.rewardAnchors = rewardAnchors;
        style = new UiStyle(context);
        setClipChildren(false);
        back = new View(context);
        LayoutParams backParams = new LayoutParams(-1, style.dp(82));
        backParams.setMargins(style.dp(18), style.dp(34), style.dp(4), 0);
        addView(back, backParams);
        middle = new View(context);
        LayoutParams middleParams = new LayoutParams(-1, style.dp(88));
        middleParams.setMargins(style.dp(8), style.dp(18), style.dp(12), 0);
        addView(middle, middleParams);

        cardSurface = new View(context);
        LayoutParams surfaceParams = new LayoutParams(-1, style.dp(320));
        surfaceParams.topMargin = style.dp(22);
        addView(cardSurface, surfaceParams);
        grain = new WoodGrainView(context);
        LayoutParams grainParams = new LayoutParams(-1, style.dp(320));
        grainParams.topMargin = style.dp(22);
        addView(grain, grainParams);

        card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(style.dp(24), style.dp(24), style.dp(28), style.dp(24));
        card.setRotation(-.7f);
        cardParams = new LayoutParams(-1, -2);
        cardParams.topMargin = style.dp(22);
        addView(card, cardParams);

        FrameLayout titleRow = new FrameLayout(context);
        titleBlock = new LinearLayout(context);
        titleBlock.setOrientation(LinearLayout.VERTICAL);
        marker = style.serif("", 20, 0, true, 300);
        titleBlock.addView(marker);
        title = style.serif("", 37, 0, false, 200);
        title.setLineSpacing(0, 1.04f);
        title.setLetterSpacing(-.02f);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-1, -2);
        titleParams.topMargin = style.dp(4);
        titleBlock.addView(title, titleParams);
        softTime = style.sans("", 17, 0, false);
        LinearLayout.LayoutParams softParams = new LinearLayout.LayoutParams(-1, -2);
        softParams.setMargins(0, style.dp(7), 0, 0);
        titleBlock.addView(softTime, softParams);
        titleRow.addView(titleBlock, new LayoutParams(-1, -2));
        ring = new XpVesselView(context);
        LayoutParams ringParams = new LayoutParams(style.dp(52), style.dp(52), Gravity.TOP | Gravity.END);
        titleRow.addView(ring, ringParams);
        taskDew = new DewDotView(context);
        taskDew.setVisibility(GONE);
        titleRow.addView(taskDew, new LayoutParams(style.dp(48), style.dp(48), Gravity.TOP | Gravity.END));
        card.addView(titleRow, new LinearLayout.LayoutParams(-1, -2));

        steps = new LinearLayout(context);
        steps.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams stepsParams = new LinearLayout.LayoutParams(-1, -2);
        stepsParams.setMargins(0, style.dp(24), 0, 0);
        card.addView(steps, stepsParams);
        doneStatus = style.serif("", 15, 0, true, 400);
        steps.addView(doneStatus, new LinearLayout.LayoutParams(-1, -2));
        moreStatus = style.serif("", 15, 0, true, 400);
        LinearLayout.LayoutParams moreStepParams = new LinearLayout.LayoutParams(-1, -2);
        moreStepParams.setMargins(style.dp(52), style.dp(10), 0, 0);
        steps.addView(moreStatus, moreStepParams);
        actions = new LinearLayout(context);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        actionParams = new LinearLayout.LayoutParams(-1, -2);
        actionParams.setMargins(0, style.dp(22), 0, 0);
        card.addView(actions, actionParams);
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
        laterParams.setMargins(style.dp(18), 0, 0, 0);
        actions.addView(later, laterParams);

        glint = new View(context);
        glint.setVisibility(INVISIBLE);
        glint.setRotation(-14f);
        LayoutParams glintParams = new LayoutParams(style.dp(64), 0);
        glintParams.topMargin = style.dp(22);
        addView(glint, glintParams);
        afterglow = new View(context);
        afterglow.setVisibility(INVISIBLE);
        LayoutParams afterglowParams = new LayoutParams(-1, 0);
        afterglowParams.topMargin = style.dp(22);
        addView(afterglow, afterglowParams);
    }

    public void bind(TaskSnapshot task, boolean stacked, boolean allowDefer,
                     DayPalette palette, TaskActions taskActions,
                     FocusStepRowView.Actions stepActions) {
        bind(task, stacked, allowDefer, palette, FocusStepLimit.AUTO,
                RepetitionInputState.idle(), taskActions, stepActions);
    }

    public void bind(TaskSnapshot task, boolean stacked, boolean allowDefer,
                     DayPalette palette, FocusStepLimit stepLimit,
                     RepetitionInputState inputState,
                     TaskActions taskActions, FocusStepRowView.Actions stepActions) {
        boolean focusChanged = boundTaskId != null && !boundTaskId.equals(task.taskId);
        boundTaskId = task.taskId;
        boolean compactOngoing = task.ongoing && task.steps.isEmpty();
        setMinimumHeight(style.dp(compactOngoing ? 205 : task.steps.isEmpty() ? 275 : 0));
        cardParams.topMargin = style.dp(compactOngoing ? 0 : 22);
        card.setLayoutParams(cardParams);
        card.setPadding(style.dp(24), style.dp(24), style.dp(28),
                style.dp(compactOngoing ? 30 : 24));
        actionParams.topMargin = style.dp(compactOngoing ? 30 : 22);
        actions.setLayoutParams(actionParams);
        back.setVisibility(stacked && !compactOngoing ? VISIBLE : GONE);
        middle.setVisibility(stacked && !compactOngoing ? VISIBLE : GONE);
        back.setBackground(style.leaf(palette.leaf3, style.edge(palette, 3), 8, 56, 8, 56));
        back.setRotation(2.2f);
        style.shadow(back, palette, 5, .75f);
        middle.setBackground(style.leaf(palette.leaf2, style.edge(palette, 2), 56, 8, 56, 8));
        middle.setRotation(-1.5f);
        style.shadow(middle, palette, 5, .75f);
        card.setBackgroundColor(Color.TRANSPARENT);
        cardSurface.setBackground(style.leaf(palette.leaf1, style.edge(palette, 1), 10, 64, 10, 64));
        cardSurface.setRotation(card.getRotation());
        style.shadow(cardSurface, palette, 12, 1f);
        // Elevated siblings ignore insertion order. Keep the paper surface behind
        // the grain and the transparent content layer.
        grain.setTranslationZ(style.dp(13));
        card.setTranslationZ(style.dp(14));
        card.setTranslationY(0f);
        card.setAlpha(1f);
        marker.setVisibility(GONE);
        title.setText(task.title);
        title.setTextSize(30);
        title.setTextColor(palette.ink);
        WoodGrainView.applyTextHalo(title, palette.leaf1);
        softTime.setVisibility(GONE);
        boolean vessel = !task.steps.isEmpty();
        ring.setVisibility(vessel ? VISIBLE : GONE);
        taskDew.setVisibility(vessel ? GONE : VISIBLE);
        int doneCount = task.steps.size() - task.remainingSteps;
        if (vessel) {
            rewardAnchors.register(new RewardAnchorKey(RewardAnchorKey.Kind.VESSEL,
                    task.occurrenceId), ring);
            ring.bind(task.collectedXp, doneCount, task.steps.size(), task.harvestReady,
                    task.comboStage, palette);
            ring.setOnClickListener(task.harvestReady
                    ? view -> taskActions.onHarvest(task) : null);
        } else {
            rewardAnchors.register(new RewardAnchorKey(task.terminalCondition
                    ? RewardAnchorKey.Kind.TASK : RewardAnchorKey.Kind.OCCURRENCE,
                    task.terminalCondition ? task.taskId : task.occurrenceId), taskDew);
            taskDew.bind(false, false, palette, task.claimableXp);
            taskDew.setContentDescription(getContext().getString(
                    R.string.content_complete_task, task.title, task.claimableXp));
            taskDew.setOnClickListener(view -> taskActions.onComplete(task));
        }
        titleBlock.setPadding(0, 0, 0, 0);
        title.setPadding(0, 0, style.dp(66), 0);
        boundStepLimit = stepLimit == null ? FocusStepLimit.AUTO : stepLimit;
        boundPalette = palette;
        bindSteps(task, palette, inputState, stepActions);
        primary.setText(R.string.action_complete_rest);
        primary.setTextColor(palette.accentText);
        primary.setBackground(style.pill(palette.accent, 26));
        style.shadow(primary, palette, 5, .7f);
        primary.setOnClickListener(view -> taskActions.onCompleteRemaining(task));
        rewardAnchors.register(new RewardAnchorKey(RewardAnchorKey.Kind.REST,
                task.occurrenceId), primary);
        primary.setVisibility(vessel && task.remainingSteps > 0 ? VISIBLE : GONE);
        later.setVisibility(allowDefer ? VISIBLE : GONE);
        later.bind(palette.hint, palette.dot);
        later.setOnClickListener(view -> {
            deferPending = true;
            taskActions.onDefer(task);
        });
        if (focusChanged) animateFocusChange(palette);
        post(() -> syncLayersAndGrain(task, palette));
    }

    private void bindSteps(TaskSnapshot task, DayPalette palette,
                           RepetitionInputState inputState,
                           FocusStepRowView.Actions callbacks) {
        steps.setVisibility(task.steps.isEmpty() ? GONE : VISIBLE);
        openSteps.clear();
        int doneCount = 0;
        for (TaskStepUiModel step : task.steps) {
            if (step.done) doneCount++;
            else openSteps.add(step);
        }
        doneStatus.setText(doneCount == 0 ? "" : getResources().getQuantityString(
                R.plurals.focus_steps_done, doneCount, doneCount));
        doneStatus.setTextColor(palette.done);
        doneStatus.setVisibility(doneCount == 0 ? GONE : VISIBLE);
        WoodGrainView.applyTextHalo(doneStatus, palette.leaf1);
        while (stepRows.size() < openSteps.size()) {
            FocusStepRowView row = new FocusStepRowView(getContext());
            steps.addView(row, steps.getChildCount() - 1,
                    new LinearLayout.LayoutParams(-1, -2));
            stepRows.add(row);
        }
        for (int i = 0; i < stepRows.size(); i++) {
            FocusStepRowView row = stepRows.get(i);
            if (i >= openSteps.size()) {
                row.setVisibility(GONE);
                continue;
            }
            row.setVisibility(VISIBLE);
            TaskStepUiModel step = openSteps.get(i);
            boolean active = i == 0;
            rewardAnchors.register(new RewardAnchorKey(RewardAnchorKey.Kind.STEP, step.id),
                    row.rewardAnchor());
            row.bind(step, active, palette, inputState, new FocusStepRowView.Actions() {
                @Override public void onToggleStep(String stepId) {
                    callbacks.onToggleStep(stepId);
                }

                @Override public void onConfirmRepetitions(String stepId, int repetitions) {
                    callbacks.onConfirmRepetitions(stepId, repetitions);
                }

                @Override public void onEditRepetition(String stepId, int index,
                                                       int repetitions) {
                    callbacks.onEditRepetition(stepId, index, repetitions);
                }

                @Override public void onRepetitionInputStateChanged(
                        RepetitionInputState state) {
                    callbacks.onRepetitionInputStateChanged(state);
                    bindSteps(task, palette, state, callbacks);
                    post(() -> syncLayersAndGrain(task, palette));
                }
            });
        }
        int available = Math.max(0, openSteps.size() - 1);
        requestedFollowingSteps = boundStepLimit.automatic()
                ? available : Math.min(boundStepLimit.maximumFollowingSteps, available);
        stepVisibilityNeedsMeasure = true;
        applyStepVisibility(requestedFollowingSteps, palette);
    }

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int safeHeight = safeCardHeight(heightMeasureSpec);
        float fontScale = getResources().getConfiguration().fontScale;
        boolean constraintsChanged = width != lastMeasureWidth || safeHeight != lastSafeHeight
                || Math.abs(fontScale - lastFontScale) > .001f;
        if (boundPalette != null && visibleFollowingSteps != requestedFollowingSteps
                && (stepVisibilityNeedsMeasure || constraintsChanged))
            applyStepVisibility(requestedFollowingSteps, boundPalette);
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if (boundPalette == null) return;
        while (visibleFollowingSteps > 0
                && naturalCardExtent() > safeHeight) {
            applyStepVisibility(visibleFollowingSteps - 1, boundPalette);
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }
        naturalCardExtent();
        stepVisibilityNeedsMeasure = false;
        lastMeasureWidth = width;
        lastSafeHeight = safeHeight;
        lastFontScale = fontScale;
    }

    private int naturalCardExtent() {
        int width = card.getMeasuredWidth();
        if (width > 0) card.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        return cardParams.topMargin + card.getMeasuredHeight();
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

    private void applyStepVisibility(int following, DayPalette palette) {
        visibleFollowingSteps = Math.max(0,
                Math.min(following, Math.max(0, openSteps.size() - 1)));
        for (int index = 0; index < stepRows.size(); index++) {
            boolean shown = index < openSteps.size()
                    && (index == 0 || index <= visibleFollowingSteps);
            stepRows.get(index).setVisibility(shown ? VISIBLE : GONE);
            if (shown) {
                LinearLayout.LayoutParams params = (LinearLayout.LayoutParams)
                        stepRows.get(index).getLayoutParams();
                params.topMargin = index == 0
                        ? (doneStatus.getVisibility() == VISIBLE ? style.dp(10) : 0)
                        : style.dp(index == 1 ? 12 : 2);
                stepRows.get(index).setLayoutParams(params);
            }
        }
        int hidden = Math.max(0, openSteps.size() - 1 - visibleFollowingSteps);
        moreStatus.setText(hidden == 0 ? "" : getResources().getQuantityString(
                R.plurals.focus_steps_more, hidden, hidden));
        moreStatus.setTextColor(palette.muted);
        moreStatus.setVisibility(hidden == 0 ? GONE : VISIBLE);
        WoodGrainView.applyTextHalo(moreStatus, palette.leaf1);
    }

    private void syncLayersAndGrain(TaskSnapshot task, DayPalette palette) {
        LayoutParams surface = (LayoutParams) cardSurface.getLayoutParams();
        surface.topMargin = cardParams.topMargin;
        surface.height = card.getHeight();
        cardSurface.setLayoutParams(surface);
        LayoutParams gp = (LayoutParams) grain.getLayoutParams();
        gp.topMargin = cardParams.topMargin;
        gp.height = card.getHeight();
        grain.setLayoutParams(gp);
        grain.post(() -> bindGrainRects(task, palette));
    }

    private void bindGrainRects(TaskSnapshot task, DayPalette palette) {
        boolean vessel = !task.steps.isEmpty();
        List<WoodGrainView.Anchor> anchors = new ArrayList<>();
        View taskAnchor = vessel ? ring : taskDew;
        anchors.add(new WoodGrainView.Anchor(grainBounds(taskAnchor), task.comboStage));
        for (int i = 0; i < openSteps.size() && i < stepRows.size(); i++)
            if (stepRows.get(i).getVisibility() == VISIBLE)
                anchors.add(new WoodGrainView.Anchor(grainBounds(stepRows.get(i).rewardAnchor()),
                        openSteps.get(i).comboStage));
        List<View> faded = new ArrayList<>();
        faded.add(title);
        if (doneStatus.getVisibility() == VISIBLE) faded.add(doneStatus);
        for (int i = 0; i < openSteps.size() && i < stepRows.size(); i++)
            if (stepRows.get(i).getVisibility() == VISIBLE)
                faded.addAll(stepRows.get(i).grainTextViews());
        if (moreStatus.getVisibility() == VISIBLE) faded.add(moreStatus);
        if (primary.getVisibility() == VISIBLE) faded.add(primary);
        if (later.getVisibility() == VISIBLE) faded.add(later);
        grain.bind(palette, anchors, WoodGrainCoordinates.visibleBounds(grain, faded));
    }

    private android.graphics.RectF grainBounds(View view) {
        if (view instanceof DewDotView)
            return WoodGrainCoordinates.centeredBounds(grain, view,
                    ((DewDotView) view).grainWidth(), ((DewDotView) view).grainHeight());
        return WoodGrainCoordinates.bounds(grain, view);
    }

    int visibleFollowingStepsForTest() { return visibleFollowingSteps; }
    int cardExtentForTest() { return cardParams.topMargin + card.getMeasuredHeight(); }

    private void playGlint(int color, long duration, float alpha) {
        glint.animate().cancel();
        LayoutParams params = (LayoutParams) glint.getLayoutParams();
        params.height = card.getHeight();
        glint.setLayoutParams(params);
        GradientDrawable sheen = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{Color.TRANSPARENT, UiStyle.alpha(color, alpha), Color.TRANSPARENT});
        glint.setBackground(sheen);
        glint.setTranslationX(-style.dp(64));
        glint.setVisibility(VISIBLE);
        glint.animate().translationX(Math.max(getWidth(), style.dp(320))).setDuration(duration)
                .setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator())
                .withEndAction(() -> glint.setVisibility(INVISIBLE));
    }

    private void animateFocusChange(DayPalette palette) {
        boolean deferred = deferPending;
        deferPending = false;
        card.animate().cancel();
        card.setTranslationY(style.dp(palette.motion.focusEnterDistanceDp));
        card.setAlpha(.86f);
        card.animate().translationY(0f).alpha(1f)
                .setDuration(palette.motion.stateChangeDurationMs)
                .setInterpolator(new android.view.animation.PathInterpolator(.2f, .7f, .3f, 1f))
                .withEndAction(() -> {
                    if (!android.animation.ValueAnimator.areAnimatorsEnabled()) return;
                    if (deferred) playAfterglow(palette);
                    else playGlint(Color.WHITE, palette.motion.glintDurationMs, .16f);
                });
    }

    private void playAfterglow(DayPalette palette) {
        afterglow.animate().cancel();
        LayoutParams params = (LayoutParams) afterglow.getLayoutParams();
        params.height = card.getHeight();
        afterglow.setLayoutParams(params);
        afterglow.setRotation(card.getRotation());
        GradientDrawable outline = style.leaf(Color.TRANSPARENT, palette.light,
                10, 64, 10, 64);
        outline.setStroke(style.dp(2), palette.light);
        afterglow.setBackground(outline);
        afterglow.setAlpha(1f);
        afterglow.setVisibility(VISIBLE);
        afterglow.animate().alpha(0f).setDuration(palette.motion.afterglowDurationMs)
                .setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator())
                .withEndAction(() -> afterglow.setVisibility(INVISIBLE));
    }

}

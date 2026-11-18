package de.thonktank.autosecretary;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import de.thonktank.autosecretary.presentation.alltasks.AllTasksUiState;
import de.thonktank.autosecretary.presentation.alltasks.AllTasksActionSink;
import de.thonktank.autosecretary.presentation.alltasks.AllTasksComposeHostView;
import de.thonktank.autosecretary.presentation.options.OptionsActionSink;
import de.thonktank.autosecretary.presentation.options.OptionsAction;
import de.thonktank.autosecretary.presentation.options.OptionsScreenState;
import de.thonktank.autosecretary.presentation.shell.AppShellScreenState;
import de.thonktank.autosecretary.ui.today.FocusCardUiModel;
import de.thonktank.autosecretary.presentation.today.FocusTaskUiModel;
import de.thonktank.autosecretary.presentation.today.TodayUiModel;
import de.thonktank.autosecretary.presentation.today.TodayAction;
import de.thonktank.autosecretary.presentation.today.TodayActionSink;
import de.thonktank.autosecretary.presentation.today.TodayScreenState;
import de.thonktank.autosecretary.ui.today.*;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import de.thonktank.autosecretary.presentation.today.TimelineTaskUiModel;


import java.util.ArrayList;
import java.util.List;

public final class DashboardRenderer {
    private final Context context;
    private final ScrollView scroll;
    private final LinearLayout content;
    private final UiStyle style;
    private final TodayActionSink todayActions;
    private final OptionsActionSink optionsActions;
    private final String version;
    private final RewardAnchorRegistry rewardAnchors;
    private final AllTasksActionSink allTasksActions;
    private NavigationDestination mounted;
    private FocusTaskView focus;
    private TodayOverviewView overview;
    private TodayDetailControls detailControls;
    private TimelineTaskUiModel pendingUndo;
    private TodayUiModel boundToday;
    private String selectedKey;
    private int overviewScroll;
    private final Runnable expireUndo = () -> {
        pendingUndo = null;
        if (detailControls != null) detailControls.undoStrip().setVisibility(View.GONE);
    };
    private CompletedTodayView completedToday;
    private AllTasksComposeHostView allTasks;
    private OptionsView options;

    public DashboardRenderer(Context context, ScrollView scroll, LinearLayout content,
                             TodayActionSink todayActions,
                             OptionsActionSink optionsActions,
                             String version,
                             RewardAnchorRegistry rewardAnchors,
                             AllTasksActionSink allTasksActions) {
        this.context = context;
        this.scroll = scroll;
        this.content = content;
        this.todayActions = todayActions;
        this.optionsActions = optionsActions;
        this.version = version;
        this.rewardAnchors = rewardAnchors;
        this.allTasksActions = allTasksActions;
        style = new UiStyle(context);
    }

    public void render(AppShellScreenState shell, TodayScreenState todayState,
                       AllTasksUiState allTasksState,
                       OptionsScreenState optionsState) {
        if (mounted != shell.navigation) mount(shell.navigation);
        if (shell.navigation == NavigationDestination.TODAY)
            bindToday(todayState, shell.palette);
        else if (shell.navigation == NavigationDestination.ALL_TASKS)
            allTasks.bind(allTasksState, shell.palette, allTasksActions,
                    todayState.today().flowRuns, shell.nowEpochMillis,
                    () -> optionsActions.emit(OptionsAction.openFlowRunsSelected()));
        else options.bind(optionsState, version);
    }

    public void animateEditorTransition(Runnable finished) {
        if (!android.animation.ValueAnimator.areAnimatorsEnabled()) {
            trace("editor-settled", "animations=false");
            finished.run();
            return;
        }
        List<View> leaves = new ArrayList<>();
        if (focus != null && focus.getVisibility() == View.VISIBLE) leaves.add(focus);
        if (overview != null && overview.isShown()) leaves.add(overview);
        if (leaves.isEmpty()) {
            trace("editor-settled", "leaves=0");
            finished.run();
            return;
        }
        traceLeaves("editor-start", leaves.size());
        final int[] remaining = {leaves.size()};
        MotionTokens motion = MotionTokens.standard();
        for (int i = 0; i < leaves.size(); i++) {
            View leaf = leaves.get(i);
            float originalRotation = leaf.getRotation();
            boolean[] completed = {false};
            Runnable completeLeaf = () -> {
                if (completed[0]) return;
                completed[0] = true;
                leaf.animate().setListener(null);
                leaf.setTranslationX(0f);
                leaf.setTranslationY(0f);
                leaf.setRotation(originalRotation);
                leaf.setAlpha(1f);
                if (--remaining[0] == 0) {
                    traceLeaves("editor-end", leaves.size());
                    finished.run();
                }
            };
            leaf.animate().translationX(style.dp(motion.leafFlightXDp + i * 10))
                    .translationY(style.dp(motion.leafFlightYDp + i * 6))
                    .rotation(originalRotation + motion.leafFlightRotationDegrees).alpha(0f)
                    .setDuration(motion.leafFlightDurationMs)
                    .setInterpolator(new android.view.animation.PathInterpolator(.2f, .7f, .3f, 1f))
                    .setListener(new AnimatorListenerAdapter() {
                        @Override public void onAnimationCancel(Animator animation) {
                            completeLeaf.run();
                        }

                        @Override public void onAnimationEnd(Animator animation) {
                            completeLeaf.run();
                        }
                    }).start();
        }
    }

    private static void trace(String kind, String detail) {
        if (PresentationTrace.enabled())
            PresentationTrace.emit("dashboard-motion", kind, detail);
    }

    private static void traceLeaves(String kind, int count) {
        if (PresentationTrace.enabled())
            PresentationTrace.emit("dashboard-motion", kind, "leaves=" + count);
    }

    private void mount(NavigationDestination destination) {
        if (mounted == NavigationDestination.TODAY && selectedKey == null)
            overviewScroll = scroll.getScrollY();
        if (detailControls != null) detailControls.hide();
        selectedKey = null;
        boundToday = null;
        content.removeAllViews();
        scroll.setVisibility(destination == NavigationDestination.ALL_TASKS
                ? View.GONE : View.VISIBLE);
        if (allTasks != null) {
            if (destination != NavigationDestination.ALL_TASKS) allTasks.closeTransientState();
            allTasks.setVisibility(destination == NavigationDestination.ALL_TASKS
                    ? View.VISIBLE : View.GONE);
        }
        mounted = destination;
        if (destination == NavigationDestination.TODAY) mountToday();
        else if (destination == NavigationDestination.ALL_TASKS) {
            content.setPadding(0, 0, 0, 0);
            if (allTasks == null) {
                allTasks = new AllTasksComposeHostView(context);
                allTasks.setId(R.id.all_tasks_compose_host);
            }
            ViewGroupParent.mountBesideScroll(scroll, content, allTasks);
        } else {
            content.setPadding(0, 0, 0, 0);
            if (options == null) options = new OptionsView(context, optionsActions);
            content.addView(options, new LinearLayout.LayoutParams(-1, -2));
        }
        if (destination == NavigationDestination.TODAY)
            androidx.core.view.OneShotPreDrawListener.add(scroll, () -> {
                if (mounted == NavigationDestination.TODAY && selectedKey == null)
                    scroll.scrollTo(0, overviewScroll);
            });
        else if (destination != NavigationDestination.ALL_TASKS) scroll.scrollTo(0, 0);
    }

    private static final class ViewGroupParent {
        private ViewGroupParent() { }

        static void mountBesideScroll(ScrollView scroll, LinearLayout fallback,
                                      AllTasksComposeHostView allTasks) {
            if (allTasks.getParent() != null) return;
            if (scroll.getParent() instanceof LinearLayout) {
                LinearLayout parent = (LinearLayout) scroll.getParent();
                int index = parent.indexOfChild(scroll) + 1;
                parent.addView(allTasks, index,
                        new LinearLayout.LayoutParams(-1, 0, 1));
            } else {
                // Renderer unit tests mount only the scroll content, without the production shell.
                fallback.addView(allTasks, new LinearLayout.LayoutParams(-1, -2));
            }
        }
    }

    public void dispose() {
        scroll.removeCallbacks(expireUndo);
        if (detailControls != null) detailControls.hide();
    }

    public boolean handleTodayBack() {
        if (mounted != NavigationDestination.TODAY || selectedKey == null) return false;
        todayActions.emit(TodayAction.showOverview());
        return true;
    }

    private void mountToday() {
        if (detailControls == null) {
            detailControls = new TodayDetailControls(context, todayActions);
            if (scroll.getParent() instanceof LinearLayout) {
                LinearLayout parent = (LinearLayout) scroll.getParent();
                parent.addView(detailControls.navigation(), parent.indexOfChild(scroll), controlInsets());
                parent.addView(detailControls.tools(), parent.indexOfChild(scroll) + 1, controlInsets());
                parent.addView(detailControls.undoStrip(), parent.indexOfChild(scroll) + 1, controlInsets());
            }
        }
        content.setPadding(style.dp(24), style.dp(7), style.dp(16), style.dp(14));
        focus = new FocusTaskView(context, rewardAnchors,
                new EdgeAutoScroller.AndroidScrollHost(scroll));
        focus.setId(R.id.dashboard_focus);
        content.addView(focus, new LinearLayout.LayoutParams(-1, -2));
        overview = new TodayOverviewView(context, todayActions, item -> {
            pendingUndo = item;
            scroll.removeCallbacks(expireUndo);
            scroll.postDelayed(expireUndo, 9000);
            todayActions.emit(TodayAction.hideTask(item.actionTarget));
        });
        content.addView(overview, new LinearLayout.LayoutParams(-1, -2));
        completedToday = new CompletedTodayView(context, todayActions);
        LinearLayout.LayoutParams completedParams = new LinearLayout.LayoutParams(-1, -2);
        completedParams.topMargin = style.dp(7);
        content.addView(completedToday, completedParams);
    }

    private LinearLayout.LayoutParams controlInsets() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMarginStart(style.dp(24));
        params.setMarginEnd(style.dp(16));
        return params;
    }

    private void bindToday(TodayScreenState state, DayPalette palette) {
        TodayUiModel dashboard = state.today();
        FocusTaskUiModel selected = dashboard.focus;
        String key = selected == null ? null : selected.actionTarget.item.key();
        if (!java.util.Objects.equals(selectedKey, key) || boundToday == null) {
            if (selectedKey == null && key != null && boundToday != null)
                overviewScroll = scroll.getScrollY();
            selectedKey = key;
            androidx.core.view.OneShotPreDrawListener.add(scroll, () -> {
                if (java.util.Objects.equals(key, selectedKey))
                    scroll.scrollTo(0, key == null ? overviewScroll : 0);
            });
        }
        boundToday = dashboard;
        rewardAnchors.clearDynamic();
        focus.setVisibility(selected == null ? View.GONE : View.VISIBLE);
        overview.setVisibility(selected == null ? View.VISIBLE : View.GONE);
        detailControls.bind(dashboard, palette);
        if (selected == null) {
            overview.bind(dashboard, palette);
            completedToday.bind(dashboard.completedToday, palette);
            detailControls.bindUndo(dashboard, pendingUndo, palette, expireUndo);
            return;
        }
        completedToday.setVisibility(View.GONE);
        FocusCardUiModel focusModel = new FocusCardUiModel(selected, state.feature.focus,
                palette, state.focusStepLimit, state.repetitionInput, state.feature.reorder,
                state.timers, true);
        focus.bind(focusModel, false, todayActions);
    }
}

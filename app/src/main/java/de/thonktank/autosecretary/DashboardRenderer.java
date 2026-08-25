package de.thonktank.autosecretary;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import de.thonktank.autosecretary.presentation.alltasks.AllTasksUiState;
import de.thonktank.autosecretary.presentation.alltasks.AllTasksView;
import de.thonktank.autosecretary.presentation.options.OptionsActionSink;
import de.thonktank.autosecretary.presentation.options.OptionsScreenState;
import de.thonktank.autosecretary.presentation.shell.AppShellScreenState;
import de.thonktank.autosecretary.ui.today.FocusCardUiModel;
import de.thonktank.autosecretary.presentation.today.FocusTaskUiModel;
import de.thonktank.autosecretary.presentation.today.TimelineItemUiModel;
import de.thonktank.autosecretary.presentation.today.TodayUiModel;
import de.thonktank.autosecretary.presentation.today.TodayAction;
import de.thonktank.autosecretary.presentation.today.TodayActionSink;
import de.thonktank.autosecretary.presentation.today.TodayScreenState;
import de.thonktank.autosecretary.ui.today.*;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import de.thonktank.autosecretary.data.preferences.FocusStepLimit;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DashboardRenderer {
    private final Context context;
    private final ScrollView scroll;
    private final LinearLayout content;
    private final UiStyle style;
    private final TodayActionSink todayActions;
    private final OptionsActionSink optionsActions;
    private final String version;
    private final RewardAnchorRegistry rewardAnchors;
    private final AllTasksView.Listener allTasksListener;
    private NavigationDestination mounted;
    private FocusTaskView focus;
    private LinearLayout timeline;
    private TextView more;
    private EmptyStateView empty;
    private CompletedTodayView completedToday;
    private AllTasksView allTasks;
    private OptionsView options;
    private final Map<String, View> timelineViews = new LinkedHashMap<>();

    public DashboardRenderer(Context context, ScrollView scroll, LinearLayout content,
                             TodayActionSink todayActions,
                             OptionsActionSink optionsActions,
                             String version,
                             RewardAnchorRegistry rewardAnchors,
                             AllTasksView.Listener allTasksListener) {
        this.context = context;
        this.scroll = scroll;
        this.content = content;
        this.todayActions = todayActions;
        this.optionsActions = optionsActions;
        this.version = version;
        this.rewardAnchors = rewardAnchors;
        this.allTasksListener = allTasksListener;
        style = new UiStyle(context);
    }

    public void render(AppShellScreenState shell, TodayScreenState todayState,
                       AllTasksUiState allTasksState,
                       OptionsScreenState optionsState) {
        if (mounted != shell.navigation) mount(shell.navigation);
        if (shell.navigation == NavigationDestination.TODAY)
            bindToday(todayState, shell.palette, todayState.focusStepLimit);
        else if (shell.navigation == NavigationDestination.ALL_TASKS)
            allTasks.bind(allTasksState, shell.palette);
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
        if (timeline != null) {
            for (int i = 0; i < timeline.getChildCount() && leaves.size() < 5; i++)
                leaves.add(timeline.getChildAt(i));
        }
        if (leaves.isEmpty() && empty != null && empty.getVisibility() == View.VISIBLE)
            leaves.add(empty);
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
        int scrollY = scroll.getScrollY();
        content.removeAllViews();
        scroll.setVisibility(destination == NavigationDestination.ALL_TASKS
                ? View.GONE : View.VISIBLE);
        if (allTasks != null) allTasks.setVisibility(destination == NavigationDestination.ALL_TASKS
                ? View.VISIBLE : View.GONE);
        mounted = destination;
        if (destination == NavigationDestination.TODAY) mountToday();
        else if (destination == NavigationDestination.ALL_TASKS) {
            content.setPadding(0, 0, 0, 0);
            if (allTasks == null) allTasks = new AllTasksView(context, allTasksListener);
            ViewGroupParent.mountBesideScroll(scroll, content, allTasks);
        } else {
            content.setPadding(0, 0, 0, 0);
            if (options == null) options = new OptionsView(context, optionsActions);
            content.addView(options, new LinearLayout.LayoutParams(-1, -2));
        }
        if (destination != NavigationDestination.ALL_TASKS)
            scroll.post(() -> scroll.scrollTo(0, scrollY));
    }

    private static final class ViewGroupParent {
        private ViewGroupParent() { }

        static void mountBesideScroll(ScrollView scroll, LinearLayout fallback,
                                      AllTasksView allTasks) {
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

    private void mountToday() {
        content.setPadding(style.dimen(R.dimen.page_start), style.dimen(R.dimen.content_top),
                style.dimen(R.dimen.page_end), style.dp(26));
        focus = new FocusTaskView(context, rewardAnchors,
                new EdgeAutoScroller.AndroidScrollHost(scroll));
        focus.setId(R.id.dashboard_focus);
        content.addView(focus, new LinearLayout.LayoutParams(-1, -2));
        timeline = new LinearLayout(context);
        timeline.setId(R.id.dashboard_timeline);
        timeline.setOrientation(LinearLayout.VERTICAL);
        content.addView(timeline, new LinearLayout.LayoutParams(-1, -2));
        more = style.serif("", 14, 0, true, 300);
        LinearLayout.LayoutParams moreParams = new LinearLayout.LayoutParams(-1, -2);
        moreParams.setMargins(0, style.dp(16), 0, 0);
        content.addView(more, moreParams);
        empty = new EmptyStateView(context, () -> todayActions.emit(TodayAction.addTask()));
        content.addView(empty, new LinearLayout.LayoutParams(-1, -2));
        completedToday = new CompletedTodayView(context, todayActions);
        LinearLayout.LayoutParams completedParams = new LinearLayout.LayoutParams(-1, -2);
        completedParams.topMargin = style.dp(14);
        content.addView(completedToday, completedParams);
    }

    private void bindToday(TodayScreenState state, DayPalette palette,
                           FocusStepLimit focusStepLimit) {
        TodayUiModel dashboard = state.today();
        rewardAnchors.clearDynamic();
        FocusTaskUiModel focusTask = dashboard.focus;
        boolean hasFocus = focusTask != null;
        focus.setVisibility(hasFocus ? View.VISIBLE : View.GONE);
        timeline.setVisibility(hasFocus ? View.VISIBLE : View.GONE);
        more.setVisibility(hasFocus && dashboard.timeline.size() > 3 ? View.VISIBLE : View.GONE);
        empty.setVisibility(hasFocus ? View.GONE : View.VISIBLE);
        completedToday.bind(dashboard.completedToday, palette);
        if (!hasFocus) {
            content.setPadding(style.dimen(R.dimen.page_start), style.dp(120),
                    style.dimen(R.dimen.page_end), style.dp(26));
            empty.bind(palette, false);
            return;
        }
        content.setPadding(style.dimen(R.dimen.page_start), style.dimen(R.dimen.content_top),
                style.dimen(R.dimen.page_end), style.dp(26));
        focus.bind(focusTask, dashboard.timeline.size() > 0, palette,
                focusStepLimit, state.repetitionInput, state.feature.reorder,
                state.timers, todayActions);
        bindTimeline(dashboard.timeline, focusTask.overdue, focusTask.ongoing, palette);
        int remaining = dashboard.timeline.size() - Math.min(3, dashboard.timeline.size());
        more.setText(context.getResources().getQuantityString(
                R.plurals.more_items, remaining, remaining));
        more.setTextColor(palette.muted);
    }

    private void bindTimeline(List<TimelineItemUiModel> items, boolean overdueShown,
                              boolean afterAssigned,
                              DayPalette palette) {
        int shown = Math.min(3, items.size());
        List<String> desired = new ArrayList<>();
        for (int i = 0; i < shown; i++) {
            TimelineItemUiModel item = items.get(i);
            String key = item.event != null
                    ? "event:" + item.event.minuteOfDay + ":" + item.event.title
                    : "task:" + (item.task.occurrenceId.isEmpty()
                    ? item.task.taskId : item.task.occurrenceId);
            desired.add(key);
            View view = timelineViews.get(key);
            if (item.event != null) {
                if (!(view instanceof CalendarLeafView)) {
                    view = new CalendarLeafView(context);
                    timelineViews.put(key, view);
                }
                ((CalendarLeafView) view).bind(item.event, palette);
            } else {
                if (!(view instanceof TaskLeafView)) {
                    view = new TaskLeafView(context);
                    timelineViews.put(key, view);
                }
                boolean firstOpenAfterFocus = !afterAssigned;
                afterAssigned = true;
                boolean bad = item.task.overdue && !overdueShown;
                overdueShown |= bad;
                String marker = context.getString(bad ? R.string.marker_overdue
                        : firstOpenAfterFocus ? R.string.marker_after : R.string.marker_later);
                ((TaskLeafView) view).bind(item.task, marker,
                        !firstOpenAfterFocus, palette,
                        task -> todayActions.emit(task.terminalCondition
                                ? TodayAction.requestClose(task.taskId, task.title)
                                : TodayAction.completeOccurrence(task.occurrenceId)),
                        task -> todayActions.emit(TodayAction.openTaskMenu(task.actionTarget)));
                RewardAnchorKey.Kind kind = item.task.terminalCondition
                        ? RewardAnchorKey.Kind.TASK : RewardAnchorKey.Kind.OCCURRENCE;
                rewardAnchors.register(new RewardAnchorKey(kind, item.task.terminalCondition
                        ? item.task.taskId : item.task.occurrenceId),
                        ((TaskLeafView) view).rewardAnchor());
            }
            if (view.getParent() != timeline) {
                if (view.getParent() instanceof android.view.ViewGroup)
                    ((android.view.ViewGroup) view.getParent()).removeView(view);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
                params.setMargins(0, i == 0 ? style.dp(12) : style.dimen(R.dimen.content_gap), 0, 0);
                timeline.addView(view, Math.min(i, timeline.getChildCount()), params);
            } else if (timeline.indexOfChild(view) != i) {
                timeline.removeView(view);
                timeline.addView(view, i);
            }
            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) view.getLayoutParams();
            params.width = -1;
            params.height = -2;
            params.setMargins(0, i == 0 ? style.dp(12) : style.dimen(R.dimen.content_gap), 0, 0);
            view.setLayoutParams(params);
        }
        for (int i = timeline.getChildCount() - 1; i >= 0; i--) {
            View child = timeline.getChildAt(i);
            String childKey = null;
            for (Map.Entry<String, View> entry : timelineViews.entrySet())
                if (entry.getValue() == child) { childKey = entry.getKey(); break; }
            if (!desired.contains(childKey)) timeline.removeViewAt(i);
        }
        timelineViews.keySet().retainAll(desired);
    }
}

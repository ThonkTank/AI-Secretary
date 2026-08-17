package de.thonktank.autosecretary;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import de.thonktank.autosecretary.data.preferences.UiThemeMode;
import de.thonktank.autosecretary.update.presentation.UpdateUiState;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DashboardRenderer {
    public interface Actions extends FocusTaskView.Actions, OptionsView.Actions {
        void onAddTask();
        void onTaskAction(TaskSnapshot task);
        void onTaskMenu(TaskSnapshot task);
    }

    private final Context context;
    private final ScrollView scroll;
    private final LinearLayout content;
    private final UiStyle style;
    private final Actions actions;
    private final String version;
    private NavigationDestination mounted;
    private TextView xp;
    private FocusTaskView focus;
    private LinearLayout timeline;
    private TextView more;
    private EmptyStateView empty;
    private EmptyStateView allPlaceholder;
    private OptionsView options;
    private final Map<String, View> timelineViews = new LinkedHashMap<>();

    public DashboardRenderer(Context context, ScrollView scroll, LinearLayout content,
                             Actions actions, String version) {
        this.context = context;
        this.scroll = scroll;
        this.content = content;
        this.actions = actions;
        this.version = version;
        style = new UiStyle(context);
    }

    public void render(DashboardUiState state, UiThemeMode themeMode, UpdateUiState updateState) {
        if (mounted != state.navigation) mount(state.navigation);
        if (state.navigation == NavigationDestination.TODAY) bindToday(state.dashboard, state.palette);
        else if (state.navigation == NavigationDestination.ALL_TASKS)
            allPlaceholder.bind(state.palette, true);
        else options.bind(state.palette, themeMode, state.calendarPermission, state.calendar,
                    version, updateState);
    }

    public void animateEditorTransition(Runnable finished) {
        if (!android.animation.ValueAnimator.areAnimatorsEnabled()) {
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
            finished.run();
            return;
        }
        final int[] remaining = {leaves.size()};
        MotionTokens motion = MotionTokens.standard();
        for (int i = 0; i < leaves.size(); i++) {
            View leaf = leaves.get(i);
            float originalRotation = leaf.getRotation();
            leaf.animate().translationX(style.dp(motion.leafFlightXDp + i * 10))
                    .translationY(style.dp(motion.leafFlightYDp + i * 6))
                    .rotation(originalRotation + motion.leafFlightRotationDegrees).alpha(0f)
                    .setDuration(motion.leafFlightDurationMs)
                    .setInterpolator(new android.view.animation.PathInterpolator(.2f, .7f, .3f, 1f))
                    .withEndAction(() -> {
                        leaf.setTranslationX(0f);
                        leaf.setTranslationY(0f);
                        leaf.setRotation(originalRotation);
                        leaf.setAlpha(1f);
                        if (--remaining[0] == 0) finished.run();
                    });
        }
    }

    private void mount(NavigationDestination destination) {
        int scrollY = scroll.getScrollY();
        content.removeAllViews();
        mounted = destination;
        if (destination == NavigationDestination.TODAY) mountToday();
        else if (destination == NavigationDestination.ALL_TASKS) {
            content.setPadding(style.dimen(R.dimen.page_start), style.dp(120),
                    style.dimen(R.dimen.page_end), style.dp(26));
            if (allPlaceholder == null) allPlaceholder = new EmptyStateView(context, actions::onAddTask);
            content.addView(allPlaceholder, new LinearLayout.LayoutParams(-1, -2));
        } else {
            content.setPadding(0, 0, 0, 0);
            if (options == null) options = new OptionsView(context, actions);
            content.addView(options, new LinearLayout.LayoutParams(-1, -2));
        }
        scroll.post(() -> scroll.scrollTo(0, scrollY));
    }

    private void mountToday() {
        content.setPadding(style.dimen(R.dimen.page_start), style.dimen(R.dimen.content_top),
                style.dimen(R.dimen.page_end), style.dp(26));
        xp = style.serif("", 14, 0, true, 300);
        LinearLayout.LayoutParams xpParams = new LinearLayout.LayoutParams(-1, -2);
        xpParams.setMargins(0, 0, 0, style.dp(12));
        content.addView(xp, xpParams);
        focus = new FocusTaskView(context);
        content.addView(focus, new LinearLayout.LayoutParams(-1, -2));
        timeline = new LinearLayout(context);
        timeline.setOrientation(LinearLayout.VERTICAL);
        content.addView(timeline, new LinearLayout.LayoutParams(-1, -2));
        more = style.serif("", 14, 0, true, 300);
        LinearLayout.LayoutParams moreParams = new LinearLayout.LayoutParams(-1, -2);
        moreParams.setMargins(0, style.dp(16), 0, 0);
        content.addView(more, moreParams);
        empty = new EmptyStateView(context, actions::onAddTask);
        content.addView(empty, new LinearLayout.LayoutParams(-1, -2));
    }

    private void bindToday(DashboardUiModel dashboard, DayPalette palette) {
        TaskSnapshot focusTask = dashboard.firstOpen();
        boolean hasFocus = focusTask != null;
        xp.setVisibility(hasFocus ? View.VISIBLE : View.GONE);
        focus.setVisibility(hasFocus ? View.VISIBLE : View.GONE);
        timeline.setVisibility(hasFocus ? View.VISIBLE : View.GONE);
        more.setVisibility(hasFocus && dashboard.timeline.size() > 3 ? View.VISIBLE : View.GONE);
        empty.setVisibility(hasFocus ? View.GONE : View.VISIBLE);
        if (!hasFocus) {
            content.setPadding(style.dimen(R.dimen.page_start), style.dp(120),
                    style.dimen(R.dimen.page_end), style.dp(26));
            empty.bind(palette, false);
            return;
        }
        content.setPadding(style.dimen(R.dimen.page_start), style.dimen(R.dimen.content_top),
                style.dimen(R.dimen.page_end), style.dp(26));
        xp.setText(context.getString(R.string.xp_summary, dashboard.xp));
        xp.setTextColor(palette.muted);
        int open = 0;
        for (TaskSnapshot task : dashboard.tasks) if (!task.done) open++;
        focus.bind(focusTask, dashboard.timeline.size() > 0, open > 1, palette, actions);
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
                    : "task:" + item.task.taskId;
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
                boolean firstOpenAfterFocus = !item.task.done && !afterAssigned;
                if (!item.task.done) afterAssigned = true;
                boolean bad = item.task.overdue && !overdueShown;
                overdueShown |= bad;
                String marker = context.getString(item.task.done ? R.string.marker_done
                        : bad ? R.string.marker_overdue
                        : firstOpenAfterFocus ? R.string.marker_after : R.string.marker_later);
                ((TaskLeafView) view).bind(item.task, marker,
                        !item.task.done && !firstOpenAfterFocus, palette,
                        actions::onTaskAction, actions::onTaskMenu);
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

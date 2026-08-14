package com.autosecretary.widget;

import android.content.Context;
import android.content.Intent;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.StrikethroughSpan;
import android.view.View;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import com.autosecretary.presentation.R;
import com.autosecretary.app.AppGraph;
import com.autosecretary.application.DashboardData;
import com.autosecretary.application.StepCompletion;
import com.autosecretary.application.TodayTimeline;
import com.autosecretary.domain.BusyInterval;
import com.autosecretary.domain.PlanAssignment;
import com.autosecretary.domain.Routine;
import com.autosecretary.domain.Step;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

final class FocusWidgetFactory implements RemoteViewsService.RemoteViewsFactory {
    private record Row(PlanAssignment assignment, List<Step> steps, List<StepCompletion> completions,
                       BusyInterval preceding, BusyInterval calendar, boolean undo) {
        static Row focus(PlanAssignment assignment, List<Step> steps,
                         List<StepCompletion> completions, BusyInterval preceding) {
            return new Row(assignment, steps, completions, preceding, null, false);
        }
        static Row calendar(BusyInterval calendar) {
            return new Row(null, List.of(), List.of(), null, calendar, false);
        }
        static Row undoRow() {
            return new Row(null, List.of(), List.of(), null, null, true);
        }
    }

    private final Context context;
    private final AppGraph graph;
    private final int maxRows;
    private final boolean showSteps;
    private final boolean wide;
    private final int palette;
    private List<Row> rows = new ArrayList<>();

    FocusWidgetFactory(Context context, AppGraph graph) {
        this(context, graph, 3, true, false, 0);
    }

    FocusWidgetFactory(
            Context context,
            AppGraph graph,
            int maxRows,
            boolean showSteps,
            boolean wide,
            int palette) {
        this.context = context;
        this.graph = graph;
        this.maxRows = maxRows;
        this.showSteps = showSteps;
        this.wide = wide;
        this.palette = palette;
    }

    @Override public void onCreate() { }

    @Override
    public void onDataSetChanged() {
        if (graph == null) {
            rows = new ArrayList<>();
            return;
        }
        DashboardData dashboard;
        try {
            dashboard = graph.executors().callDatabase(
                    () -> graph.planFocus().execute(maxRows));
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            rows = new ArrayList<>();
            return;
        } catch (java.util.concurrent.ExecutionException error) {
            rows = new ArrayList<>();
            return;
        }
        rows = orderedEntries(dashboard, graph.clock().localNow(), maxRows).stream()
                .map(entry -> entry instanceof TodayTimeline.Calendar calendar
                        ? Row.calendar(calendar.value())
                        : focusRow((TodayTimeline.Assignment) entry, dashboard))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        if (dashboard.undoLabel() != null
                && dashboard.undoLabel().startsWith("Aus heute genommen")) {
            while (rows.size() >= maxRows) rows.remove(rows.size() - 1);
            rows.add(Row.undoRow());
        }
    }

    @Override public int getCount() { return rows.size(); }

    @Override
    public RemoteViews getViewAt(int position) {
        if (position < 0 || position >= rows.size()) return null;
        Row rowData = rows.get(position);
        if (rowData.undo()) return undoView();
        if (rowData.calendar() != null) return calendarView(rowData.calendar(), position);
        PlanAssignment assignment = rowData.assignment();
        RemoteViews row = new RemoteViews(context.getPackageName(), wide
                ? R.layout.widget_focus_row_wide : R.layout.widget_focus_row);
        row.setInt(R.id.WidgetRowRoot, "setBackgroundResource", rowBackground(position));
        row.setTextViewText(R.id.WidgetPosition,
                context.getString(position == 0 ? R.string.now : position == 1
                        ? R.string.next : R.string.later));
        row.setTextViewText(R.id.WidgetTitle, assignment.workItem().title());
        row.setTextColor(R.id.WidgetPosition, actionColor());
        row.setTextColor(R.id.WidgetTitle, inkColor());
        row.setTextColor(R.id.WidgetMeta, mutedColor());
        row.setTextColor(R.id.WidgetDone, actionColor());
        row.setTextColor(R.id.WidgetLater, mutedColor());
        long completedSteps = rowData.steps().stream()
                .filter(step -> isCompleted(rowData, step.id())).count();
        row.setViewVisibility(R.id.WidgetProgress,
                rowData.steps().isEmpty() ? View.GONE : View.VISIBLE);
        row.setProgressBar(R.id.WidgetProgress, Math.max(1, rowData.steps().size()),
                (int) completedSteps, false);
        row.setViewVisibility(R.id.WidgetSteps,
                !showSteps || rowData.steps().isEmpty() ? View.GONE : View.VISIBLE);
        row.removeAllViews(R.id.WidgetSteps);
        int displayedSteps = showSteps ? Math.min(3, rowData.steps().size()) : 0;
        for (int index = 0; index < displayedSteps; index++) {
            Step stepValue = rowData.steps().get(index);
            boolean completed = isCompleted(rowData, stepValue.id());
            RemoteViews step = new RemoteViews(context.getPackageName(), R.layout.widget_focus_step);
            SpannableString stepLabel = new SpannableString(
                    (completed ? "●  " : "○  ") + stepValue.title());
            if (completed) stepLabel.setSpan(new StrikethroughSpan(), 3, stepLabel.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            step.setTextViewText(R.id.WidgetStepToggle, stepLabel);
            step.setTextColor(R.id.WidgetStepToggle,
                    completed ? completedColor() : inkColor());
            Intent toggle = new Intent().setAction(FocusWidgetProvider.ACTION_TOGGLE_STEP)
                    .putExtra(FocusWidgetProvider.EXTRA_ID, assignment.workItem().id())
                    .putExtra(FocusWidgetProvider.EXTRA_STEP_ID, stepValue.id())
                    .putExtra(FocusWidgetProvider.EXTRA_STEP_COMPLETED, !completed);
            step.setOnClickFillInIntent(R.id.WidgetStepToggle, toggle);
            row.addView(R.id.WidgetSteps, step);
        }
        if (rowData.steps().size() > displayedSteps) {
            RemoteViews more = new RemoteViews(context.getPackageName(), R.layout.widget_focus_step);
            int remaining = rowData.steps().size() - displayedSteps;
            more.setTextViewText(R.id.WidgetStepToggle,
                    "und " + countWord(remaining) + " weitere");
            more.setTextColor(R.id.WidgetStepToggle, markerColor());
            row.addView(R.id.WidgetSteps, more);
        }
        String time = "voraussichtlich ab "
                + assignment.start().format(DateTimeFormatter.ofPattern("HH:mm"));
        String calendarContext = rowData.preceding() == null ? ""
                : " · nach " + rowData.preceding().title();
        row.setTextViewText(R.id.WidgetMeta, time + " · ca. "
                + assignment.workItem().durationMinutes() + " Min" + calendarContext);
        row.setTextViewText(R.id.WidgetDone, rowData.steps().isEmpty() ? "Erledigt"
                : completedSteps == 0 ? "Alle erledigen"
                : rowData.steps().size() - completedSteps == 1 ? "letzten Schritt erledigen"
                : "Rest erledigen");

        Intent complete = new Intent().setAction(FocusWidgetProvider.ACTION_COMPLETE)
                .putExtra(FocusWidgetProvider.EXTRA_ID, assignment.workItem().id());
        row.setOnClickFillInIntent(R.id.WidgetDone, complete);
        Intent later = new Intent().setAction(FocusWidgetProvider.ACTION_LATER)
                .putExtra(FocusWidgetProvider.EXTRA_ID, assignment.workItem().id());
        row.setOnClickFillInIntent(R.id.WidgetLater, later);
        return row;
    }

    private static List<Step> activeSteps(PlanAssignment assignment) {
        LocalDate effective = assignment.workItem() instanceof Routine routine
                ? routine.nextDueDate() : assignment.start().toLocalDate();
        return assignment.workItem().steps().stream()
                .filter(step -> step.appliesOn(effective.getDayOfWeek()))
                .collect(java.util.stream.Collectors.toList());
    }

    static List<TodayTimeline.Entry> orderedEntries(
            DashboardData dashboard, LocalDateTime now, int maximum) {
        return TodayTimeline.from(dashboard, now).entries().stream()
                .limit(Math.max(0, maximum)).collect(java.util.stream.Collectors.toList());
    }

    private static Row focusRow(TodayTimeline.Assignment entry, DashboardData dashboard) {
        PlanAssignment assignment = entry.value();
        return Row.focus(assignment, activeSteps(assignment),
                dashboard.stepCompletions().stream()
                        .filter(value -> value.occurrenceKey().equals(assignment.occurrenceKey()))
                        .collect(java.util.stream.Collectors.toList()),
                entry.precedingCalendar());
    }

    private RemoteViews calendarView(BusyInterval calendar, int position) {
        RemoteViews row = new RemoteViews(
                context.getPackageName(), R.layout.widget_calendar_row);
        row.setInt(R.id.WidgetCalendarRoot, "setBackgroundResource", palette == 2
                ? R.drawable.bg_calendar_leaf_evening : palette == 1
                ? R.drawable.widget_calendar_leaf_dark : R.drawable.bg_calendar_leaf);
        int calendarInk = palette == 2 ? 0xFF93C3D2
                : palette == 1 ? 0xFF8FBACB : 0xFF2B5666;
        int calendarLabel = palette == 0 ? 0xFF4F7482 : 0xFF7096A6;
        row.setTextColor(R.id.WidgetCalendarTime, calendarInk);
        row.setTextColor(R.id.WidgetCalendarTitle, calendarInk);
        row.setTextColor(R.id.WidgetCalendarLabel, calendarLabel);
        row.setViewVisibility(R.id.WidgetCalendarTime, View.VISIBLE);
        row.setTextViewText(R.id.WidgetCalendarTime,
                calendar.start().format(DateTimeFormatter.ofPattern("HH:mm")));
        row.setTextViewText(R.id.WidgetCalendarTitle, calendar.title());
        String marker = context.getString(position == 0 ? R.string.now
                : position == 1 ? R.string.next : R.string.later);
        row.setTextViewText(R.id.WidgetCalendarLabel, marker + " · "
                + (calendar.titleVisibility() == BusyInterval.TitleVisibility.HIDDEN
                ? "privat · Titel nicht lesbar" : "im Kalender, fest"));
        return row;
    }

    private RemoteViews undoView() {
        RemoteViews row = new RemoteViews(context.getPackageName(), R.layout.widget_undo_row);
        row.setTextColor(R.id.WidgetUndo, markerColor());
        row.setOnClickFillInIntent(R.id.WidgetUndo,
                new Intent().setAction(FocusWidgetProvider.ACTION_UNDO));
        return row;
    }

    private static boolean isCompleted(Row row, String stepId) {
        return row.completions().stream().anyMatch(value -> value.stepId().equals(stepId));
    }

    private static String countWord(int value) {
        return switch (value) {
            case 1 -> "ein";
            case 2 -> "zwei";
            case 3 -> "drei";
            case 4 -> "vier";
            case 5 -> "fünf";
            default -> Integer.toString(value);
        };
    }

    private int rowBackground(int position) {
        if (palette == 2) {
            return position == 0 ? R.drawable.widget_leaf_focus_evening
                    : position == 1 ? R.drawable.bg_leaf_middle_evening
                    : R.drawable.bg_leaf_low_evening;
        }
        if (palette == 1) {
            return position == 0 ? R.drawable.widget_leaf_focus_dark
                    : position == 1 ? R.drawable.widget_leaf_middle_dark
                    : R.drawable.widget_leaf_low_dark;
        }
        return position == 0 ? R.drawable.bg_leaf_focus
                : position == 1 ? R.drawable.bg_leaf_middle : R.drawable.bg_leaf_low;
    }

    private int inkColor() {
        return palette == 2 ? 0xFFF8ECD2 : palette == 1 ? 0xFFF4EEDA : 0xFF1A2618;
    }

    private int mutedColor() {
        return palette == 2 ? 0xFFC3AE86 : palette == 1 ? 0xFFB2BCA4 : 0xFF586250;
    }

    private int markerColor() {
        return palette == 2 ? 0xFFA08B62 : palette == 1 ? 0xFF8E9A84 : 0xFF6D7860;
    }

    private int completedColor() {
        return palette == 2 ? 0xFF7A6742 : palette == 1 ? 0xFF6B7458 : 0xFFA79A7C;
    }

    private int actionColor() {
        return palette == 2 ? 0xFFF0A03C : palette == 1 ? 0xFFE8A83E : 0xFF2E6B44;
    }

    @Override public RemoteViews getLoadingView() {
        return new RemoteViews(context.getPackageName(), R.layout.widget_loading_row);
    }
    @Override public int getViewTypeCount() { return 3; }
    @Override public long getItemId(int position) {
        Row row = rows.get(position);
        if (row.undo()) return -1;
        return row.calendar() == null ? row.assignment().workItem().id().hashCode()
                : (row.calendar().start().toString() + row.calendar().title()).hashCode();
    }
    @Override public boolean hasStableIds() { return true; }
    @Override public void onDestroy() { rows = List.of(); }
}

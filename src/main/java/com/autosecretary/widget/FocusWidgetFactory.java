package com.autosecretary.widget;

import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import com.autosecretary.R;
import com.autosecretary.app.AppGraph;
import com.autosecretary.application.DashboardData;
import com.autosecretary.application.StepCompletion;
import com.autosecretary.domain.BusyInterval;
import com.autosecretary.domain.PlanAssignment;
import com.autosecretary.domain.Routine;
import com.autosecretary.domain.Step;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

final class FocusWidgetFactory implements RemoteViewsService.RemoteViewsFactory {
    private record Row(PlanAssignment assignment, List<Step> steps, List<StepCompletion> completions,
                       BusyInterval preceding) { }

    private final Context context;
    private final AppGraph graph;
    private List<Row> rows = new ArrayList<>();

    FocusWidgetFactory(Context context, AppGraph graph) {
        this.context = context;
        this.graph = graph;
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
                    () -> graph.planFocus().execute(3, false));
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            rows = new ArrayList<>();
            return;
        } catch (java.util.concurrent.ExecutionException error) {
            rows = new ArrayList<>();
            return;
        }
        rows = dashboard.focus().stream().map(assignment -> new Row(
                assignment,
                activeSteps(assignment),
                dashboard.stepCompletions().stream()
                        .filter(value -> value.occurrenceKey().equals(assignment.occurrenceKey()))
                        .collect(java.util.stream.Collectors.toList()),
                dashboard.calendar().stream()
                        .filter(value -> !value.end().isAfter(assignment.start()))
                        .filter(value -> value.end().toLocalDate().equals(
                                assignment.start().toLocalDate()))
                        .reduce((left, right) -> right).orElse(null)))
                .collect(java.util.stream.Collectors.toList());
    }

    @Override public int getCount() { return rows.size(); }

    @Override
    public RemoteViews getViewAt(int position) {
        if (position < 0 || position >= rows.size()) return null;
        Row rowData = rows.get(position);
        PlanAssignment assignment = rowData.assignment();
        RemoteViews row = new RemoteViews(context.getPackageName(), R.layout.widget_focus_row);
        row.setTextViewText(R.id.WidgetPosition,
                context.getString(position == 0 ? R.string.now : position == 1
                        ? R.string.next : R.string.later));
        row.setTextViewText(R.id.WidgetTitle, assignment.workItem().title());
        row.setViewVisibility(R.id.WidgetSteps, rowData.steps().isEmpty() ? View.GONE : View.VISIBLE);
        row.removeAllViews(R.id.WidgetSteps);
        int displayedSteps = Math.min(3, rowData.steps().size());
        for (int index = 0; index < displayedSteps; index++) {
            Step stepValue = rowData.steps().get(index);
            boolean completed = isCompleted(rowData, stepValue.id());
            RemoteViews step = new RemoteViews(context.getPackageName(), R.layout.widget_focus_step);
            step.setTextViewText(R.id.WidgetStepToggle,
                    (completed ? "●  " : "○  ") + stepValue.title());
            Intent toggle = new Intent().setAction(FocusWidgetProvider.ACTION_TOGGLE_STEP)
                    .putExtra(FocusWidgetProvider.EXTRA_ID, assignment.workItem().id())
                    .putExtra(FocusWidgetProvider.EXTRA_STEP_ID, stepValue.id())
                    .putExtra(FocusWidgetProvider.EXTRA_STEP_COMPLETED, !completed);
            step.setOnClickFillInIntent(R.id.WidgetStepToggle, toggle);
            row.addView(R.id.WidgetSteps, step);
        }
        if (rowData.steps().size() > displayedSteps) {
            RemoteViews more = new RemoteViews(context.getPackageName(), R.layout.widget_focus_step);
            more.setTextViewText(R.id.WidgetStepToggle,
                    "und " + (rowData.steps().size() - displayedSteps) + " weitere");
            row.addView(R.id.WidgetSteps, more);
        }
        String time = "voraussichtlich ab "
                + assignment.start().format(DateTimeFormatter.ofPattern("HH:mm"));
        String calendarContext = rowData.preceding() == null ? ""
                : " · nach " + rowData.preceding().title();
        row.setTextViewText(R.id.WidgetMeta, time + " · ca. "
                + assignment.workItem().durationMinutes() + " Min" + calendarContext);
        long completedSteps = rowData.steps().stream()
                .filter(step -> isCompleted(rowData, step.id())).count();
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

    private static boolean isCompleted(Row row, String stepId) {
        return row.completions().stream().anyMatch(value -> value.stepId().equals(stepId));
    }

    @Override public RemoteViews getLoadingView() { return null; }
    @Override public int getViewTypeCount() { return 1; }
    @Override public long getItemId(int position) {
        return rows.get(position).assignment().workItem().id().hashCode();
    }
    @Override public boolean hasStableIds() { return true; }
    @Override public void onDestroy() { rows = List.of(); }
}

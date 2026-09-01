package de.thonktank.autosecretary.widget;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import de.thonktank.autosecretary.R;
import de.thonktank.autosecretary.domain.model.Dashboard;
import de.thonktank.autosecretary.domain.model.DashboardTask;
import de.thonktank.autosecretary.domain.model.OccurrenceStep;
import de.thonktank.autosecretary.domain.model.OccurrenceKind;
import de.thonktank.autosecretary.domain.model.Task;
import de.thonktank.autosecretary.presentation.StepTextFormatter;
import de.thonktank.autosecretary.presentation.UiTextProvider;

/** Maps domain dashboard data directly into the widget-owned presentation model. */
public final class WidgetDashboardMapper {
    private final UiTextProvider texts;
    private final StepTextFormatter stepTexts;

    public WidgetDashboardMapper(UiTextProvider texts) {
        this.texts = texts;
        this.stepTexts = new StepTextFormatter(texts);
    }

    public WidgetDashboardUiModel map(Dashboard dashboard, LocalDate today) {
        DashboardTask focus = null;
        DashboardTask fallback = null;
        String afterTitle = null;
        for (DashboardTask item : dashboard.tasks) {
            if (item.done) continue;
            if (fallback == null) fallback = item;
            if (focus == null && canOwnFocus(item)) focus = item;
        }
        if (focus == null) focus = fallback;
        if (focus == null) return WidgetDashboardUiModel.empty();
        for (DashboardTask item : dashboard.tasks)
            if (!item.done && item != focus && canOwnFocus(item)) {
                afterTitle = item.task.title;
                break;
            }
        return WidgetDashboardUiModel.of(task(focus, today), afterTitle);
    }

    private static boolean canOwnFocus(DashboardTask item) {
        if (item.occurrence == null || item.occurrence.kind != OccurrenceKind.FLOW_SHEET)
            return true;
        for (OccurrenceStep step : item.steps) if (!step.done) return true;
        return false;
    }

    private WidgetTaskUiModel task(DashboardTask item, LocalDate today) {
        Task task = item.task;
        List<WidgetStepUiModel> steps = new ArrayList<>();
        int remaining = 0;
        for (OccurrenceStep step : item.steps) {
            boolean done = item.done || step.done;
            if (!done) remaining++;
            steps.add(WidgetStepUiModel.of(step.id, step.text,
                    stepTexts.format(step.prescription.amount, step.note), done));
        }
        LocalDate due = task.deadlineOn == null || item.occurrence == null
                ? item.occurrence == null ? null : item.occurrence.scheduledOn : task.deadlineOn;
        boolean overdue = !item.done && due != null && due.isBefore(today);
        boolean terminal = !task.conditionText.isEmpty();
        int action = terminal ? R.string.condition_met
                : steps.isEmpty() ? R.string.action_complete
                : remaining == 0 ? R.string.action_complete_all : R.string.action_complete_rest;
        return WidgetTaskUiModel.of(task.id.value,
                item.occurrence == null ? "" : item.occurrence.id, task.title, overdue, terminal,
                texts.text(action), steps);
    }
}

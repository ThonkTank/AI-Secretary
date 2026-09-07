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
import de.thonktank.autosecretary.domain.model.FlowTaskSheet;
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
        List<WidgetSource> sources = new ArrayList<>();
        for (DashboardTask item : dashboard.tasks) if (!item.done)
            sources.add(WidgetSource.task(item));
        for (FlowTaskSheet sheet : dashboard.flowTaskSheets)
            sources.add(WidgetSource.sheet(sheet));
        sources.sort(java.util.Comparator.comparing((WidgetSource value) -> value.date())
                .thenComparingInt(value -> value.slotRank())
                .thenComparingInt(WidgetSource::order).thenComparing(WidgetSource::id));
        if (sources.isEmpty()) return WidgetDashboardUiModel.empty();
        WidgetSource focus = sources.get(0);
        String afterTitle = null;
        if (sources.size() > 1) afterTitle = sources.get(1).title();
        return WidgetDashboardUiModel.of(focus.sheet == null
                ? task(focus.task, today) : task(focus.sheet), afterTitle);
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
        return WidgetTaskUiModel.of(task.id.value, item.occurrence == null ? ""
                : item.occurrence.id, task.title, overdue, terminal, texts.text(action), steps);
    }

    private WidgetTaskUiModel task(FlowTaskSheet sheet) {
        List<WidgetStepUiModel> steps = new ArrayList<>();
        for (FlowTaskSheet.Entry entry : sheet.entries) {
            de.thonktank.autosecretary.domain.model.StepPrescription prescription =
                    entry.kind == FlowTaskSheet.Entry.Kind.CANDIDATE
                            ? entry.candidateTemplate.prescription : entry.runStep.prescription;
            String note = entry.kind == FlowTaskSheet.Entry.Kind.CANDIDATE
                    ? entry.candidateTemplate.note : entry.runStep.note;
            steps.add(WidgetStepUiModel.requiringApp(entry.targetId, entry.title,
                    stepTexts.format(prescription.amount, note), false));
        }
        return WidgetTaskUiModel.requiringApp(sheet.task.id.value, sheet.placement.id,
                sheet.task.title, false, texts.text(R.string.action_complete_rest), steps);
    }

    private static final class WidgetSource {
        final DashboardTask task;
        final FlowTaskSheet sheet;
        private WidgetSource(DashboardTask task, FlowTaskSheet sheet) {
            this.task = task; this.sheet = sheet;
        }
        static WidgetSource task(DashboardTask value) { return new WidgetSource(value, null); }
        static WidgetSource sheet(FlowTaskSheet value) { return new WidgetSource(null, value); }
        LocalDate date() { return sheet == null
                ? task.occurrence == null ? LocalDate.MAX : task.occurrence.scheduledOn
                : sheet.placement.displayOn; }
        int slotRank() { return sheet == null ? task.displaySlot.rank : sheet.placement.slot.rank; }
        int order() { return sheet == null
                ? task.occurrence == null ? Integer.MAX_VALUE : task.occurrence.sortOrder
                : sheet.placement.sortOrder; }
        String id() { return sheet == null
                ? task.occurrence == null ? task.task.id.value : task.occurrence.id
                : sheet.placement.id; }
        String title() { return sheet == null ? task.task.title : sheet.task.title; }
    }
}

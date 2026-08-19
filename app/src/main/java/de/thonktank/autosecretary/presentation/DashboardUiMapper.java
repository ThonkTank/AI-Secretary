package de.thonktank.autosecretary.presentation;

import de.thonktank.autosecretary.TodayUiModel;
import de.thonktank.autosecretary.R;
import de.thonktank.autosecretary.TaskSnapshot;
import de.thonktank.autosecretary.domain.model.Dashboard;
import de.thonktank.autosecretary.domain.model.DashboardTask;
import de.thonktank.autosecretary.domain.model.OccurrenceStep;
import de.thonktank.autosecretary.domain.model.Recurrence;
import de.thonktank.autosecretary.domain.model.RewardPolicy;
import de.thonktank.autosecretary.domain.model.Task;
import de.thonktank.autosecretary.domain.model.TaskSlot;
import de.thonktank.autosecretary.domain.model.ComboProgress;
import de.thonktank.autosecretary.domain.model.XpProgress;
import de.thonktank.autosecretary.domain.model.StepAmountKind;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public final class DashboardUiMapper {
    private final UiTextProvider texts;
    private final StepTextFormatter stepTexts;

    public DashboardUiMapper(UiTextProvider texts) {
        this.texts = texts;
        this.stepTexts = new StepTextFormatter(texts);
    }

    public TodayUiModel map(Dashboard dashboard, LocalDate today) {
        List<TaskSnapshot> snapshots = new ArrayList<>();
        for (DashboardTask item : dashboard.tasks) snapshots.add(snapshot(item, today, dashboard));
        TaskSnapshot focus = null;
        for (TaskSnapshot task : snapshots) if (!task.done) { focus = task; break; }
        return new TodayUiModel(dashboard.xp, new XpProgress(dashboard.xp), snapshots, focus);
    }

    private TaskSnapshot snapshot(DashboardTask item, LocalDate today, Dashboard dashboard) {
        Task task = item.task;
        List<TaskStepUiModel> steps = new ArrayList<>();
        int remaining = 0;
        String next = task.conditionText;
        for (OccurrenceStep step : item.steps) {
            boolean done = item.done || step.done;
            ComboProgress stepCombo = dashboard.combos.get(step.comboOwnerId);
            int stepStage = stepCombo == null ? 0 : stepCombo.level();
            int claimable = RewardPolicy.stepXp(stepCombo);
            SetProgressUiModel setProgress = step.amountKind == StepAmountKind.SETS_REPS
                    && step.plannedSets != null && step.plannedReps != null
                    ? new SetProgressUiModel(step.plannedSets, step.plannedReps,
                            step.note, step.actualRepetitions)
                    : null;
            steps.add(new TaskStepUiModel(step.id, step.text,
                    stepTexts.format(step.amountKind, step.plannedSets, step.plannedReps,
                            step.plannedDurationSeconds, step.note),
                    done, setProgress, stepStage, claimable, item.earnedXp(step.id)));
            if (!done) {
                remaining++;
                if (remaining == 1) next = step.text;
            }
        }
        if (next == null || next.isEmpty())
            next = texts.text(steps.isEmpty() ? R.string.next_mark_done : R.string.next_all_done);
        LocalDate due = task.deadlineOn == null || item.occurrence == null
                ? item.occurrence == null ? null : item.occurrence.scheduledOn : task.deadlineOn;
        boolean overdue = !item.done && due != null && due.isBefore(today);
        ComboProgress taskCombo = dashboard.combos.get(ComboProgress.taskOwner(task.id));
        int taskStage = taskCombo == null ? 0 : taskCombo.level();
        int collected = 0, projected = 0;
        for (TaskStepUiModel step : steps) {
            collected += step.earnedXp;
            projected += step.done ? step.earnedXp : step.claimableXp;
        }
        int claimable;
        if (steps.isEmpty()) {
            long late = item.occurrence == null ? 0
                    : RewardPolicy.lateDays(task, item.occurrence, today);
            claimable = RewardPolicy.singleTaskXp(late, taskCombo);
        } else claimable = RewardPolicy.routineXp(projected, taskCombo);
        TaskSlot displaySlot = item.occurrence == null ? task.slot : item.occurrence.slot;
        return new TaskSnapshot(task.id.value, item.occurrence == null ? "" : item.occurrence.id,
                task.title, displaySlot, softTime(displaySlot, task.ongoing), next, task.recurrence,
                steps, remaining, !task.conditionText.isEmpty(), task.ongoing, item.done, overdue,
                taskStage, task.displayOrder, claimable, item.done ? 0 : collected,
                item.awardedXp,
                !item.done && !steps.isEmpty() && remaining == 0 && collected > 0,
                item.done && item.occurrence != null);
    }

    public String softTime(TaskSlot slot, boolean ongoing) {
        if (ongoing) return texts.text(R.string.soft_time_ongoing);
        if (slot == TaskSlot.MORNING) return texts.text(R.string.soft_time_morning);
        if (slot == TaskSlot.MIDDAY) return texts.text(R.string.soft_time_midday);
        if (slot == TaskSlot.EVENING) return texts.text(R.string.soft_time_evening);
        return texts.text(R.string.soft_time_later);
    }
}

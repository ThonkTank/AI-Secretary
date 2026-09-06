package de.thonktank.autosecretary.presentation;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

import de.thonktank.autosecretary.R;
import de.thonktank.autosecretary.domain.model.ComboProgress;
import de.thonktank.autosecretary.domain.model.Dashboard;
import de.thonktank.autosecretary.domain.model.DashboardTask;
import de.thonktank.autosecretary.domain.model.OccurrenceStep;
import de.thonktank.autosecretary.domain.model.OccurrenceKind;
import de.thonktank.autosecretary.domain.model.Recurrence;
import de.thonktank.autosecretary.domain.model.RewardBreakdown;
import de.thonktank.autosecretary.domain.model.RewardPolicy;
import de.thonktank.autosecretary.domain.model.StepAmount;
import de.thonktank.autosecretary.domain.model.Task;
import de.thonktank.autosecretary.domain.model.TaskSlot;
import de.thonktank.autosecretary.domain.model.XpProgress;
import de.thonktank.autosecretary.domain.model.FlowDelayPolicy;
import de.thonktank.autosecretary.domain.model.FlowRunSummary;
import de.thonktank.autosecretary.domain.model.StepFlowRunState;
import de.thonktank.autosecretary.domain.model.TrainingContext;
import de.thonktank.autosecretary.domain.model.TrainingLoadRequest;
import de.thonktank.autosecretary.presentation.today.CompletedTaskUiModel;
import de.thonktank.autosecretary.presentation.today.FocusStepUiModel;
import de.thonktank.autosecretary.presentation.today.FocusTaskUiModel;
import de.thonktank.autosecretary.presentation.today.RepetitionProgressUiModel;
import de.thonktank.autosecretary.presentation.today.RewardTextFormatter;
import de.thonktank.autosecretary.presentation.today.StepExecutionUiAction;
import de.thonktank.autosecretary.presentation.today.TaskActionTarget;
import de.thonktank.autosecretary.presentation.today.TimelineItemUiModel;
import de.thonktank.autosecretary.presentation.today.TimelineStepUiModel;
import de.thonktank.autosecretary.presentation.today.TimelineTaskUiModel;
import de.thonktank.autosecretary.presentation.today.TodayUiModel;
import de.thonktank.autosecretary.presentation.today.XpVesselUiModel;
import de.thonktank.autosecretary.presentation.today.TrainingPromptUiModel;

public final class DashboardUiMapper {
    private final UiTextProvider texts;
    private final StepTextFormatter stepTexts;
    private final RewardTextFormatter rewardTexts;

    public DashboardUiMapper(UiTextProvider texts) {
        this(texts, new RewardTextFormatter(Locale.GERMANY));
    }

    public DashboardUiMapper(UiTextProvider texts, RewardTextFormatter rewardTexts) {
        if (texts == null || rewardTexts == null)
            throw new IllegalArgumentException("Dashboard formatters are required");
        this.texts = texts;
        this.stepTexts = new StepTextFormatter(texts);
        this.rewardTexts = rewardTexts;
    }

    public TodayUiModel map(Dashboard dashboard, LocalDate today) {
        DashboardTask focusSource = null;
        DashboardTask fallbackFocus = null;
        Set<String> actionableIds = new LinkedHashSet<>();
        for (DashboardTask item : dashboard.tasks) {
            if (!item.done) {
                if (fallbackFocus == null) fallbackFocus = item;
                if (canOwnFocus(item)) {
                    actionableIds.add(stableId(item));
                    if (focusSource == null) focusSource = item;
                }
            }
        }
        if (focusSource == null) focusSource = fallbackFocus;

        FocusTaskUiModel focus = focusSource == null ? null
                : focus(focusSource, today, dashboard, actionableIds.size() > 1);
        String focusId = focusSource == null ? null : stableId(focusSource);
        List<TimelineItemUiModel> timeline = new ArrayList<>();
        List<CompletedTaskUiModel> completed = new ArrayList<>();
        for (DashboardTask item : dashboard.tasks) {
            if (item.done) {
                if (item.occurrence != null)
                    completed.add(CompletedTaskUiModel.of(item.occurrence.id, item.task.title,
                            item.awardedXp, true));
            } else if (!stableId(item).equals(focusId)) {
                timeline.add(TimelineItemUiModel.task(timeline(item, today, dashboard)));
            }
        }
        return new TodayUiModel(new XpProgress(dashboard.xp), focus,
                timeline, completed, dashboard.flowRuns);
    }

    private static boolean canOwnFocus(DashboardTask item) {
        if (item.occurrence == null || item.occurrence.kind != OccurrenceKind.FLOW_SHEET)
            return true;
        for (OccurrenceStep step : item.steps) if (!step.done) return true;
        return false;
    }

    private FocusTaskUiModel focus(DashboardTask item, LocalDate today, Dashboard dashboard,
                                   boolean allowDefer) {
        Task task = item.task;
        List<FocusStepUiModel> steps = focusSteps(item, dashboard);
        int remaining = 0;
        String next = task.conditionText;
        for (FocusStepUiModel step : steps) {
            if (!step.isDone()) {
                remaining++;
                if (remaining == 1) next = step.title;
            }
        }
        if (next == null || next.isEmpty())
            next = texts.text(steps.isEmpty() ? R.string.next_mark_done : R.string.next_all_done);
        ComboProgress taskCombo = dashboard.combos.get(ComboProgress.taskOwner(task.id));
        int collected = collectedXp(steps);
        int planned = plannedXp(steps);
        RewardBreakdown reward = taskReward(item, today, taskCombo, steps, collected);
        int done = steps.size() - remaining;
        TaskActionTarget target = actionTarget(item);
        return FocusTaskUiModel.builder(target)
                .nextAction(next)
                .steps(steps, remaining)
                .ongoing(task.ongoing)
                .overdue(overdue(item, today))
                .backlogCount(item.backlogCount)
                .allowDefer(allowDefer && !item.flowAggregate)
                .flowAggregate(item.flowAggregate)
                .harvestReady(!item.flowAggregate && !steps.isEmpty() && collected > 0)
                .reward(reward, XpVesselUiModel.quantitative(reward, done, steps.size(),
                        collected, planned, !steps.isEmpty() && collected > 0, rewardTexts))
                .build();
    }

    private TimelineTaskUiModel timeline(DashboardTask item, LocalDate today,
                                         Dashboard dashboard) {
        List<FocusStepUiModel> focusSteps = focusSteps(item, dashboard);
        List<TimelineStepUiModel> steps = new ArrayList<>();
        for (FocusStepUiModel step : focusSteps)
            steps.add(TimelineStepUiModel.completion(step.isDone()));
        ComboProgress taskCombo = dashboard.combos.get(ComboProgress.taskOwner(item.task.id));
        RewardBreakdown reward = taskReward(item, today, taskCombo, focusSteps,
                collectedXp(focusSteps));
        TaskSlot slot = item.displaySlot;
        return TimelineTaskUiModel.of(actionTarget(item), item.task.id.value,
                item.occurrence == null ? "" : item.occurrence.id, item.task.title, slot,
                softTime(slot, item.task.ongoing), steps, !item.task.conditionText.isEmpty(),
                overdue(item, today), item.task.catalogOrder, reward);
    }

    private List<FocusStepUiModel> focusSteps(DashboardTask item, Dashboard dashboard) {
        List<FocusStepUiModel> steps = new ArrayList<>();
        for (OccurrenceStep step : item.steps) {
            if (item.done && !step.done) continue;
            boolean done = item.done || step.done;
            ComboProgress combo = dashboard.combos.get(step.comboOwnerId);
            RewardBreakdown reward = RewardPolicy.step(combo);
            RepetitionProgressUiModel repetition = repetition(step);
            StepExecutionUiAction action;
            FlowRunSummary flow = item.flowRunByStepId.get(step.id);
            if (done) action = StepExecutionUiAction.none();
            else if (repetition != null)
                action = StepExecutionUiAction.submitRepetition(step.id);
            else if (flow != null && flow.delayAfter != null
                    && flow.delayAfter.mode == FlowDelayPolicy.Mode.REMEMBER_LAST)
                action = StepExecutionUiAction.toggleWithDelay(step.id,
                        flow.delayAfter.proposedDelayMillis());
            else action = StepExecutionUiAction.toggle(step.id);
            int earnedXp = item.earnedXp(step.id);
            int plannedXp = item.plannedXp(step.id,
                    earnedXp > 0 ? earnedXp : reward.resultXp);
            String title = flow != null && flow.currentPosition > 0
                    ? flow.seedTitle + ": " + step.text : step.text;
            FocusStepUiModel mapped = FocusStepUiModel.executable(step.id, title,
                    stepTexts.compactAmount(step.prescription.amount), step.note, done, action,
                    repetition, reward, earnedXp, plannedXp);
            if (step.prescription.amount instanceof StepAmount.Duration)
                mapped = mapped.withDurationSeconds(
                        ((StepAmount.Duration) step.prescription.amount).seconds);
            if (flow != null) mapped = mapped.withContextLabel(texts.text(
                    flow.state == StepFlowRunState.PENDING_START
                            ? R.string.flow_step_start : R.string.flow_step_running));
            TrainingContext training = step.sourceTemplateId == null ? null
                    : dashboard.trainingContexts.get(step.sourceTemplateId);
            TrainingPromptUiModel prompt = training == null ? null : trainingPrompt(training);
            if (prompt != null) mapped = mapped.withTrainingPrompt(prompt);
            steps.add(mapped);
        }
        return steps;
    }

    private TrainingPromptUiModel trainingPrompt(TrainingContext value) {
        TrainingLoadRequest request = value.openLoadRequest;
        return request == null ? null : new TrainingPromptUiModel(value.templateId,
                request.direction, request.currentLoad);
    }

    private static RepetitionProgressUiModel repetition(OccurrenceStep step) {
        if (step.prescription.amount instanceof StepAmount.SetsReps) {
            StepAmount.SetsReps amount = (StepAmount.SetsReps) step.prescription.amount;
            return RepetitionProgressUiModel.trainingSets(amount.sets, amount.repetitions,
                    step.repetitionProgress.repetitions(), step.prescription.plannedLoad(),
                    step.prescription.targetRir());
        }
        if (step.prescription.amount instanceof StepAmount.Repetitions)
            return RepetitionProgressUiModel.single(
                    ((StepAmount.Repetitions) step.prescription.amount).repetitions,
                    step.repetitionProgress.repetitions());
        return null;
    }

    private RewardBreakdown taskReward(DashboardTask item, LocalDate today,
                                       ComboProgress taskCombo, List<FocusStepUiModel> steps,
                                       int collected) {
        if (steps.isEmpty()) {
            long late = item.occurrence == null ? 0
                    : RewardPolicy.lateDays(item.task, item.occurrence, today);
            return RewardPolicy.singleTask(late, taskCombo);
        }
        return RewardPolicy.routine(collected, taskCombo);
    }

    private static int collectedXp(List<FocusStepUiModel> steps) {
        int collected = 0;
        for (FocusStepUiModel step : steps) collected += step.earnedXp;
        return collected;
    }

    private static int plannedXp(List<FocusStepUiModel> steps) {
        int planned = 0;
        for (FocusStepUiModel step : steps) planned += step.plannedXp;
        return planned;
    }

    private static String stableId(DashboardTask item) {
        return item.occurrence == null ? "task:" + item.task.id.value
                : "occurrence:" + item.occurrence.id;
    }

    private static TaskActionTarget actionTarget(DashboardTask item) {
        return TaskActionTarget.of(item.task.id.value,
                item.occurrence == null ? "" : item.occurrence.id, item.task.title,
                item.displaySlot, item.task.recurrence != Recurrence.ONCE,
                !item.task.conditionText.isEmpty());
    }

    private static boolean overdue(DashboardTask item, LocalDate today) {
        LocalDate due = item.task.deadlineOn == null || item.occurrence == null
                ? item.occurrence == null ? null : item.occurrence.scheduledOn
                : item.task.deadlineOn;
        return !item.done && due != null && due.isBefore(today);
    }

    public String softTime(TaskSlot slot, boolean ongoing) {
        if (ongoing) return texts.text(R.string.soft_time_ongoing);
        if (slot == TaskSlot.MORNING) return texts.text(R.string.soft_time_morning);
        if (slot == TaskSlot.MIDDAY) return texts.text(R.string.soft_time_midday);
        if (slot == TaskSlot.EVENING) return texts.text(R.string.soft_time_evening);
        return texts.text(R.string.soft_time_later);
    }
}

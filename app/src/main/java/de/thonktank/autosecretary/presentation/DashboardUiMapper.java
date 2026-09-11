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
import de.thonktank.autosecretary.domain.model.FlowTaskSheet;
import de.thonktank.autosecretary.domain.model.TrainingContext;
import de.thonktank.autosecretary.domain.model.TrainingLoadRequest;
import de.thonktank.autosecretary.presentation.today.CompletedTaskUiModel;
import de.thonktank.autosecretary.presentation.today.FocusStepUiModel;
import de.thonktank.autosecretary.presentation.today.FocusTaskUiModel;
import de.thonktank.autosecretary.presentation.today.RepetitionProgressUiModel;
import de.thonktank.autosecretary.presentation.today.RewardTextFormatter;
import de.thonktank.autosecretary.presentation.today.StepExecutionUiAction;
import de.thonktank.autosecretary.presentation.today.TaskActionTarget;
import de.thonktank.autosecretary.presentation.today.TodayItemTarget;
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
        List<TodaySource> open = new ArrayList<>();
        for (DashboardTask item : dashboard.tasks) {
            if (!item.done) open.add(TodaySource.task(item));
        }
        for (FlowTaskSheet sheet : dashboard.flowTaskSheets) open.add(TodaySource.sheet(sheet));
        open.sort(java.util.Comparator.comparing((TodaySource value) -> value.date())
                .thenComparingInt(value -> value.slot().rank)
                .thenComparingInt(TodaySource::order)
                .thenComparing(TodaySource::id));
        TodaySource focusSource = open.isEmpty() ? null : open.get(0);

        FocusTaskUiModel focus = focusSource == null ? null
                : focusSource.sheet == null
                ? focus(focusSource.task, today, dashboard, open.size() > 1)
                : focus(focusSource.sheet, dashboard, open.size() > 1);
        String focusId = focusSource == null ? null : focusSource.id();
        List<TimelineItemUiModel> timeline = new ArrayList<>();
        List<CompletedTaskUiModel> completed = new ArrayList<>();
        for (TodaySource item : open) if (!item.id().equals(focusId))
            timeline.add(TimelineItemUiModel.task(item.sheet == null
                    ? timeline(item.task, today, dashboard)
                    : timeline(item.sheet, dashboard)));
        for (DashboardTask item : dashboard.tasks) {
            if (item.done) {
                if (item.occurrence != null)
                    completed.add(CompletedTaskUiModel.of(item.occurrence.id, item.task.title,
                            item.awardedXp, true));
            }
        }
        return new TodayUiModel(new XpProgress(dashboard.xp), focus,
                timeline, completed, dashboard.flowRuns);
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
                .allowDefer(allowDefer)
                .allowBulkComplete(true)
                .harvestReady(!steps.isEmpty() && collected > 0)
                .reward(reward, XpVesselUiModel.quantitative(reward, done, steps.size(),
                        collected, planned, !steps.isEmpty() && collected > 0, rewardTexts))
                .build();
    }

    private FocusTaskUiModel focus(FlowTaskSheet sheet, Dashboard dashboard,
                                   boolean allowDefer) {
        List<FocusStepUiModel> steps = flowSteps(sheet, dashboard);
        int remaining = steps.size();
        String next = steps.isEmpty() ? texts.text(R.string.next_all_done) : steps.get(0).title;
        RewardBreakdown reward = RewardPolicy.routine(0, null);
        return FocusTaskUiModel.builder(TaskActionTarget.of(sheet.task.id.value,
                        TodayItemTarget.flowTaskSheet(sheet.placement.id), sheet.task.title,
                        sheet.placement.slot, sheet.task.recurrence != Recurrence.ONCE, false))
                .nextAction(next)
                .steps(steps, remaining)
                .ongoing(false)
                .overdue(false)
                .allowDefer(allowDefer)
                .allowBulkComplete(false)
                .waits(flowWaits(sheet))
                .harvestReady(false)
                .reward(reward, XpVesselUiModel.quantitative(reward, 0, steps.size(),
                        0, plannedXp(steps), false, rewardTexts))
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
                item.task.title, slot,
                softTime(slot, item.task.ongoing), steps, !item.task.conditionText.isEmpty(),
                overdue(item, today), item.task.catalogOrder, reward);
    }

    private TimelineTaskUiModel timeline(FlowTaskSheet sheet, Dashboard dashboard) {
        List<TimelineStepUiModel> steps = new ArrayList<>();
        for (int index = 0; index < sheet.entries.size(); index++)
            steps.add(TimelineStepUiModel.completion(false));
        return TimelineTaskUiModel.of(TaskActionTarget.of(sheet.task.id.value,
                        TodayItemTarget.flowTaskSheet(sheet.placement.id), sheet.task.title,
                        sheet.placement.slot, sheet.task.recurrence != Recurrence.ONCE, false),
                sheet.task.id.value, sheet.task.title, sheet.placement.slot,
                softTime(sheet.placement.slot, false), steps, false, false,
                sheet.placement.sortOrder, RewardBreakdown.fromStage(0, 0));
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
            if (done) action = StepExecutionUiAction.none();
            else if (repetition != null)
                action = StepExecutionUiAction.submitRepetition(step.id);
            else action = StepExecutionUiAction.toggle(step.id);
            int earnedXp = item.earnedXp(step.id);
            int plannedXp = item.plannedXp(step.id,
                    earnedXp > 0 ? earnedXp : reward.resultXp);
            FocusStepUiModel mapped = FocusStepUiModel.executable(step.id, step.text,
                    stepTexts.compactAmount(step.prescription.amount), step.note, done, action,
                    repetition, reward, earnedXp, plannedXp);
            if (step.prescription.amount instanceof StepAmount.Duration)
                mapped = mapped.withDurationSeconds(
                        ((StepAmount.Duration) step.prescription.amount).seconds);
            TrainingContext training = step.sourceTemplateId == null ? null
                    : dashboard.trainingContexts.get(step.sourceTemplateId);
            TrainingPromptUiModel prompt = training == null ? null : trainingPrompt(training);
            if (prompt != null) mapped = mapped.withTrainingPrompt(prompt);
            steps.add(mapped);
        }
        return steps;
    }

    private List<FocusStepUiModel> flowSteps(FlowTaskSheet sheet, Dashboard dashboard) {
        List<FocusStepUiModel> result = new ArrayList<>();
        for (FlowTaskSheet.Entry entry : sheet.entries) {
            if (entry.kind == FlowTaskSheet.Entry.Kind.COLLECTION) {
                RewardBreakdown reward = RewardPolicy.routine(Math.toIntExact(entry.finalTau),
                        dashboard.combos.get(ComboProgress.taskOwner(sheet.task.id)));
                result.add(FocusStepUiModel.executable(entry.targetId, entry.title,
                        reward.resultXp + " Tau", "", false, StepExecutionUiAction.collectFlow(entry.targetId),
                        null, reward, 0, reward.resultXp));
                continue;
            }
            de.thonktank.autosecretary.domain.model.StepPrescription prescription;
            String note;
            String id;
            StepExecutionUiAction action;
            if (entry.kind == FlowTaskSheet.Entry.Kind.CANDIDATE) {
                prescription = entry.candidateTemplate.prescription;
                note = entry.candidateTemplate.note;
                id = entry.targetId;
                action = entry.waitAfter != null
                        ? entry.waitAfter.mode == FlowDelayPolicy.Mode.REMEMBER_LAST
                            ? StepExecutionUiAction.startFlowCandidateWithDelay(entry.targetId, entry.waitAfter.proposedDelayMillis())
                            : StepExecutionUiAction.startFlowCandidate(entry.targetId)
                        : prescription.amount instanceof StepAmount.Duration
                        ? StepExecutionUiAction.startFlowCandidateWithDelay(entry.targetId,
                        ((StepAmount.Duration) prescription.amount).seconds * 1_000L)
                        : StepExecutionUiAction.startFlowCandidate(entry.targetId);
            } else {
                prescription = entry.runStep.prescription;
                note = entry.runStep.note;
                id = entry.run.steps.isEmpty() ? entry.runStep.id : entry.targetId;
                action = entry.waitAfter != null && entry.waitAfter.mode == FlowDelayPolicy.Mode.REMEMBER_LAST
                        ? StepExecutionUiAction.toggleFlowRunStepWithDelay(id, entry.waitAfter.proposedDelayMillis())
                        : StepExecutionUiAction.toggleFlowRunStep(id);
            }
            ComboProgress combo = dashboard.combos.get(ComboProgress.stepOwner(
                    entry.kind == FlowTaskSheet.Entry.Kind.CANDIDATE
                            ? entry.candidateTemplate.id : entry.runStep.sourceTemplateId));
            RewardBreakdown reward = entry.finalTau == null ? RewardBreakdown.fromStage(0, 0)
                    : RewardPolicy.routine(Math.toIntExact(entry.finalTau),
                    dashboard.combos.get(ComboProgress.taskOwner(sheet.task.id)));
            String amount = stepTexts.compactAmount(prescription.amount);
            if (entry.finalTau != null) amount = (amount.isEmpty() ? "" : amount + " · ") + reward.resultXp + " Tau";
            FocusStepUiModel mapped = FocusStepUiModel.executable(id, entry.title,
                    amount, note, false, action,
                    null, reward, 0, reward.resultXp);
            result.add(mapped);
        }
        return result;
    }

    private List<de.thonktank.autosecretary.presentation.today.FlowWaitUiModel> flowWaits(FlowTaskSheet sheet) {
        List<de.thonktank.autosecretary.presentation.today.FlowWaitUiModel> result = new ArrayList<>();
        for (FlowRunSummary run : sheet.running) for (FlowRunSummary.Step step : run.steps)
            if (step.state == de.thonktank.autosecretary.domain.model.FlowGraphRun.State.WAITING_TIME
                    || step.state == de.thonktank.autosecretary.domain.model.FlowGraphRun.State.WAITING_RESOURCE) {
                String title = run.seedTitle.equals(step.title) ? run.seedTitle : run.seedTitle + ": " + step.title;
                result.add(new de.thonktank.autosecretary.presentation.today.FlowWaitUiModel(
                        run.id, step.waitId, title, step.readyAtEpochMillis));
            }
        return result;
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
                item.occurrence == null ? TodayItemTarget.task(item.task.id.value)
                        : TodayItemTarget.occurrence(item.occurrence.id), item.task.title,
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

    private static final class TodaySource {
        final DashboardTask task;
        final FlowTaskSheet sheet;

        private TodaySource(DashboardTask task, FlowTaskSheet sheet) {
            this.task = task; this.sheet = sheet;
        }

        static TodaySource task(DashboardTask value) { return new TodaySource(value, null); }
        static TodaySource sheet(FlowTaskSheet value) { return new TodaySource(null, value); }
        String id() { return sheet == null ? stableId(task) : "flow-sheet:" + sheet.placement.id; }
        LocalDate date() { return sheet == null
                ? task.occurrence == null ? LocalDate.MAX : task.occurrence.scheduledOn
                : sheet.placement.displayOn; }
        TaskSlot slot() { return sheet == null ? task.displaySlot : sheet.placement.slot; }
        int order() { return sheet == null
                ? task.occurrence == null ? Integer.MAX_VALUE : task.occurrence.sortOrder
                : sheet.placement.sortOrder; }
    }
}

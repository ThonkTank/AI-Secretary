package de.thonktank.autosecretary;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import de.thonktank.autosecretary.domain.model.Recurrence;
import de.thonktank.autosecretary.domain.model.RewardBreakdown;
import de.thonktank.autosecretary.domain.model.TaskSlot;
import de.thonktank.autosecretary.presentation.today.FocusStepUiModel;
import de.thonktank.autosecretary.presentation.today.RewardTextFormatter;
import de.thonktank.autosecretary.presentation.today.FocusTaskUiModel;
import de.thonktank.autosecretary.presentation.today.StepExecutionUiAction;
import de.thonktank.autosecretary.presentation.today.TaskActionTarget;
import de.thonktank.autosecretary.presentation.today.TimelineStepUiModel;
import de.thonktank.autosecretary.presentation.today.TimelineTaskUiModel;
import de.thonktank.autosecretary.presentation.today.XpVesselUiModel;

/** Named, fluent focus fixtures without positional snapshot constructors. */
final class FocusTaskFixtures {
    private static final RewardTextFormatter REWARDS = new RewardTextFormatter(Locale.GERMANY);

    private FocusTaskFixtures() { }

    static Builder task(String id, String title) { return new Builder(id, title); }

    static FocusStepUiModel simpleStep(String id, String title, boolean done) {
        return step(id, title).done(done).build();
    }

    static StepBuilder step(String id, String title) { return new StepBuilder(id, title); }

    static TimelineTaskUiModel timeline(FocusTaskUiModel source, String softTime, long order) {
        List<TimelineStepUiModel> steps = new ArrayList<>();
        for (FocusStepUiModel step : source.steps)
            steps.add(TimelineStepUiModel.completion(step.isDone()));
        TaskActionTarget target = source.actionTarget;
        return TimelineTaskUiModel.of(target, target.taskId, target.occurrenceId, target.title,
                target.slot, softTime == null ? "" : softTime, steps,
                target.terminalCondition, source.overdue, order, source.reward);
    }

    static final class Builder {
        private final String id;
        private final String title;
        private String occurrenceId;
        private TaskSlot slot = TaskSlot.MORNING;
        private Recurrence recurrence = Recurrence.ONCE;
        private List<FocusStepUiModel> steps = Collections.emptyList();
        private boolean terminal;
        private boolean ongoing;
        private boolean overdue;
        private boolean allowDefer;
        private boolean harvestReady;
        private int comboStage;
        private Integer rewardBase;

        private Builder(String id, String title) {
            this.id = id;
            this.title = title;
            this.occurrenceId = id + "-today";
        }

        Builder occurrence(String value) { occurrenceId = value; return this; }
        Builder slot(TaskSlot value) { slot = value; return this; }
        Builder recurrence(Recurrence value) { recurrence = value; return this; }
        Builder steps(List<FocusStepUiModel> value) { steps = value; return this; }
        Builder terminal(boolean value) { terminal = value; return this; }
        Builder ongoing(boolean value) { ongoing = value; return this; }
        Builder overdue(boolean value) { overdue = value; return this; }
        Builder allowDefer(boolean value) { allowDefer = value; return this; }
        Builder harvestReady(boolean value) { harvestReady = value; return this; }
        Builder combo(int value) { comboStage = value; return this; }
        Builder rewardBase(int value) { rewardBase = value; return this; }

        FocusTaskUiModel build() {
            List<FocusStepUiModel> explicit = new ArrayList<>();
            int remaining = 0;
            for (FocusStepUiModel step : steps) {
                if (step.isDone()) explicit.add(step);
                else {
                    StepExecutionUiAction action = step.repetitionProgress == null
                            ? StepExecutionUiAction.toggle(step.id)
                            : StepExecutionUiAction.submitRepetition(step.id);
                    FocusStepUiModel mapped = FocusStepUiModel.executable(step.id, step.title,
                            step.amountLabel, step.note, false, action,
                            step.repetitionProgress, step.reward, step.earnedXp);
                    if (step.durationSeconds > 0)
                        mapped = mapped.withDurationSeconds(step.durationSeconds);
                    if (step.trainingContext != null)
                        mapped = mapped.withTrainingContext(step.trainingContext);
                    explicit.add(mapped);
                    remaining++;
                }
            }
            int base = rewardBase == null ? 0 : rewardBase;
            if (rewardBase == null) {
                for (FocusStepUiModel step : explicit) base += step.earnedXp;
                if (explicit.isEmpty()) base = 10;
            }
            RewardBreakdown reward = RewardBreakdown.fromStage(base, comboStage);
            boolean ready = harvestReady;
            TaskActionTarget target = TaskActionTarget.of(id, occurrenceId, title, slot,
                    recurrence != Recurrence.ONCE, terminal);
            return FocusTaskUiModel.builder(target).nextAction("Nächster Schritt")
                    .steps(explicit, remaining).ongoing(ongoing).overdue(overdue)
                    .allowDefer(allowDefer).harvestReady(ready)
                    .reward(reward, XpVesselUiModel.of(reward,
                            explicit.size() - remaining, explicit.size(), ready, REWARDS)).build();
        }
    }

    static final class StepBuilder {
        private final String id;
        private final String title;
        private String amount = "";
        private String note = "";
        private boolean done;
        private de.thonktank.autosecretary.presentation.today.RepetitionProgressUiModel repetition;
        private int comboStage;
        private int baseXp = 10;
        private Integer earnedXp;

        private StepBuilder(String id, String title) { this.id = id; this.title = title; }
        StepBuilder amount(String value) { amount = value; return this; }
        StepBuilder note(String value) { note = value; return this; }
        StepBuilder done(boolean value) { done = value; return this; }
        StepBuilder repetition(de.thonktank.autosecretary.presentation.today.RepetitionProgressUiModel value) {
            repetition = value; return this;
        }
        StepBuilder combo(int value) { comboStage = value; return this; }
        StepBuilder baseXp(int value) { baseXp = value; return this; }
        StepBuilder earnedXp(int value) { earnedXp = value; return this; }

        FocusStepUiModel build() {
            RewardBreakdown reward = RewardBreakdown.fromStage(baseXp, comboStage);
            return FocusStepUiModel.executable(id, title, amount, note,
                    done,
                    done ? StepExecutionUiAction.none()
                            : repetition == null ? StepExecutionUiAction.toggle(id)
                            : StepExecutionUiAction.submitRepetition(id),
                    repetition, reward,
                    earnedXp == null ? done ? reward.resultXp : 0 : earnedXp);
        }
    }
}

package de.thonktank.autosecretary.testing;

import java.util.List;

import de.thonktank.autosecretary.domain.model.CarryForwardReason;
import de.thonktank.autosecretary.domain.model.OccurrenceStep;
import de.thonktank.autosecretary.domain.model.RestTimerPolicy;
import de.thonktank.autosecretary.domain.model.StepActivationKind;
import de.thonktank.autosecretary.domain.model.StepAmount;
import de.thonktank.autosecretary.domain.model.StepPrescription;
import de.thonktank.autosecretary.domain.model.TaskId;
import de.thonktank.autosecretary.domain.model.TaskStepDefinition;
import de.thonktank.autosecretary.domain.model.TaskStepTemplate;
import de.thonktank.autosecretary.domain.model.TrainingAssistantConfig;
import de.thonktank.autosecretary.domain.model.TrainingAssistantPolicy;
import de.thonktank.autosecretary.domain.model.TrainingAssistantProfile;
import de.thonktank.autosecretary.domain.model.TrainingAssistantState;

/** Concise construction belongs to test data, not the production model API. */
public final class StepTestFixtures {
    private StepTestFixtures() { }

    public static TaskStepDefinition definition(String id, int position, String text,
                                                int weekdays, StepAmount amount, String note) {
        return definition(id, position, text, weekdays, 0, amount, note);
    }

    public static TaskStepDefinition definition(String id, int position, String text,
                                                int weekdays, int interval, StepAmount amount,
                                                String note) {
        return new TaskStepDefinition(id, position, text, weekdays, interval,
                StepPrescription.forAmount(amount), null, note, StepActivationKind.SCHEDULED);
    }

    public static TaskStepDefinition definition(String id, int position, String text,
                                                int weekdays, int interval, StepAmount amount,
                                                String note, StepActivationKind activation) {
        return new TaskStepDefinition(id, position, text, weekdays, interval,
                StepPrescription.forAmount(amount), null, note, activation);
    }

    public static TaskStepDefinition definition(String id, int position, String text,
                                                int weekdays, int interval, StepAmount amount,
                                                RestTimerPolicy rest, String note) {
        return new TaskStepDefinition(id, position, text, weekdays, interval,
                new StepPrescription(amount, rest, null), null, note,
                StepActivationKind.SCHEDULED);
    }

    public static TaskStepDefinition definition(String id, int position, String text,
                                                int weekdays, int interval,
                                                StepPrescription prescription,
                                                TrainingAssistantPolicy policy, String note,
                                                StepActivationKind activation) {
        return new TaskStepDefinition(id, position, text, weekdays, interval, prescription,
                policy, note, activation);
    }

    public static TaskStepTemplate template(String id, TaskId taskId, int position, String text) {
        return template(id, taskId, position, text, 0, StepAmount.none(), "");
    }

    public static TaskStepTemplate template(String id, TaskId taskId, int position, String text,
                                            int weekdays, StepAmount amount, String note) {
        return template(id, taskId, position, text, weekdays, 0, amount, note);
    }

    public static TaskStepTemplate template(String id, TaskId taskId, int position, String text,
                                            int weekdays, int interval, StepAmount amount,
                                            String note) {
        return new TaskStepTemplate(id, taskId, position, text, weekdays, interval,
                StepPrescription.forAmount(amount), null, note, StepActivationKind.SCHEDULED);
    }

    public static TaskStepTemplate template(String id, TaskId taskId, int position, String text,
                                            int weekdays, int interval, StepAmount amount,
                                            String note, StepActivationKind activation) {
        return new TaskStepTemplate(id, taskId, position, text, weekdays, interval,
                StepPrescription.forAmount(amount), null, note, activation);
    }

    public static TaskStepTemplate template(String id, TaskId taskId, int position, String text,
                                            int weekdays, int interval, StepAmount amount,
                                            RestTimerPolicy rest, String note,
                                            StepActivationKind activation) {
        return new TaskStepTemplate(id, taskId, position, text, weekdays, interval,
                new StepPrescription(amount, rest, null), null, note, activation);
    }

    public static TaskStepTemplate template(String id, TaskId taskId, int position, String text,
                                            int weekdays, int interval, StepAmount amount,
                                            RestTimerPolicy rest, TrainingAssistantConfig config,
                                            TrainingAssistantState state, String note,
                                            StepActivationKind activation) {
        TrainingAssistantProfile profile = config.enabled ? new TrainingAssistantProfile(
                new TrainingAssistantPolicy(config.minSets, config.maxSets,
                        config.minRepetitions, config.maxRepetitions,
                        config.automaticWeeklySetCeiling, config.primaryMuscle,
                        config.secondaryMuscles), state) : null;
        return new TaskStepTemplate(id, taskId, position, text, weekdays, interval,
                StepPrescription.restore(amount, rest, config.load, config.targetRir), profile,
                note, activation);
    }

    public static TaskStepTemplate template(String id, TaskId taskId, int position, String text,
                                            int weekdays, int interval,
                                            StepPrescription prescription,
                                            TrainingAssistantProfile profile, String note,
                                            StepActivationKind activation) {
        return new TaskStepTemplate(id, taskId, position, text, weekdays, interval, prescription,
                profile, note, activation);
    }

    public static OccurrenceStep occurrence(String id, String occurrenceId, int position,
                                            String text, boolean done) {
        return new OccurrenceStep(id, occurrenceId, position, text, done,
                StepPrescription.forAmount(StepAmount.none()), "", List.of(), null,
                "step:" + id, null, CarryForwardReason.NONE);
    }

    public static OccurrenceStep occurrence(String id, String occurrenceId, int position,
                                            String text, boolean done, StepAmount amount,
                                            String note, List<Integer> repetitions) {
        return occurrence(id, occurrenceId, position, text, done, amount, note, repetitions,
                null, "step:" + id);
    }

    public static OccurrenceStep occurrence(String id, String occurrenceId, int position,
                                            String text, boolean done, StepAmount amount,
                                            String note, List<Integer> repetitions,
                                            String sourceTemplateId, String comboOwnerId) {
        return new OccurrenceStep(id, occurrenceId, position, text, done,
                StepPrescription.forAmount(amount), note, repetitions, sourceTemplateId,
                comboOwnerId, null, CarryForwardReason.NONE);
    }

    public static OccurrenceStep occurrence(String id, String occurrenceId, int position,
                                            String text, boolean done,
                                            StepPrescription prescription, String note,
                                            List<Integer> repetitions, String sourceTemplateId,
                                            String comboOwnerId, String originOccurrenceId,
                                            CarryForwardReason reason) {
        return new OccurrenceStep(id, occurrenceId, position, text, done, prescription, note,
                repetitions, sourceTemplateId, comboOwnerId, originOccurrenceId, reason);
    }
}

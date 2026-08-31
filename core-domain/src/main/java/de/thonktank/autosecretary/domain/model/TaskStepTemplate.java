package de.thonktank.autosecretary.domain.model;

/** Persisted template. Assistant learning state exists only here. */
public final class TaskStepTemplate {
    public final String id;
    public final TaskId taskId;
    public final int position;
    public final String text;
    public final int weekdayMask;
    public final int intervalDays;
    public final StepPrescription prescription;
    public final TrainingAssistantProfile assistantProfile;
    public final String note;
    public final StepActivationKind activationKind;

    /** Read-only projections of the canonical grouped value. */
    public final StepAmount amount;
    public final RestTimerPolicy restTimerPolicy;

    public TaskStepTemplate(String id, TaskId taskId, int position, String text,
                            int weekdayMask, int intervalDays, StepPrescription prescription,
                            TrainingAssistantProfile assistantProfile, String note,
                            StepActivationKind activationKind) {
        if (id == null || id.isEmpty() || taskId == null)
            throw new IllegalArgumentException("Step template identity and task are required");
        TaskStepDefinition checked = new TaskStepDefinition(id, position, text, weekdayMask,
                intervalDays, prescription,
                assistantProfile == null ? null : assistantProfile.policy, note, activationKind);
        this.id = id;
        this.taskId = taskId;
        this.position = checked.position;
        this.text = checked.text;
        this.weekdayMask = checked.weekdayMask;
        this.intervalDays = checked.intervalDays;
        this.prescription = checked.prescription;
        this.assistantProfile = assistantProfile;
        this.amount = prescription.amount;
        this.restTimerPolicy = prescription.rest;
        this.note = checked.note;
        this.activationKind = checked.activationKind;
    }

    public TaskStepDefinition definition() {
        return new TaskStepDefinition(id, position, text, weekdayMask, intervalDays,
                prescription, assistantProfile == null ? null : assistantProfile.policy,
                note, activationKind);
    }

    public TaskStepTemplate withTraining(StepPrescription value,
                                         TrainingAssistantProfile profile) {
        return new TaskStepTemplate(id, taskId, position, text, weekdayMask, intervalDays,
                value, profile, note, activationKind);
    }

    public boolean assistantEnabled() { return assistantProfile != null; }

    /** Transitional engine input removed with the load-request phase. */
    public TrainingAssistantConfig legacyTrainingConfig() {
        if (assistantProfile == null) return TrainingAssistantConfig.disabled();
        TrainingAssistantPolicy policy = assistantProfile.policy;
        TrainingPrescription training = prescription.training;
        return new TrainingAssistantConfig(true, policy.minSets, policy.maxSets,
                policy.minRepetitions, policy.maxRepetitions, training.targetRir,
                training.load.unit == ResistanceLoad.Unit.LB ? 5_000 : 2_500,
                policy.automaticWeeklySetCeiling, training.load, policy.primaryMuscle,
                policy.secondaryMuscles);
    }
}

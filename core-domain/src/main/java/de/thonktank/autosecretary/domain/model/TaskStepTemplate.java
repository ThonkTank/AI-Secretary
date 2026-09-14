package de.thonktank.autosecretary.domain.model;

/** Persisted reusable step template. */
public final class TaskStepTemplate {
    public final String id;
    public final TaskId taskId;
    public final int position;
    public final String text;
    public final int weekdayMask;
    public final int intervalDays;
    public final StepPrescription prescription;
    public final String note;
    public final StepActivationKind activationKind;

    public TaskStepTemplate(String id, TaskId taskId, int position, String text,
                            int weekdayMask, int intervalDays, StepPrescription prescription,
                            String note,
                            StepActivationKind activationKind) {
        if (id == null || id.isEmpty() || taskId == null)
            throw new IllegalArgumentException("Step template identity and task are required");
        TaskStepDefinition checked = new TaskStepDefinition(id, position, text, weekdayMask,
                intervalDays, prescription, note, activationKind);
        this.id = id;
        this.taskId = taskId;
        this.position = checked.position;
        this.text = checked.text;
        this.weekdayMask = checked.weekdayMask;
        this.intervalDays = checked.intervalDays;
        this.prescription = checked.prescription;
        this.note = checked.note;
        this.activationKind = checked.activationKind;
    }

    public TaskStepDefinition definition() {
        return new TaskStepDefinition(id, position, text, weekdayMask, intervalDays,
                prescription,
                note, activationKind);
    }




}

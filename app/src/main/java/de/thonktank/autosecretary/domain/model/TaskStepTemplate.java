package de.thonktank.autosecretary.domain.model;

public final class TaskStepTemplate {
    public final String id;
    public final TaskId taskId;
    public final int position;
    public final String text;
    public final int weekdayMask;
    public final StepAmountKind amountKind;
    public final Integer plannedSets;
    public final Integer plannedReps;
    public final Integer plannedDurationSeconds;
    public final String note;

    public TaskStepTemplate(String id, TaskId taskId, int position, String text) {
        this(id, taskId, position, text, 0, StepAmountKind.NONE, null, null, null, "");
    }

    public TaskStepTemplate(String id, TaskId taskId, int position, String text,
                            int weekdayMask, StepAmountKind amountKind, Integer plannedSets,
                            Integer plannedReps, Integer plannedDurationSeconds, String note) {
        if (id == null || id.isEmpty() || taskId == null || text == null || text.trim().isEmpty())
            throw new IllegalArgumentException("Step template identity, task and text are required");
        TaskStepDefinition checked = new TaskStepDefinition(id, position, text, weekdayMask,
                amountKind, plannedSets, plannedReps, plannedDurationSeconds, note);
        this.id = id;
        this.taskId = taskId;
        this.position = checked.position;
        this.text = checked.text;
        this.weekdayMask = checked.weekdayMask;
        this.amountKind = checked.amountKind;
        this.plannedSets = checked.plannedSets;
        this.plannedReps = checked.plannedReps;
        this.plannedDurationSeconds = checked.plannedDurationSeconds;
        this.note = checked.note;
    }

    public TaskStepDefinition definition() {
        return new TaskStepDefinition(id, position, text, weekdayMask, amountKind,
                plannedSets, plannedReps, plannedDurationSeconds, note);
    }
}

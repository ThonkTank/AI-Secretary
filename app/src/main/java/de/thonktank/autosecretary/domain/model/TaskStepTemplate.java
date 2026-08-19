package de.thonktank.autosecretary.domain.model;

public final class TaskStepTemplate {
    public final String id;
    public final TaskId taskId;
    public final int position;
    public final String text;
    public final int weekdayMask;
    public final StepAmount amount;
    public final String note;

    public TaskStepTemplate(String id, TaskId taskId, int position, String text) {
        this(id, taskId, position, text, 0, StepAmount.none(), "");
    }

    public TaskStepTemplate(String id, TaskId taskId, int position, String text,
                            int weekdayMask, StepAmount amount, String note) {
        if (id == null || id.isEmpty() || taskId == null || text == null || text.trim().isEmpty())
            throw new IllegalArgumentException("Step template identity, task and text are required");
        TaskStepDefinition checked = new TaskStepDefinition(id, position, text, weekdayMask,
                amount, note);
        this.id = id;
        this.taskId = taskId;
        this.position = checked.position;
        this.text = checked.text;
        this.weekdayMask = checked.weekdayMask;
        this.amount = checked.amount;
        this.note = checked.note;
    }

    public TaskStepDefinition definition() {
        return new TaskStepDefinition(id, position, text, weekdayMask, amount, note);
    }
}

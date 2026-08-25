package de.thonktank.autosecretary.domain.model;

public final class TaskStepTemplate {
    public final String id;
    public final TaskId taskId;
    public final int position;
    public final String text;
    public final int weekdayMask;
    public final int intervalDays;
    public final StepAmount amount;
    public final RestTimerPolicy restTimerPolicy;
    public final String note;

    public TaskStepTemplate(String id, TaskId taskId, int position, String text) {
        this(id, taskId, position, text, 0, StepAmount.none(), "");
    }

    public TaskStepTemplate(String id, TaskId taskId, int position, String text,
                            int weekdayMask, StepAmount amount, String note) {
        this(id, taskId, position, text, weekdayMask, 0, amount, note);
    }

    public TaskStepTemplate(String id, TaskId taskId, int position, String text,
                            int weekdayMask, int intervalDays, StepAmount amount, String note) {
        this(id, taskId, position, text, weekdayMask, intervalDays, amount,
                RestTimerPolicy.forAmount(amount), note);
    }

    public TaskStepTemplate(String id, TaskId taskId, int position, String text,
                            int weekdayMask, int intervalDays, StepAmount amount,
                            RestTimerPolicy restTimerPolicy, String note) {
        if (id == null || id.isEmpty() || taskId == null || text == null || text.trim().isEmpty())
            throw new IllegalArgumentException("Step template identity, task and text are required");
        TaskStepDefinition checked = new TaskStepDefinition(id, position, text, weekdayMask,
                intervalDays, amount, restTimerPolicy, note);
        this.id = id;
        this.taskId = taskId;
        this.position = checked.position;
        this.text = checked.text;
        this.weekdayMask = checked.weekdayMask;
        this.intervalDays = checked.intervalDays;
        this.amount = checked.amount;
        this.restTimerPolicy = checked.restTimerPolicy;
        this.note = checked.note;
    }

    public TaskStepDefinition definition() {
        return new TaskStepDefinition(id, position, text, weekdayMask, intervalDays, amount,
                restTimerPolicy, note);
    }
}

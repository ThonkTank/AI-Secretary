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
    public final TrainingAssistantConfig trainingAssistant;
    public final TrainingAssistantState trainingState;
    public final String note;
    public final StepActivationKind activationKind;

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
                RestTimerPolicy.forAmount(amount), TrainingAssistantConfig.disabled(),
                TrainingAssistantState.disabled(), note, StepActivationKind.SCHEDULED);
    }

    public TaskStepTemplate(String id, TaskId taskId, int position, String text,
                            int weekdayMask, int intervalDays, StepAmount amount,
                            RestTimerPolicy restTimerPolicy, String note) {
        this(id, taskId, position, text, weekdayMask, intervalDays, amount, restTimerPolicy,
                TrainingAssistantConfig.disabled(), TrainingAssistantState.disabled(), note,
                StepActivationKind.SCHEDULED);
    }

    public TaskStepTemplate(String id, TaskId taskId, int position, String text,
                            int weekdayMask, int intervalDays, StepAmount amount, String note,
                            StepActivationKind activationKind) {
        this(id, taskId, position, text, weekdayMask, intervalDays, amount,
                RestTimerPolicy.forAmount(amount), TrainingAssistantConfig.disabled(),
                TrainingAssistantState.disabled(), note, activationKind);
    }

    public TaskStepTemplate(String id, TaskId taskId, int position, String text,
                            int weekdayMask, int intervalDays, StepAmount amount,
                            RestTimerPolicy restTimerPolicy, String note,
                            StepActivationKind activationKind) {
        this(id, taskId, position, text, weekdayMask, intervalDays, amount, restTimerPolicy,
                TrainingAssistantConfig.disabled(), TrainingAssistantState.disabled(), note,
                activationKind);
    }

    public TaskStepTemplate(String id, TaskId taskId, int position, String text,
                            int weekdayMask, int intervalDays, StepAmount amount,
                            RestTimerPolicy restTimerPolicy,
                            TrainingAssistantConfig trainingAssistant,
                            TrainingAssistantState trainingState, String note) {
        this(id, taskId, position, text, weekdayMask, intervalDays, amount, restTimerPolicy,
                trainingAssistant, trainingState, note, StepActivationKind.SCHEDULED);
    }

    public TaskStepTemplate(String id, TaskId taskId, int position, String text,
                            int weekdayMask, int intervalDays, StepAmount amount,
                            RestTimerPolicy restTimerPolicy,
                            TrainingAssistantConfig trainingAssistant,
                            TrainingAssistantState trainingState, String note,
                            StepActivationKind activationKind) {
        if (id == null || id.isEmpty() || taskId == null || text == null || text.trim().isEmpty())
            throw new IllegalArgumentException("Step template identity, task and text are required");
        TaskStepDefinition checked = new TaskStepDefinition(id, position, text, weekdayMask,
                intervalDays, amount, restTimerPolicy, trainingAssistant, note, activationKind);
        this.id = id;
        this.taskId = taskId;
        this.position = checked.position;
        this.text = checked.text;
        this.weekdayMask = checked.weekdayMask;
        this.intervalDays = checked.intervalDays;
        this.amount = checked.amount;
        this.restTimerPolicy = checked.restTimerPolicy;
        this.trainingAssistant = checked.trainingAssistant;
        this.trainingState = trainingState == null
                ? checked.trainingAssistant.enabled ? TrainingAssistantState.calibrating()
                : TrainingAssistantState.disabled() : trainingState;
        this.note = checked.note;
        this.activationKind = checked.activationKind;
    }

    public TaskStepDefinition definition() {
        return new TaskStepDefinition(id, position, text, weekdayMask, intervalDays, amount,
                restTimerPolicy, trainingAssistant, note, activationKind);
    }

    public TaskStepTemplate withTraining(StepAmount value, TrainingAssistantConfig config,
                                         TrainingAssistantState state) {
        return new TaskStepTemplate(id, taskId, position, text, weekdayMask, intervalDays,
                value, restTimerPolicy, config, state, note, activationKind);
    }
}

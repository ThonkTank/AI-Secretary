package de.thonktank.autosecretary.domain.model;

/** One immutable operation proposed by the chat agent. */
public final class AgentTaskOperation {
    public enum Kind { CREATE, UPDATE, DELETE }

    public final Kind kind;
    public final TaskId taskId;
    public final String expectedFingerprint;
    public final TaskDefinition definition;

    private AgentTaskOperation(Kind kind, TaskId taskId, String expectedFingerprint,
                               TaskDefinition definition) {
        this.kind = kind;
        this.taskId = taskId;
        this.expectedFingerprint = expectedFingerprint;
        this.definition = definition;
        validate();
    }

    public static AgentTaskOperation create(TaskDefinition definition) {
        return new AgentTaskOperation(Kind.CREATE, null, null, definition);
    }

    public static AgentTaskOperation update(TaskId id, String fingerprint,
                                            TaskDefinition definition) {
        return new AgentTaskOperation(Kind.UPDATE, id, fingerprint, definition);
    }

    public static AgentTaskOperation delete(TaskId id, String fingerprint) {
        return new AgentTaskOperation(Kind.DELETE, id, fingerprint, null);
    }

    private void validate() {
        if (kind == null) throw new IllegalArgumentException("Agent operation kind is required");
        if (kind == Kind.CREATE) {
            if (taskId != null || expectedFingerprint != null || definition == null)
                throw new IllegalArgumentException("Create must contain only a definition");
        } else {
            if (taskId == null || expectedFingerprint == null
                    || expectedFingerprint.trim().isEmpty())
                throw new IllegalArgumentException("Existing task identity and fingerprint are required");
            if ((kind == Kind.UPDATE) != (definition != null))
                throw new IllegalArgumentException("Only update contains a definition");
        }
        if (definition != null)
            for (TaskStepDefinition step : definition.steps)
                if (step.activationKind != StepActivationKind.SCHEDULED)
                    throw new IllegalArgumentException("Agent cannot create flow follow-up steps");
    }
}

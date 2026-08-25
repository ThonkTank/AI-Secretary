package de.thonktank.autosecretary;

/** Closed input boundary for flow setup. */
public final class FlowSetupAction {
    public enum Kind { SELECT_TASK, UPDATE_DRAFT, SAVE_RESOURCE, SAVE, ACKNOWLEDGE_FEEDBACK }

    public final Kind kind;
    public final int taskIndex;
    public final String id;
    public final String name;
    public final int capacity;
    public final FlowSetupDraft draft;
    public final long feedbackId;

    private FlowSetupAction(Kind kind, int taskIndex, String id, String name, int capacity,
                            FlowSetupDraft draft, long feedbackId) {
        this.kind = kind;
        this.taskIndex = taskIndex;
        this.id = id;
        this.name = name;
        this.capacity = capacity;
        this.draft = draft;
        this.feedbackId = feedbackId;
    }

    public static FlowSetupAction selectTask(int index) {
        if (index < 0) throw new IllegalArgumentException("Task index is required");
        return new FlowSetupAction(Kind.SELECT_TASK, index, null, null, 0, null, 0L);
    }

    public static FlowSetupAction updateDraft(FlowSetupDraft draft) {
        if (draft == null) throw new IllegalArgumentException("Flow draft is required");
        return new FlowSetupAction(Kind.UPDATE_DRAFT, -1, null, null, 0, draft, 0L);
    }

    public static FlowSetupAction saveResource(String id, String name, int capacity) {
        if (name == null || name.trim().isEmpty() || capacity < 1)
            throw new IllegalArgumentException("Capacity resource is incomplete");
        return new FlowSetupAction(Kind.SAVE_RESOURCE, -1, id, name, capacity, null, 0L);
    }

    public static FlowSetupAction save() {
        return new FlowSetupAction(Kind.SAVE, -1, null, null, 0, null, 0L);
    }

    public static FlowSetupAction acknowledgeFeedback(long id) {
        return new FlowSetupAction(Kind.ACKNOWLEDGE_FEEDBACK, -1, null, null, 0, null, id);
    }
}

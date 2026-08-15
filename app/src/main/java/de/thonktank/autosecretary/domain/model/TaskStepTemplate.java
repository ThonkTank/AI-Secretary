package de.thonktank.autosecretary.domain.model;

public final class TaskStepTemplate {
    public final String id;
    public final TaskId taskId;
    public final int position;
    public final String text;

    public TaskStepTemplate(String id, TaskId taskId, int position, String text) {
        if (id == null || id.isEmpty() || taskId == null || text == null || text.trim().isEmpty())
            throw new IllegalArgumentException("Step template identity, task and text are required");
        this.id = id;
        this.taskId = taskId;
        this.position = position;
        this.text = text.trim();
    }
}

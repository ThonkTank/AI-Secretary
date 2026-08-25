package de.thonktank.autosecretary.presentation.navigation;

import androidx.annotation.Nullable;

import de.thonktank.autosecretary.domain.model.TaskId;
import de.thonktank.autosecretary.domain.model.TaskStepId;

/** Immutable application destination independent from the current Android navigation host. */
public abstract class AppDestination {
    private AppDestination() { }

    public static final class TaskEditor extends AppDestination {
        public enum Entrance { DIRECT, HEADER_FLIGHT }

        @Nullable public final TaskId taskId;
        @Nullable public final TaskStepId stepId;
        public final boolean addStep;
        public final Entrance entrance;

        private TaskEditor(@Nullable TaskId taskId, @Nullable TaskStepId stepId,
                           boolean addStep, Entrance entrance) {
            if (stepId != null && taskId == null)
                throw new IllegalArgumentException("A step destination needs a task");
            if (addStep && taskId == null)
                throw new IllegalArgumentException("Adding a step needs a task");
            this.taskId = taskId;
            this.stepId = stepId;
            this.addStep = addStep;
            this.entrance = required(entrance);
        }
    }

    public static AppDestination newTask() {
        return new TaskEditor(null, null, false, TaskEditor.Entrance.DIRECT);
    }

    public static AppDestination newTaskFromHeader() {
        return new TaskEditor(null, null, false, TaskEditor.Entrance.HEADER_FLIGHT);
    }

    public static AppDestination editTask(TaskId taskId) {
        return new TaskEditor(required(taskId), null, false, TaskEditor.Entrance.DIRECT);
    }

    public static AppDestination editStep(TaskId taskId, TaskStepId stepId) {
        return new TaskEditor(required(taskId), required(stepId), false,
                TaskEditor.Entrance.DIRECT);
    }

    public static AppDestination addStep(TaskId taskId) {
        return new TaskEditor(required(taskId), null, true, TaskEditor.Entrance.DIRECT);
    }

    private static <T> T required(T value) {
        if (value == null) throw new IllegalArgumentException("Destination value is required");
        return value;
    }
}

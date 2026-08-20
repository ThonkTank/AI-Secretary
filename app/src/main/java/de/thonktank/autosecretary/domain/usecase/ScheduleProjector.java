package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.domain.model.Occurrence;
import de.thonktank.autosecretary.domain.model.Recurrence;
import de.thonktank.autosecretary.domain.model.Task;

import java.time.LocalDate;

/** Pure projection of occurrence state onto task archive and due-date fields. */
public final class ScheduleProjector {
    public Task project(Task task, Input input) {
        if (task == null) throw new IllegalArgumentException("Schedule projection needs a task");
        if (input == null) throw new IllegalArgumentException("Schedule projection needs a snapshot");
        // Materialization advances the calendar cursor. Completion and undo must never
        // rewind it to the date on which the user happened to finish the occurrence.
        LocalDate next = task.nextDueOn;
        boolean archived = task.recurrence == Recurrence.ONCE
                && input.earliestOpen == null && input.latestCompleted != null;
        return task.withOccurrenceState(archived, next,
                input.latestCompleted == null ? task.lastScheduledOn : input.latestCompleted.scheduledOn,
                input.latestCompleted == null ? task.lastCompletedOn : input.latestCompleted.completedOn,
                input.latestCompleted != null || task.hasCompletedOccurrence);
    }

    public static final class Input {
        public final Occurrence earliestOpen;
        public final Occurrence latestCompleted;

        public Input(Occurrence earliestOpen, Occurrence latestCompleted) {
            this.earliestOpen = earliestOpen;
            this.latestCompleted = latestCompleted;
        }
    }
}

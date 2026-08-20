package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.domain.model.Occurrence;
import de.thonktank.autosecretary.domain.model.Recurrence;
import de.thonktank.autosecretary.domain.model.Task;

import java.time.LocalDate;

/** Pure projection of occurrence state onto task archive and due-date fields. */
public final class ScheduleProjector {
    public Task project(Task task, Occurrence earliestOpen, Occurrence latestCompleted) {
        if (task == null) throw new IllegalArgumentException("Schedule projection needs a task");
        // Materialization advances the calendar cursor. Completion and undo must never
        // rewind it to the date on which the user happened to finish the occurrence.
        LocalDate next = task.nextDueOn;
        boolean archived = task.recurrence == Recurrence.ONCE
                && earliestOpen == null && latestCompleted != null;
        return task.withOccurrenceState(archived, next,
                latestCompleted == null ? null : latestCompleted.scheduledOn,
                latestCompleted == null ? null : latestCompleted.completedOn,
                latestCompleted != null);
    }
}

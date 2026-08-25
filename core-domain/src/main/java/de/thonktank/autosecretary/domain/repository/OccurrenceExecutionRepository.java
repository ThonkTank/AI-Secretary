package de.thonktank.autosecretary.domain.repository;

import java.time.LocalDate;
import java.util.List;

import de.thonktank.autosecretary.domain.model.Occurrence;
import de.thonktank.autosecretary.domain.model.OccurrenceStep;
import de.thonktank.autosecretary.domain.model.Task;
import de.thonktank.autosecretary.domain.model.TaskId;
import de.thonktank.autosecretary.domain.model.TaskScheduleEntry;
import de.thonktank.autosecretary.domain.model.TaskSlot;
import de.thonktank.autosecretary.domain.today.TodayStepPositionUpdate;

/** Mutable occurrence and step capability used by execution workflows. */
public interface OccurrenceExecutionRepository extends TransactionalRepository {
    Task findTask(TaskId id);
    void updateTask(Task task);
    void insertOccurrence(Occurrence occurrence);
    Occurrence findOccurrence(String id);
    Occurrence findOccurrence(TaskId taskId, LocalDate scheduledOn, TaskSlot slot);
    List<Occurrence> openOccurrences(TaskId taskId, LocalDate scheduledOn);
    Occurrence openOccurrence(TaskId taskId);
    List<Occurrence> openOccurrences(TaskId taskId);
    List<Occurrence> occurrences(TaskId taskId);
    List<Occurrence> openOccurrences(TaskSlot slot);
    Occurrence earliestOpenOccurrence(TaskId taskId);
    Occurrence latestCompletedOccurrence(TaskId taskId);
    void updateOccurrence(Occurrence occurrence);
    default void deleteOccurrence(String id) {
        throw new UnsupportedOperationException("Occurrence deletion is not supported");
    }
    void insertOccurrenceSteps(List<OccurrenceStep> steps);
    OccurrenceStep findOccurrenceStep(String id);
    List<OccurrenceStep> occurrenceSteps(String occurrenceId);
    void updateOccurrenceStep(OccurrenceStep step);
    default void deleteOccurrenceStep(String id) {
        throw new UnsupportedOperationException("Occurrence step deletion is not supported");
    }
    void updateOccurrenceStepPositions(List<TodayStepPositionUpdate> updates);
    List<TaskScheduleEntry> scheduleEntries(TaskId taskId);
}

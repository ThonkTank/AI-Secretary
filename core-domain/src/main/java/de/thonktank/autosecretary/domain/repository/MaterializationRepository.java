package de.thonktank.autosecretary.domain.repository;

import java.util.List;

import de.thonktank.autosecretary.domain.model.Occurrence;
import de.thonktank.autosecretary.domain.model.OccurrenceStep;
import de.thonktank.autosecretary.domain.model.Task;
import de.thonktank.autosecretary.domain.model.TaskId;
import de.thonktank.autosecretary.domain.model.TaskScheduleEntry;
import de.thonktank.autosecretary.domain.model.TaskStepTemplate;

/** Capability used only by due-occurrence planning and carry-forward materialization. */
public interface MaterializationRepository extends TransactionalRepository {
    List<Task> activeTasks();
    void updateTask(Task task);
    List<TaskStepTemplate> templatesFor(List<TaskId> taskIds);
    List<TaskScheduleEntry> scheduleEntriesFor(List<TaskId> taskIds);
    List<Occurrence> allOccurrences();
    List<OccurrenceStep> occurrenceStepsFor(List<String> occurrenceIds);
    void insertOccurrence(Occurrence occurrence);
    void updateOccurrence(Occurrence occurrence);
    void insertOccurrenceSteps(List<OccurrenceStep> steps);
}

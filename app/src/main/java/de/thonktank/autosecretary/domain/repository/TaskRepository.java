package de.thonktank.autosecretary.domain.repository;

import de.thonktank.autosecretary.domain.model.Occurrence;
import de.thonktank.autosecretary.domain.model.OccurrenceStep;
import de.thonktank.autosecretary.domain.model.Task;
import de.thonktank.autosecretary.domain.model.TaskId;
import de.thonktank.autosecretary.domain.model.TaskSlot;
import de.thonktank.autosecretary.domain.model.TaskStepTemplate;

import java.time.LocalDate;
import java.util.List;

public interface TaskRepository {
    void inTransaction(Runnable operation);

    void insertTask(Task task);
    void updateTask(Task task);
    Task findTask(TaskId id);
    List<Task> activeTasks();
    List<Task> allTasks();
    void deleteTask(TaskId id);
    void insertTemplates(List<TaskStepTemplate> steps);
    void deleteTemplates(TaskId taskId);
    List<TaskStepTemplate> templates(TaskId taskId);
    List<TaskStepTemplate> templatesFor(List<TaskId> taskIds);

    void insertOccurrence(Occurrence occurrence);
    void updateOccurrence(Occurrence occurrence);
    Occurrence findOccurrence(String id);
    Occurrence openOccurrence(TaskId taskId);
    List<Occurrence> openOccurrences();
    List<Occurrence> completedOccurrences(LocalDate date);
    void insertOccurrenceSteps(List<OccurrenceStep> steps);
    List<OccurrenceStep> occurrenceSteps(String occurrenceId);
    List<OccurrenceStep> occurrenceStepsFor(List<String> occurrenceIds);
    OccurrenceStep findOccurrenceStep(String id);
    void updateOccurrenceStep(OccurrenceStep step);

    int xp();
    void setXp(int xp);
}

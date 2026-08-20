package de.thonktank.autosecretary.domain.repository;

import de.thonktank.autosecretary.domain.model.Task;
import de.thonktank.autosecretary.domain.model.TaskId;
import de.thonktank.autosecretary.domain.model.TaskStepTemplate;

import java.util.List;

/** Narrow port for task definitions and their reusable step templates. */
public interface TaskDefinitionRepository extends TransactionalRepository {
    void insertTask(Task task);
    void updateTask(Task task);
    Task findTask(TaskId id);
    List<Task> activeTasks();
    List<Task> allTasks();
    void deleteTask(TaskId id);
    void insertTemplates(List<TaskStepTemplate> steps);
    void deleteTemplates(TaskId taskId);
    void deleteTemplate(String id);
    List<TaskStepTemplate> templates(TaskId taskId);
    List<TaskStepTemplate> templatesFor(List<TaskId> taskIds);
}

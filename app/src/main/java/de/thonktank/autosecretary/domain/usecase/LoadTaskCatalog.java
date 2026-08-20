package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.domain.model.Task;
import de.thonktank.autosecretary.domain.model.TaskCatalog;
import de.thonktank.autosecretary.domain.model.TaskId;
import de.thonktank.autosecretary.domain.model.TaskScheduleEntry;
import de.thonktank.autosecretary.domain.model.TaskStepTemplate;
import de.thonktank.autosecretary.domain.repository.TaskDefinitionRepository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class LoadTaskCatalog {
    private final TaskDefinitionRepository repository;

    public LoadTaskCatalog(TaskDefinitionRepository repository) {
        this.repository = repository;
    }

    public TaskCatalog execute() {
        List<Task> tasks = repository.allTasks();
        tasks.sort(Comparator.comparingLong(value -> value.displayOrder));
        List<TaskId> ids = new ArrayList<>();
        for (Task task : tasks) ids.add(task.id);
        Map<TaskId, List<TaskStepTemplate>> steps = new LinkedHashMap<>();
        for (TaskStepTemplate step : repository.templatesFor(ids))
            steps.computeIfAbsent(step.taskId, ignored -> new ArrayList<>()).add(step);
        Map<TaskId, List<TaskScheduleEntry>> schedule = new LinkedHashMap<>();
        for (TaskScheduleEntry entry : repository.scheduleEntriesFor(ids))
            schedule.computeIfAbsent(entry.taskId, ignored -> new ArrayList<>()).add(entry);
        List<TaskCatalog.Item> items = new ArrayList<>();
        for (Task task : tasks)
            items.add(new TaskCatalog.Item(task,
                    steps.getOrDefault(task.id, java.util.Collections.emptyList()),
                    schedule.getOrDefault(task.id, java.util.Collections.emptyList())));
        return new TaskCatalog(items);
    }
}

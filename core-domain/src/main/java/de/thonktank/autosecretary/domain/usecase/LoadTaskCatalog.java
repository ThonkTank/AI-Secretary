package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.domain.model.Task;
import de.thonktank.autosecretary.domain.model.TaskCatalog;
import de.thonktank.autosecretary.domain.model.TaskId;
import de.thonktank.autosecretary.domain.model.TaskSchedule;
import de.thonktank.autosecretary.domain.transaction.TransactionRunner;
import de.thonktank.autosecretary.domain.model.TaskStepTemplate;
import de.thonktank.autosecretary.domain.repository.CatalogRepository;
import de.thonktank.autosecretary.domain.repository.StepRepository;
import de.thonktank.autosecretary.domain.repository.TaskCatalogQuery;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class LoadTaskCatalog implements TaskCatalogQuery {
    private final CatalogRepository catalog;
    private final StepRepository steps;

    private final TransactionRunner transactions;

    public LoadTaskCatalog(CatalogRepository catalog, StepRepository steps, TransactionRunner transactions) {
        this.transactions = transactions;
        this.catalog = catalog;
        this.steps = steps;
    }

    @Override public TaskCatalog execute() {
        return transactions.inTransaction(this::read);
    }

    private TaskCatalog read() {
        List<Task> tasks = catalog.allTasks();
        tasks.sort(Comparator.comparingLong(value -> value.catalogOrder));
        Map<TaskId, List<TaskStepTemplate>> stepsByTask = new LinkedHashMap<>();
        for (TaskStepTemplate step : steps.allTemplates())
            stepsByTask.computeIfAbsent(step.taskId, ignored -> new ArrayList<>()).add(step);
        TaskSchedule schedule = new TaskSchedule(catalog.scheduleEntries());
        List<TaskCatalog.Item> items = new ArrayList<>();
        for (Task task : tasks)
            items.add(new TaskCatalog.Item(task,
                    stepsByTask.getOrDefault(task.id, java.util.Collections.emptyList()),
                    schedule.placements(task.id)));
        return new TaskCatalog(items);
    }
}

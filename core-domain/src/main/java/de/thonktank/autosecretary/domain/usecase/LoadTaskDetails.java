package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.domain.model.Task;
import de.thonktank.autosecretary.domain.model.TaskDetails;
import de.thonktank.autosecretary.domain.model.TaskId;
import de.thonktank.autosecretary.domain.model.TaskSchedule;
import de.thonktank.autosecretary.domain.repository.CatalogRepository;
import de.thonktank.autosecretary.domain.repository.StepRepository;

public final class LoadTaskDetails {
    private final CatalogRepository catalog;
    private final StepRepository steps;

    public LoadTaskDetails(CatalogRepository catalog, StepRepository steps) {
        this.catalog = catalog;
        this.steps = steps;
    }

    public TaskDetails execute(TaskId id) {
        Task task = catalog.findTask(id);
        return task == null ? null : new TaskDetails(task, steps.templates(id),
                new TaskSchedule(catalog.scheduleEntries(id)));
    }
}

package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.domain.repository.TaskCatalogQuery;
import de.thonktank.autosecretary.domain.schedule.MoveScheduleEntry;
import de.thonktank.autosecretary.domain.schedule.MoveTaskPlacement;
import de.thonktank.autosecretary.domain.steps.MoveTaskStep;
import de.thonktank.autosecretary.domain.steps.SwapTaskSteps;

/** Focused application commands and queries for reusable task definitions. */
public final class CatalogUseCases {
    public final CreateTask create;
    public final UpdateTask update;
    public final MoveTaskPlacement moveTaskPlacement;
    public final DeleteTask delete;
    public final LoadTaskDetails loadTaskDetails;
    public final TaskCatalogQuery loadTaskCatalog;
    public final MoveScheduleEntry moveScheduleEntry;
    public final MoveTaskStep moveTaskStep;
    public final SwapTaskSteps swapTaskSteps;
    public final SaveTaskConfiguration saveTaskConfiguration;

    public CatalogUseCases(CreateTask create, UpdateTask update,
                           MoveTaskPlacement moveTaskPlacement, DeleteTask delete,
                           LoadTaskDetails loadTaskDetails, TaskCatalogQuery loadTaskCatalog,
                           MoveScheduleEntry moveScheduleEntry, MoveTaskStep moveTaskStep,
                           SwapTaskSteps swapTaskSteps,
                           SaveTaskConfiguration saveTaskConfiguration) {
        this.create = create;
        this.update = update;
        this.moveTaskPlacement = moveTaskPlacement;
        this.delete = delete;
        this.loadTaskDetails = loadTaskDetails;
        this.loadTaskCatalog = loadTaskCatalog;
        this.moveScheduleEntry = moveScheduleEntry;
        this.moveTaskStep = moveTaskStep;
        this.swapTaskSteps = swapTaskSteps;
        this.saveTaskConfiguration = saveTaskConfiguration;
    }
}

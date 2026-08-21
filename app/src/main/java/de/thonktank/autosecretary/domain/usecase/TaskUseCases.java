package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.domain.schedule.MoveScheduleEntry;
import de.thonktank.autosecretary.domain.schedule.MoveTaskPlacement;
import de.thonktank.autosecretary.domain.steps.MoveTaskStep;
import de.thonktank.autosecretary.domain.steps.SwapTaskSteps;

import de.thonktank.autosecretary.Clock;
import de.thonktank.autosecretary.domain.repository.ApplicationTaskRepository;
import de.thonktank.autosecretary.domain.repository.TaskCatalogQuery;

public final class TaskUseCases {
    public final CreateTask create;
    public final UpdateTask update;
    public final MoveTaskPlacement moveTaskPlacement;
    public final DeleteTask delete;
    public final DeferTask defer;
    public final ToggleStep toggleStep;
    public final RecordRepetitionResult recordRepetitionResult;
    public final CorrectRepetitionResult correctRepetitionResult;
    public final CompleteOccurrence complete;
    public final CompleteRemainingSteps completeRemainingSteps;
    public final HarvestOccurrence harvest;
    public final UndoOccurrence undoOccurrence;
    public final ApplyComboDecay applyComboDecay;
    public final CloseOngoingTask closeOngoing;
    public final MaterializeDueOccurrences materializeDue;
    public final LoadDashboard loadDashboard;
    public final LoadTaskDetails loadTaskDetails;
    public final TaskCatalogQuery loadTaskCatalog;
    public final MoveScheduleEntry moveScheduleEntry;
    public final MoveTaskStep moveTaskStep;
    public final SwapTaskSteps swapTaskSteps;

    public TaskUseCases(ApplicationTaskRepository repository, Clock clock, IdGenerator ids) {
        loadDashboard = new LoadDashboard(repository);
        materializeDue = new MaterializeDueOccurrences(repository, clock, ids);
        create = new CreateTask(repository, repository, clock, ids);
        update = new UpdateTask(repository, repository, ids, clock);
        moveTaskPlacement = new MoveTaskPlacement(repository);
        delete = new DeleteTask(repository);
        defer = new DeferTask(repository, repository);
        toggleStep = new ToggleStep(repository, clock);
        recordRepetitionResult = new RecordRepetitionResult(repository, clock);
        correctRepetitionResult = new CorrectRepetitionResult(repository);
        complete = new CompleteOccurrence(repository, clock);
        completeRemainingSteps = new CompleteRemainingSteps(repository, clock);
        harvest = new HarvestOccurrence(repository, clock);
        undoOccurrence = new UndoOccurrence(repository, clock);
        applyComboDecay = new ApplyComboDecay(repository, clock);
        closeOngoing = new CloseOngoingTask(repository, clock);
        loadTaskDetails = new LoadTaskDetails(repository);
        loadTaskCatalog = new LoadTaskCatalog(repository);
        moveScheduleEntry = new MoveScheduleEntry(repository);
        moveTaskStep = new MoveTaskStep(repository);
        swapTaskSteps = new SwapTaskSteps(repository);
    }
}

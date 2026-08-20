package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.Clock;
import de.thonktank.autosecretary.domain.model.TaskOrdering;
import de.thonktank.autosecretary.domain.repository.TaskRepository;

public final class TaskUseCases {
    public final CreateTask create;
    public final UpdateTask update;
    public final MoveTask move;
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
    public final LoadTaskCatalog loadTaskCatalog;
    public final MoveScheduleEntry moveScheduleEntry;
    public final OrganizeTaskStep organizeTaskStep;

    public TaskUseCases(TaskRepository repository, Clock clock, IdGenerator ids) {
        TaskOrdering ordering = new TaskOrdering();
        loadDashboard = new LoadDashboard(repository);
        materializeDue = new MaterializeDueOccurrences(repository, clock, ids);
        create = new CreateTask(repository, clock, ids, ordering);
        update = new UpdateTask(repository, ordering, ids, clock);
        move = new MoveTask(repository, ordering);
        delete = new DeleteTask(repository);
        defer = new DeferTask(repository, loadDashboard, ordering, clock);
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
        moveScheduleEntry = new MoveScheduleEntry(repository, clock);
        organizeTaskStep = new OrganizeTaskStep(repository);
    }
}

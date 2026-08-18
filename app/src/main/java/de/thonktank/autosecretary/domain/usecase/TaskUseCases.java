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
    public final ConfirmSet confirmSet;
    public final FinishExercise finishExercise;
    public final EditStepProgress editStepProgress;
    public final CompleteOccurrence complete;
    public final CompleteRemainingSteps completeRemainingSteps;
    public final HarvestOccurrence harvest;
    public final UndoOccurrence undoOccurrence;
    public final ApplyComboDecay applyComboDecay;
    public final CloseOngoingTask closeOngoing;
    public final MaterializeDueOccurrences materializeDue;
    public final LoadDashboard loadDashboard;
    public final LoadTaskDetails loadTaskDetails;

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
        confirmSet = new ConfirmSet(repository, clock);
        finishExercise = new FinishExercise(repository, clock);
        editStepProgress = new EditStepProgress(repository, clock);
        complete = new CompleteOccurrence(repository, clock);
        completeRemainingSteps = new CompleteRemainingSteps(repository, clock);
        harvest = new HarvestOccurrence(repository, clock);
        undoOccurrence = new UndoOccurrence(repository, clock);
        applyComboDecay = new ApplyComboDecay(repository, clock);
        closeOngoing = new CloseOngoingTask(repository, clock);
        loadTaskDetails = new LoadTaskDetails(repository);
    }
}

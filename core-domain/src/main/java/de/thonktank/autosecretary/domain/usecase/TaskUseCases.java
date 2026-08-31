package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.domain.schedule.MoveScheduleEntry;
import de.thonktank.autosecretary.domain.schedule.MoveTaskPlacement;
import de.thonktank.autosecretary.domain.steps.MoveTaskStep;
import de.thonktank.autosecretary.domain.steps.SwapTaskSteps;

import de.thonktank.autosecretary.Clock;
import de.thonktank.autosecretary.MomentSource;
import de.thonktank.autosecretary.SystemMomentSource;
import de.thonktank.autosecretary.domain.repository.ApplicationTaskRepository;
import de.thonktank.autosecretary.domain.repository.TaskCatalogQuery;
import de.thonktank.autosecretary.domain.repository.ComboPolicySource;

public final class TaskUseCases {
    public final CreateTask create;
    public final UpdateTask update;
    public final MoveTaskPlacement moveTaskPlacement;
    public final DeleteTask delete;
    public final DeferTask defer;
    public final ToggleStep toggleStep;
    public final AdvanceTodayStep advanceTodayStep;
    public final MoveTodayStep moveTodayStep;
    public final RecordRepetitionResult recordRepetitionResult;
    public final CorrectRepetitionResult correctRepetitionResult;
    public final RecordSetResult recordSetResult;
    public final CorrectSetResult correctSetResult;
    public final UndoLatestTrainingAdjustment undoLatestTrainingAdjustment;
    public final ResolveTrainingLoadRequest resolveTrainingLoadRequest;
    public final FinishStepForToday finishStepForToday;
    public final CompleteOccurrence complete;
    public final CompleteRemainingSteps completeRemainingSteps;
    public final HarvestOccurrence harvest;
    public final UndoOccurrence undoOccurrence;
    public final ApplyComboDecay applyComboDecay;
    public final SettlePreviousPartialOccurrences settlePreviousPartialOccurrences;
    public final CloseOngoingTask closeOngoing;
    public final MaterializeDueOccurrences materializeDue;
    public final LoadDashboard loadDashboard;
    public final LoadTaskDetails loadTaskDetails;
    public final TaskCatalogQuery loadTaskCatalog;
    public final MoveScheduleEntry moveScheduleEntry;
    public final MoveTaskStep moveTaskStep;
    public final SwapTaskSteps swapTaskSteps;
    public final SaveCapacityResource saveCapacityResource;
    public final SaveStepFlowDefinition saveStepFlowDefinition;
    public final LoadStepFlowSetup loadStepFlowSetup;
    public final SaveStepFlowSetup saveStepFlowSetup;
    public final LoadCapacityResources loadCapacityResources;
    public final SaveTaskConfiguration saveTaskConfiguration;
    public final ActivateReadyFlows activateReadyFlows;
    public final DeferFlowRun deferFlowRun;
    public final CancelFlowRun cancelFlowRun;
    public final AdjustFlowRunReadyAt adjustFlowRunReadyAt;
    public final ReorderFlowRun reorderFlowRun;
    public final LoadFlowRuns loadFlowRuns;

    public TaskUseCases(ApplicationTaskRepository repository, Clock clock, IdGenerator ids) {
        this(repository, clock, new SystemMomentSource(), ids, ComboPolicySource.defaults());
    }

    public TaskUseCases(ApplicationTaskRepository repository, Clock clock, IdGenerator ids,
                        ComboPolicySource policies) {
        this(repository, clock, new SystemMomentSource(), ids, policies);
    }

    public TaskUseCases(ApplicationTaskRepository repository, Clock clock,
                        MomentSource moments, IdGenerator ids) {
        this(repository, clock, moments, ids, ComboPolicySource.defaults());
    }

    public TaskUseCases(ApplicationTaskRepository repository, Clock clock,
                        MomentSource moments, IdGenerator ids, ComboPolicySource policies) {
        FlowRuntimeCoordinator flowRuntime = new FlowRuntimeCoordinator(repository, clock,
                moments, ids);
        loadDashboard = new LoadDashboard(repository, repository);
        materializeDue = new MaterializeDueOccurrences(repository, clock, moments, ids);
        create = new CreateTask(repository, repository, clock, ids);
        update = new UpdateTask(repository, repository, ids, clock);
        moveTaskPlacement = new MoveTaskPlacement(repository);
        delete = new DeleteTask(repository);
        defer = new DeferTask(repository, repository, flowRuntime);
        toggleStep = new ToggleStep(repository, clock, policies, flowRuntime);
        advanceTodayStep = new AdvanceTodayStep(repository, clock, policies, flowRuntime);
        moveTodayStep = new MoveTodayStep(repository);
        recordRepetitionResult = new RecordRepetitionResult(repository, clock, policies,
                flowRuntime);
        correctRepetitionResult = new CorrectRepetitionResult(repository, clock, policies);
        recordSetResult = new RecordSetResult(repository, clock, ids, policies);
        correctSetResult = new CorrectSetResult(repository, clock, policies);
        undoLatestTrainingAdjustment = new UndoLatestTrainingAdjustment(repository, clock);
        resolveTrainingLoadRequest = new ResolveTrainingLoadRequest(repository, clock, ids);
        finishStepForToday = new FinishStepForToday(repository, clock, policies);
        complete = new CompleteOccurrence(repository, clock, policies, flowRuntime);
        completeRemainingSteps = new CompleteRemainingSteps(repository, clock, policies,
                flowRuntime);
        harvest = new HarvestOccurrence(repository, clock, policies, flowRuntime);
        undoOccurrence = new UndoOccurrence(repository, clock, policies, flowRuntime);
        applyComboDecay = new ApplyComboDecay(repository, clock, policies);
        settlePreviousPartialOccurrences = new SettlePreviousPartialOccurrences(
                repository, clock, policies);
        closeOngoing = new CloseOngoingTask(repository, clock);
        loadTaskDetails = new LoadTaskDetails(repository);
        loadTaskCatalog = new LoadTaskCatalog(repository);
        moveScheduleEntry = new MoveScheduleEntry(repository);
        moveTaskStep = new MoveTaskStep(repository);
        swapTaskSteps = new SwapTaskSteps(repository);
        saveCapacityResource = new SaveCapacityResource(repository, repository, ids);
        saveStepFlowDefinition = new SaveStepFlowDefinition(repository, repository);
        loadStepFlowSetup = new LoadStepFlowSetup(repository, repository);
        saveStepFlowSetup = new SaveStepFlowSetup(repository, repository);
        loadCapacityResources = new LoadCapacityResources(repository);
        saveTaskConfiguration = new SaveTaskConfiguration(repository, repository,
                create, update, ids);
        activateReadyFlows = new ActivateReadyFlows(flowRuntime);
        deferFlowRun = new DeferFlowRun(flowRuntime);
        cancelFlowRun = new CancelFlowRun(flowRuntime);
        adjustFlowRunReadyAt = new AdjustFlowRunReadyAt(flowRuntime);
        reorderFlowRun = new ReorderFlowRun(flowRuntime);
        loadFlowRuns = new LoadFlowRuns(repository, repository);
    }
}

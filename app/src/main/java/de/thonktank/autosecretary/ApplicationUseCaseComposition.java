package de.thonktank.autosecretary;

import de.thonktank.autosecretary.data.local.TaskStore;
import de.thonktank.autosecretary.domain.repository.ComboPolicySource;
import de.thonktank.autosecretary.domain.repository.TrainingRepository;
import de.thonktank.autosecretary.domain.schedule.MoveScheduleEntry;
import de.thonktank.autosecretary.domain.schedule.MoveTaskPlacement;
import de.thonktank.autosecretary.domain.steps.MoveTaskStep;
import de.thonktank.autosecretary.domain.steps.SwapTaskSteps;
import de.thonktank.autosecretary.domain.transaction.TransactionRunner;
import de.thonktank.autosecretary.domain.usecase.ActivateReadyFlows;
import de.thonktank.autosecretary.domain.usecase.AdjustFlowRunReadyAt;
import de.thonktank.autosecretary.domain.usecase.AdvanceTodayStep;
import de.thonktank.autosecretary.domain.usecase.ApplyComboDecay;
import de.thonktank.autosecretary.domain.usecase.CancelFlowRun;
import de.thonktank.autosecretary.domain.usecase.CatalogUseCases;
import de.thonktank.autosecretary.domain.usecase.CloseOngoingTask;
import de.thonktank.autosecretary.domain.usecase.CompleteOccurrence;
import de.thonktank.autosecretary.domain.usecase.CompleteRemainingSteps;
import de.thonktank.autosecretary.domain.usecase.CorrectRepetitionResult;
import de.thonktank.autosecretary.domain.usecase.CorrectSetResult;
import de.thonktank.autosecretary.domain.usecase.CreateTask;
import de.thonktank.autosecretary.domain.usecase.DeferFlowRun;
import de.thonktank.autosecretary.domain.usecase.DeferTask;
import de.thonktank.autosecretary.domain.usecase.DeleteTask;
import de.thonktank.autosecretary.domain.usecase.FinishStepForToday;
import de.thonktank.autosecretary.domain.usecase.FlowRuntimeCoordinator;
import de.thonktank.autosecretary.domain.usecase.FlowUseCases;
import de.thonktank.autosecretary.domain.usecase.HarvestOccurrence;
import de.thonktank.autosecretary.domain.usecase.IdGenerator;
import de.thonktank.autosecretary.domain.usecase.LoadCapacityResources;
import de.thonktank.autosecretary.domain.usecase.LoadDashboard;
import de.thonktank.autosecretary.domain.usecase.LoadFlowRuns;
import de.thonktank.autosecretary.domain.usecase.LoadStepFlowSetup;
import de.thonktank.autosecretary.domain.usecase.LoadTaskCatalog;
import de.thonktank.autosecretary.domain.usecase.LoadTaskDetails;
import de.thonktank.autosecretary.domain.usecase.LoadTrainingContext;
import de.thonktank.autosecretary.domain.usecase.MaterializeDueOccurrences;
import de.thonktank.autosecretary.domain.usecase.MoveTodayStep;
import de.thonktank.autosecretary.domain.usecase.RecordRepetitionResult;
import de.thonktank.autosecretary.domain.usecase.RecordSetResult;
import de.thonktank.autosecretary.domain.usecase.ReorderFlowRun;
import de.thonktank.autosecretary.domain.usecase.ResolveTrainingLoadRequest;
import de.thonktank.autosecretary.domain.usecase.SaveCapacityResource;
import de.thonktank.autosecretary.domain.usecase.SaveStepFlowDefinition;
import de.thonktank.autosecretary.domain.usecase.SaveStepFlowSetup;
import de.thonktank.autosecretary.domain.usecase.SaveTaskConfiguration;
import de.thonktank.autosecretary.domain.usecase.SettlePreviousPartialOccurrences;
import de.thonktank.autosecretary.domain.usecase.TodayUseCases;
import de.thonktank.autosecretary.domain.usecase.ToggleStep;
import de.thonktank.autosecretary.domain.usecase.TrainingUseCases;
import de.thonktank.autosecretary.domain.usecase.UndoLatestTrainingAdjustment;
import de.thonktank.autosecretary.domain.usecase.UndoOccurrence;
import de.thonktank.autosecretary.domain.usecase.UpdateTask;

/** Concrete composition root for the four focused application slices. */
final class ApplicationUseCaseComposition {
    final CatalogUseCases catalog;
    final TodayUseCases today;
    final FlowUseCases flows;
    final TrainingUseCases training;

    ApplicationUseCaseComposition(TaskStore store, TrainingRepository trainingRepository,
                                  TransactionRunner transactions, Clock clock, IdGenerator ids,
                                  ComboPolicySource policies) {
        this(store, trainingRepository, transactions, clock, new SystemMomentSource(), ids,
                policies);
    }

    ApplicationUseCaseComposition(TaskStore store, TrainingRepository trainingRepository,
                                  TransactionRunner transactions, Clock clock,
                                  MomentSource moments, IdGenerator ids,
                                  ComboPolicySource policies) {
        FlowRuntimeCoordinator flowRuntime = new FlowRuntimeCoordinator(store, store, store,
                transactions, clock, moments, ids);

        CreateTask create = new CreateTask(store, store, transactions, clock, ids);
        UpdateTask update = new UpdateTask(store, store, store, trainingRepository,
                transactions, ids, clock);
        catalog = new CatalogUseCases(create, update,
                new MoveTaskPlacement(store, transactions), new DeleteTask(store, transactions),
                new LoadTaskDetails(store), new LoadTaskCatalog(store),
                new MoveScheduleEntry(store, transactions),
                new MoveTaskStep(store, transactions), new SwapTaskSteps(store, transactions),
                new SaveTaskConfiguration(store, store, transactions, create, update, ids));

        LoadTrainingContext loadTrainingContext = new LoadTrainingContext(trainingRepository,
                transactions);
        training = new TrainingUseCases(new UndoLatestTrainingAdjustment(trainingRepository,
                transactions, clock), new ResolveTrainingLoadRequest(trainingRepository,
                transactions, clock, ids), loadTrainingContext);

        today = new TodayUseCases(new DeferTask(store, store, transactions, flowRuntime),
                new ToggleStep(store, store, store, transactions, clock, policies, flowRuntime),
                new AdvanceTodayStep(store, store, store, transactions, clock, policies,
                        flowRuntime),
                new MoveTodayStep(store, transactions),
                new RecordRepetitionResult(store, store, store, transactions, clock, policies,
                        flowRuntime),
                new CorrectRepetitionResult(store, store, store, transactions, clock, policies),
                new RecordSetResult(store, store, store, trainingRepository, transactions, clock,
                        ids, policies, flowRuntime),
                new CorrectSetResult(store, store, store, trainingRepository, transactions,
                        clock, policies, flowRuntime),
                new FinishStepForToday(store, store, store, transactions, clock, policies),
                new CompleteOccurrence(store, store, store, transactions, clock, policies,
                        flowRuntime),
                new CompleteRemainingSteps(store, store, store, transactions, clock, policies,
                        flowRuntime),
                new HarvestOccurrence(store, store, store, transactions, clock, policies,
                        flowRuntime),
                new UndoOccurrence(store, store, store, transactions, clock, policies,
                        flowRuntime),
                new ApplyComboDecay(store, store, transactions, clock, policies),
                new SettlePreviousPartialOccurrences(store, store, store, store, transactions,
                        clock, policies),
                new CloseOngoingTask(store, store, store, transactions, clock),
                new MaterializeDueOccurrences(store, store, store, store, transactions, clock,
                        moments, ids),
                new LoadDashboard(store, store, trainingRepository, transactions));

        flows = new FlowUseCases(new SaveCapacityResource(store, store, transactions, ids),
                new SaveStepFlowDefinition(store, store, transactions),
                new LoadStepFlowSetup(store, store),
                new SaveStepFlowSetup(store, store, transactions),
                new LoadCapacityResources(store), new ActivateReadyFlows(flowRuntime),
                new DeferFlowRun(flowRuntime), new CancelFlowRun(flowRuntime),
                new AdjustFlowRunReadyAt(flowRuntime), new ReorderFlowRun(flowRuntime),
                new LoadFlowRuns(store, store));
    }
}

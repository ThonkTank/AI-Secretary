package de.thonktank.autosecretary;

import de.thonktank.autosecretary.data.local.RoomCatalogRepository;
import de.thonktank.autosecretary.data.local.RoomFlowRepository;
import de.thonktank.autosecretary.data.local.RoomStepRepository;
import de.thonktank.autosecretary.data.local.RoomTodayRepository;
import de.thonktank.autosecretary.data.local.RoomTrainingRepository;
import de.thonktank.autosecretary.data.local.RoomTransactionRunner;
import de.thonktank.autosecretary.domain.repository.CatalogRepository;
import de.thonktank.autosecretary.domain.repository.ComboPolicySource;
import de.thonktank.autosecretary.domain.repository.FlowRepository;
import de.thonktank.autosecretary.domain.repository.StepRepository;
import de.thonktank.autosecretary.domain.repository.TodayRepository;
import de.thonktank.autosecretary.domain.repository.TrainingRepository;
import de.thonktank.autosecretary.domain.schedule.MoveScheduleEntry;
import de.thonktank.autosecretary.domain.schedule.MoveTaskPlacement;
import de.thonktank.autosecretary.domain.steps.MoveTaskStep;
import de.thonktank.autosecretary.domain.steps.SwapTaskSteps;
import de.thonktank.autosecretary.domain.transaction.TransactionRunner;
import de.thonktank.autosecretary.domain.usecase.ActivateReadyFlows;
import de.thonktank.autosecretary.domain.usecase.AdvanceTodayStep;
import de.thonktank.autosecretary.domain.usecase.ApplyComboDecay;
import de.thonktank.autosecretary.domain.usecase.CatalogUseCases;
import de.thonktank.autosecretary.domain.usecase.CloseOngoingTask;
import de.thonktank.autosecretary.domain.usecase.CompleteOccurrence;
import de.thonktank.autosecretary.domain.usecase.CompleteRemainingSteps;
import de.thonktank.autosecretary.domain.usecase.CorrectRepetitionResult;
import de.thonktank.autosecretary.domain.usecase.CorrectSetResult;
import de.thonktank.autosecretary.domain.usecase.CreateTask;
import de.thonktank.autosecretary.domain.usecase.DeleteTask;
import de.thonktank.autosecretary.domain.usecase.FinishStepForToday;
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
import de.thonktank.autosecretary.domain.usecase.ResolveTrainingLoadRequest;
import de.thonktank.autosecretary.domain.usecase.SaveCapacityResource;
import de.thonktank.autosecretary.domain.usecase.SaveStepFlowDefinition;
import de.thonktank.autosecretary.domain.usecase.SaveStepFlowSetup;
import de.thonktank.autosecretary.domain.usecase.SaveTaskConfiguration;
import de.thonktank.autosecretary.domain.usecase.SettlePreviousPartialOccurrences;
import de.thonktank.autosecretary.domain.usecase.StartFlowCandidate;
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

    ApplicationUseCaseComposition(AppDatabase database, Clock clock, IdGenerator ids,
                                  ComboPolicySource policies) {
        this(database, clock, new SystemMomentSource(), ids, policies);
    }

    ApplicationUseCaseComposition(AppDatabase database, Clock clock,
                                  MomentSource moments, IdGenerator ids,
                                  ComboPolicySource policies) {
        TransactionRunner transactions = new RoomTransactionRunner(database);
        CatalogRepository catalogRepository = new RoomCatalogRepository(database);
        StepRepository stepRepository = new RoomStepRepository(database, transactions);
        TodayRepository todayRepository = new RoomTodayRepository(database);
        FlowRepository flowRepository = new RoomFlowRepository(database);
        TrainingRepository trainingRepository = new RoomTrainingRepository(database);

        de.thonktank.autosecretary.domain.repository.FlowGraphDefinitionRepository definitions =
                new de.thonktank.autosecretary.data.local.SqlFlowGraphDefinitionRepository(
                        () -> database.getOpenHelper().getWritableDatabase(), stepRepository);
        de.thonktank.autosecretary.domain.repository.FlowGraphRunRepository graphRuns =
                new de.thonktank.autosecretary.data.local.SqlFlowGraphRunRepository(
                        () -> database.getOpenHelper().getWritableDatabase());
        de.thonktank.autosecretary.domain.usecase.GraphFlowRuntime flowRuntime =
                new de.thonktank.autosecretary.domain.usecase.GraphFlowRuntime(catalogRepository,
                        stepRepository, todayRepository, flowRepository, definitions, graphRuns,
                        transactions, clock, moments, ids, policies);
        de.thonktank.autosecretary.domain.usecase.LoadGraphFlowSheets graphSheets =
                new de.thonktank.autosecretary.domain.usecase.LoadGraphFlowSheets(catalogRepository,
                        stepRepository, todayRepository, flowRepository, definitions, graphRuns, transactions);

        CreateTask create = new CreateTask(catalogRepository, stepRepository, todayRepository,
                transactions, clock, ids);
        UpdateTask update = new UpdateTask(catalogRepository, stepRepository, todayRepository,
                flowRepository, trainingRepository,
                transactions, ids, clock);
        catalog = new CatalogUseCases(create, update,
                new MoveTaskPlacement(catalogRepository, todayRepository, transactions),
                new DeleteTask(catalogRepository, transactions),
                new LoadTaskDetails(catalogRepository, stepRepository),
                new LoadTaskCatalog(catalogRepository, stepRepository),
                new MoveScheduleEntry(catalogRepository, todayRepository, transactions),
                new MoveTaskStep(catalogRepository, stepRepository, todayRepository, transactions),
                new SwapTaskSteps(catalogRepository, stepRepository, todayRepository, transactions),
                new SaveTaskConfiguration(catalogRepository, stepRepository, flowRepository,
                        transactions, create, update, ids));

        LoadTrainingContext loadTrainingContext = new LoadTrainingContext(stepRepository,
                trainingRepository, transactions);
        training = new TrainingUseCases(new UndoLatestTrainingAdjustment(stepRepository,
                trainingRepository, transactions, clock),
                new ResolveTrainingLoadRequest(stepRepository, trainingRepository,
                        transactions, clock, ids), loadTrainingContext);

        ToggleStep toggleStep = new ToggleStep(catalogRepository, stepRepository,
                todayRepository, transactions, clock, policies, flowRuntime);
        LoadDashboard dashboard = new LoadDashboard(catalogRepository, stepRepository, todayRepository,
                flowRepository, trainingRepository, transactions, graphSheets);
        today = new TodayUseCases(toggleStep,
                new AdvanceTodayStep(catalogRepository, stepRepository, todayRepository,
                        transactions, clock, policies,
                        flowRuntime),
                new MoveTodayStep(stepRepository, todayRepository, transactions),
                new RecordRepetitionResult(catalogRepository, stepRepository, todayRepository,
                        transactions, clock, policies,
                        flowRuntime),
                new CorrectRepetitionResult(catalogRepository, stepRepository, todayRepository,
                        transactions, clock, policies),
                new RecordSetResult(catalogRepository, stepRepository, todayRepository,
                        trainingRepository, transactions, clock,
                        ids, policies, flowRuntime),
                new CorrectSetResult(catalogRepository, stepRepository, todayRepository,
                        trainingRepository, transactions,
                        clock, policies, flowRuntime),
                new FinishStepForToday(catalogRepository, stepRepository, todayRepository,
                        transactions, clock, policies),
                new CompleteOccurrence(catalogRepository, stepRepository, todayRepository,
                        transactions, clock, policies,
                        flowRuntime),
                new CompleteRemainingSteps(catalogRepository, stepRepository, todayRepository,
                        transactions, clock, policies,
                        flowRuntime),
                new HarvestOccurrence(catalogRepository, stepRepository, todayRepository,
                        transactions, clock, policies,
                        flowRuntime),
                new UndoOccurrence(catalogRepository, stepRepository, todayRepository,
                        transactions, clock, policies,
                        flowRuntime),
                new ApplyComboDecay(todayRepository, transactions, clock, policies),
                new SettlePreviousPartialOccurrences(catalogRepository, stepRepository,
                        todayRepository, transactions,
                        clock, policies),
                new CloseOngoingTask(catalogRepository, stepRepository, todayRepository,
                        transactions, clock),
                new MaterializeDueOccurrences(catalogRepository, stepRepository, todayRepository,
                        flowRepository, transactions, clock, moments, ids, definitions, graphRuns),
                dashboard, new de.thonktank.autosecretary.domain.usecase.MoveTodayItem(
                        todayRepository, dashboard, transactions, clock));

        flows = new FlowUseCases(flowRuntime,
                new de.thonktank.autosecretary.domain.usecase.LoadFlowGraph(catalogRepository,
                        stepRepository, flowRepository, definitions, transactions),
                new de.thonktank.autosecretary.domain.usecase.SaveFlowGraph(catalogRepository,
                        stepRepository, flowRepository, definitions, transactions, create, ids),
                graphSheets, new LoadCapacityResources(flowRepository), clock);
    }
}

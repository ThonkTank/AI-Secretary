package com.autosecretary.app;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.autosecretary.database.AppDatabase;
import com.autosecretary.app.settings.SettingsDataService;
import com.autosecretary.features.budget.application.importing.ApplyRecurringSuggestionsUseCase;
import com.autosecretary.features.budget.application.importing.BudgetImportUseCase;
import com.autosecretary.features.budget.data.api.ClaudeStatementApiClient;
import com.autosecretary.shared.ClaudeApiKeyStore;
import com.autosecretary.shared.ClaudeEndpointStore;
import com.autosecretary.shared.ClaudeMessagesClient;
import com.autosecretary.shared.ClaudeModelStore;
import com.autosecretary.features.task.application.ApplyTaskChangesUseCase;
import com.autosecretary.features.task.application.UndoTaskChangesUseCase;
import com.autosecretary.features.task.application.TaskChangeUndoHolder;
import com.autosecretary.features.assistant.application.AssistantChatUseCase;
import com.autosecretary.features.assistant.application.AssistantConversation;
import com.autosecretary.features.assistant.application.ConfirmAssistantProposalUseCase;
import com.autosecretary.features.assistant.application.internal.AssistantTool;
import com.autosecretary.features.assistant.application.internal.AssistantToolRegistry;
import com.autosecretary.features.assistant.application.internal.BudgetTools;
import com.autosecretary.features.assistant.application.internal.DbCalls;
import com.autosecretary.features.assistant.application.internal.MealTools;
import com.autosecretary.features.assistant.application.internal.TaskTools;
import com.autosecretary.features.assistant.application.internal.AssistantBudgetGateway;
import com.autosecretary.features.assistant.application.internal.AssistantTransactionImportExecutor;
import com.autosecretary.features.assistant.application.internal.AssistantMealGateway;
import com.autosecretary.features.assistant.ui.AssistantViewModelFactory;
import com.autosecretary.features.budget.application.importing.internal.StatementFileParser;
import com.autosecretary.features.budget.application.BudgetSeedService;
import com.autosecretary.features.budget.application.BudgetTransactionMutationUseCase;
import com.autosecretary.features.budget.application.CalculateEffectiveBudgetLimitUseCase;
import com.autosecretary.features.budget.application.CreateTransferUseCase;
import com.autosecretary.features.budget.application.LoadBudgetLimitOverviewUseCase;
import com.autosecretary.features.budget.application.LoadBudgetWidgetSummaryUseCase;
import com.autosecretary.features.budget.application.LoadBudgetOverviewUseCase;
import com.autosecretary.features.budget.application.ResolveBudgetAccountUseCase;
import com.autosecretary.features.budget.data.repository.BudgetImportRoomRepository;
import com.autosecretary.features.budget.data.repository.BudgetRoomRepository;
import com.autosecretary.features.budget.ui.BudgetViewModelFactory;
import com.autosecretary.features.budget.ui.widget.BudgetWidgetProvider;
import com.autosecretary.features.meal.application.MealPlannerDataService;
import com.autosecretary.features.task.application.internal.meal.TaskMealCompletionFromMealPlanner;
import com.autosecretary.features.meal.data.repository.MealRoomRepository;
import com.autosecretary.features.meal.data.repository.MealRecipeRoomRepository;
import com.autosecretary.features.meal.data.repository.MealPantryRoomRepository;
import com.autosecretary.features.meal.domain.MealRepository;
import com.autosecretary.features.meal.domain.RecipeRepository;
import com.autosecretary.features.meal.domain.PantryRepository;
import com.autosecretary.features.meal.ui.MealPlannerViewModelFactory;
import com.autosecretary.features.task.application.AdjustTaskProgressUseCase;
import com.autosecretary.features.task.application.CheckOffTaskUseCase;
import com.autosecretary.features.task.application.LoadTaskWidgetItemsUseCase;
import com.autosecretary.features.task.application.RegenerateScheduleUseCase;
import com.autosecretary.features.task.application.TaskDataService;
import com.autosecretary.features.task.application.UndoTaskCheckOffUseCase;
import com.autosecretary.features.task.application.calendar.TaskCalendarService;
import com.autosecretary.features.task.application.edit.CreateDefaultTaskPrefSlotUseCase;
import com.autosecretary.features.task.application.edit.TaskEditReferenceDataUseCase;
import com.autosecretary.features.task.application.listmodel.TaskListItemMapper;
import com.autosecretary.features.task.application.config.TaskScheduleConfigRepository;
import com.autosecretary.features.task.application.config.TaskCategoryWindowRepository;
import com.autosecretary.features.task.application.config.TaskCategoryRepository;
import com.autosecretary.features.task.application.config.SchedulingSettings;
import com.autosecretary.features.task.application.internal.budget.BookTaskCompletionExpenseUseCase;
import com.autosecretary.features.task.application.internal.budget.TaskBudgetEligibilityFromBudgetLookup;
import com.autosecretary.features.task.application.internal.calendar.CalendarReader;
import com.autosecretary.features.task.application.internal.calendar.DeviceCalendarBlockedIntervalProvider;
import com.autosecretary.features.task.application.internal.completion.TaskCompletionEffects;
import com.autosecretary.features.task.application.internal.completion.TaskTransitionRecorder;
import com.autosecretary.features.task.application.internal.mutations.TaskSlotUndoMutation;
import com.autosecretary.features.task.application.internal.mutations.TaskSlotToggleMutation;
import com.autosecretary.features.task.data.TaskDao;
import com.autosecretary.features.task.domain.TaskCompletionService;
import com.autosecretary.features.task.domain.TaskLifecycleManager;
import com.autosecretary.features.task.domain.scheduling.TaskSlotGenerator;
import com.autosecretary.features.task.domain.scheduling.TaskSlotGenerators;
import com.autosecretary.features.task.domain.scheduling.TransitionStat;
import com.autosecretary.features.task.domain.scheduling.TaskTransitionStatLoader;
import com.autosecretary.features.task.ui.TaskScheduleConfigViewModelFactory;
import com.autosecretary.features.task.ui.TaskCategoryViewModelFactory;
import com.autosecretary.features.task.ui.TaskCategoryWindowViewModelFactory;
import com.autosecretary.features.task.ui.edit.TaskEditViewModelFactory;
import com.autosecretary.features.task.ui.list.TaskViewModelFactory;
import com.autosecretary.features.task.ui.widget.TaskWidgetProvider;
import com.autosecretary.shared.WidgetRefreshNotifier;
import com.autosecretary.shared.ContentDocumentReader;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Manual dependency injection root for the entire application.
 *
 * <p>This class is the single place where all feature dependencies are wired together. It
 * replaces a DI framework (Dagger, Hilt, etc.) with plain Java factory methods. Every
 * non-trivial object that needs to be shared across the app — DAOs, repositories, use-cases,
 * and ViewModel factories — is created here and cached for reuse.</p>
 *
 * <h2>How to read this file</h2>
 * <ul>
 *   <li>Public {@code get…()} methods are called by UI components (Activities, Fragments,
 *       BroadcastReceivers). Each returns the shared, lazily-created instance of the object.</li>
 *   <li>{@link #initTaskGraph()} wires the entire task feature graph at once. It is guarded by
 *       a null-check so it runs only the first time any task-related getter is called.</li>
 *   <li>Budget and meal dependencies each have their own inline lazy-init guards.</li>
 *   <li>All methods that touch lazily-initialised state are {@code synchronized} to be safe
 *       if ever called from a background thread (e.g. from alarm receivers).</li>
 * </ul>
 *
 * <h2>Executors</h2>
 * A single-threaded {@link java.util.concurrent.ExecutorService} named {@code dbExecutor}
 * serializes Room/repository work. A separate {@code ioExecutor} owns file and network work.
 * Results are posted back to the main thread via {@link android.os.Handler}.
 *
 * <h2>Data reload</h2>
 * After a backup restore or factory reset (see {@link com.autosecretary.app.settings.SettingsDataService}),
 * all cached singletons must be rebuilt against the new database. Call {@link #resetForDataReload()}
 * to null out all cached instances; the next getter call will recreate them from scratch.
 *
 * <h2>Instantiation</h2>
 * Created once in {@link AutoSecretaryApplication#onCreate()}, accessed via
 * {@link AutoSecretaryApplication#from(android.content.Context)}.getAppCompositionRoot().
 */
public class AppCompositionRoot implements SettingsDataService.DatabaseLifecycle {
    private final Application app;
    private final ExecutorService dbExecutor;
    private final ExecutorService ioExecutor;
    private TaskViewModelFactory taskViewModelFactory;
    private AssistantViewModelFactory assistantViewModelFactory;
    private TaskChangeUndoHolder taskChangeUndoHolder;
    private AssistantConversation assistantConversation;
    private BudgetImportRoomRepository budgetImportRoomRepository;
    private TaskEditViewModelFactory taskEditViewModelFactory;
    private RegenerateScheduleUseCase regenerateScheduleUseCase;
    private BudgetViewModelFactory budgetViewModelFactory;
    private ContentDocumentReader contentDocumentReader;
    private TaskScheduleConfigRepository taskScheduleConfigRepository;
    private TaskScheduleConfigViewModelFactory taskScheduleConfigViewModelFactory;
    private TaskCategoryViewModelFactory taskCategoryViewModelFactory;
    private TaskCategoryRepository taskCategoryRepository;
    private TaskCategoryWindowRepository taskCategoryWindowRepository;
    private TaskCategoryWindowViewModelFactory taskCategoryWindowViewModelFactory;
    private TaskDao taskDao;
    private TaskSlotToggleMutation taskSlotToggleMutation;
    private BudgetRoomRepository budgetRoomRepository;
    private MealRepository mealRepository;
    private RecipeRepository recipeRepository;
    private PantryRepository pantryRepository;
    private MealPlannerDataService mealPlannerDataService;
    private MealPlannerViewModelFactory mealPlannerViewModelFactory;
    private final TaskCompletionService taskCompletionService;
    private final TaskLifecycleManager taskLifecycleManager;
    private final WidgetRefreshNotifier widgetRefreshNotifier;

    public AppCompositionRoot(Application app) {
        this.app = app;
        this.dbExecutor = newSingleThreadExecutor("DbExecutor");
        this.ioExecutor = newSingleThreadExecutor("IoExecutor");
        this.taskCompletionService = new TaskCompletionService();
        this.taskLifecycleManager = new TaskLifecycleManager();
        this.widgetRefreshNotifier = new WidgetRefreshNotifier() {
            @Override
            public void refreshTaskWidgets() {
                TaskWidgetProvider.notifyWidgetUpdate(app);
            }

            @Override
            public void refreshBudgetWidgets() {
                BudgetWidgetProvider.notifyWidgetUpdate(app);
            }
        };
    }

    private static ExecutorService newSingleThreadExecutor(String logTag) {
        return Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable);
            thread.setUncaughtExceptionHandler((t, e) ->
                    Log.e(logTag, "Background crash", e)
            );
            return thread;
        });
    }

    public ExecutorService getDbExecutor() {
        return dbExecutor;
    }

    public ExecutorService getIoExecutor() {
        return ioExecutor;
    }

    public synchronized TaskViewModelFactory getTaskViewModelFactory() {
        initTaskGraph();
        return taskViewModelFactory;
    }

    public synchronized TaskEditViewModelFactory getTaskEditViewModelFactory() {
        initTaskGraph();
        return taskEditViewModelFactory;
    }

    /**
     * Lazily wires the entire task feature dependency graph.
     *
     * <p>Called by any task-related getter on the first access. Subsequent calls return
     * immediately because {@code taskViewModelFactory} serves as the "already initialised" sentinel.
     * All task dependencies (DAO, use-cases, scheduler, ViewModel factory) are created together
     * because they form a tight graph — separating them would require storing many more
     * intermediate fields.</p>
     */
    private void initTaskGraph() {
        if (taskViewModelFactory != null) {
            return;
        }

        AppDatabase db = AppDatabase.getInstance(app);
        TaskDao dao = getTaskDao();
        Handler mainHandler = new Handler(Looper.getMainLooper());

        TaskScheduleConfigRepository scheduleConfigRepository =
                getTaskScheduleConfigRepository();
        TaskCategoryWindowRepository categoryWindowRepository =
                getTaskCategoryWindowRepository();

        TaskTransitionStatLoader transitionStatLoader = () ->
                db.taskTransitionStatDao().readAll().stream()
                        .map(s -> new TransitionStat(s.fromTaskId, s.toTaskId, s.weight))
                        .toList();
        TaskSlotGenerator generator = TaskSlotGenerators.builder(taskLifecycleManager)
                .logger(message -> Log.d("SlotGen", message))
                .schedulingWindowProvider(scheduleConfigRepository)
                .categoryWindowProvider(categoryWindowRepository)
                .calendarBlockedIntervalProvider(new DeviceCalendarBlockedIntervalProvider(app))
                .transitionStatLoader(transitionStatLoader)
                .taskBudgetEligibilityService(new TaskBudgetEligibilityFromBudgetLookup(getBudgetRoomRepository()))
                .build();
        TaskListItemMapper mapper = new TaskListItemMapper();
        TaskCalendarService taskCalendarService = new CalendarReader(app);

        ensureMealRepositories();
        TaskDataService taskDataService = new TaskDataService(
                dao,
                db.taskCategoryDao(),
                mapper,
                dbExecutor,
                mainHandler::post,
                mealRepository
        );
        taskEditViewModelFactory = new TaskEditViewModelFactory(
                taskDataService,
                new TaskEditReferenceDataUseCase(taskDataService, getBudgetRoomRepository()),
                new CreateDefaultTaskPrefSlotUseCase(),
                dbExecutor,
                mainHandler::post
        );
        BookTaskCompletionExpenseUseCase bookTaskCompletionExpenseUseCase =
                new BookTaskCompletionExpenseUseCase(getBudgetRoomRepository());
        TaskMealCompletionFromMealPlanner taskMealCompletionService = new TaskMealCompletionFromMealPlanner(
                mealRepository, recipeRepository, pantryRepository
        );
        TaskTransitionRecorder taskTransitionRecorder =
                new TaskTransitionRecorder(dao, db.taskTransitionStatDao());
        TaskCompletionEffects taskCompletionEffects = new TaskCompletionEffects(
                bookTaskCompletionExpenseUseCase,
                taskMealCompletionService,
                dao,
                widgetRefreshNotifier
        );

        taskSlotToggleMutation = new TaskSlotToggleMutation(
                dao,
                taskCompletionService,
                taskLifecycleManager,
                taskTransitionRecorder,
                mainHandler::post
        );
        CheckOffTaskUseCase checkOffTaskUseCase = new CheckOffTaskUseCase(
                taskSlotToggleMutation,
                dbExecutor,
                taskCompletionEffects
        );
        UndoTaskCheckOffUseCase undoTaskCheckOffUseCase = new UndoTaskCheckOffUseCase(
                new TaskSlotUndoMutation(dao, mainHandler::post),
                dbExecutor
        );
        regenerateScheduleUseCase = new RegenerateScheduleUseCase(
                dao,
                generator,
                dbExecutor,
                mainHandler::post,
                () -> SchedulingSettings.isSchedulingEnabled(app)
        );
        AdjustTaskProgressUseCase adjustTaskProgressUseCase = new AdjustTaskProgressUseCase(
                dao,
                dbExecutor,
                mainHandler::post,
                taskLifecycleManager,
                taskCompletionEffects,
                taskTransitionRecorder
        );

        // Claude multi-domain assistant: shared transport + key/endpoint/model stores, in-memory
        // conversation + undo stack; cross-feature reads/writes go through the meal/budget gateways.
        ClaudeApiKeyStore claudeApiKeyStore = new ClaudeApiKeyStore(app);
        ClaudeEndpointStore claudeEndpointStore = new ClaudeEndpointStore(app);
        ClaudeModelStore claudeModelStore = new ClaudeModelStore(app);
        ensureMealRepositories();
        AssistantMealGateway assistantMealGateway =
                new AssistantMealGateway(recipeRepository, mealRepository);
        AssistantBudgetGateway assistantBudgetGateway =
                new AssistantBudgetGateway(getBudgetRoomRepository());
        AssistantTransactionImportExecutor assistantImportExecutor =
                new AssistantTransactionImportExecutor(getBudgetImportRoomRepository(), getBudgetRoomRepository());
        assistantConversation = new AssistantConversation();
        taskChangeUndoHolder = new TaskChangeUndoHolder();
        DbCalls assistantDbCalls = new DbCalls(dbExecutor);
        List<AssistantTool> assistantTools = new ArrayList<>();
        assistantTools.addAll(new TaskTools(dao, db.taskCategoryDao(), assistantDbCalls).tools());
        assistantTools.addAll(new MealTools(assistantMealGateway, assistantDbCalls).tools());
        assistantTools.addAll(new BudgetTools(assistantBudgetGateway, assistantImportExecutor,
                assistantConversation::currentStatement, assistantDbCalls).tools());
        AssistantChatUseCase assistantChatUseCase = new AssistantChatUseCase(
                new ClaudeMessagesClient(), assistantConversation,
                new AssistantToolRegistry(assistantTools),
                claudeApiKeyStore, claudeEndpointStore, claudeModelStore,
                ioExecutor, mainHandler::post);
        ApplyTaskChangesUseCase applyTaskChangesUseCase = new ApplyTaskChangesUseCase(
                db, dao, db.taskCategoryDao(), db.taskCategoryWindowDao(),
                taskChangeUndoHolder, dbExecutor, mainHandler::post);
        ConfirmAssistantProposalUseCase confirmAssistantProposalUseCase = new ConfirmAssistantProposalUseCase(
                applyTaskChangesUseCase, assistantMealGateway, assistantImportExecutor,
                dbExecutor, mainHandler::post);
        UndoTaskChangesUseCase undoTaskChangesUseCase = new UndoTaskChangesUseCase(
                db, dao, db.taskCategoryDao(), db.taskCategoryWindowDao(),
                taskChangeUndoHolder, dbExecutor, mainHandler::post);
        assistantViewModelFactory = new AssistantViewModelFactory(
                assistantChatUseCase, confirmAssistantProposalUseCase, undoTaskChangesUseCase);

        taskViewModelFactory = new TaskViewModelFactory(
                taskDataService,
                checkOffTaskUseCase,
                undoTaskCheckOffUseCase,
                regenerateScheduleUseCase,
                adjustTaskProgressUseCase,
                taskCalendarService,
                scheduleConfigRepository,
                widgetRefreshNotifier
        );
    }

    public synchronized AssistantViewModelFactory getAssistantViewModelFactory() {
        initTaskGraph();
        return assistantViewModelFactory;
    }

    public synchronized TaskScheduleConfigRepository getTaskScheduleConfigRepository() {
        if (taskScheduleConfigRepository == null) {
            AppDatabase db = AppDatabase.getInstance(app);
            taskScheduleConfigRepository = new TaskScheduleConfigRepository(db.taskScheduleConfigDao());
        }
        return taskScheduleConfigRepository;
    }

    public synchronized TaskCategoryRepository getTaskCategoryRepository() {
        if (taskCategoryRepository == null) {
            AppDatabase db = AppDatabase.getInstance(app);
            taskCategoryRepository = new TaskCategoryRepository(
                    db.taskCategoryDao(), db.taskCategoryWindowDao());
        }
        return taskCategoryRepository;
    }

    public synchronized TaskCategoryWindowRepository getTaskCategoryWindowRepository() {
        if (taskCategoryWindowRepository == null) {
            AppDatabase db = AppDatabase.getInstance(app);
            taskCategoryWindowRepository = new TaskCategoryWindowRepository(
                    db.taskCategoryWindowDao(), db.taskCategoryDao());
        }
        return taskCategoryWindowRepository;
    }

    public synchronized TaskScheduleConfigViewModelFactory getTaskScheduleConfigViewModelFactory() {
        if (taskScheduleConfigViewModelFactory == null) {
            Handler mainHandler = new Handler(Looper.getMainLooper());
            taskScheduleConfigViewModelFactory = new TaskScheduleConfigViewModelFactory(
                    getTaskScheduleConfigRepository(),
                    getRegenerateScheduleUseCase(),
                    dbExecutor,
                    mainHandler::post
            );
        }
        return taskScheduleConfigViewModelFactory;
    }

    public synchronized TaskCategoryWindowViewModelFactory getTaskCategoryWindowViewModelFactory() {
        if (taskCategoryWindowViewModelFactory == null) {
            Handler mainHandler = new Handler(Looper.getMainLooper());
            taskCategoryWindowViewModelFactory = new TaskCategoryWindowViewModelFactory(
                    getTaskCategoryWindowRepository(),
                    getTaskCategoryRepository(),
                    dbExecutor,
                    mainHandler::post
            );
        }
        return taskCategoryWindowViewModelFactory;
    }

    public synchronized TaskCategoryViewModelFactory getTaskCategoryViewModelFactory() {
        if (taskCategoryViewModelFactory == null) {
            Handler mainHandler = new Handler(Looper.getMainLooper());
            taskCategoryViewModelFactory = new TaskCategoryViewModelFactory(
                    getTaskCategoryRepository(),
                    dbExecutor,
                    mainHandler::post
            );
        }
        return taskCategoryViewModelFactory;
    }

    public synchronized RegenerateScheduleUseCase getRegenerateScheduleUseCase() {
        initTaskGraph();
        return regenerateScheduleUseCase;
    }

    public WidgetRefreshNotifier getWidgetRefreshNotifier() {
        return widgetRefreshNotifier;
    }

    public synchronized BudgetViewModelFactory getBudgetViewModelFactory() {
        if (budgetViewModelFactory == null) {
            BudgetRoomRepository repository = getBudgetRoomRepository();
            BudgetImportRoomRepository importRepository = getBudgetImportRoomRepository();

            StatementFileParser parser = new StatementFileParser(
                    new ClaudeStatementApiClient(
                            new ClaudeMessagesClient(),
                            new ClaudeEndpointStore(app),
                            new ClaudeModelStore(app)),
                    new ClaudeApiKeyStore(app),
                    importRepository
            );

            BudgetImportUseCase importUseCase = new BudgetImportUseCase(
                    importRepository, parser
            );

            ApplyRecurringSuggestionsUseCase applyRecurringUseCase = new ApplyRecurringSuggestionsUseCase(
                    importRepository
            );

            CreateTransferUseCase createTransferUseCase = new CreateTransferUseCase(repository);
            CalculateEffectiveBudgetLimitUseCase calculateEffectiveLimitUseCase =
                    new CalculateEffectiveBudgetLimitUseCase(repository);
            LoadBudgetOverviewUseCase loadBudgetOverviewUseCase =
                    new LoadBudgetOverviewUseCase(repository, app.getResources());

            budgetViewModelFactory = new BudgetViewModelFactory(
                    dbExecutor,
                    ioExecutor,
                    importUseCase,
                    applyRecurringUseCase,
                    createTransferUseCase,
                    new BudgetTransactionMutationUseCase(repository),
                    new ResolveBudgetAccountUseCase(repository),
                    new LoadBudgetLimitOverviewUseCase(repository, calculateEffectiveLimitUseCase),
                    new BudgetSeedService(repository),
                    loadBudgetOverviewUseCase
            );
        }
        return budgetViewModelFactory;
    }

    public synchronized ContentDocumentReader getContentDocumentReader() {
        if (contentDocumentReader == null) {
            contentDocumentReader = new ContentDocumentReader(app);
        }
        return contentDocumentReader;
    }

    public LoadBudgetWidgetSummaryUseCase createLoadBudgetWidgetSummaryUseCase() {
        return new LoadBudgetWidgetSummaryUseCase(getBudgetRoomRepository());
    }

    public LoadTaskWidgetItemsUseCase createLoadTaskWidgetItemsUseCase() {
        return new LoadTaskWidgetItemsUseCase(
                getTaskDao(),
                AppDatabase.getInstance(app).taskCategoryDao(),
                new TaskListItemMapper());
    }

    public synchronized BudgetRoomRepository getBudgetRoomRepository() {
        if (budgetRoomRepository == null) {
            AppDatabase db = AppDatabase.getInstance(app);
            budgetRoomRepository = new BudgetRoomRepository(
                    db.budgetAccountCategoryDao(),
                    db.budgetTransactionDao(),
                    db.budgetLimitDao(),
                    db.budgetRecurringTemplateDao(),
                    db
            );
        }
        return budgetRoomRepository;
    }

    /** Cached import repository, shared by the budget import graph and the assistant import executor. */
    public synchronized BudgetImportRoomRepository getBudgetImportRoomRepository() {
        if (budgetImportRoomRepository == null) {
            AppDatabase db = AppDatabase.getInstance(app);
            budgetImportRoomRepository = new BudgetImportRoomRepository(
                    db.budgetImportDao(),
                    db.budgetRecurringTemplateDao(),
                    db.budgetTransactionDao(),
                    db.budgetAccountCategoryDao()
            );
        }
        return budgetImportRoomRepository;
    }

    public synchronized TaskSlotToggleMutation getTaskSlotToggleMutation() {
        initTaskGraph();
        return taskSlotToggleMutation;
    }

    public synchronized TaskDao getTaskDao() {
        if (taskDao == null) {
            taskDao = AppDatabase.getInstance(app).taskDao();
        }
        return taskDao;
    }

    /**
     * Nulls out all cached singletons so they will be recreated on the next access.
     *
     * <p>Call this after a backup restore or factory reset, <em>before</em> calling
     * {@link android.app.Activity#recreate()} on the hosting activity. The next call to any
     * {@code get…()} method will rebuild the full dependency graph against the new database
     * instance. Executors are intentionally not reset: they have no per-database state.</p>
     *
     * @see com.autosecretary.app.settings.SettingsDataService
     */
    public synchronized void resetForDataReload() {
        taskViewModelFactory = null;
        assistantViewModelFactory = null;
        if (taskChangeUndoHolder != null) {
            taskChangeUndoHolder.clear();
        }
        taskChangeUndoHolder = null;
        if (assistantConversation != null) {
            assistantConversation.clear();
        }
        assistantConversation = null;
        budgetImportRoomRepository = null;
        taskEditViewModelFactory = null;
        regenerateScheduleUseCase = null;
        budgetViewModelFactory = null;
        contentDocumentReader = null;
        taskScheduleConfigRepository = null;
        taskScheduleConfigViewModelFactory = null;
        taskCategoryViewModelFactory = null;
        taskCategoryRepository = null;
        taskCategoryWindowRepository = null;
        taskCategoryWindowViewModelFactory = null;
        taskDao = null;
        taskSlotToggleMutation = null;
        budgetRoomRepository = null;
        mealPlannerDataService = null;
        mealPlannerViewModelFactory = null;
        mealRepository = null;
        recipeRepository = null;
        pantryRepository = null;
    }

    public synchronized MealPlannerDataService getMealPlannerDataService() {
        if (mealPlannerDataService == null) {
            ensureMealRepositories();
            Handler mainHandler = new Handler(Looper.getMainLooper());
            mealPlannerDataService = new MealPlannerDataService(
                    mealRepository, recipeRepository, pantryRepository,
                    dbExecutor, mainHandler::post);
        }
        return mealPlannerDataService;
    }

    public synchronized MealPlannerViewModelFactory getMealPlannerViewModelFactory() {
        if (mealPlannerViewModelFactory == null) {
            mealPlannerViewModelFactory = new MealPlannerViewModelFactory(getMealPlannerDataService());
        }
        return mealPlannerViewModelFactory;
    }


    private void ensureMealRepositories() {
        if (mealRepository != null) return;
        AppDatabase db = AppDatabase.getInstance(app);
        mealRepository = new MealRoomRepository(
                db.mealPlanDao(),
                db.mealConsumptionLogDao(),
                db.mealHouseholdMemberDao(),
                db.mealCookingPreferencesDao(),
                db.mealWeeklyFoodTargetDao());
        recipeRepository = new MealRecipeRoomRepository(db.mealRecipeDao(), db.mealIngredientDao());
        pantryRepository = new MealPantryRoomRepository(db.mealPantryDao());
    }

    @Override
    public void runDatabaseCheckpoint() {
        AppDatabase database = AppDatabase.getInstance(app);
        database.getOpenHelper().getWritableDatabase().execSQL("PRAGMA wal_checkpoint(FULL)");
    }

    @Override
    public void closeDatabaseForFileReplacement() {
        AppDatabase.closeAndReset();
    }

    @Override
    public void openDatabaseAfterFileReplacement() {
        AppDatabase.getInstance(app);
        resetForDataReload();
    }
}

package com.autosecretary.app;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.autosecretary.database.AppDatabase;
import com.autosecretary.features.budget.application.importing.ApplyRecurringSuggestionsUseCase;
import com.autosecretary.features.budget.application.importing.BudgetImportUseCase;
import com.autosecretary.features.budget.data.api.ClaudeStatementApiClient;
import com.autosecretary.features.budget.data.api.ClaudeApiKeyStore;
import com.autosecretary.features.budget.application.importing.StatementFileParser;
import com.autosecretary.features.budget.application.CreateTransferUseCase;
import com.autosecretary.features.budget.application.LoadBudgetWidgetSummaryUseCase;
import com.autosecretary.features.budget.data.repository.BudgetImportRoomRepository;
import com.autosecretary.features.budget.data.repository.BudgetRoomRepository;
import com.autosecretary.features.budget.ui.BudgetViewModelFactory;
import com.autosecretary.features.meal.application.TaskMealIntegrationService;
import com.autosecretary.features.meal.application.MealPlannerPresenter;
import com.autosecretary.features.meal.data.internal.repository.StorageMealRepository;
import com.autosecretary.features.meal.data.internal.repository.StoragePantryRepository;
import com.autosecretary.features.meal.data.internal.repository.StorageRecipeRepository;
import com.autosecretary.features.meal.data.internal.storage.InMemoryMealStorage;
import com.autosecretary.features.task.application.AdjustTaskProgressUseCase;
import com.autosecretary.features.task.application.CheckOffTaskUseCase;
import com.autosecretary.features.task.application.RegenerateScheduleUseCase;
import com.autosecretary.features.task.application.TaskDataService;
import com.autosecretary.features.task.application.calendar.TaskCalendarService;
import com.autosecretary.features.task.application.listmodel.TaskListItemMapper;
import com.autosecretary.features.task.application.config.TaskScheduleConfigRepository;
import com.autosecretary.features.task.application.internal.budget.BookTaskCompletionExpenseUseCase;
import com.autosecretary.features.task.application.internal.budget.TaskBudgetEligibilityFromBudgetLookup;
import com.autosecretary.features.task.application.internal.calendar.CalendarReader;
import com.autosecretary.features.task.application.internal.calendar.DeviceCalendarBlockedIntervalProvider;
import com.autosecretary.features.task.application.internal.mutations.TaskSlotToggleMutation;
import com.autosecretary.features.task.data.TaskDao;
import com.autosecretary.features.task.domain.TaskCompletionService;
import com.autosecretary.features.task.domain.TaskLifecycleManager;
import com.autosecretary.features.task.domain.scheduling.TaskSlotGenerator;
import com.autosecretary.features.task.domain.scheduling.TransitionStat;
import com.autosecretary.features.task.domain.scheduling.TaskTransitionStatLoader;
import com.autosecretary.features.task.domain.internal.scheduling.DefaultTaskSlotGenerator;
import com.autosecretary.features.task.ui.list.TaskViewModelFactory;

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
 * <h2>Shared executor</h2>
 * A single-threaded {@link java.util.concurrent.ExecutorService} is used by all features.
 * Running all DB and network work on one thread prevents concurrent writes to Room and
 * simplifies reasoning about ordering. Results are posted back to the main thread via
 * {@link android.os.Handler}.
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
public class AppCompositionRoot {
    private final Application app;
    private final ExecutorService sharedExecutor;
    private TaskViewModelFactory taskViewModelFactory;
    private RegenerateScheduleUseCase regenerateScheduleUseCase;
    private BudgetViewModelFactory budgetViewModelFactory;
    private TaskScheduleConfigRepository taskScheduleConfigRepository;
    private TaskDao taskDao;
    private TaskSlotToggleMutation taskSlotToggleMutation;
    private BudgetRoomRepository budgetRoomRepository;
    private final InMemoryMealStorage mealStorage;
    private MealPlannerPresenter mealPlannerPresenter;
    private final TaskCompletionService taskCompletionService;
    private final TaskLifecycleManager taskLifecycleManager;

    public AppCompositionRoot(Application app) {
        this.app = app;
        this.sharedExecutor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable);
            thread.setUncaughtExceptionHandler((t, e) ->
                    Log.e("SharedExecutor", "Background crash", e)
            );
            return thread;
        });
        this.mealStorage = new InMemoryMealStorage();
        this.taskCompletionService = new TaskCompletionService();
        this.taskLifecycleManager = new TaskLifecycleManager();
    }

    public ExecutorService getSharedExecutor() {
        return sharedExecutor;
    }

    public synchronized TaskViewModelFactory getTaskViewModelFactory() {
        initTaskGraph();
        return taskViewModelFactory;
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

        TaskTransitionStatLoader transitionStatLoader = () ->
                db.taskTransitionStatDao().readAll().stream()
                        .map(s -> new TransitionStat(s.fromTaskId, s.toTaskId, s.weight))
                        .toList();
        TaskSlotGenerator generator = new DefaultTaskSlotGenerator(
                taskLifecycleManager,
                message -> Log.d("SlotGen", message),
                scheduleConfigRepository,
                new DeviceCalendarBlockedIntervalProvider(app),
                transitionStatLoader,
                new TaskBudgetEligibilityFromBudgetLookup(getBudgetRoomRepository())
        );
        TaskListItemMapper mapper = new TaskListItemMapper();
        TaskCalendarService taskCalendarService = new CalendarReader(app);

        TaskDataService taskDataService = new TaskDataService(
                dao,
                mapper,
                sharedExecutor,
                mainHandler::post
        );
        BookTaskCompletionExpenseUseCase bookTaskCompletionExpenseUseCase =
                new BookTaskCompletionExpenseUseCase(getBudgetRoomRepository());
        TaskMealIntegrationService taskMealIntegrationService = new TaskMealIntegrationService(
                new StorageMealRepository(mealStorage),
                new StorageRecipeRepository(mealStorage),
                new StoragePantryRepository(mealStorage)
        );

        taskSlotToggleMutation = new TaskSlotToggleMutation(
                dao,
                taskCompletionService,
                taskLifecycleManager,
                db.taskTransitionStatDao(),
                mainHandler::post,
                db
        );
        CheckOffTaskUseCase checkOffTaskUseCase = new CheckOffTaskUseCase(
                taskSlotToggleMutation,
                dao,
                sharedExecutor,
                bookTaskCompletionExpenseUseCase,
                app,
                taskMealIntegrationService
        );
        regenerateScheduleUseCase = new RegenerateScheduleUseCase(
                dao,
                generator,
                sharedExecutor,
                mainHandler::post
        );
        AdjustTaskProgressUseCase adjustTaskProgressUseCase = new AdjustTaskProgressUseCase(
                dao,
                sharedExecutor,
                mainHandler::post,
                taskLifecycleManager
        );

        taskViewModelFactory = new TaskViewModelFactory(
                app,
                taskDataService,
                checkOffTaskUseCase,
                regenerateScheduleUseCase,
                adjustTaskProgressUseCase,
                taskCalendarService
        );
    }

    public synchronized TaskScheduleConfigRepository getTaskScheduleConfigRepository() {
        if (taskScheduleConfigRepository == null) {
            AppDatabase db = AppDatabase.getInstance(app);
            taskScheduleConfigRepository = new TaskScheduleConfigRepository(db.taskScheduleConfigDao());
        }
        return taskScheduleConfigRepository;
    }

    public synchronized RegenerateScheduleUseCase getRegenerateScheduleUseCase() {
        initTaskGraph();
        return regenerateScheduleUseCase;
    }

    public synchronized BudgetViewModelFactory getBudgetViewModelFactory() {
        if (budgetViewModelFactory == null) {
            AppDatabase db = AppDatabase.getInstance(app);
            BudgetRoomRepository repository = getBudgetRoomRepository();

            BudgetImportRoomRepository importRepository = new BudgetImportRoomRepository(
                    db.budgetImportDao(),
                    db.budgetRecurringTemplateDao(),
                    db.budgetTransactionDao(),
                    db.budgetLookupDao(),
                    () -> {}
            );

            StatementFileParser parser = new StatementFileParser(
                    new ClaudeStatementApiClient(),
                    new ClaudeApiKeyStore(app),
                    importRepository
            );

            BudgetImportUseCase importUseCase = new BudgetImportUseCase(
                    importRepository, parser, sharedExecutor
            );

            ApplyRecurringSuggestionsUseCase applyRecurringUseCase = new ApplyRecurringSuggestionsUseCase(
                    importRepository, sharedExecutor
            );

            CreateTransferUseCase createTransferUseCase = new CreateTransferUseCase(repository);

            budgetViewModelFactory = new BudgetViewModelFactory(
                    repository,
                    sharedExecutor,
                    importUseCase,
                    applyRecurringUseCase,
                    createTransferUseCase
            );
        }
        return budgetViewModelFactory;
    }

    public LoadBudgetWidgetSummaryUseCase createLoadBudgetWidgetSummaryUseCase() {
        return new LoadBudgetWidgetSummaryUseCase(getBudgetRoomRepository());
    }

    private synchronized BudgetRoomRepository getBudgetRoomRepository() {
        if (budgetRoomRepository == null) {
            AppDatabase db = AppDatabase.getInstance(app);
            budgetRoomRepository = new BudgetRoomRepository(
                    db.budgetLookupDao(),
                    db.budgetTransactionDao(),
                    db.budgetLimitDao(),
                    db.budgetRecurringTemplateDao(),
                    db
            );
        }
        return budgetRoomRepository;
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
     * instance. The shared executor and the meal storage are intentionally not reset: the
     * executor has no per-database state, and meal data lives in memory only.</p>
     *
     * @see com.autosecretary.app.settings.SettingsDataService
     */
    public synchronized void resetForDataReload() {
        taskViewModelFactory = null;
        regenerateScheduleUseCase = null;
        budgetViewModelFactory = null;
        taskScheduleConfigRepository = null;
        taskDao = null;
        taskSlotToggleMutation = null;
        budgetRoomRepository = null;
        mealPlannerPresenter = null;
    }

    // Not synchronized: MealPlannerPresenter is only accessed from the main thread
    // (via MealPlannerFragment). No concurrent access is possible, so no lock is needed.
    public MealPlannerPresenter getMealPlannerPresenter() {
        if (mealPlannerPresenter == null) {
            mealPlannerPresenter = new MealPlannerPresenter(
                    new StorageMealRepository(mealStorage),
                    new StorageRecipeRepository(mealStorage),
                    new StoragePantryRepository(mealStorage)
            );
        }
        return mealPlannerPresenter;
    }
}

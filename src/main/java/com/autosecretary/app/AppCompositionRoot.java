package com.autosecretary.app;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.autosecretary.database.AppDatabase;
import com.autosecretary.features.budget.application.importing.ApplyRecurringSuggestionsUseCase;
import com.autosecretary.features.budget.application.importing.BudgetImportUseCase;
import com.autosecretary.features.budget.data.api.ClaudeApiKeyStore;
import com.autosecretary.features.budget.application.importing.ClaudeStatementApiClient;
import com.autosecretary.features.budget.application.importing.StatementFileParser;
import com.autosecretary.features.budget.application.CreateTransferUseCase;
import com.autosecretary.features.budget.application.LoadBudgetWidgetSummaryUseCase;
import com.autosecretary.features.budget.data.repository.BudgetImportRoomRepository;
import com.autosecretary.features.budget.data.repository.BudgetRoomRepository;
import com.autosecretary.features.budget.ui.BudgetViewModelFactory;
import com.autosecretary.features.meal.application.TaskMealIntegrationService;
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
import com.autosecretary.features.task.application.internal.mutations.TaskSlotToggleMutation;
import com.autosecretary.features.task.data.TaskDao;
import com.autosecretary.features.task.domain.TaskCompletionService;
import com.autosecretary.features.task.domain.TaskLifecycleManager;
import com.autosecretary.features.task.domain.TaskSlotGenerator;
import com.autosecretary.features.task.domain.TransitionStat;
import com.autosecretary.features.task.domain.TaskTransitionStatLoader;
import com.autosecretary.features.task.domain.internal.scheduling.DefaultTaskSlotGenerator;
import com.autosecretary.features.task.ui.list.TaskViewModelFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
    private TaskCompletionService taskCompletionService;
    private TaskLifecycleManager taskLifecycleManager;

    public AppCompositionRoot(Application app) {
        this.app = app;
        this.sharedExecutor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable);
            thread.setUncaughtExceptionHandler((t, e) ->
                    Log.e("TaskUseCase", "Background crash", e)
            );
            return thread;
        });
    }

    public ExecutorService getSharedExecutor() {
        return sharedExecutor;
    }

    public synchronized TaskViewModelFactory getTaskViewModelFactory() {
        initTaskGraph();
        return taskViewModelFactory;
    }

    private void initTaskGraph() {
        if (taskViewModelFactory != null) {
            return;
        }

        AppDatabase db = AppDatabase.getInstance(app);
        TaskDao taskDao = getTaskDao();
        Handler mainHandler = new Handler(Looper.getMainLooper());

        TaskScheduleConfigRepository scheduleConfigRepository =
                getTaskScheduleConfigRepository();

        TaskTransitionStatLoader transitionStatLoader = () ->
                db.taskTransitionStatDao().readAll().stream()
                        .map(s -> new TransitionStat(s.fromTaskId, s.toTaskId, s.weight))
                        .toList();
        TaskSlotGenerator generator = new DefaultTaskSlotGenerator(
                getTaskLifecycleManager(),
                message -> Log.d("SlotGen", message),
                scheduleConfigRepository,
                new DeviceCalendarBlockedIntervalProvider(app),
                transitionStatLoader,
                new TaskBudgetEligibilityFromBudgetLookup(getBudgetRoomRepository())
        );
        TaskListItemMapper mapper = new TaskListItemMapper();
        TaskCalendarService taskCalendarService = new CalendarReader(app);

        TaskDataService taskDataService = new TaskDataService(
                taskDao,
                mapper,
                sharedExecutor,
                mainHandler::post
        );
        BookTaskCompletionExpenseUseCase bookTaskCompletionExpenseUseCase =
                new BookTaskCompletionExpenseUseCase(getBudgetRoomRepository());
        InMemoryMealStorage mealStorage = new InMemoryMealStorage();
        TaskMealIntegrationService taskMealIntegrationService = new TaskMealIntegrationService(
                new StorageMealRepository(mealStorage),
                new StorageRecipeRepository(mealStorage),
                new StoragePantryRepository(mealStorage)
        );

        taskSlotToggleMutation = new TaskSlotToggleMutation(
                taskDao,
                getTaskCompletionService(),
                getTaskLifecycleManager(),
                db.taskTransitionStatDao(),
                mainHandler::post,
                db
        );
        CheckOffTaskUseCase checkOffTaskUseCase = new CheckOffTaskUseCase(
                taskSlotToggleMutation,
                taskDao,
                sharedExecutor,
                bookTaskCompletionExpenseUseCase,
                app,
                taskMealIntegrationService
        );
        regenerateScheduleUseCase = new RegenerateScheduleUseCase(
                taskDao,
                generator,
                sharedExecutor,
                mainHandler::post
        );
        AdjustTaskProgressUseCase adjustTaskProgressUseCase = new AdjustTaskProgressUseCase(
                taskDao,
                sharedExecutor,
                mainHandler::post,
                getTaskLifecycleManager()
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
        if (budgetViewModelFactory != null) {
            return budgetViewModelFactory;
        }

        AppDatabase db = AppDatabase.getInstance(app);
        Handler mainHandler = new Handler(Looper.getMainLooper());

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
                mainHandler::post,
                importUseCase,
                applyRecurringUseCase,
                createTransferUseCase
        );

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

    public synchronized TaskCompletionService getTaskCompletionService() {
        if (taskCompletionService == null) {
            taskCompletionService = new TaskCompletionService();
        }
        return taskCompletionService;
    }

    public synchronized TaskLifecycleManager getTaskLifecycleManager() {
        if (taskLifecycleManager == null) {
            taskLifecycleManager = new TaskLifecycleManager();
        }
        return taskLifecycleManager;
    }

    public synchronized void resetForDataReload() {
        taskViewModelFactory = null;
        regenerateScheduleUseCase = null;
        budgetViewModelFactory = null;
        taskScheduleConfigRepository = null;
        taskDao = null;
        taskSlotToggleMutation = null;
        budgetRoomRepository = null;
    }
}

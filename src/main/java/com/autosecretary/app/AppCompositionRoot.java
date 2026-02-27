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
import com.autosecretary.features.task.data.TaskDAO;
import com.autosecretary.features.task.domain.TaskCompletionService;
import com.autosecretary.features.task.domain.TaskLifecycleManager;
import com.autosecretary.features.task.domain.TaskSlotGenerator;
import com.autosecretary.features.task.domain.internal.scheduling.DefaultTaskSlotGenerator;
import com.autosecretary.features.task.ui.list.TaskViewModelFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AppCompositionRoot {
    private final Application app;
    private final ExecutorService taskUseCaseExecutor;
    private TaskViewModelFactory taskViewModelFactory;
    private RegenerateScheduleUseCase regenerateScheduleUseCase;
    private BudgetViewModelFactory budgetViewModelFactory;
    private TaskScheduleConfigRepository taskScheduleConfigRepository;
    private TaskDAO taskDAO;
    private TaskSlotToggleMutation taskSlotToggleMutation;
    private BudgetRoomRepository budgetRoomRepository;
    private TaskCompletionService taskCompletionService;
    private TaskLifecycleManager taskLifecycleManager;

    public AppCompositionRoot(Application app) {
        this.app = app;
        this.taskUseCaseExecutor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable);
            thread.setUncaughtExceptionHandler((t, e) ->
                    Log.e("TaskUseCase", "Background crash", e)
            );
            return thread;
        });
    }

    public ExecutorService getSharedExecutor() {
        return taskUseCaseExecutor;
    }

    public TaskViewModelFactory createTaskViewModelFactory() {
        if (taskViewModelFactory != null) {
            return taskViewModelFactory;
        }

        AppDatabase db = AppDatabase.getInstance(app);
        TaskDAO taskDao = db.taskDao();
        Handler mainHandler = new Handler(Looper.getMainLooper());

        TaskScheduleConfigRepository scheduleConfigRepository =
                getTaskScheduleConfigRepository();

        TaskSlotGenerator generator = new DefaultTaskSlotGenerator(
                getTaskLifecycleManager(),
                message -> Log.d("SlotGen", message),
                scheduleConfigRepository,
                new DeviceCalendarBlockedIntervalProvider(app),
                db.taskTransitionStatDao(),
                new TaskBudgetEligibilityFromBudgetLookup(db.budgetLookupDao())
        );
        TaskListItemMapper mapper = new TaskListItemMapper();
        TaskCalendarService taskCalendarService = new CalendarReader(app);

        TaskDataService taskDataService = new TaskDataService(
                taskDao,
                mapper,
                taskUseCaseExecutor,
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
                taskUseCaseExecutor,
                bookTaskCompletionExpenseUseCase,
                app,
                taskMealIntegrationService
        );
        regenerateScheduleUseCase = new RegenerateScheduleUseCase(
                taskDao,
                generator,
                taskUseCaseExecutor
        );
        AdjustTaskProgressUseCase adjustTaskProgressUseCase = new AdjustTaskProgressUseCase(
                taskDao,
                taskUseCaseExecutor,
                mainHandler::post
        );

        taskViewModelFactory = new TaskViewModelFactory(
                app,
                taskDataService,
                checkOffTaskUseCase,
                regenerateScheduleUseCase,
                taskCalendarService,
                adjustTaskProgressUseCase
        );

        return taskViewModelFactory;
    }

    public synchronized TaskScheduleConfigRepository getTaskScheduleConfigRepository() {
        if (taskScheduleConfigRepository == null) {
            AppDatabase db = AppDatabase.getInstance(app);
            taskScheduleConfigRepository = new TaskScheduleConfigRepository(db.taskScheduleConfigDao());
        }
        return taskScheduleConfigRepository;
    }

    public RegenerateScheduleUseCase getRegenerateScheduleUseCase() {
        if (regenerateScheduleUseCase == null) {
            createTaskViewModelFactory();
        }
        return regenerateScheduleUseCase;
    }

    public BudgetViewModelFactory createBudgetViewModelFactory() {
        if (budgetViewModelFactory != null) {
            return budgetViewModelFactory;
        }

        AppDatabase db = AppDatabase.getInstance(app);
        Handler mainHandler = new Handler(Looper.getMainLooper());

        BudgetRoomRepository repository = getBudgetRoomRepository();

        BudgetImportRoomRepository importRepository = new BudgetImportRoomRepository(
                db.budgetImportDao(),
                db.budgetRecurringTemplateDao(),
                db.transactionDao(),
                db.budgetLookupDao(),
                () -> {}
        );

        StatementFileParser parser = new StatementFileParser(
                new ClaudeStatementApiClient(),
                new ClaudeApiKeyStore(app),
                importRepository
        );

        BudgetImportUseCase importUseCase = new BudgetImportUseCase(
                importRepository, parser, taskUseCaseExecutor
        );

        ApplyRecurringSuggestionsUseCase applyRecurringUseCase = new ApplyRecurringSuggestionsUseCase(
                importRepository, taskUseCaseExecutor
        );

        CreateTransferUseCase createTransferUseCase = new CreateTransferUseCase(repository);

        budgetViewModelFactory = new BudgetViewModelFactory(
                repository,
                taskUseCaseExecutor,
                mainHandler::post,
                importUseCase,
                applyRecurringUseCase,
                createTransferUseCase
        );

        return budgetViewModelFactory;
    }

    public LoadBudgetWidgetSummaryUseCase createLoadBudgetWidgetSummaryUseCase() {
        AppDatabase db = AppDatabase.getInstance(app);
        return new LoadBudgetWidgetSummaryUseCase(
                db.transactionDao(),
                db.budgetLimitDao()
        );
    }

    private synchronized BudgetRoomRepository getBudgetRoomRepository() {
        if (budgetRoomRepository == null) {
            AppDatabase db = AppDatabase.getInstance(app);
            budgetRoomRepository = new BudgetRoomRepository(
                    db.budgetLookupDao(),
                    db.transactionDao(),
                    db.budgetLimitDao(),
                    db.budgetRecurringTemplateDao(),
                    db
            );
        }
        return budgetRoomRepository;
    }

    public synchronized TaskSlotToggleMutation getTaskSlotToggleMutation() {
        if (taskSlotToggleMutation == null) {
            createTaskViewModelFactory();
        }
        return taskSlotToggleMutation;
    }

    public synchronized TaskDAO getTaskDAO() {
        if (taskDAO == null) {
            taskDAO = AppDatabase.getInstance(app).taskDao();
        }
        return taskDAO;
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
        taskDAO = null;
        taskSlotToggleMutation = null;
        budgetRoomRepository = null;
        taskCompletionService = null;
        taskLifecycleManager = null;
    }
}

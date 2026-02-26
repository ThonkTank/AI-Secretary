package com.autosecretary.app;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.autosecretary.database.AppDatabase;
import com.autosecretary.features.budget.application.importing.ApplyRecurringSuggestionsUseCase;
import com.autosecretary.features.budget.application.importing.BudgetImportUseCase;
import com.autosecretary.features.budget.application.importing.ClaudeApiKeyStore;
import com.autosecretary.features.budget.application.importing.ClaudeStatementApiClient;
import com.autosecretary.features.budget.application.importing.StatementFileParser;
import com.autosecretary.features.budget.application.CreateTransferUseCase;
import com.autosecretary.features.budget.data.repository.BudgetImportRoomRepository;
import com.autosecretary.features.budget.data.repository.BudgetRoomRepository;
import com.autosecretary.features.budget.ui.BudgetViewModelFactory;
import com.autosecretary.features.task.application.AdjustTaskProgressUseCase;
import com.autosecretary.features.task.application.CheckOffTaskUseCase;
import com.autosecretary.features.task.application.RegenerateScheduleUseCase;
import com.autosecretary.features.task.application.TaskAsyncDataService;
import com.autosecretary.features.task.application.calendar.TaskCalendarService;
import com.autosecretary.features.task.application.listmodel.TaskListItemMapper;
import com.autosecretary.features.task.application.config.TaskScheduleConfigRepository;
import com.autosecretary.features.task.application.config.TaskScheduleConfigService;
import com.autosecretary.features.task.application.internal.budget.BookTaskCompletionExpenseUseCase;
import com.autosecretary.features.task.application.internal.budget.TaskBudgetEligibilityFromBudgetLookup;
import com.autosecretary.features.task.application.internal.calendar.CalendarReader;
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
    private TaskScheduleConfigService taskScheduleConfigService;

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

    public TaskViewModelFactory createTaskViewModelFactory() {
        if (taskViewModelFactory != null) {
            return taskViewModelFactory;
        }

        AppDatabase db = AppDatabase.getInstance(app);
        TaskDAO taskDao = db.taskDao();
        Handler mainHandler = new Handler(Looper.getMainLooper());

        TaskLifecycleManager lifecycleManager = new TaskLifecycleManager();
        TaskCompletionService completionService = new TaskCompletionService();

        TaskScheduleConfigRepository scheduleConfigRepository =
                new TaskScheduleConfigRepository(db.taskScheduleConfigDao());

        TaskSlotGenerator generator = new DefaultTaskSlotGenerator(
                lifecycleManager,
                message -> Log.d("SlotGen", message),
                scheduleConfigRepository,
                new DeviceCalendarBlockedIntervalProvider(app),
                db.taskTransitionStatDao(),
                new TaskBudgetEligibilityFromBudgetLookup(db.budgetLookupDao())
        );
        TaskListItemMapper mapper = new TaskListItemMapper();
        TaskCalendarService taskCalendarService = new CalendarReader(app);

        TaskAsyncDataService taskAsyncDataService = new TaskAsyncDataService(
                taskDao,
                mapper,
                taskUseCaseExecutor,
                mainHandler::post
        );
        BudgetRoomRepository budgetRepository = new BudgetRoomRepository(
                db.budgetLookupDao(),
                db.transactionDao(),
                db.budgetLimitDao(),
                db.budgetRecurringTemplateDao()
        );
        BookTaskCompletionExpenseUseCase bookTaskCompletionExpenseUseCase =
                new BookTaskCompletionExpenseUseCase(budgetRepository);

        CheckOffTaskUseCase checkOffTaskUseCase = new CheckOffTaskUseCase(
                taskDao,
                completionService,
                lifecycleManager,
                db.taskTransitionStatDao(),
                taskUseCaseExecutor,
                mainHandler::post,
                bookTaskCompletionExpenseUseCase,
                db,
                app
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

        this.taskScheduleConfigService = new TaskScheduleConfigService(
                scheduleConfigRepository,
                taskUseCaseExecutor,
                mainHandler::post
        );

        taskViewModelFactory = new TaskViewModelFactory(
                app,
                taskAsyncDataService,
                checkOffTaskUseCase,
                regenerateScheduleUseCase,
                taskCalendarService,
                adjustTaskProgressUseCase
        );

        return taskViewModelFactory;
    }

    public TaskScheduleConfigService getTaskScheduleConfigService() {
        if (taskScheduleConfigService == null) {
            createTaskViewModelFactory();
        }
        return taskScheduleConfigService;
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

        BudgetRoomRepository repository = new BudgetRoomRepository(
                db.budgetLookupDao(),
                db.transactionDao(),
                db.budgetLimitDao(),
                db.budgetRecurringTemplateDao()
        );

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

    public synchronized void resetForDataReload() {
        taskViewModelFactory = null;
        regenerateScheduleUseCase = null;
        budgetViewModelFactory = null;
        taskScheduleConfigService = null;
    }
}

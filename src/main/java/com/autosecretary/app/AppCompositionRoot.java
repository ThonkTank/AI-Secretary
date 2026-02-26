package com.autosecretary.app;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.autosecretary.database.AppDatabase;
import com.autosecretary.features.budget.application.CalculateEffectiveBudgetLimitUseCase;
import com.autosecretary.features.budget.application.importing.ApplyRecurringSuggestionsUseCase;
import com.autosecretary.features.budget.application.importing.BudgetImportUseCase;
import com.autosecretary.features.budget.application.importing.ClaudeApiKeyStore;
import com.autosecretary.features.budget.application.importing.ClaudeStatementApiClient;
import com.autosecretary.features.budget.application.importing.StatementFileParser;
import com.autosecretary.features.budget.application.CreateTransferUseCase;
import com.autosecretary.features.budget.data.repository.BudgetImportRoomRepository;
import com.autosecretary.features.budget.data.repository.BudgetRoomRepository;
import com.autosecretary.features.budget.domain.AccountBalanceTimelineService;
import com.autosecretary.features.budget.domain.CalculateFreeBudgetUseCase;
import com.autosecretary.features.budget.ui.BudgetViewModelFactory;
import com.autosecretary.features.task.application.CheckOffTaskUseCase;
import com.autosecretary.features.task.application.DecrementTaskProgressUseCase;
import com.autosecretary.features.task.application.DeleteTaskUseCase;
import com.autosecretary.features.task.application.IncrementTaskProgressUseCase;
import com.autosecretary.features.task.application.RegenerateScheduleUseCase;
import com.autosecretary.features.task.application.TaskAsyncDataService;
import com.autosecretary.features.task.application.TaskBudgetProvider;
import com.autosecretary.features.task.application.calendar.TaskCalendarService;
import com.autosecretary.features.task.application.listmodel.TaskListItemMapper;
import com.autosecretary.features.task.application.config.TaskScheduleConfigRepository;
import com.autosecretary.features.task.application.config.TaskScheduleConfigService;
import com.autosecretary.features.task.application.internal.calendar.CalendarReader;
import com.autosecretary.features.task.data.TaskDAO;
import com.autosecretary.features.task.domain.TaskCompletionService;
import com.autosecretary.features.task.domain.TaskLifecycleManager;
import com.autosecretary.features.task.domain.internal.scheduling.DefaultTaskSlotGenerator;
import com.autosecretary.features.task.ui.list.TaskViewModelFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AppCompositionRoot {
    private final Application app;
    private final ExecutorService taskUseCaseExecutor;
    private TaskViewModelFactory taskViewModelFactory;
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

        DefaultTaskSlotGenerator generator = new DefaultTaskSlotGenerator(
                lifecycleManager,
                message -> Log.d("SlotGen", message),
                scheduleConfigRepository,
                new DeviceCalendarBlockedIntervalProvider(app)
        );
        TaskListItemMapper mapper = new TaskListItemMapper();
        TaskCalendarService taskCalendarService = new CalendarReader(app);

        BudgetRoomRepository budgetRepository = new BudgetRoomRepository(
                db.budgetLookupDao(),
                db.transactionDao(),
                db.budgetLimitDao()
        );
        CalculateEffectiveBudgetLimitUseCase calculateEffectiveBudgetLimitUseCase =
                new CalculateEffectiveBudgetLimitUseCase(budgetRepository);
        TaskBudgetProvider taskBudgetProvider = day -> {
            long totalBalanceCents = 0L;
            for (com.autosecretary.features.budget.data.entity.BudgetAccount account : budgetRepository.findActiveAccounts()) {
                totalBalanceCents += account.currentBalanceCents;
            }

            String yearMonth = day.getYear() + "-" + String.format("%02d", day.getMonthValue());
            long totalCategoryRemainingCents = 0L;
            for (com.autosecretary.features.budget.data.entity.BudgetCategory category : budgetRepository.getActiveCategories()) {
                CalculateEffectiveBudgetLimitUseCase.Result result =
                        calculateEffectiveBudgetLimitUseCase.execute(category.id, yearMonth);
                long categoryRemaining = Math.max(0L, result.getEffectiveLimitCents() - result.getSpentCents());
                totalCategoryRemainingCents += categoryRemaining;
            }

            if (totalCategoryRemainingCents > 0L) {
                return Math.min(Math.max(0L, totalBalanceCents), totalCategoryRemainingCents);
            }
            return Math.max(0L, totalBalanceCents);
        };

        TaskAsyncDataService taskAsyncDataService = new TaskAsyncDataService(
                taskDao,
                mapper,
                taskUseCaseExecutor,
                mainHandler::post
        );
        CheckOffTaskUseCase checkOffTaskUseCase = new CheckOffTaskUseCase(
                taskDao,
                completionService,
                lifecycleManager,
                taskUseCaseExecutor,
                mainHandler::post
        );
        RegenerateScheduleUseCase regenerateScheduleUseCase = new RegenerateScheduleUseCase(
                taskDao,
                generator,
                taskBudgetProvider,
                taskUseCaseExecutor
        );
        DeleteTaskUseCase deleteTaskUseCase = new DeleteTaskUseCase(
                taskDao,
                taskUseCaseExecutor,
                mainHandler::post
        );
        IncrementTaskProgressUseCase incrementTaskProgressUseCase = new IncrementTaskProgressUseCase(
                taskDao,
                taskUseCaseExecutor,
                mainHandler::post
        );
        DecrementTaskProgressUseCase decrementTaskProgressUseCase = new DecrementTaskProgressUseCase(
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
                deleteTaskUseCase,
                taskCalendarService,
                incrementTaskProgressUseCase,
                decrementTaskProgressUseCase
        );

        return taskViewModelFactory;
    }

    public TaskScheduleConfigService getTaskScheduleConfigService() {
        if (taskScheduleConfigService == null) {
            createTaskViewModelFactory();
        }
        return taskScheduleConfigService;
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
                db.budgetLimitDao()
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
        AccountBalanceTimelineService balanceTimelineService = new AccountBalanceTimelineService();

        CalculateFreeBudgetUseCase calculateFreeBudgetUseCase =
                new CalculateFreeBudgetUseCase(repository);

        budgetViewModelFactory = new BudgetViewModelFactory(
                repository,
                parser,
                taskUseCaseExecutor,
                mainHandler::post,
                importUseCase,
                applyRecurringUseCase,
                createTransferUseCase,
                balanceTimelineService,
                calculateFreeBudgetUseCase
        );

        return budgetViewModelFactory;
    }
}

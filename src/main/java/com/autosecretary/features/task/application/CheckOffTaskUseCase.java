package com.autosecretary.features.task.application;

import android.content.Context;

import com.autosecretary.database.AppDatabase;
import com.autosecretary.features.budget.ui.widget.BudgetWidgetProvider;
import com.autosecretary.features.task.application.internal.actions.TaskSlotToggleAction;
import com.autosecretary.features.task.application.listmodel.TaskListItem;
import com.autosecretary.features.task.application.internal.budget.BookTaskCompletionExpenseUseCase;
import com.autosecretary.features.task.data.TaskDAO;
import com.autosecretary.features.task.data.TaskTransitionStatDao;
import com.autosecretary.features.task.domain.TaskCompletionService;
import com.autosecretary.features.task.domain.TaskLifecycleManager;

import java.time.LocalDate;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;

/**
 * Orchestrates task completion: reads the task from the database, delegates the
 * two-phase check-off logic to {@link TaskCompletionService}, and writes the
 * results back. The write scope depends on the phase returned.
 *
 * Contract: DAO work runs on {@code executor}; when present, {@code onChanged}
 * runs on {@code callbackDispatcher}.
 */
public class CheckOffTaskUseCase {
    private final TaskDAO taskDao;
    private final TaskCompletionService completionService;
    private final TaskLifecycleManager lifecycleManager;
    private final TaskTransitionStatDao transitionDao;
    private final ExecutorService executor;
    private final Executor callbackDispatcher;
    private final BookTaskCompletionExpenseUseCase bookTaskCompletionExpenseUseCase;
    private final AppDatabase database;
    private final Context appContext;

    public CheckOffTaskUseCase(TaskDAO taskDao, TaskCompletionService completionService,
                               TaskLifecycleManager lifecycleManager,
                               TaskTransitionStatDao transitionDao,
                               ExecutorService executor,
                               Executor callbackDispatcher,
                               BookTaskCompletionExpenseUseCase bookTaskCompletionExpenseUseCase,
                               AppDatabase database,
                               Context appContext) {
        this.taskDao = taskDao;
        this.completionService = completionService;
        this.lifecycleManager = lifecycleManager;
        this.transitionDao = transitionDao;
        this.executor = executor;
        this.callbackDispatcher = callbackDispatcher;
        this.bookTaskCompletionExpenseUseCase = bookTaskCompletionExpenseUseCase;
        this.database = database;
        this.appContext = appContext;
    }

    /**
     * Checks off a task slot on a background thread. Reads the full task from the DB,
     * runs the completion logic, persists the changes, and invokes {@code onChanged}
     * so the UI can refresh.
     *
     * @param listItem  the display model identifying the task and slot to check off
     * @param onChanged callback invoked after a successful write, typically triggers a list refresh
     */
    public void execute(TaskListItem listItem, Runnable onChanged) {
        executor.execute(() -> TaskSlotToggleAction.execute(
                taskDao,
                completionService,
                lifecycleManager,
                transitionDao,
                listItem.taskId,
                listItem.slotId,
                callbackDispatcher,
                onChanged,
                task -> {
                    boolean booked = bookTaskCompletionExpenseUseCase.execute(task, LocalDate.now());
                    if (booked) {
                        BudgetWidgetProvider.notifyWidgetUpdate(appContext);
                    }
                },
                database
        ));
    }
}

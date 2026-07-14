package com.autosecretary.app;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import com.autosecretary.features.task.application.internal.alarms.DailyPlanningScheduler;
import com.autosecretary.features.task.ui.TaskScheduleConfigViewModelFactory;
import com.autosecretary.features.task.ui.TaskCategoryViewModelFactory;
import com.autosecretary.features.task.ui.TaskCategoryWindowViewModelFactory;
import com.autosecretary.features.assistant.ui.AssistantViewModelFactory;
import com.autosecretary.features.task.ui.edit.TaskEditViewModelFactory;
import com.autosecretary.features.task.ui.list.TaskViewModelFactory;
import com.autosecretary.features.task.application.LoadTaskWidgetItemsUseCase;
import com.autosecretary.features.task.application.RegenerateScheduleUseCase;
import com.autosecretary.features.task.application.internal.mutations.TaskSlotToggleMutation;
import com.autosecretary.features.budget.ui.BudgetViewModelFactory;
import com.autosecretary.features.budget.application.LoadBudgetWidgetSummaryUseCase;
import com.autosecretary.features.meal.ui.MealPlannerViewModelFactory;
import com.autosecretary.shared.ContentDocumentReader;
import com.autosecretary.shared.WidgetRefreshNotifier;

import java.util.concurrent.ExecutorService;

/**
 * Application subclass — the real entry point of the app.
 *
 * <p>Android creates exactly one instance of this class per process lifetime, before any
 * Activity or BroadcastReceiver. {@link #onCreate()} is the right place for one-time
 * setup that must happen before the UI starts.</p>
 *
 * <h2>Responsibilities</h2>
 * <ol>
 *   <li>Create {@link AppCompositionRoot} — the app's manual DI root.</li>
 *   <li>Schedule the daily task-planning alarm via {@link DailyPlanningScheduler}.</li>
 *   <li>Register a receiver that refreshes the home screen widget when the user unlocks.</li>
 * </ol>
 *
 * <h2>Feature access</h2>
 * This class is the single feature-facing accessor: feature entry points (fragments,
 * widget providers, receivers) call {@link #from(Context)} and one of the {@code get…()}
 * methods below, which forward to {@link AppCompositionRoot}. Feature code therefore never
 * imports the composition root itself.
 *
 * @see AppCompositionRoot
 */
public class AutoSecretaryApplication extends Application {
    private AppCompositionRoot appCompositionRoot;

    @Override
    public void onCreate() {
        super.onCreate();
        appCompositionRoot = new AppCompositionRoot(this);
        DailyPlanningScheduler.scheduleDaily(this);
        registerWidgetRefreshOnUnlock();
    }

    /**
     * Registers a receiver for {@link Intent#ACTION_USER_PRESENT} (fired when the user dismisses
     * the lock screen) to refresh the task home-screen widget immediately on unlock.
     *
     * <p>Without this, the widget would only update on its scheduled interval. Refreshing on
     * unlock ensures the user sees current task data the moment they pick up their phone.</p>
     *
     * <p>{@link Context#RECEIVER_NOT_EXPORTED} is required for dynamically-registered receivers
     * on Android 13+ (API 33+) to restrict the broadcast to the app's own process.</p>
     */
    private void registerWidgetRefreshOnUnlock() {
        IntentFilter filter = new IntentFilter(Intent.ACTION_USER_PRESENT);
        registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                refreshTaskWidgetsAfterUnlock();
            }
        }, filter, Context.RECEIVER_NOT_EXPORTED);
    }

    void refreshTaskWidgetsAfterUnlock() {
        appCompositionRoot.getWidgetRefreshNotifier().refreshTaskWidgets();
    }

    public AppCompositionRoot getAppCompositionRoot() {
        return appCompositionRoot;
    }

    public BudgetViewModelFactory getBudgetViewModelFactory() {
        return appCompositionRoot.getBudgetViewModelFactory();
    }

    public ContentDocumentReader getContentDocumentReader() {
        return appCompositionRoot.getContentDocumentReader();
    }

    public ExecutorService getIoExecutor() {
        return appCompositionRoot.getIoExecutor();
    }

    public LoadBudgetWidgetSummaryUseCase createLoadBudgetWidgetSummaryUseCase() {
        return appCompositionRoot.createLoadBudgetWidgetSummaryUseCase();
    }

    public MealPlannerViewModelFactory getMealPlannerViewModelFactory() {
        return appCompositionRoot.getMealPlannerViewModelFactory();
    }

    public TaskViewModelFactory getTaskViewModelFactory() {
        return appCompositionRoot.getTaskViewModelFactory();
    }

    public TaskEditViewModelFactory getTaskEditViewModelFactory() {
        return appCompositionRoot.getTaskEditViewModelFactory();
    }

    public TaskScheduleConfigViewModelFactory getTaskScheduleConfigViewModelFactory() {
        return appCompositionRoot.getTaskScheduleConfigViewModelFactory();
    }

    public TaskCategoryViewModelFactory getTaskCategoryViewModelFactory() {
        return appCompositionRoot.getTaskCategoryViewModelFactory();
    }

    public TaskCategoryWindowViewModelFactory getTaskCategoryWindowViewModelFactory() {
        return appCompositionRoot.getTaskCategoryWindowViewModelFactory();
    }

    public AssistantViewModelFactory getAssistantViewModelFactory() {
        return appCompositionRoot.getAssistantViewModelFactory();
    }

    public ExecutorService getDbExecutor() {
        return appCompositionRoot.getDbExecutor();
    }

    public LoadTaskWidgetItemsUseCase createLoadTaskWidgetItemsUseCase() {
        return appCompositionRoot.createLoadTaskWidgetItemsUseCase();
    }

    public TaskSlotToggleMutation getTaskSlotToggleMutation() {
        return appCompositionRoot.getTaskSlotToggleMutation();
    }

    public RegenerateScheduleUseCase getRegenerateScheduleUseCase() {
        return appCompositionRoot.getRegenerateScheduleUseCase();
    }

    public WidgetRefreshNotifier getWidgetRefreshNotifier() {
        return appCompositionRoot.getWidgetRefreshNotifier();
    }

    /** Convenience accessor: cast the application context to this class. */
    public static AutoSecretaryApplication from(Context context) {
        return (AutoSecretaryApplication) context.getApplicationContext();
    }
}

package com.autosecretary.app;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.autosecretary.R;
import com.autosecretary.app.settings.SettingsController;
import com.autosecretary.app.settings.SettingsDataService;
import com.autosecretary.app.update.UpdateChecker;
import com.autosecretary.features.budget.ui.BudgetFragment;
import com.autosecretary.features.budget.ui.widget.BudgetWidgetProvider;
import com.autosecretary.features.meal.application.MealPlannerDataService;
import com.autosecretary.features.meal.ui.MealPlannerFragment;
import com.autosecretary.features.meal.ui.internal.MealCookingPrefsDialogController;
import com.autosecretary.features.task.ui.TaskScheduleConfigDialog;
import com.autosecretary.features.task.ui.TaskCategoryDialog;
import com.autosecretary.features.task.ui.TaskCategoryWindowDialog;
import com.autosecretary.features.task.ui.assistant.TaskAssistantFragment;
import com.autosecretary.features.task.ui.list.TaskListFragment;
import com.autosecretary.features.task.ui.widget.TaskWidgetProvider;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.snackbar.Snackbar;

/**
 * The single Activity that hosts the feature tabs: Tasks, Assistant, Budget, and Meal Planner.
 *
 * <h2>Navigation</h2>
 * A {@link com.google.android.material.bottomnavigation.BottomNavigationView} switches between
 * {@link com.autosecretary.features.task.ui.list.TaskListFragment},
 * {@link com.autosecretary.features.budget.ui.BudgetFragment}, and
 * {@link com.autosecretary.features.meal.ui.MealPlannerFragment} by replacing the central
 * container fragment. Each tab switch creates a fresh fragment instance.
 *
 * <h2>Deep-link handling</h2>
 * Home-screen widgets can launch the app with special extras that open a specific tab or dialog
 * immediately. {@link #navigateToIntentTarget(android.content.Intent, com.google.android.material.bottomnavigation.BottomNavigationView)}
 * inspects the launch intent on first start and also handles {@link #onNewIntent(android.content.Intent)}
 * for subsequent launches from the widget while the activity is already running.
 *
 * <h2>Settings</h2>
 * The settings gear in the bottom nav opens a dialog managed by {@link SettingsController}.
 * After a data reset, the composition root reopens the database and clears cached graph state;
 * {@link #reloadUiStateAfterDataReset()} recreates the activity so fragments re-bind.
 *
 * <h2>Self-update</h2>
 * {@link com.autosecretary.app.update.UpdateChecker} is started once per activity lifetime
 * from {@link #startUpdateCheckIfNeeded()}. The boolean guard prevents a second check if the
 * activity is re-delivered an intent (onNewIntent) without being recreated.
 */
public class MainActivity extends AppCompatActivity {

    private SettingsController settingsController;
    private UpdateChecker updateChecker;
    /** Guards against starting a second update check if onNewIntent fires before the first finishes. */
    private boolean updateCheckStarted;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.app_main_activity);

        AppCompositionRoot compositionRoot = AutoSecretaryApplication.from(this).getAppCompositionRoot();
        updateChecker = new UpdateChecker(this, compositionRoot.getIoExecutor());
        settingsController = new SettingsController(this, new SettingsDataService(this, compositionRoot),
                this::reloadUiStateAfterDataReset,
                this::showScheduleConfigDialog,
                this::showCategoryDialog,
                this::showCategoryWindowDialog,
                this::checkForUpdateManually,
                this::showCookingPrefsDialog,
                compositionRoot.getIoExecutor());

        BottomNavigationView tabBar = findViewById(R.id.TabBar);

        if (savedInstanceState == null) {
            navigateToIntentTarget(getIntent(), tabBar);
        }

        tabBar.setOnItemSelectedListener(item -> {
            if (item.getItemId() == R.id.nav_settings) {
                settingsController.showSettingsMenu();
                return false;
            } else if (item.getItemId() == R.id.nav_assistant) {
                showAssistantFragment();
            } else if (item.getItemId() == R.id.nav_budget) {
                showBudgetFragment(false);
            } else if (item.getItemId() == R.id.nav_meal) {
                showMealFragment();
            } else {
                showTaskFragment(false);
            }
            return true;
        });

        startUpdateCheckIfNeeded();
    }

    /**
     * Start the background update check, at most once per activity lifetime.
     *
     * <p>{@link #onNewIntent(android.content.Intent)} is called when the activity is already
     * running and a widget launches it again. Without the {@code updateCheckStarted} guard,
     * each widget tap would spawn a new network request. The guard limits the check to one
     * per activity instance.</p>
     */
    private void startUpdateCheckIfNeeded() {
        if (updateCheckStarted) {
            return;
        }
        updateCheckStarted = true;
        updateChecker.checkForUpdate();
    }

    private void checkForUpdateManually() {
        updateChecker.checkForUpdateManually();
    }

    private boolean shouldOpenBudgetFromIntent(Intent intent) {
        return BudgetWidgetProvider.TAB_BUDGET.equals(
                intent.getStringExtra(BudgetWidgetProvider.EXTRA_OPEN_TAB)
        );
    }

    private boolean shouldOpenTaskCreateFromIntent(Intent intent) {
        return TaskWidgetProvider.ACTION_ADD_TASK.equals(intent.getAction())
                || intent.getBooleanExtra(TaskWidgetProvider.EXTRA_OPEN_TASK_FLOW, false);
    }

    private void showTaskFragment(boolean openCreateDialog) {
        TaskListFragment fragment = new TaskListFragment();
        if (openCreateDialog) {
            Bundle args = new Bundle();
            args.putBoolean(TaskListFragment.ARG_OPEN_CREATE_TASK, true);
            fragment.setArguments(args);
        }
        replaceContent(fragment);
    }

    private void showBudgetFragment(boolean openAddDialog) {
        BudgetFragment fragment = new BudgetFragment();
        if (openAddDialog) {
            Bundle args = new Bundle();
            args.putBoolean(BudgetFragment.ARG_OPEN_ADD_TRANSACTION, true);
            fragment.setArguments(args);
        }
        replaceContent(fragment);
    }

    private void showMealFragment() {
        replaceContent(new MealPlannerFragment());
    }

    private void showAssistantFragment() {
        replaceContent(new TaskAssistantFragment());
    }

    private void replaceContent(Fragment fragment) {
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.Container, fragment)
                .commit();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        BottomNavigationView tabBar = findViewById(R.id.TabBar);
        navigateToIntentTarget(intent, tabBar);
    }

    private void navigateToIntentTarget(Intent intent, BottomNavigationView tabBar) {
        if (shouldOpenBudgetFromIntent(intent)) {
            boolean openAddDialog = BudgetWidgetProvider.ACTION_ADD_TRANSACTION.equals(
                    intent.getStringExtra(BudgetWidgetProvider.EXTRA_BUDGET_ACTION));
            showBudgetFragment(openAddDialog);
            tabBar.setSelectedItemId(R.id.nav_budget);
        } else {
            showTaskFragment(shouldOpenTaskCreateFromIntent(intent));
            tabBar.setSelectedItemId(R.id.nav_schedule);
        }
    }

    private void showScheduleConfigDialog() {
        new TaskScheduleConfigDialog().show(getSupportFragmentManager(), TaskScheduleConfigDialog.TAG);
    }

    private void showCategoryDialog() {
        new TaskCategoryDialog().show(getSupportFragmentManager(), TaskCategoryDialog.TAG);
    }

    private void showCategoryWindowDialog() {
        new TaskCategoryWindowDialog().show(getSupportFragmentManager(), TaskCategoryWindowDialog.TAG);
    }

    private void showCookingPrefsDialog() {
        MealPlannerDataService dataService = AutoSecretaryApplication.from(this)
                .getAppCompositionRoot().getMealPlannerDataService();
        dataService.loadCookingPreferences(prefs -> {
            MealCookingPrefsDialogController controller = new MealCookingPrefsDialogController(
                    this,
                    savedPrefs -> dataService.saveCookingPreferences(savedPrefs, () ->
                            Snackbar.make(findViewById(R.id.Container),
                                    R.string.meal_success_cooking_prefs_saved,
                                    Snackbar.LENGTH_SHORT).show()
                    ));
            controller.show(prefs);
        });
    }

    private void reloadUiStateAfterDataReset() {
        recreate();
    }
}

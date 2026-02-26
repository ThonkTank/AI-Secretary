package com.autosecretary.app;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.autosecretary.R;
import com.autosecretary.app.settings.SettingsController;
import com.autosecretary.app.update.UpdateChecker;
import com.autosecretary.database.AppDatabase;
import com.autosecretary.features.budget.ui.BudgetFragment;
import com.autosecretary.features.budget.ui.widget.BudgetWidgetProvider;
import com.autosecretary.features.meal.ui.MealPlannerFragment;
import com.autosecretary.features.task.ui.list.TaskListFragment;
import com.autosecretary.features.task.ui.widget.TaskWidgetProvider;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

    private SettingsController settingsController;
    private boolean updateCheckStarted;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.app_main_activity);

        MaterialToolbar toolbar = findViewById(R.id.MainToolbar);
        setSupportActionBar(toolbar);

        settingsController = new SettingsController(this, this::reloadUiStateAfterDataReset);

        BottomNavigationView tabBar = findViewById(R.id.TabBar);

        if (savedInstanceState == null) {
            if (shouldOpenBudgetFromIntent()) {
                boolean openAddDialog = BudgetWidgetProvider.ACTION_ADD_TRANSACTION.equals(
                        getIntent().getStringExtra(BudgetWidgetProvider.EXTRA_BUDGET_ACTION)
                );
                showBudgetFragment(openAddDialog);
                tabBar.setSelectedItemId(R.id.tab_manage);
            } else {
                showTaskFragment(shouldOpenTaskCreateFromIntent(getIntent()));
                tabBar.setSelectedItemId(R.id.tab_schedule);
            }
        }

        tabBar.setOnItemSelectedListener(item -> {
            if (item.getItemId() == R.id.tab_manage) {
                showBudgetFragment(false);
            } else if (item.getItemId() == R.id.tab_meal) {
                showMealFragment();
            } else {
                showTaskFragment();
            }
            return true;
        });

        startUpdateCheckIfNeeded();
    }

    private void startUpdateCheckIfNeeded() {
        if (updateCheckStarted) {
            return;
        }
        updateCheckStarted = true;
        new UpdateChecker(this).checkForUpdate();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_toolbar_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_settings) {
            settingsController.showSettingsMenu();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private boolean shouldOpenBudgetFromIntent() {
        return BudgetWidgetProvider.TAB_BUDGET.equals(
                getIntent().getStringExtra(BudgetWidgetProvider.EXTRA_OPEN_TAB)
        );
    }

    private boolean shouldOpenTaskCreateFromIntent(Intent intent) {
        if (intent == null) {
            return false;
        }
        return TaskWidgetProvider.ACTION_ADD_TASK.equals(intent.getAction())
                || intent.getBooleanExtra(TaskWidgetProvider.EXTRA_OPEN_TASK_FLOW, false);
    }

    private void showTaskFragment() {
        showTaskFragment(false);
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

    private void replaceContent(Fragment fragment) {
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.Container, fragment)
                .commit();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (shouldOpenBudgetFromIntent()) {
            showBudgetFragment(BudgetWidgetProvider.ACTION_ADD_TRANSACTION.equals(
                    getIntent().getStringExtra(BudgetWidgetProvider.EXTRA_BUDGET_ACTION)));
            BottomNavigationView tabBar = findViewById(R.id.TabBar);
            tabBar.setSelectedItemId(R.id.tab_manage);
            return;
        }
        if (shouldOpenTaskCreateFromIntent(intent)) {
            showTaskFragment(true);
            BottomNavigationView tabBar = findViewById(R.id.TabBar);
            tabBar.setSelectedItemId(R.id.tab_schedule);
        }
    }

    private void reloadUiStateAfterDataReset() {
        AppDatabase.closeAndReset();
        AutoSecretaryApplication.from(this)
                .getAppCompositionRoot()
                .resetForDataReload();
        recreate();
    }
}

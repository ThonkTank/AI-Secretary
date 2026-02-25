package com.autosecretary.app;

import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import androidx.fragment.app.Fragment;

import com.autosecretary.R;
import com.autosecretary.features.budget.ui.BudgetFragment;
import com.autosecretary.features.budget.ui.widget.BudgetWidgetProvider;
import com.autosecretary.features.task.ui.ListFragment;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.app_main_activity);

        BottomNavigationView tabBar = findViewById(R.id.TabBar);

        if (savedInstanceState == null) {
            if (shouldOpenBudgetFromIntent()) {
                boolean openAddDialog = BudgetWidgetProvider.ACTION_ADD_TRANSACTION.equals(
                        getIntent().getStringExtra(BudgetWidgetProvider.EXTRA_BUDGET_ACTION)
                );
                showBudgetFragment(openAddDialog);
                tabBar.setSelectedItemId(R.id.tab_manage);
            } else {
                showTaskFragment();
                tabBar.setSelectedItemId(R.id.tab_schedule);
            }
        }

        tabBar.setOnItemSelectedListener(item -> {
            if (item.getItemId() == R.id.tab_manage) {
                showBudgetFragment(false);
            } else {
                showTaskFragment();
            }
            return true;
        });
    }

    private boolean shouldOpenBudgetFromIntent() {
        return BudgetWidgetProvider.TAB_BUDGET.equals(
                getIntent().getStringExtra(BudgetWidgetProvider.EXTRA_OPEN_TAB)
        );
    }

    private void showTaskFragment() {
        replaceContent(new ListFragment());
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

    private void replaceContent(Fragment fragment) {
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.Container, fragment)
                .commit();
    }
}

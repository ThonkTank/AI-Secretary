package com.autosecretary.app;

import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import androidx.fragment.app.Fragment;

import com.autosecretary.R;

import com.autosecretary.features.budget.ui.BudgetFragment;
import com.autosecretary.features.task.ui.ListFragment;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.app_main_activity);

        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                .replace(R.id.Container, new ListFragment())
                .commit();
        }
        BottomNavigationView tabBar = findViewById(R.id.TabBar);

        tabBar.setOnItemSelectedListener(item -> {
            Fragment fragment;
            if (item.getItemId() == R.id.tab_schedule) {
                fragment = new ListFragment();
            } else if (item.getItemId() == R.id.tab_manage) {
                fragment = new BudgetFragment();
            } else {
                fragment = new ListFragment();
            }
            getSupportFragmentManager().beginTransaction()
                .replace(R.id.Container, fragment)
                .commit();
            return true;
        });
    }
}

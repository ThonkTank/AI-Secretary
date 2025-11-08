package com.aisecretary.taskmaster;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.viewpager2.widget.ViewPager2;

import com.aisecretary.taskmaster.adapter.TaskPagerAdapter;
import com.aisecretary.taskmaster.database.TaskEntity;
import com.aisecretary.taskmaster.fragments.TaskBasisFragment;
import com.aisecretary.taskmaster.fragments.TaskDetailsFragment;
import com.aisecretary.taskmaster.fragments.TaskRecurrenceFragment;
import com.aisecretary.taskmaster.repository.TaskRepository;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

/**
 * AddTaskActivity - Task-Erstellungs-Dialog mit Tab-Layout
 *
 * Implementiert Phase 2.1 der Roadmap:
 * - Tab 1: Basis (Titel, Beschreibung, Priorität, Fälligkeit)
 * - Tab 2: Wiederholung (Recurrence-Konfiguration)
 * - Tab 3: Details (Dauer, Zeit, Kategorie)
 */
public class AddTaskActivity extends Activity {

    private TabLayout tabLayout;
    private ViewPager2 viewPager;
    private TaskPagerAdapter pagerAdapter;

    private TextView cancelButtonTop;
    private Button cancelButtonBottom;
    private Button saveButton;

    private TaskRepository repository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_task);

        // Initialize repository
        repository = TaskRepository.getInstance(this);

        // Find views
        tabLayout = findViewById(R.id.tab_layout);
        viewPager = findViewById(R.id.view_pager);
        cancelButtonTop = findViewById(R.id.cancel_button);
        cancelButtonBottom = findViewById(R.id.cancel_button_bottom);
        saveButton = findViewById(R.id.save_button);

        // Set up ViewPager2 with adapter
        pagerAdapter = new TaskPagerAdapter(this);
        viewPager.setAdapter(pagerAdapter);

        // Connect TabLayout with ViewPager2
        new TabLayoutMediator(tabLayout, viewPager,
            (tab, position) -> {
                switch (position) {
                    case 0: tab.setText("Basis"); break;
                    case 1: tab.setText("Wiederholung"); break;
                    case 2: tab.setText("Details"); break;
                }
            }
        ).attach();

        // Set up button listeners
        setupButtons();
    }

    private void setupButtons() {
        // Cancel buttons
        cancelButtonTop.setOnClickListener(v -> finish());
        cancelButtonBottom.setOnClickListener(v -> finish());

        // Save button
        saveButton.setOnClickListener(v -> saveTask());
    }

    private void saveTask() {
        // Get data from fragments
        TaskBasisFragment basisFragment = pagerAdapter.getBasisFragment();
        TaskRecurrenceFragment recurrenceFragment = pagerAdapter.getRecurrenceFragment();
        TaskDetailsFragment detailsFragment = pagerAdapter.getDetailsFragment();

        // Validate: Title is required
        String title = basisFragment.getTitle();
        if (title.isEmpty()) {
            Toast.makeText(this, "Titel ist erforderlich!", Toast.LENGTH_SHORT).show();
            viewPager.setCurrentItem(0); // Navigate to Basis tab
            return;
        }

        // Collect all data
        String description = basisFragment.getDescription();
        int priority = basisFragment.getPriority();
        long dueDate = basisFragment.getDueDate();

        boolean isRecurring = recurrenceFragment.isRecurring();
        String recurrenceType = recurrenceFragment.getRecurrenceType();
        int recurrenceX = recurrenceFragment.getRecurrenceX();
        String recurrenceY = recurrenceFragment.getRecurrenceY();

        long estimatedDuration = detailsFragment.getEstimatedDuration();
        String preferredTimeOfDay = detailsFragment.getPreferredTimeOfDay();
        String category = detailsFragment.getCategory();

        // Create TaskEntity
        TaskEntity task = new TaskEntity(title, description, priority);
        task.dueAt = dueDate;
        task.isRecurring = isRecurring;
        task.recurrenceType = recurrenceType;
        task.recurrenceX = recurrenceX;
        task.recurrenceY = recurrenceY;
        task.averageCompletionTime = estimatedDuration;
        task.preferredTimeOfDay = preferredTimeOfDay;
        task.category = category;

        // Save to database
        long taskId = repository.createTask(title, description, priority, dueDate);

        // Update with additional data
        TaskEntity savedTask = repository.getTask(taskId);
        if (savedTask != null) {
            savedTask.isRecurring = isRecurring;
            savedTask.recurrenceType = recurrenceType;
            savedTask.recurrenceX = recurrenceX;
            savedTask.recurrenceY = recurrenceY;
            savedTask.averageCompletionTime = estimatedDuration;
            savedTask.preferredTimeOfDay = preferredTimeOfDay;
            savedTask.category = category;

            repository.updateTask(savedTask);
        }

        // Show success message
        String message = "Aufgabe erstellt!";
        if (isRecurring) {
            if (recurrenceType.equals("x_per_y")) {
                message += " (" + recurrenceX + " mal pro " + getYDisplayName(recurrenceY) + ")";
            } else if (recurrenceType.equals("every_x_y")) {
                message += " (Alle " + recurrenceX + " " + getYDisplayName(recurrenceY) + ")";
            }
        }
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();

        // Close activity and return to MainActivity
        finish();
    }

    private String getYDisplayName(String y) {
        switch (y) {
            case "day": return "Tag";
            case "week": return "Woche";
            case "month": return "Monat";
            default: return y;
        }
    }
}

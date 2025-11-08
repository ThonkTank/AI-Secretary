package com.aisecretary.taskmaster;

import androidx.appcompat.app.AppCompatActivity;
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
 * AddTaskActivity - Task-Erstellungs/Bearbeitungs-Dialog mit Tab-Layout
 *
 * Implementiert Phase 2.1 & 2.2 der Roadmap:
 * - Tab 1: Basis (Titel, Beschreibung, Priorität, Fälligkeit)
 * - Tab 2: Wiederholung (Recurrence-Konfiguration)
 * - Tab 3: Details (Dauer, Zeit, Kategorie)
 *
 * Unterstützt sowohl Add- als auch Edit-Modus:
 * - Add-Modus: Neue Aufgabe erstellen
 * - Edit-Modus: Bestehende Aufgabe bearbeiten (via EXTRA_TASK_ID)
 */
public class AddTaskActivity extends AppCompatActivity {

    public static final String EXTRA_TASK_ID = "task_id";

    private TabLayout tabLayout;
    private ViewPager2 viewPager;
    private TaskPagerAdapter pagerAdapter;

    private TextView headerTitle;
    private TextView cancelButtonTop;
    private Button cancelButtonBottom;
    private Button saveButton;

    private TaskRepository repository;

    // Edit mode
    private boolean isEditMode = false;
    private long editingTaskId = -1;
    private TaskEntity editingTask = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_task);

        // Initialize repository
        repository = TaskRepository.getInstance(this);

        // Check for edit mode
        if (getIntent().hasExtra(EXTRA_TASK_ID)) {
            isEditMode = true;
            editingTaskId = getIntent().getLongExtra(EXTRA_TASK_ID, -1);
            editingTask = repository.getTask(editingTaskId);
        }

        // Find views
        headerTitle = findViewById(R.id.header_title);
        tabLayout = findViewById(R.id.tab_layout);
        viewPager = findViewById(R.id.view_pager);
        cancelButtonTop = findViewById(R.id.cancel_button);
        cancelButtonBottom = findViewById(R.id.cancel_button_bottom);
        saveButton = findViewById(R.id.save_button);

        // Set header title
        if (isEditMode) {
            headerTitle.setText("Aufgabe bearbeiten");
            saveButton.setText("Aktualisieren");
        } else {
            headerTitle.setText("Neue Aufgabe");
            saveButton.setText("Speichern");
        }

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

        // If edit mode, populate fragments with existing data
        if (isEditMode && editingTask != null) {
            // Delay to ensure fragments are created
            viewPager.post(() -> populateFragments());
        }
    }

    private void setupButtons() {
        // Cancel buttons
        cancelButtonTop.setOnClickListener(v -> finish());
        cancelButtonBottom.setOnClickListener(v -> finish());

        // Save button
        saveButton.setOnClickListener(v -> saveTask());
    }

    /**
     * Populate fragments with existing task data (Edit Mode)
     */
    private void populateFragments() {
        if (editingTask == null) return;

        TaskBasisFragment basisFragment = pagerAdapter.getBasisFragment();
        TaskRecurrenceFragment recurrenceFragment = pagerAdapter.getRecurrenceFragment();
        TaskDetailsFragment detailsFragment = pagerAdapter.getDetailsFragment();

        // Populate Basis tab
        if (basisFragment != null) {
            basisFragment.setTitle(editingTask.title);
            basisFragment.setDescription(editingTask.description);
            basisFragment.setPriority(editingTask.priority);
            basisFragment.setDueDate(editingTask.dueAt);
        }

        // Populate Recurrence tab
        if (recurrenceFragment != null) {
            recurrenceFragment.setRecurrenceType(editingTask.recurrenceType);
            recurrenceFragment.setRecurrenceX(editingTask.recurrenceX);
            recurrenceFragment.setRecurrenceY(editingTask.recurrenceY);
        }

        // Populate Details tab
        if (detailsFragment != null) {
            detailsFragment.setEstimatedDuration(editingTask.averageCompletionTime);
            detailsFragment.setPreferredTimeOfDay(editingTask.preferredTimeOfDay);
            detailsFragment.setCategory(editingTask.category);
        }
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

        if (isEditMode) {
            // Update existing task
            editingTask.title = title;
            editingTask.description = description;
            editingTask.priority = priority;
            editingTask.dueAt = dueDate;
            editingTask.isRecurring = isRecurring;
            editingTask.recurrenceType = recurrenceType;
            editingTask.recurrenceX = recurrenceX;
            editingTask.recurrenceY = recurrenceY;
            editingTask.averageCompletionTime = estimatedDuration;
            editingTask.preferredTimeOfDay = preferredTimeOfDay;
            editingTask.category = category;

            repository.updateTask(editingTask);

            Toast.makeText(this, "Aufgabe aktualisiert!", Toast.LENGTH_SHORT).show();
        } else {
            // Create new task
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
        }

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

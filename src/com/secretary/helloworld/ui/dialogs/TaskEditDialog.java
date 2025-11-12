package com.secretary.helloworld.ui.dialogs;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.*;

import com.secretary.helloworld.R;
import com.secretary.helloworld.Task;
import com.secretary.helloworld.AppLogger;
import com.secretary.helloworld.data.dao.TaskDao;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Dialog for creating and editing tasks.
 * Extracted from TaskActivity for better code organization.
 */
public class TaskEditDialog {
    private static final String TAG = "TaskEditDialog";

    private final Context context;
    private final TaskDao taskDao;
    private final AppLogger logger;
    private final SimpleDateFormat dateFormat;

    private AlertDialog dialog;
    private EditText titleInput;
    private EditText descriptionInput;
    private AutoCompleteTextView categoryInput;
    private Spinner prioritySpinner;
    private TextView dueDateText;
    private CheckBox recurrenceCheckBox;
    private LinearLayout recurrenceLayout;
    private Spinner recurrenceTypeSpinner;
    private Spinner recurrenceUnitSpinner;
    private EditText recurrenceAmountInput;

    private long selectedDueDate = 0;
    private Task editingTask = null;
    private TaskSaveListener listener;

    /**
     * Listener interface for task save events
     */
    public interface TaskSaveListener {
        void onTaskSaved(Task task);
    }

    public TaskEditDialog(Context context, TaskSaveListener listener) {
        this.context = context;
        this.taskDao = new TaskDao(context);
        this.logger = AppLogger.getInstance(context);
        this.dateFormat = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault());
        this.listener = listener;
    }

    /**
     * Show dialog for creating a new task
     */
    public void showAddDialog() {
        this.editingTask = null;
        this.selectedDueDate = 0;
        buildDialog("Add Task");
        dialog.show();
    }

    /**
     * Show dialog for editing an existing task
     */
    public void showEditDialog(Task task) {
        this.editingTask = task;
        this.selectedDueDate = task.getDueDate();
        buildDialog("Edit Task");
        populateFields(task);
        dialog.show();
    }

    /**
     * Build the dialog layout
     */
    private void buildDialog(String title) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_add_task, null);
        builder.setView(dialogView);
        builder.setTitle(title);

        // Find views
        titleInput = dialogView.findViewById(R.id.taskTitleInput);
        descriptionInput = dialogView.findViewById(R.id.taskDescriptionInput);
        categoryInput = dialogView.findViewById(R.id.taskCategoryInput);
        prioritySpinner = dialogView.findViewById(R.id.taskPrioritySpinner);
        dueDateText = dialogView.findViewById(R.id.taskDueDateText);
        Button selectDateButton = dialogView.findViewById(R.id.selectDueDateButton);
        Button clearDateButton = dialogView.findViewById(R.id.clearDueDateButton);
        recurrenceCheckBox = dialogView.findViewById(R.id.recurrenceCheckBox);
        recurrenceLayout = dialogView.findViewById(R.id.recurrenceLayout);
        recurrenceTypeSpinner = dialogView.findViewById(R.id.recurrenceTypeSpinner);
        recurrenceUnitSpinner = dialogView.findViewById(R.id.recurrenceUnitSpinner);
        recurrenceAmountInput = dialogView.findViewById(R.id.recurrenceAmountInput);

        // Setup category autocomplete
        setupCategoryAutocomplete();

        // Setup priority spinner
        String[] priorities = {"Low", "Medium", "High"};
        prioritySpinner.setAdapter(new ArrayAdapter<>(context,
                android.R.layout.simple_spinner_dropdown_item, priorities));

        // Setup recurrence spinners
        setupRecurrenceSpinners();

        // Date selection
        selectDateButton.setOnClickListener(v -> showDatePicker());
        clearDateButton.setOnClickListener(v -> {
            selectedDueDate = 0;
            dueDateText.setText("No due date");
        });

        // Recurrence toggle
        recurrenceCheckBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            recurrenceLayout.setVisibility(isChecked ? View.VISIBLE : View.GONE);
        });

        // Dialog buttons
        builder.setPositiveButton("Save", null);
        builder.setNegativeButton("Cancel", null);

        dialog = builder.create();

        // Override positive button to add validation
        dialog.setOnShowListener(dialogInterface -> {
            Button saveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            saveButton.setOnClickListener(v -> saveTask());
        });
    }

    /**
     * Setup category autocomplete with existing categories
     */
    private void setupCategoryAutocomplete() {
        List<String> categories = taskDao.getAllCategories();
        ArrayAdapter<String> adapter = new ArrayAdapter<>(context,
                android.R.layout.simple_dropdown_item_1line, categories);
        categoryInput.setAdapter(adapter);
        categoryInput.setThreshold(1);
    }

    /**
     * Setup recurrence spinners
     */
    private void setupRecurrenceSpinners() {
        String[] recurrenceTypes = {"Interval (reappears after)", "Frequency (X times per period)"};
        recurrenceTypeSpinner.setAdapter(new ArrayAdapter<>(context,
                android.R.layout.simple_spinner_dropdown_item, recurrenceTypes));

        String[] recurrenceUnits = {"Day(s)", "Week(s)", "Month(s)"};
        recurrenceUnitSpinner.setAdapter(new ArrayAdapter<>(context,
                android.R.layout.simple_spinner_dropdown_item, recurrenceUnits));
    }

    /**
     * Show date picker dialog
     */
    private void showDatePicker() {
        Calendar calendar = Calendar.getInstance();
        if (selectedDueDate > 0) {
            calendar.setTimeInMillis(selectedDueDate);
        }

        DatePickerDialog datePickerDialog = new DatePickerDialog(context,
                (view, year, month, dayOfMonth) -> {
                    calendar.set(year, month, dayOfMonth);
                    selectedDueDate = calendar.getTimeInMillis();
                    dueDateText.setText("Due: " + dateFormat.format(new Date(selectedDueDate)));
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH));

        datePickerDialog.show();
    }

    /**
     * Populate fields when editing an existing task
     */
    private void populateFields(Task task) {
        titleInput.setText(task.getTitle());
        descriptionInput.setText(task.getDescription());
        categoryInput.setText(task.getCategory());
        prioritySpinner.setSelection(task.getPriority());

        if (task.getDueDate() > 0) {
            dueDateText.setText("Due: " + dateFormat.format(new Date(task.getDueDate())));
        }

        if (task.isRecurring()) {
            recurrenceCheckBox.setChecked(true);
            recurrenceLayout.setVisibility(View.VISIBLE);
            recurrenceTypeSpinner.setSelection(task.getRecurrenceType() == Task.RECURRENCE_INTERVAL ? 0 : 1);
            recurrenceAmountInput.setText(String.valueOf(task.getRecurrenceAmount()));
            recurrenceUnitSpinner.setSelection(task.getRecurrenceUnit());
        }
    }

    /**
     * Save the task (create or update)
     */
    private void saveTask() {
        String title = titleInput.getText().toString().trim();
        String description = descriptionInput.getText().toString().trim();
        String category = categoryInput.getText().toString().trim();
        int priority = prioritySpinner.getSelectedItemPosition();

        // Validation
        if (title.isEmpty()) {
            Toast.makeText(context, "Please enter a task title", Toast.LENGTH_SHORT).show();
            return;
        }

        // Create or update task object
        Task task = editingTask != null ? editingTask : new Task();
        task.setTitle(title);
        task.setDescription(description);
        task.setCategory(category);
        task.setPriority(priority);
        task.setDueDate(selectedDueDate);

        // Handle recurrence
        if (recurrenceCheckBox.isChecked()) {
            String amountStr = recurrenceAmountInput.getText().toString().trim();
            int amount = 1;
            try {
                amount = Integer.parseInt(amountStr);
                if (amount < 1) amount = 1;
            } catch (NumberFormatException e) {
                amount = 1;
            }

            int recurrenceType = recurrenceTypeSpinner.getSelectedItemPosition() == 0 ?
                Task.RECURRENCE_INTERVAL : Task.RECURRENCE_FREQUENCY;

            task.setRecurrenceType(recurrenceType);
            task.setRecurrenceAmount(amount);
            task.setRecurrenceUnit(recurrenceUnitSpinner.getSelectedItemPosition());

            // Initialize period for frequency tasks
            if (recurrenceType == Task.RECURRENCE_FREQUENCY && task.getCurrentPeriodStart() == 0) {
                task.setCurrentPeriodStart(System.currentTimeMillis());
                task.setCompletionsThisPeriod(0);
            }
        } else {
            task.setRecurrenceType(Task.RECURRENCE_NONE);
            task.setRecurrenceAmount(0);
            task.setRecurrenceUnit(Task.UNIT_DAY);
            task.setCompletionsThisPeriod(0);
            task.setCurrentPeriodStart(0);
        }

        // Save to database
        if (editingTask != null) {
            taskDao.updateTask(task);
            logger.info(TAG, "Task updated: " + title);
        } else {
            task.setCreatedAt(System.currentTimeMillis());
            long id = taskDao.insertTask(task);
            task.setId(id);
            logger.info(TAG, "Task created: " + title);
        }

        // Notify listener
        if (listener != null) {
            listener.onTaskSaved(task);
        }

        dialog.dismiss();
    }

    /**
     * Dismiss the dialog
     */
    public void dismiss() {
        if (dialog != null && dialog.isShowing()) {
            dialog.dismiss();
        }
    }
}
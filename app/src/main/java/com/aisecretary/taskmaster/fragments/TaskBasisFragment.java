package com.aisecretary.taskmaster.fragments;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.aisecretary.taskmaster.R;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * TaskBasisFragment - Tab 1: Basis-Informationen
 *
 * Erfasst: Titel, Beschreibung, Priorität, Fälligkeit
 */
public class TaskBasisFragment extends Fragment {

    private EditText inputTitle;
    private EditText inputDescription;
    private TextView selectedDueDateText;

    private int selectedPriority = 2; // Default: Priorität 2
    private long selectedDueDate = 0; // Default: Heute (wird in onViewCreated gesetzt)

    private Button priority1, priority2, priority3, priority4;
    private Button dueToday, dueTomorrow, dueCustom;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_task_basis, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Find views
        inputTitle = view.findViewById(R.id.input_title);
        inputDescription = view.findViewById(R.id.input_description);
        selectedDueDateText = view.findViewById(R.id.selected_due_date);

        // Priority buttons
        priority1 = view.findViewById(R.id.priority_1);
        priority2 = view.findViewById(R.id.priority_2);
        priority3 = view.findViewById(R.id.priority_3);
        priority4 = view.findViewById(R.id.priority_4);

        // Due date buttons
        dueToday = view.findViewById(R.id.due_today);
        dueTomorrow = view.findViewById(R.id.due_tomorrow);
        dueCustom = view.findViewById(R.id.due_custom);

        // Set default due date to today
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 23);
        cal.set(Calendar.MINUTE, 59);
        cal.set(Calendar.SECOND, 59);
        selectedDueDate = cal.getTimeInMillis();

        // Set up priority buttons
        setupPriorityButtons();

        // Set up due date buttons
        setupDueDateButtons();
    }

    private void setupPriorityButtons() {
        priority1.setOnClickListener(v -> selectPriority(1));
        priority2.setOnClickListener(v -> selectPriority(2));
        priority3.setOnClickListener(v -> selectPriority(3));
        priority4.setOnClickListener(v -> selectPriority(4));

        // Set default selection
        selectPriority(2);
    }

    private void selectPriority(int priority) {
        selectedPriority = priority;

        // Reset all buttons
        priority1.setBackgroundColor(getResources().getColor(R.color.cardBackground, null));
        priority2.setBackgroundColor(getResources().getColor(R.color.cardBackground, null));
        priority3.setBackgroundColor(getResources().getColor(R.color.cardBackground, null));
        priority4.setBackgroundColor(getResources().getColor(R.color.cardBackground, null));

        // Highlight selected button
        Button selectedButton = null;
        switch (priority) {
            case 1: selectedButton = priority1; break;
            case 2: selectedButton = priority2; break;
            case 3: selectedButton = priority3; break;
            case 4: selectedButton = priority4; break;
        }

        if (selectedButton != null) {
            selectedButton.setBackgroundColor(getResources().getColor(R.color.colorAccent, null));
        }
    }

    private void setupDueDateButtons() {
        dueToday.setOnClickListener(v -> selectDueDate(0)); // Today
        dueTomorrow.setOnClickListener(v -> selectDueDate(1)); // Tomorrow
        dueCustom.setOnClickListener(v -> showDatePicker());

        // Set default selection
        selectDueDate(0);
    }

    private void selectDueDate(int daysFromNow) {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, daysFromNow);
        cal.set(Calendar.HOUR_OF_DAY, 23);
        cal.set(Calendar.MINUTE, 59);
        cal.set(Calendar.SECOND, 59);
        selectedDueDate = cal.getTimeInMillis();

        // Reset button colors
        dueToday.setBackgroundColor(getResources().getColor(R.color.cardBackground, null));
        dueToday.setTextColor(getResources().getColor(R.color.textPrimary, null));
        dueTomorrow.setBackgroundColor(getResources().getColor(R.color.cardBackground, null));
        dueTomorrow.setTextColor(getResources().getColor(R.color.textPrimary, null));
        dueCustom.setBackgroundColor(getResources().getColor(R.color.cardBackground, null));
        dueCustom.setTextColor(getResources().getColor(R.color.textPrimary, null));

        // Highlight selected button
        if (daysFromNow == 0) {
            dueToday.setBackgroundColor(getResources().getColor(R.color.colorPrimary, null));
            dueToday.setTextColor(getResources().getColor(R.color.textOnPrimary, null));
            selectedDueDateText.setText("📅 Heute");
        } else if (daysFromNow == 1) {
            dueTomorrow.setBackgroundColor(getResources().getColor(R.color.colorPrimary, null));
            dueTomorrow.setTextColor(getResources().getColor(R.color.textOnPrimary, null));
            selectedDueDateText.setText("📅 Morgen");
        }
    }

    private void showDatePicker() {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(selectedDueDate);

        DatePickerDialog dialog = new DatePickerDialog(
            requireContext(),
            (view, year, month, dayOfMonth) -> {
                Calendar selected = Calendar.getInstance();
                selected.set(year, month, dayOfMonth, 23, 59, 59);
                selectedDueDate = selected.getTimeInMillis();

                // Highlight custom button
                dueToday.setBackgroundColor(getResources().getColor(R.color.cardBackground, null));
                dueToday.setTextColor(getResources().getColor(R.color.textPrimary, null));
                dueTomorrow.setBackgroundColor(getResources().getColor(R.color.cardBackground, null));
                dueTomorrow.setTextColor(getResources().getColor(R.color.textPrimary, null));
                dueCustom.setBackgroundColor(getResources().getColor(R.color.colorPrimary, null));
                dueCustom.setTextColor(getResources().getColor(R.color.textOnPrimary, null));

                // Update text
                SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault());
                selectedDueDateText.setText("📅 " + sdf.format(new Date(selectedDueDate)));
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        );

        dialog.show();
    }

    // Public getters for data
    public String getTitle() {
        return inputTitle.getText().toString().trim();
    }

    public String getDescription() {
        return inputDescription.getText().toString().trim();
    }

    public int getPriority() {
        return selectedPriority;
    }

    public long getDueDate() {
        return selectedDueDate;
    }
}

package com.aisecretary.taskmaster.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.aisecretary.taskmaster.R;

/**
 * TaskRecurrenceFragment - Tab 2: Wiederholungs-Konfiguration
 *
 * Erfasst: Recurrence-Typ, x pro y, alle x y
 */
public class TaskRecurrenceFragment extends Fragment {

    private RadioGroup recurrenceTypeGroup;
    private RadioButton radioOnce, radioXPerY, radioEveryXY, radioScheduled;

    private LinearLayout xPerYContainer;
    private LinearLayout everyXYContainer;
    private LinearLayout scheduledContainer;

    private EditText inputXCount;
    private Spinner spinnerYPeriod;
    private EditText inputXInterval;
    private Spinner spinnerYUnit;

    private TextView recurrenceInfo;

    private String selectedRecurrenceType = "once";
    private int recurrenceX = 1;
    private String recurrenceY = "day";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_task_recurrence, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Find views
        recurrenceTypeGroup = view.findViewById(R.id.recurrence_type_group);
        radioOnce = view.findViewById(R.id.radio_once);
        radioXPerY = view.findViewById(R.id.radio_x_per_y);
        radioEveryXY = view.findViewById(R.id.radio_every_x_y);
        radioScheduled = view.findViewById(R.id.radio_scheduled);

        xPerYContainer = view.findViewById(R.id.x_per_y_container);
        everyXYContainer = view.findViewById(R.id.every_x_y_container);
        scheduledContainer = view.findViewById(R.id.scheduled_container);

        inputXCount = view.findViewById(R.id.input_x_count);
        spinnerYPeriod = view.findViewById(R.id.spinner_y_period);
        inputXInterval = view.findViewById(R.id.input_x_interval);
        spinnerYUnit = view.findViewById(R.id.spinner_y_unit);

        recurrenceInfo = view.findViewById(R.id.recurrence_info);

        // Set up spinners
        setupSpinners();

        // Set up radio group listener
        setupRadioGroup();
    }

    private void setupSpinners() {
        // Period options for "x per y"
        String[] periods = {"Tag", "Woche", "Monat"};
        ArrayAdapter<String> periodAdapter = new ArrayAdapter<>(
            requireContext(),
            android.R.layout.simple_spinner_item,
            periods
        );
        periodAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerYPeriod.setAdapter(periodAdapter);
        spinnerYPeriod.setSelection(1); // Default: Woche

        // Unit options for "every x y"
        String[] units = {"Tag(e)", "Woche(n)", "Monat(e)"};
        ArrayAdapter<String> unitAdapter = new ArrayAdapter<>(
            requireContext(),
            android.R.layout.simple_spinner_item,
            units
        );
        unitAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerYUnit.setAdapter(unitAdapter);
        spinnerYUnit.setSelection(0); // Default: Tag(e)
    }

    private void setupRadioGroup() {
        recurrenceTypeGroup.setOnCheckedChangeListener((group, checkedId) -> {
            // Hide all containers
            xPerYContainer.setVisibility(View.GONE);
            everyXYContainer.setVisibility(View.GONE);
            scheduledContainer.setVisibility(View.GONE);

            if (checkedId == R.id.radio_once) {
                selectedRecurrenceType = "once";
                recurrenceInfo.setText("💡 Einmalige Aufgabe");
            } else if (checkedId == R.id.radio_x_per_y) {
                selectedRecurrenceType = "x_per_y";
                xPerYContainer.setVisibility(View.VISIBLE);
                updateXPerYInfo();
            } else if (checkedId == R.id.radio_every_x_y) {
                selectedRecurrenceType = "every_x_y";
                everyXYContainer.setVisibility(View.VISIBLE);
                updateEveryXYInfo();
            } else if (checkedId == R.id.radio_scheduled) {
                selectedRecurrenceType = "scheduled";
                scheduledContainer.setVisibility(View.VISIBLE);
                recurrenceInfo.setText("💡 Geplante Wiederholung (Phase 3)");
            }
        });
    }

    private void updateXPerYInfo() {
        String count = inputXCount.getText().toString();
        if (count.isEmpty()) count = "3";
        String period = spinnerYPeriod.getSelectedItem().toString().toLowerCase();
        recurrenceInfo.setText("💡 " + count + " mal pro " + period);
    }

    private void updateEveryXYInfo() {
        String interval = inputXInterval.getText().toString();
        if (interval.isEmpty()) interval = "2";
        String unit = spinnerYUnit.getSelectedItem().toString();
        recurrenceInfo.setText("💡 Alle " + interval + " " + unit);
    }

    // Public getters for data
    public boolean isRecurring() {
        return !selectedRecurrenceType.equals("once");
    }

    public String getRecurrenceType() {
        return selectedRecurrenceType;
    }

    public int getRecurrenceX() {
        if (selectedRecurrenceType.equals("x_per_y")) {
            String count = inputXCount.getText().toString();
            return count.isEmpty() ? 3 : Integer.parseInt(count);
        } else if (selectedRecurrenceType.equals("every_x_y")) {
            String interval = inputXInterval.getText().toString();
            return interval.isEmpty() ? 2 : Integer.parseInt(interval);
        }
        return 1;
    }

    public String getRecurrenceY() {
        if (selectedRecurrenceType.equals("x_per_y")) {
            int position = spinnerYPeriod.getSelectedItemPosition();
            return position == 0 ? "day" : (position == 1 ? "week" : "month");
        } else if (selectedRecurrenceType.equals("every_x_y")) {
            int position = spinnerYUnit.getSelectedItemPosition();
            return position == 0 ? "day" : (position == 1 ? "week" : "month");
        }
        return "day";
    }

    // Setter methods for Edit Mode

    public void setRecurrenceType(String recurrenceType) {
        selectedRecurrenceType = recurrenceType;

        // Select appropriate radio button
        if ("once".equals(recurrenceType)) {
            radioOnce.setChecked(true);
        } else if ("x_per_y".equals(recurrenceType)) {
            radioXPerY.setChecked(true);
        } else if ("every_x_y".equals(recurrenceType)) {
            radioEveryXY.setChecked(true);
        } else if ("scheduled".equals(recurrenceType)) {
            radioScheduled.setChecked(true);
        }
    }

    public void setRecurrenceX(int x) {
        if (selectedRecurrenceType.equals("x_per_y") && inputXCount != null) {
            inputXCount.setText(String.valueOf(x));
        } else if (selectedRecurrenceType.equals("every_x_y") && inputXInterval != null) {
            inputXInterval.setText(String.valueOf(x));
        }
    }

    public void setRecurrenceY(String y) {
        int position = 0;
        switch (y) {
            case "day": position = 0; break;
            case "week": position = 1; break;
            case "month": position = 2; break;
        }

        if (selectedRecurrenceType.equals("x_per_y") && spinnerYPeriod != null) {
            spinnerYPeriod.setSelection(position);
        } else if (selectedRecurrenceType.equals("every_x_y") && spinnerYUnit != null) {
            spinnerYUnit.setSelection(position);
        }
    }
}

package com.aisecretary.taskmaster.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.aisecretary.taskmaster.R;

/**
 * TaskDetailsFragment - Tab 3: Detail-Informationen
 *
 * Erfasst: Geschätzte Dauer, Bevorzugte Zeit, Kategorie
 */
public class TaskDetailsFragment extends Fragment {

    private TextView selectedDurationText;
    private EditText inputCategory;

    private Button duration5min, duration15min, duration30min;
    private Button duration1h, duration2h, durationCustom;
    private Button timeMorning, timeAfternoon, timeEvening;

    private long estimatedDuration = 0; // In milliseconds, 0 = not set
    private String preferredTimeOfDay = ""; // Empty = not set
    private String category = "";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_task_details, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Find views
        selectedDurationText = view.findViewById(R.id.selected_duration);
        inputCategory = view.findViewById(R.id.input_category);

        // Duration buttons
        duration5min = view.findViewById(R.id.duration_5min);
        duration15min = view.findViewById(R.id.duration_15min);
        duration30min = view.findViewById(R.id.duration_30min);
        duration1h = view.findViewById(R.id.duration_1h);
        duration2h = view.findViewById(R.id.duration_2h);
        durationCustom = view.findViewById(R.id.duration_custom);

        // Time buttons
        timeMorning = view.findViewById(R.id.time_morning);
        timeAfternoon = view.findViewById(R.id.time_afternoon);
        timeEvening = view.findViewById(R.id.time_evening);

        // Set up duration buttons
        setupDurationButtons();

        // Set up time buttons
        setupTimeButtons();
    }

    private void setupDurationButtons() {
        duration5min.setOnClickListener(v -> selectDuration(5 * 60 * 1000, "5 Min"));
        duration15min.setOnClickListener(v -> selectDuration(15 * 60 * 1000, "15 Min"));
        duration30min.setOnClickListener(v -> selectDuration(30 * 60 * 1000, "30 Min"));
        duration1h.setOnClickListener(v -> selectDuration(60 * 60 * 1000, "1 Std"));
        duration2h.setOnClickListener(v -> selectDuration(2 * 60 * 60 * 1000, "2 Std"));
        durationCustom.setOnClickListener(v -> {
            // TODO: Custom duration input in Phase 3
            Toast.makeText(requireContext(), "Benutzerdefinierte Dauer in Phase 3", Toast.LENGTH_SHORT).show();
        });
    }

    private void selectDuration(long durationMillis, String displayText) {
        estimatedDuration = durationMillis;

        // Reset all button colors
        resetButtonColor(duration5min);
        resetButtonColor(duration15min);
        resetButtonColor(duration30min);
        resetButtonColor(duration1h);
        resetButtonColor(duration2h);
        resetButtonColor(durationCustom);

        // Highlight selected button
        Button selectedButton = null;
        if (durationMillis == 5 * 60 * 1000) selectedButton = duration5min;
        else if (durationMillis == 15 * 60 * 1000) selectedButton = duration15min;
        else if (durationMillis == 30 * 60 * 1000) selectedButton = duration30min;
        else if (durationMillis == 60 * 60 * 1000) selectedButton = duration1h;
        else if (durationMillis == 2 * 60 * 60 * 1000) selectedButton = duration2h;

        if (selectedButton != null) {
            selectedButton.setBackgroundColor(getResources().getColor(R.color.colorAccent, null));
            selectedButton.setTextColor(getResources().getColor(R.color.textOnPrimary, null));
        }

        selectedDurationText.setText("⏱️ " + displayText);
    }

    private void setupTimeButtons() {
        timeMorning.setOnClickListener(v -> selectTimeOfDay("morning", "🌅 Morgen"));
        timeAfternoon.setOnClickListener(v -> selectTimeOfDay("afternoon", "☀️ Mittag"));
        timeEvening.setOnClickListener(v -> selectTimeOfDay("evening", "🌙 Abend"));
    }

    private void selectTimeOfDay(String time, String displayText) {
        preferredTimeOfDay = time;

        // Reset all button colors
        resetButtonColor(timeMorning);
        resetButtonColor(timeAfternoon);
        resetButtonColor(timeEvening);

        // Highlight selected button
        Button selectedButton = null;
        if (time.equals("morning")) selectedButton = timeMorning;
        else if (time.equals("afternoon")) selectedButton = timeAfternoon;
        else if (time.equals("evening")) selectedButton = timeEvening;

        if (selectedButton != null) {
            selectedButton.setBackgroundColor(getResources().getColor(R.color.colorAccent, null));
            selectedButton.setTextColor(getResources().getColor(R.color.textOnPrimary, null));
        }
    }

    private void resetButtonColor(Button button) {
        button.setBackgroundColor(getResources().getColor(R.color.cardBackground, null));
        button.setTextColor(getResources().getColor(R.color.textPrimary, null));
    }

    // Public getters for data
    public long getEstimatedDuration() {
        return estimatedDuration;
    }

    public String getPreferredTimeOfDay() {
        return preferredTimeOfDay;
    }

    public String getCategory() {
        return inputCategory.getText().toString().trim();
    }
}

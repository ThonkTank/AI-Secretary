package com.aisecretary.taskmaster.fragments;

import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.aisecretary.taskmaster.R;
import com.aisecretary.taskmaster.utils.CategoryManager;

import java.util.List;

/**
 * TaskDetailsFragment - Tab 3: Detail-Informationen
 *
 * Erfasst: Geschätzte Dauer, Bevorzugte Zeit, Kategorie
 */
public class TaskDetailsFragment extends Fragment {

    private TextView selectedDurationText;
    private TextView selectedCategoryText;
    private LinearLayout categoryButtonsContainer;

    private Button duration5min, duration15min, duration30min;
    private Button duration1h, duration2h, durationCustom;
    private Button timeMorning, timeAfternoon, timeEvening;

    private long estimatedDuration = 0; // In milliseconds, 0 = not set
    private String preferredTimeOfDay = ""; // Empty = not set
    private String category = ""; // Category ID
    private List<CategoryManager.Category> categories;

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
        selectedCategoryText = view.findViewById(R.id.selected_category);
        categoryButtonsContainer = view.findViewById(R.id.category_buttons_container);

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

        // Set up category buttons (Phase 8.2)
        setupCategoryButtons();
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

    /**
     * Setup category buttons (Phase 8.2)
     */
    private void setupCategoryButtons() {
        categories = CategoryManager.getAllCategories();

        // Create rows of 3 buttons each
        LinearLayout currentRow = null;
        int buttonsInRow = 0;

        for (int i = 0; i < categories.size(); i++) {
            CategoryManager.Category cat = categories.get(i);

            // Create new row every 3 buttons
            if (buttonsInRow == 0) {
                currentRow = new LinearLayout(requireContext());
                currentRow.setOrientation(LinearLayout.HORIZONTAL);
                currentRow.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ));
                categoryButtonsContainer.addView(currentRow);
            }

            // Create button
            Button categoryButton = new Button(requireContext());
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1.0f
            );
            params.setMargins(8, 8, 8, 8);
            categoryButton.setLayoutParams(params);
            categoryButton.setText(cat.getDisplayName());
            categoryButton.setTextSize(12);
            categoryButton.setBackgroundColor(getResources().getColor(R.color.cardBackground, null));
            categoryButton.setTextColor(getResources().getColor(R.color.textPrimary, null));
            categoryButton.setTag(cat.id);

            categoryButton.setOnClickListener(v -> selectCategory(cat));

            currentRow.addView(categoryButton);
            buttonsInRow++;

            // Reset counter after 3 buttons
            if (buttonsInRow >= 3) {
                buttonsInRow = 0;
            }
        }
    }

    /**
     * Select a category
     */
    private void selectCategory(CategoryManager.Category cat) {
        category = cat.id;

        // Reset all category buttons
        for (int i = 0; i < categoryButtonsContainer.getChildCount(); i++) {
            LinearLayout row = (LinearLayout) categoryButtonsContainer.getChildAt(i);
            for (int j = 0; j < row.getChildCount(); j++) {
                Button button = (Button) row.getChildAt(j);
                resetButtonColor(button);
            }
        }

        // Highlight selected button
        for (int i = 0; i < categoryButtonsContainer.getChildCount(); i++) {
            LinearLayout row = (LinearLayout) categoryButtonsContainer.getChildAt(i);
            for (int j = 0; j < row.getChildCount(); j++) {
                Button button = (Button) row.getChildAt(j);
                if (cat.id.equals(button.getTag())) {
                    button.setBackgroundColor(getResources().getColor(R.color.colorAccent, null));
                    button.setTextColor(getResources().getColor(R.color.textOnPrimary, null));
                }
            }
        }

        selectedCategoryText.setText(cat.getDisplayName());
    }

    // Public getters for data
    public long getEstimatedDuration() {
        return estimatedDuration;
    }

    public String getPreferredTimeOfDay() {
        return preferredTimeOfDay;
    }

    public String getCategory() {
        return category;
    }

    // Setter methods for Edit Mode

    public void setEstimatedDuration(long durationMillis) {
        if (durationMillis == 0) return;

        estimatedDuration = durationMillis;

        // Select appropriate button and update display
        if (durationMillis == 5 * 60 * 1000) {
            selectDuration(durationMillis, "5 Min");
        } else if (durationMillis == 15 * 60 * 1000) {
            selectDuration(durationMillis, "15 Min");
        } else if (durationMillis == 30 * 60 * 1000) {
            selectDuration(durationMillis, "30 Min");
        } else if (durationMillis == 60 * 60 * 1000) {
            selectDuration(durationMillis, "1 Std");
        } else if (durationMillis == 2 * 60 * 60 * 1000) {
            selectDuration(durationMillis, "2 Std");
        } else {
            // Custom duration
            long minutes = durationMillis / (60 * 1000);
            long hours = minutes / 60;
            long remainingMinutes = minutes % 60;

            String displayText;
            if (hours > 0) {
                displayText = hours + " Std " + (remainingMinutes > 0 ? remainingMinutes + " Min" : "");
            } else {
                displayText = minutes + " Min";
            }
            selectedDurationText.setText("⏱️ " + displayText);
        }
    }

    public void setPreferredTimeOfDay(String time) {
        if (time == null || time.isEmpty()) return;

        preferredTimeOfDay = time;

        String displayText = "";
        if ("morning".equals(time)) {
            displayText = "🌅 Morgen";
        } else if ("afternoon".equals(time)) {
            displayText = "☀️ Mittag";
        } else if ("evening".equals(time)) {
            displayText = "🌙 Abend";
        }

        if (!displayText.isEmpty()) {
            selectTimeOfDay(time, displayText);
        }
    }

    public void setCategory(String categoryId) {
        if (categoryId == null || categoryId.isEmpty()) {
            return;
        }

        this.category = categoryId;

        // Find and select the category
        CategoryManager.Category cat = CategoryManager.getCategoryById(categoryId);
        if (cat != null) {
            selectCategory(cat);
        }
    }
}

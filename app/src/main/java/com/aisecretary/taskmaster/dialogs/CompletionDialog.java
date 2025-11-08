package com.aisecretary.taskmaster.dialogs;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.TextView;

import com.aisecretary.taskmaster.R;
import com.aisecretary.taskmaster.database.TaskEntity;

/**
 * CompletionDialog - Dialog für Task-Completion mit Tracking
 *
 * Implementiert Phase 3.1 der Roadmap:
 * - Zeit-Input mit Quick-Select (5 Min, 15 Min, 30 Min, 1 Std)
 * - Schwierigkeits-Rating (5 Sterne)
 * - Streak-Feedback
 * - Überspringen-Button für schnelles Abhaken
 */
public class CompletionDialog extends Dialog {

    private TaskEntity task;
    private CompletionListener listener;

    private TextView dialogTitle;
    private TextView taskTitle;
    private TextView selectedTime;
    private TextView difficultyLabel;
    private TextView streakFeedback;

    private Button time5min, time15min, time30min, time1h;
    private TextView star1, star2, star3, star4, star5;
    private Button skipButton, saveButton;

    private long selectedDuration = 0; // In milliseconds
    private int selectedDifficulty = 0; // 0 = none, 1-5 = rating

    /**
     * Interface for completion callbacks
     */
    public interface CompletionListener {
        void onCompleteWithTracking(TaskEntity task, long duration, float difficulty);
        void onCompleteWithoutTracking(TaskEntity task);
        void onCancel();
    }

    public CompletionDialog(Context context, TaskEntity task, CompletionListener listener) {
        super(context);
        this.task = task;
        this.listener = listener;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dialog_completion);

        // Find views
        dialogTitle = findViewById(R.id.dialog_title);
        taskTitle = findViewById(R.id.task_title);
        selectedTime = findViewById(R.id.selected_time);
        difficultyLabel = findViewById(R.id.difficulty_label);
        streakFeedback = findViewById(R.id.streak_feedback);

        time5min = findViewById(R.id.time_5min);
        time15min = findViewById(R.id.time_15min);
        time30min = findViewById(R.id.time_30min);
        time1h = findViewById(R.id.time_1h);

        star1 = findViewById(R.id.star_1);
        star2 = findViewById(R.id.star_2);
        star3 = findViewById(R.id.star_3);
        star4 = findViewById(R.id.star_4);
        star5 = findViewById(R.id.star_5);

        skipButton = findViewById(R.id.skip_button);
        saveButton = findViewById(R.id.save_button);

        // Set task title
        taskTitle.setText(task.title);

        // Show streak feedback if recurring
        if (task.isRecurring && task.currentStreak >= 0) {
            int newStreak = task.currentStreak + 1;
            streakFeedback.setText("🔥 Streak: " + newStreak + " Tage!");
            streakFeedback.setVisibility(View.VISIBLE);

            // Check for milestone
            if (newStreak == 10 || newStreak == 25 || newStreak == 50 || newStreak == 100) {
                dialogTitle.setText("🎉 Meilenstein erreicht!");
                streakFeedback.setText("🔥🎉 " + newStreak + " Tage Streak! 🎉🔥");
            }
        }

        // Set up time buttons
        setupTimeButtons();

        // Set up star rating
        setupStarRating();

        // Set up action buttons
        setupActionButtons();
    }

    private void setupTimeButtons() {
        time5min.setOnClickListener(v -> selectTime(5 * 60 * 1000, "5 Min"));
        time15min.setOnClickListener(v -> selectTime(15 * 60 * 1000, "15 Min"));
        time30min.setOnClickListener(v -> selectTime(30 * 60 * 1000, "30 Min"));
        time1h.setOnClickListener(v -> selectTime(60 * 60 * 1000, "1 Std"));
    }

    private void selectTime(long durationMillis, String displayText) {
        selectedDuration = durationMillis;

        // Reset all buttons
        resetTimeButton(time5min);
        resetTimeButton(time15min);
        resetTimeButton(time30min);
        resetTimeButton(time1h);

        // Highlight selected button
        Button selectedButton = null;
        if (durationMillis == 5 * 60 * 1000) selectedButton = time5min;
        else if (durationMillis == 15 * 60 * 1000) selectedButton = time15min;
        else if (durationMillis == 30 * 60 * 1000) selectedButton = time30min;
        else if (durationMillis == 60 * 60 * 1000) selectedButton = time1h;

        if (selectedButton != null) {
            selectedButton.setBackgroundColor(getContext().getResources().getColor(R.color.colorAccent, null));
            selectedButton.setTextColor(getContext().getResources().getColor(R.color.textOnPrimary, null));
        }

        selectedTime.setText("⏱️ " + displayText);
    }

    private void resetTimeButton(Button button) {
        button.setBackgroundColor(getContext().getResources().getColor(R.color.cardBackground, null));
        button.setTextColor(getContext().getResources().getColor(R.color.textPrimary, null));
    }

    private void setupStarRating() {
        star1.setOnClickListener(v -> selectDifficulty(1));
        star2.setOnClickListener(v -> selectDifficulty(2));
        star3.setOnClickListener(v -> selectDifficulty(3));
        star4.setOnClickListener(v -> selectDifficulty(4));
        star5.setOnClickListener(v -> selectDifficulty(5));
    }

    private void selectDifficulty(int rating) {
        selectedDifficulty = rating;

        // Update stars
        TextView[] stars = {star1, star2, star3, star4, star5};
        for (int i = 0; i < stars.length; i++) {
            if (i < rating) {
                stars[i].setText("★");
                stars[i].setTextColor(getContext().getResources().getColor(R.color.colorAccent, null));
            } else {
                stars[i].setText("☆");
                stars[i].setTextColor(getContext().getResources().getColor(R.color.textSecondary, null));
            }
        }

        // Update label
        String[] labels = {"", "Sehr einfach", "Einfach", "Mittel", "Schwierig", "Sehr schwierig"};
        difficultyLabel.setText(labels[rating]);
    }

    private void setupActionButtons() {
        // Skip button - complete without tracking
        skipButton.setOnClickListener(v -> {
            if (listener != null) {
                listener.onCompleteWithoutTracking(task);
            }
            dismiss();
        });

        // Save button - complete with tracking
        saveButton.setOnClickListener(v -> {
            if (listener != null) {
                // Use defaults if not selected
                long duration = selectedDuration > 0 ? selectedDuration : 0;
                float difficulty = selectedDifficulty > 0 ? (float) selectedDifficulty : 3.0f; // Default: medium

                listener.onCompleteWithTracking(task, duration, difficulty);
            }
            dismiss();
        });

        // Cancel on outside touch
        setCancelable(true);
        setOnCancelListener(dialog -> {
            if (listener != null) {
                listener.onCancel();
            }
        });
    }
}

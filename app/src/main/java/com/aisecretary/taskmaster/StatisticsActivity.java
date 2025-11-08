package com.aisecretary.taskmaster;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.aisecretary.taskmaster.database.TaskEntity;
import com.aisecretary.taskmaster.repository.TaskRepository;
import com.aisecretary.taskmaster.utils.StatsManager;

import java.util.List;

/**
 * StatisticsActivity - Detailed statistics and streak overview
 *
 * Phase 4.2: Statistik-Dashboard
 * Displays comprehensive statistics about task completion and streaks.
 */
public class StatisticsActivity extends Activity {

    private TaskRepository repository;

    // Today's stats
    private TextView todayCountText;
    private TextView todayPercentageText;
    private TextView todayMotivationalText;

    // Weekly stats
    private TextView weekTotalText;
    private TextView weekAverageText;

    // Productivity
    private TextView productivityScoreText;
    private TextView productivityLevelText;

    // Streaks
    private LinearLayout streaksContainer;
    private TextView atRiskHeader;
    private LinearLayout atRiskContainer;
    private TextView bestStreakText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_statistics);

        repository = TaskRepository.getInstance(this);

        // Find views
        todayCountText = findViewById(R.id.stats_today_count);
        todayPercentageText = findViewById(R.id.stats_today_percentage);
        todayMotivationalText = findViewById(R.id.stats_today_motivational);

        weekTotalText = findViewById(R.id.stats_week_total);
        weekAverageText = findViewById(R.id.stats_week_average);

        productivityScoreText = findViewById(R.id.stats_productivity_score);
        productivityLevelText = findViewById(R.id.stats_productivity_level);

        streaksContainer = findViewById(R.id.stats_streaks_container);
        atRiskHeader = findViewById(R.id.stats_at_risk_header);
        atRiskContainer = findViewById(R.id.stats_at_risk_container);
        bestStreakText = findViewById(R.id.stats_best_streak);

        // Load and display statistics
        loadStatistics();
    }

    /**
     * Load and display all statistics
     */
    private void loadStatistics() {
        // Today's stats
        int completedToday = repository.getCompletedTodayCount();
        List<TaskEntity> todayTasks = repository.getTodayTasks();
        int totalToday = todayTasks.size() + completedToday;

        StatsManager.TodayStats todayStats = new StatsManager.TodayStats(completedToday, totalToday);
        todayCountText.setText(completedToday + "/" + totalToday + " Tasks");
        todayPercentageText.setText(StatsManager.formatCompletionPercentage(completedToday, totalToday));
        todayMotivationalText.setText(StatsManager.getMotivationalMessage(todayStats.completionPercentage));

        // Weekly stats
        int weeklyCompleted = repository.getCompletedLast7DaysCount();
        float weeklyAverage = repository.getAverageTasksPerDay();

        StatsManager.WeeklyStats weeklyStats = new StatsManager.WeeklyStats(weeklyCompleted, 7);
        weekTotalText.setText(weeklyCompleted + " Tasks erledigt");
        weekAverageText.setText(String.format("⌀ %.1f Tasks/Tag", weeklyAverage));

        // Productivity score
        List<TaskEntity> allStreakTasks = repository.getTasksWithStreaks();
        int longestCurrentStreak = 0;
        for (TaskEntity task : allStreakTasks) {
            if (task.currentStreak > longestCurrentStreak) {
                longestCurrentStreak = task.currentStreak;
            }
        }

        int productivityScore = StatsManager.calculateProductivityScore(
                completedToday,
                totalToday,
                weeklyAverage,
                longestCurrentStreak
        );
        productivityScoreText.setText(productivityScore + "/100");
        productivityLevelText.setText(StatsManager.getProductivityLevel(productivityScore));

        // Top streaks
        List<StatsManager.StreakSummary> topStreaks = StatsManager.getTopStreaks(allStreakTasks, 5);
        displayStreaks(streaksContainer, topStreaks);

        // At-risk streaks
        List<StatsManager.StreakSummary> atRiskStreaks = StatsManager.getStreaksAtRisk(allStreakTasks);
        if (!atRiskStreaks.isEmpty()) {
            atRiskHeader.setVisibility(View.VISIBLE);
            atRiskContainer.setVisibility(View.VISIBLE);
            displayAtRiskStreaks(atRiskContainer, atRiskStreaks);
        } else {
            atRiskHeader.setVisibility(View.GONE);
            atRiskContainer.setVisibility(View.GONE);
        }

        // Best streak ever
        List<TaskEntity> allTasks = repository.getAllTasks();
        int bestStreak = StatsManager.getLongestStreakEver(allTasks);
        bestStreakText.setText(bestStreak + " Tage");
    }

    /**
     * Display top streaks in container
     */
    private void displayStreaks(LinearLayout container, List<StatsManager.StreakSummary> streaks) {
        container.removeAllViews();

        if (streaks.isEmpty()) {
            TextView emptyText = new TextView(this);
            emptyText.setText("Keine aktiven Streaks");
            emptyText.setTextColor(getResources().getColor(R.color.textSecondary, null));
            emptyText.setPadding(16, 16, 16, 16);
            container.addView(emptyText);
            return;
        }

        for (StatsManager.StreakSummary streak : streaks) {
            // Create streak card
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackgroundColor(getResources().getColor(R.color.cardBackground, null));
            card.setPadding(32, 24, 32, 24);

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            params.setMargins(0, 0, 0, 16);
            card.setLayoutParams(params);

            // Task title
            TextView titleText = new TextView(this);
            titleText.setText(streak.task.title);
            titleText.setTextColor(getResources().getColor(R.color.textPrimary, null));
            titleText.setTextSize(16);
            titleText.setTypeface(null, android.graphics.Typeface.BOLD);
            card.addView(titleText);

            // Streak info
            TextView streakText = new TextView(this);
            streakText.setText(streak.emoji + " " + streak.streak + " Tage - " + streak.description);
            streakText.setTextColor(getResources().getColor(R.color.colorAccent, null));
            streakText.setTextSize(14);
            streakText.setPadding(0, 8, 0, 0);
            card.addView(streakText);

            container.addView(card);
        }
    }

    /**
     * Display at-risk streaks
     */
    private void displayAtRiskStreaks(LinearLayout container, List<StatsManager.StreakSummary> streaks) {
        container.removeAllViews();

        for (StatsManager.StreakSummary streak : streaks) {
            // Create warning card
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackgroundColor(getResources().getColor(R.color.warningBackground, null));
            card.setPadding(32, 24, 32, 24);

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            params.setMargins(0, 0, 0, 16);
            card.setLayoutParams(params);

            // Task title
            TextView titleText = new TextView(this);
            titleText.setText("⚠️ " + streak.task.title);
            titleText.setTextColor(getResources().getColor(R.color.textWarning, null));
            titleText.setTextSize(16);
            titleText.setTypeface(null, android.graphics.Typeface.BOLD);
            card.addView(titleText);

            // Warning message
            TextView warningText = new TextView(this);
            String message;
            if (streak.daysUntilExpire == 0) {
                message = "Streak läuft heute ab! " + streak.emoji + " " + streak.streak + " Tage";
            } else if (streak.daysUntilExpire == 1) {
                message = "Läuft morgen ab! " + streak.emoji + " " + streak.streak + " Tage";
            } else {
                message = "Läuft in " + streak.daysUntilExpire + " Tagen ab - " + streak.emoji + " " + streak.streak + " Tage";
            }
            warningText.setText(message);
            warningText.setTextColor(getResources().getColor(R.color.textSecondary, null));
            warningText.setTextSize(14);
            warningText.setPadding(0, 8, 0, 0);
            card.addView(warningText);

            container.addView(card);
        }
    }
}

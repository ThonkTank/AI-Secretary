package com.aisecretary.taskmaster;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.aisecretary.taskmaster.database.CompletionHistoryEntity;
import com.aisecretary.taskmaster.database.TaskEntity;
import com.aisecretary.taskmaster.repository.TaskRepository;
import com.aisecretary.taskmaster.utils.StatsManager;
import com.aisecretary.taskmaster.utils.TimeAnalyzer;

import java.util.ArrayList;
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

    // Time Analysis (Phase 3.3)
    private TextView timeRecommendationText;
    private LinearLayout timeOfDayContainer;

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

        timeRecommendationText = findViewById(R.id.stats_time_recommendation);
        timeOfDayContainer = findViewById(R.id.stats_time_of_day_container);

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

        // Time analysis (Phase 3.3)
        loadTimeAnalysis();
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

    /**
     * Load and display time analysis
     * Phase 3.3: Zeitpunkt-Analyse
     */
    private void loadTimeAnalysis() {
        // Get all completion history
        List<TaskEntity> allTasks = repository.getAllTasks();
        List<CompletionHistoryEntity> allHistory = new ArrayList<>();

        for (TaskEntity task : allTasks) {
            List<CompletionHistoryEntity> taskHistory = repository.getTaskHistory(task.id);
            allHistory.addAll(taskHistory);
        }

        if (allHistory.isEmpty()) {
            timeRecommendationText.setText("Noch keine Daten für Zeitanalyse");
            timeOfDayContainer.removeAllViews();
            return;
        }

        // Get recommendation
        String recommendation = TimeAnalyzer.getTimeOfDayRecommendation(allHistory);
        timeRecommendationText.setText(recommendation);

        // Get time of day summaries
        List<TimeAnalyzer.TimeOfDaySummary> summaries = TimeAnalyzer.analyzeByTimeOfDay(allHistory);
        displayTimeOfDaySummaries(timeOfDayContainer, summaries);
    }

    /**
     * Display time of day summaries
     */
    private void displayTimeOfDaySummaries(LinearLayout container, List<TimeAnalyzer.TimeOfDaySummary> summaries) {
        container.removeAllViews();

        if (summaries.isEmpty()) {
            TextView emptyText = new TextView(this);
            emptyText.setText("Keine Daten");
            emptyText.setTextColor(getResources().getColor(R.color.textSecondary, null));
            emptyText.setPadding(16, 16, 16, 16);
            container.addView(emptyText);
            return;
        }

        for (TimeAnalyzer.TimeOfDaySummary summary : summaries) {
            // Create row
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0, 12, 0, 12);

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            row.setLayoutParams(params);

            // Time of day label
            TextView labelText = new TextView(this);
            labelText.setText(summary.emoji + " " + summary.label);
            labelText.setTextColor(getResources().getColor(R.color.textPrimary, null));
            labelText.setTextSize(14);
            LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1.0f
            );
            labelText.setLayoutParams(labelParams);
            row.addView(labelText);

            // Count and percentage
            TextView countText = new TextView(this);
            countText.setText(summary.count + " Tasks (" + String.format("%.0f%%", summary.percentage) + ")");
            countText.setTextColor(getResources().getColor(R.color.textSecondary, null));
            countText.setTextSize(14);
            row.addView(countText);

            container.addView(row);

            // Progress bar
            LinearLayout barContainer = new LinearLayout(this);
            barContainer.setOrientation(LinearLayout.HORIZONTAL);
            barContainer.setBackgroundColor(getResources().getColor(R.color.background, null));
            LinearLayout.LayoutParams barContainerParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    24
            );
            barContainerParams.setMargins(0, 4, 0, 12);
            barContainer.setLayoutParams(barContainerParams);

            // Progress fill
            View progressBar = new View(this);
            progressBar.setBackgroundColor(getResources().getColor(R.color.primary, null));
            LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    summary.percentage / 100.0f
            );
            progressBar.setLayoutParams(barParams);
            barContainer.addView(progressBar);

            // Empty space
            View emptySpace = new View(this);
            LinearLayout.LayoutParams emptyParams = new LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    (100.0f - summary.percentage) / 100.0f
            );
            emptySpace.setLayoutParams(emptyParams);
            barContainer.addView(emptySpace);

            container.addView(barContainer);
        }
    }
}

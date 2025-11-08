package com.aisecretary.taskmaster.utils;

import com.aisecretary.taskmaster.database.CompletionHistoryEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * TimeAnalyzer - Analyze completion patterns by time of day
 *
 * Provides insights into when tasks are typically completed
 * Phase 3.3: Zeitpunkt-Analyse
 */
public class TimeAnalyzer {

    /**
     * Data class for hourly completion distribution
     */
    public static class HourlyDistribution {
        public int hour; // 0-23
        public int count;
        public float percentage;
        public String label; // "00:00", "01:00", etc.
        public String timeOfDay; // "morning", "afternoon", "evening", "night"

        public HourlyDistribution(int hour, int count, float percentage) {
            this.hour = hour;
            this.count = count;
            this.percentage = percentage;
            this.label = String.format("%02d:00", hour);
            this.timeOfDay = getTimeOfDayLabel(hour);
        }

        private String getTimeOfDayLabel(int hour) {
            if (hour >= 5 && hour < 12) {
                return "morning";
            } else if (hour >= 12 && hour < 18) {
                return "afternoon";
            } else if (hour >= 18 && hour < 22) {
                return "evening";
            } else {
                return "night";
            }
        }
    }

    /**
     * Data class for time-of-day summary
     */
    public static class TimeOfDaySummary {
        public String timeOfDay;
        public int count;
        public float percentage;
        public String emoji;
        public String label;

        public TimeOfDaySummary(String timeOfDay, int count, float percentage) {
            this.timeOfDay = timeOfDay;
            this.count = count;
            this.percentage = percentage;
            this.emoji = getTimeOfDayEmoji(timeOfDay);
            this.label = getTimeOfDayLabel(timeOfDay);
        }

        private String getTimeOfDayEmoji(String timeOfDay) {
            switch (timeOfDay) {
                case "morning":
                    return "🌅";
                case "afternoon":
                    return "☀️";
                case "evening":
                    return "🌆";
                case "night":
                    return "🌙";
                default:
                    return "⏰";
            }
        }

        private String getTimeOfDayLabel(String timeOfDay) {
            switch (timeOfDay) {
                case "morning":
                    return "Morgen (5-12 Uhr)";
                case "afternoon":
                    return "Nachmittag (12-18 Uhr)";
                case "evening":
                    return "Abend (18-22 Uhr)";
                case "night":
                    return "Nacht (22-5 Uhr)";
                default:
                    return "Unbekannt";
            }
        }
    }

    /**
     * Analyze completion history by hour
     *
     * @param history List of completion history entries
     * @return Hourly distribution of completions
     */
    public static List<HourlyDistribution> analyzeByHour(List<CompletionHistoryEntity> history) {
        if (history == null || history.isEmpty()) {
            return new ArrayList<>();
        }

        // Count completions by hour
        Map<Integer, Integer> hourCounts = new HashMap<>();
        for (CompletionHistoryEntity entry : history) {
            int hour = entry.timeOfDay;
            hourCounts.put(hour, hourCounts.getOrDefault(hour, 0) + 1);
        }

        int total = history.size();

        // Build distribution list
        List<HourlyDistribution> distribution = new ArrayList<>();
        for (int hour = 0; hour < 24; hour++) {
            int count = hourCounts.getOrDefault(hour, 0);
            float percentage = (count / (float) total) * 100.0f;
            distribution.add(new HourlyDistribution(hour, count, percentage));
        }

        return distribution;
    }

    /**
     * Analyze completion history by time of day
     *
     * @param history List of completion history entries
     * @return Time-of-day summary
     */
    public static List<TimeOfDaySummary> analyzeByTimeOfDay(List<CompletionHistoryEntity> history) {
        if (history == null || history.isEmpty()) {
            return new ArrayList<>();
        }

        // Count completions by time of day
        Map<String, Integer> timeOfDayCounts = new HashMap<>();
        for (CompletionHistoryEntity entry : history) {
            int hour = entry.timeOfDay;
            String timeOfDay;
            if (hour >= 5 && hour < 12) {
                timeOfDay = "morning";
            } else if (hour >= 12 && hour < 18) {
                timeOfDay = "afternoon";
            } else if (hour >= 18 && hour < 22) {
                timeOfDay = "evening";
            } else {
                timeOfDay = "night";
            }
            timeOfDayCounts.put(timeOfDay, timeOfDayCounts.getOrDefault(timeOfDay, 0) + 1);
        }

        int total = history.size();

        // Build summary list (sorted: morning, afternoon, evening, night)
        List<TimeOfDaySummary> summaries = new ArrayList<>();
        String[] order = {"morning", "afternoon", "evening", "night"};
        for (String timeOfDay : order) {
            int count = timeOfDayCounts.getOrDefault(timeOfDay, 0);
            if (count > 0) {
                float percentage = (count / (float) total) * 100.0f;
                summaries.add(new TimeOfDaySummary(timeOfDay, count, percentage));
            }
        }

        return summaries;
    }

    /**
     * Get most productive time of day
     *
     * @param history List of completion history entries
     * @return Most productive time of day summary, or null if no data
     */
    public static TimeOfDaySummary getMostProductiveTimeOfDay(List<CompletionHistoryEntity> history) {
        List<TimeOfDaySummary> summaries = analyzeByTimeOfDay(history);
        if (summaries.isEmpty()) {
            return null;
        }

        TimeOfDaySummary most = summaries.get(0);
        for (TimeOfDaySummary summary : summaries) {
            if (summary.count > most.count) {
                most = summary;
            }
        }

        return most;
    }

    /**
     * Get peak hour (hour with most completions)
     *
     * @param history List of completion history entries
     * @return Peak hour distribution, or null if no data
     */
    public static HourlyDistribution getPeakHour(List<CompletionHistoryEntity> history) {
        List<HourlyDistribution> distribution = analyzeByHour(history);
        if (distribution.isEmpty()) {
            return null;
        }

        HourlyDistribution peak = distribution.get(0);
        for (HourlyDistribution dist : distribution) {
            if (dist.count > peak.count) {
                peak = dist;
            }
        }

        return peak.count > 0 ? peak : null;
    }

    /**
     * Generate visual bar chart for hourly distribution (ASCII)
     *
     * @param history List of completion history entries
     * @param maxBars Maximum number of bars to show (default: 24)
     * @return ASCII bar chart string
     */
    public static String generateHourlyChart(List<CompletionHistoryEntity> history, int maxBars) {
        List<HourlyDistribution> distribution = analyzeByHour(history);
        if (distribution.isEmpty()) {
            return "Keine Daten";
        }

        // Find max count for scaling
        int maxCount = 0;
        for (HourlyDistribution dist : distribution) {
            if (dist.count > maxCount) {
                maxCount = dist.count;
            }
        }

        if (maxCount == 0) {
            return "Keine Daten";
        }

        StringBuilder chart = new StringBuilder();
        for (HourlyDistribution dist : distribution) {
            if (dist.count > 0) {
                // Scale bar length
                int barLength = (int) Math.ceil((dist.count / (float) maxCount) * 20);
                String bar = "";
                for (int i = 0; i < barLength; i++) {
                    bar += "█";
                }

                chart.append(String.format("%s: %s (%d)\n", dist.label, bar, dist.count));
            }
        }

        return chart.toString();
    }

    /**
     * Get time-of-day recommendation based on history
     *
     * @param history List of completion history entries
     * @return Recommendation text
     */
    public static String getTimeOfDayRecommendation(List<CompletionHistoryEntity> history) {
        TimeOfDaySummary most = getMostProductiveTimeOfDay(history);
        if (most == null) {
            return "Noch keine Daten für Empfehlung";
        }

        return String.format("Du bist am produktivsten %s %s (%.0f%% deiner Tasks)",
                most.timeOfDay.equals("morning") ? "am" : "am",
                most.label.split(" ")[0],
                most.percentage);
    }
}

package data;

import com.autosecretary.R;
import controller.TodoManager.TaskEntry;

import java.util.ArrayList;
import java.util.List;

/**
 * Gemeinsame Datenaufbereitung für Task-Listen in App und Widget.
 * Wandelt TaskEntry-Listen in DisplayRow-Listen um (Goal-Header + Tasks/Events).
 */
public class TaskListData {

    // ══════════════════════════════════════════════════════════════════════════
    // DisplayRow — Sealed Interface für die drei Zeilentypen
    // ══════════════════════════════════════════════════════════════════════════

    public sealed interface DisplayRow permits GoalHeader, TaskItem, CalendarEvent {}

    public record GoalHeader(String title, String icon, String color) implements DisplayRow {}
    public record TaskItem(TaskEntry entry) implements DisplayRow {}
    public record CalendarEvent(TaskEntry entry) implements DisplayRow {}

    // ══════════════════════════════════════════════════════════════════════════
    // fromEntries — Konvertiert TaskEntry-Liste in DisplayRow-Liste
    // ══════════════════════════════════════════════════════════════════════════

    public static List<DisplayRow> fromEntries(List<TaskEntry> entries) {
        List<DisplayRow> rows = new ArrayList<>();
        String currentGoal = "";

        for (TaskEntry entry : entries) {
            // Kalender-Events werden inline eingefuegt, OHNE eigenen Header
            if (entry.isCalendarEvent()) {
                rows.add(new CalendarEvent(entry));
                continue;
            }

            // Neuer Goal-Header wenn sich der Goal-Titel aendert (nur fuer Tasks)
            if (!entry.goalTitle().equals(currentGoal)) {
                currentGoal = entry.goalTitle();
                rows.add(new GoalHeader(currentGoal, entry.goalIcon(), entry.goalColor()));
            }

            rows.add(new TaskItem(entry));
        }

        return rows;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // getStreakRarityColorRes — MMO-Rarity-Farbe basierend auf Streak
    // ══════════════════════════════════════════════════════════════════════════

    public static int getStreakRarityColorRes(int streak) {
        if (streak >= 100) return R.color.rarity_legendary;
        if (streak >= 60)  return R.color.rarity_epic;
        if (streak >= 30)  return R.color.rarity_rare;
        if (streak >= 10)  return R.color.rarity_uncommon;
        return R.color.rarity_common;
    }
}

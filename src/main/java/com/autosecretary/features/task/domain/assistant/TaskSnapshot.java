package com.autosecretary.features.task.domain.assistant;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

/**
 * Read-only picture of the current categories, tasks and reserved category windows, sent to Claude so
 * it references real ids when proposing changes and so the confirmation diff can show old→new values.
 * Android-free domain value type.
 */
public record TaskSnapshot(List<CategoryInfo> categories, List<TaskInfo> tasks, List<WindowInfo> windows) {

    /** Convenience for snapshots without reserved category windows. */
    public TaskSnapshot(List<CategoryInfo> categories, List<TaskInfo> tasks) {
        this(categories, tasks, List.of());
    }

    /** One existing category, by id. */
    public record CategoryInfo(String id, String name, String icon, String colorHex) {}

    /** One existing task, by id, with the fields the assistant may reorganise. */
    public record TaskInfo(String id, String title, String categoryId, String priority, boolean leisure) {}

    /** One existing reserved category window, by id, for the confirmation diff. */
    public record WindowInfo(String id, String categoryId, DayOfWeek dayOfWeek,
                             LocalTime startTime, LocalTime endTime) {}
}

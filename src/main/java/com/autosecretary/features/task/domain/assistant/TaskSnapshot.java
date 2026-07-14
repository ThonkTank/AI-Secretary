package com.autosecretary.features.task.domain.assistant;

import java.util.List;

/**
 * Read-only picture of the current categories and tasks, sent to Claude so it references real ids
 * when proposing changes. Android-free domain value type.
 */
public record TaskSnapshot(List<CategoryInfo> categories, List<TaskInfo> tasks) {

    /** One existing category, by id. */
    public record CategoryInfo(String id, String name, String icon, String colorHex) {}

    /** One existing task, by id, with the fields the assistant may reorganise. */
    public record TaskInfo(String id, String title, String categoryId, String priority, boolean leisure) {}
}

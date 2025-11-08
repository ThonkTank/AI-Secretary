package com.aisecretary.taskmaster.utils;

import com.aisecretary.taskmaster.database.TaskEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * CategoryManager - Manages task categories and filtering
 *
 * Features:
 * - Predefined category list with emojis
 * - Category-based filtering
 * - Category statistics
 * - Phase 8.2
 */
public class CategoryManager {

    /**
     * Category data class
     */
    public static class Category {
        public String id;
        public String name;
        public String emoji;
        public int color;

        public Category(String id, String name, String emoji, int color) {
            this.id = id;
            this.name = name;
            this.emoji = emoji;
            this.color = color;
        }

        public String getDisplayName() {
            return emoji + " " + name;
        }
    }

    /**
     * Category statistics
     */
    public static class CategoryStats {
        public Category category;
        public int totalTasks;
        public int completedTasks;
        public int incompleteTasks;
        public float completionRate;
        public int currentStreak;
        public long totalTimeSpent;

        public CategoryStats(Category category) {
            this.category = category;
        }
    }

    // Predefined categories
    private static final List<Category> PREDEFINED_CATEGORIES = new ArrayList<>();

    static {
        PREDEFINED_CATEGORIES.add(new Category("work", "Arbeit", "💼", 0xFF2196F3));
        PREDEFINED_CATEGORIES.add(new Category("personal", "Persönlich", "🏠", 0xFF4CAF50));
        PREDEFINED_CATEGORIES.add(new Category("health", "Gesundheit", "💪", 0xFFFF5722));
        PREDEFINED_CATEGORIES.add(new Category("learning", "Lernen", "📚", 0xFF9C27B0));
        PREDEFINED_CATEGORIES.add(new Category("finance", "Finanzen", "💰", 0xFFFF9800));
        PREDEFINED_CATEGORIES.add(new Category("social", "Sozial", "👥", 0xFFE91E63));
        PREDEFINED_CATEGORIES.add(new Category("creative", "Kreativ", "🎨", 0xFF673AB7));
        PREDEFINED_CATEGORIES.add(new Category("shopping", "Einkaufen", "🛒", 0xFF795548));
        PREDEFINED_CATEGORIES.add(new Category("household", "Haushalt", "🏡", 0xFF607D8B));
        PREDEFINED_CATEGORIES.add(new Category("project", "Projekt", "🚀", 0xFF00BCD4));
    }

    /**
     * Get all predefined categories
     */
    public static List<Category> getAllCategories() {
        return new ArrayList<>(PREDEFINED_CATEGORIES);
    }

    /**
     * Get category by ID
     */
    public static Category getCategoryById(String id) {
        if (id == null || id.isEmpty()) {
            return null;
        }

        for (Category category : PREDEFINED_CATEGORIES) {
            if (category.id.equals(id)) {
                return category;
            }
        }

        return null;
    }

    /**
     * Get category by name (case-insensitive)
     */
    public static Category getCategoryByName(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }

        for (Category category : PREDEFINED_CATEGORIES) {
            if (category.name.equalsIgnoreCase(name)) {
                return category;
            }
        }

        return null;
    }

    /**
     * Filter tasks by category
     */
    public static List<TaskEntity> filterByCategory(List<TaskEntity> tasks, String categoryId) {
        List<TaskEntity> filtered = new ArrayList<>();

        if (categoryId == null || categoryId.isEmpty() || categoryId.equals("all")) {
            return new ArrayList<>(tasks);
        }

        for (TaskEntity task : tasks) {
            if (categoryId.equals(task.category)) {
                filtered.add(task);
            }
        }

        return filtered;
    }

    /**
     * Get tasks without category
     */
    public static List<TaskEntity> getUncategorizedTasks(List<TaskEntity> tasks) {
        List<TaskEntity> uncategorized = new ArrayList<>();

        for (TaskEntity task : tasks) {
            if (task.category == null || task.category.isEmpty()) {
                uncategorized.add(task);
            }
        }

        return uncategorized;
    }

    /**
     * Get all unique categories from tasks
     */
    public static List<Category> getUsedCategories(List<TaskEntity> tasks) {
        Map<String, Category> usedCategories = new HashMap<>();

        for (TaskEntity task : tasks) {
            if (task.category != null && !task.category.isEmpty()) {
                Category category = getCategoryById(task.category);
                if (category != null) {
                    usedCategories.put(category.id, category);
                }
            }
        }

        return new ArrayList<>(usedCategories.values());
    }

    /**
     * Calculate category statistics
     */
    public static CategoryStats getCategoryStats(List<TaskEntity> tasks, String categoryId) {
        Category category = getCategoryById(categoryId);
        if (category == null) {
            return null;
        }

        CategoryStats stats = new CategoryStats(category);

        for (TaskEntity task : tasks) {
            if (categoryId.equals(task.category)) {
                stats.totalTasks++;

                if (task.completed) {
                    stats.completedTasks++;
                } else {
                    stats.incompleteTasks++;
                }

                if (task.isRecurring && task.currentStreak > 0) {
                    stats.currentStreak = Math.max(stats.currentStreak, task.currentStreak);
                }

                if (task.averageCompletionTime > 0) {
                    stats.totalTimeSpent += task.averageCompletionTime * task.completionCount;
                }
            }
        }

        if (stats.totalTasks > 0) {
            stats.completionRate = (float) stats.completedTasks / stats.totalTasks * 100f;
        }

        return stats;
    }

    /**
     * Get all category statistics
     */
    public static List<CategoryStats> getAllCategoryStats(List<TaskEntity> tasks) {
        List<CategoryStats> allStats = new ArrayList<>();

        for (Category category : PREDEFINED_CATEGORIES) {
            CategoryStats stats = getCategoryStats(tasks, category.id);
            if (stats != null && stats.totalTasks > 0) {
                allStats.add(stats);
            }
        }

        // Sort by total tasks (descending)
        allStats.sort((a, b) -> Integer.compare(b.totalTasks, a.totalTasks));

        return allStats;
    }

    /**
     * Get category distribution (for visualization)
     */
    public static Map<String, Integer> getCategoryDistribution(List<TaskEntity> tasks) {
        Map<String, Integer> distribution = new HashMap<>();

        for (TaskEntity task : tasks) {
            String categoryId = task.category;
            if (categoryId == null || categoryId.isEmpty()) {
                categoryId = "uncategorized";
            }

            distribution.put(categoryId, distribution.getOrDefault(categoryId, 0) + 1);
        }

        return distribution;
    }

    /**
     * Format time duration for display
     */
    public static String formatDuration(long milliseconds) {
        if (milliseconds <= 0) {
            return "0 Min";
        }

        long hours = milliseconds / 3600000;
        long minutes = (milliseconds % 3600000) / 60000;

        if (hours > 0) {
            return hours + "h " + minutes + "m";
        } else {
            return minutes + " Min";
        }
    }

    /**
     * Get category display name with task count
     */
    public static String getCategoryDisplayWithCount(String categoryId, List<TaskEntity> tasks) {
        Category category = getCategoryById(categoryId);
        if (category == null) {
            return "Alle";
        }

        int count = filterByCategory(tasks, categoryId).size();
        return category.getDisplayName() + " (" + count + ")";
    }
}

package com.secretary.helloworld;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Manages filtering and sorting of tasks.
 * Extracted from TaskActivity for better separation of concerns.
 */
public class TaskFilterManager {

    // Filter options
    private String searchQuery = "";
    private String categoryFilter = null;
    private CompletionFilter completionFilter = CompletionFilter.ALL;
    private SortOption sortOption = SortOption.PRIORITY;

    public enum CompletionFilter {
        ALL,
        ACTIVE_ONLY,
        COMPLETED_ONLY
    }

    public enum SortOption {
        PRIORITY("Priority (High to Low)"),
        DUE_DATE("Due Date (Nearest First)"),
        CREATED("Created (Newest First)"),
        TITLE("Title (A-Z)"),
        CATEGORY("Category");

        private final String displayName;

        SortOption(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    // Setters for filter options
    public void setSearchQuery(String query) {
        this.searchQuery = query != null ? query.toLowerCase().trim() : "";
    }

    public void setCategoryFilter(String category) {
        this.categoryFilter = category;
    }

    public void setCompletionFilter(CompletionFilter filter) {
        this.completionFilter = filter != null ? filter : CompletionFilter.ALL;
    }

    public void setSortOption(SortOption option) {
        this.sortOption = option != null ? option : SortOption.PRIORITY;
    }

    /**
     * Apply all filters to the task list
     */
    public List<Task> applyFilters(List<Task> allTasks) {
        List<Task> filteredList = new ArrayList<>();

        for (Task task : allTasks) {
            if (passesAllFilters(task)) {
                filteredList.add(task);
            }
        }

        return filteredList;
    }

    /**
     * Check if a task passes all active filters
     */
    private boolean passesAllFilters(Task task) {
        // Completion filter
        if (!passesCompletionFilter(task)) {
            return false;
        }

        // Category filter
        if (!passesCategoryFilter(task)) {
            return false;
        }

        // Search filter
        if (!passesSearchFilter(task)) {
            return false;
        }

        return true;
    }

    private boolean passesCompletionFilter(Task task) {
        switch (completionFilter) {
            case ACTIVE_ONLY:
                return !task.isCompleted();
            case COMPLETED_ONLY:
                return task.isCompleted();
            default:
                return true;
        }
    }

    private boolean passesCategoryFilter(Task task) {
        if (categoryFilter == null || categoryFilter.isEmpty()) {
            return true;
        }

        String taskCategory = task.getCategory();
        return taskCategory != null && taskCategory.equals(categoryFilter);
    }

    private boolean passesSearchFilter(Task task) {
        if (searchQuery.isEmpty()) {
            return true;
        }

        // Search in title
        if (task.getTitle() != null &&
            task.getTitle().toLowerCase().contains(searchQuery)) {
            return true;
        }

        // Search in description
        if (task.getDescription() != null &&
            task.getDescription().toLowerCase().contains(searchQuery)) {
            return true;
        }

        // Search in category
        if (task.getCategory() != null &&
            task.getCategory().toLowerCase().contains(searchQuery)) {
            return true;
        }

        return false;
    }

    /**
     * Sort the filtered task list
     */
    public void sortTasks(List<Task> tasks) {
        Collections.sort(tasks, getComparator());
    }

    /**
     * Get the comparator for the current sort option
     */
    private Comparator<Task> getComparator() {
        switch (sortOption) {
            case PRIORITY:
                return (t1, t2) -> Integer.compare(t2.getPriority(), t1.getPriority());

            case DUE_DATE:
                return (t1, t2) -> {
                    // Tasks without due dates go last
                    if (t1.getDueDate() == 0) return 1;
                    if (t2.getDueDate() == 0) return -1;
                    return Long.compare(t1.getDueDate(), t2.getDueDate());
                };

            case CREATED:
                return (t1, t2) -> Long.compare(t2.getCreatedAt(), t1.getCreatedAt());

            case TITLE:
                return (t1, t2) -> {
                    String title1 = t1.getTitle() != null ? t1.getTitle() : "";
                    String title2 = t2.getTitle() != null ? t2.getTitle() : "";
                    return title1.compareToIgnoreCase(title2);
                };

            case CATEGORY:
                return (t1, t2) -> {
                    String cat1 = t1.getCategory() != null ? t1.getCategory() : "";
                    String cat2 = t2.getCategory() != null ? t2.getCategory() : "";
                    int categoryCompare = cat1.compareToIgnoreCase(cat2);

                    // If categories are same, sort by priority
                    if (categoryCompare == 0) {
                        return Integer.compare(t2.getPriority(), t1.getPriority());
                    }
                    return categoryCompare;
                };

            default:
                return (t1, t2) -> 0;
        }
    }

    /**
     * Get display names for sort options
     */
    public static String[] getSortOptionNames() {
        SortOption[] options = SortOption.values();
        String[] names = new String[options.length];
        for (int i = 0; i < options.length; i++) {
            names[i] = options[i].getDisplayName();
        }
        return names;
    }
}
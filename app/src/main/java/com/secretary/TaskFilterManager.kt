package com.secretary

/**
 * Task filtering and sorting manager.
 * Phase 4.5.3 Wave 6: Converted to Kotlin
 *
 * Manages search, filtering, and sorting logic for task lists.
 * Provides type-safe enums and clean functional filtering.
 */
class TaskFilterManager {

    // ========== Filter State ==========

    /**
     * Search query for title/description matching
     */
    var searchQuery: String = ""

    /**
     * Category filter (null = all categories)
     */
    var categoryFilter: String? = null

    /**
     * Completion status filter
     */
    var completionFilter: CompletionFilter = CompletionFilter.ALL

    /**
     * Current sort option
     */
    var sortOption: SortOption = SortOption.PRIORITY

    // ========== Enums ==========

    /**
     * Task completion filter options
     */
    enum class CompletionFilter {
        ALL,
        ACTIVE_ONLY,
        COMPLETED_ONLY
    }

    /**
     * Task sort options with display names
     */
    enum class SortOption(val displayName: String) {
        PRIORITY("Priority (High to Low)"),
        DUE_DATE("Due Date (Nearest First)"),
        CREATED("Created (Newest First)"),
        TITLE("Title (A-Z)"),
        CATEGORY("Category")
    }

    // ========== Filtering ==========

    /**
     * Apply all active filters to task list
     *
     * @param allTasks The complete task list
     * @return Filtered task list
     */
    fun applyFilters(allTasks: List<Task>): List<Task> {
        return allTasks
            .filter { matchesCompletionFilter(it) }
            .filter { matchesCategoryFilter(it) }
            .filter { matchesSearchQuery(it) }
    }

    /**
     * Check if task matches completion status filter
     */
    private fun matchesCompletionFilter(task: Task): Boolean = when (completionFilter) {
        CompletionFilter.ALL -> true
        CompletionFilter.ACTIVE_ONLY -> !task.isCompleted
        CompletionFilter.COMPLETED_ONLY -> task.isCompleted
    }

    /**
     * Check if task matches category filter
     */
    private fun matchesCategoryFilter(task: Task): Boolean {
        return categoryFilter == null || task.category == categoryFilter
    }

    /**
     * Check if task matches search query
     */
    private fun matchesSearchQuery(task: Task): Boolean {
        if (searchQuery.isBlank()) return true

        val query = searchQuery.lowercase()
        return task.title.lowercase().contains(query) ||
               task.description?.lowercase()?.contains(query) == true
    }

    // ========== Sorting ==========

    /**
     * Sort task list according to current sort option
     *
     * @param tasks Mutable list to sort in-place
     */
    fun sortTasks(tasks: MutableList<Task>) {
        tasks.sortWith(getComparator())
    }

    /**
     * Get comparator for current sort option
     */
    private fun getComparator(): Comparator<Task> = when (sortOption) {
        SortOption.PRIORITY -> compareByDescending { it.priority }

        SortOption.DUE_DATE -> compareBy {
            // Tasks without due date go to end
            if (it.dueDate == 0L) Long.MAX_VALUE else it.dueDate
        }

        SortOption.CREATED -> compareByDescending { it.createdAt }

        SortOption.TITLE -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.title }

        SortOption.CATEGORY -> compareBy { it.category }
    }

    // ========== Filter Reset ==========

    /**
     * Clear all filters except sort option
     */
    fun clearFilters() {
        searchQuery = ""
        categoryFilter = null
        completionFilter = CompletionFilter.ALL
    }

    /**
     * Check if any filters are active (excluding sort)
     */
    fun hasActiveFilters(): Boolean {
        return searchQuery.isNotBlank() ||
               categoryFilter != null ||
               completionFilter != CompletionFilter.ALL
    }
}

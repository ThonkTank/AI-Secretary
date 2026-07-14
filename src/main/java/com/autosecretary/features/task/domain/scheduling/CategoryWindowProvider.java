package com.autosecretary.features.task.domain.scheduling;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Supplies the per-day category time windows that reserve parts of a day's scheduling window
 * for tasks of a specific category. Unlike {@link SchedulingWindowProvider} (which defines the
 * outer bounds of a day), this carves that outer window into category-reserved sub-windows.
 *
 * <p>Semantics (soft-exclusive, no-waste): within a category window the scheduler first places
 * only tasks of that category; any reserved time left over is then filled with other tasks in a
 * second, non-displacing pass. When no windows are defined for a day the scheduler behaves
 * exactly as before (a single free window, one pass).
 */
public interface CategoryWindowProvider {

    /** One category reservation on a given day: {@code [start, end)} reserved for {@code categoryId}. */
    record CategoryWindow(String categoryId, LocalTime start, LocalTime end) {}

    /** Returns the category windows defined for {@code day}; empty means "no reservations". */
    List<CategoryWindow> windowsForDay(LocalDate day);

    /** No-op provider: never reserves any time, so scheduling matches the pre-category behaviour. */
    CategoryWindowProvider NONE = day -> List.of();
}

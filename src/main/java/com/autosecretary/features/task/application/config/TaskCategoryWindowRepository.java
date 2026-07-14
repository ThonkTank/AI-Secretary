package com.autosecretary.features.task.application.config;

import com.autosecretary.features.task.data.TaskCategoryDao;
import com.autosecretary.features.task.data.TaskCategoryWindowDao;
import com.autosecretary.features.task.domain.model.TaskCategory;
import com.autosecretary.features.task.domain.model.TaskCategoryWindow;
import com.autosecretary.features.task.domain.scheduling.CategoryWindowProvider;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Repository for per-weekday category time windows, implementing {@link CategoryWindowProvider}
 * for the scheduler with a lazily-built per-weekday cache (mirrors
 * {@link TaskScheduleConfigRepository}).
 *
 * <p>Windows whose {@code categoryId} no longer resolves to an existing category are dropped when
 * the cache is built, so orphaned reservations never affect scheduling. The cache is invalidated
 * on {@link #saveWindows}; the editing UI reads fresh rows via {@link #loadAll()}.
 */
public class TaskCategoryWindowRepository implements CategoryWindowProvider {

    private final TaskCategoryWindowDao windowDao;
    private final TaskCategoryDao categoryDao;
    private Map<DayOfWeek, List<CategoryWindow>> cachedByDay;

    public TaskCategoryWindowRepository(TaskCategoryWindowDao windowDao, TaskCategoryDao categoryDao) {
        this.windowDao = windowDao;
        this.categoryDao = categoryDao;
    }

    /** Reads all stored windows (unfiltered), for the editing UI. */
    public List<TaskCategoryWindow> loadAll() {
        return windowDao.readAll();
    }

    /**
     * Replaces the entire set of windows: deletes the removed ids, writes the surviving rows, and
     * invalidates the cache. Ids are supplied explicitly so the caller (the dialog) can track which
     * rows the user removed.
     */
    public void saveWindows(List<TaskCategoryWindow> windows, List<String> deletedIds) {
        for (String deletedId : deletedIds) {
            windowDao.delete(deletedId);
        }
        for (TaskCategoryWindow window : windows) {
            windowDao.write(window);
        }
        cachedByDay = null;
    }

    @Override
    public List<CategoryWindow> windowsForDay(LocalDate day) {
        ensureCached();
        return cachedByDay.getOrDefault(day.getDayOfWeek(), List.of());
    }

    private void ensureCached() {
        if (cachedByDay != null) {
            return;
        }
        Set<String> validCategoryIds = new HashSet<>();
        for (TaskCategory category : categoryDao.readAll()) {
            validCategoryIds.add(category.id);
        }
        Map<DayOfWeek, List<CategoryWindow>> byDay = new EnumMap<>(DayOfWeek.class);
        for (TaskCategoryWindow window : windowDao.readAll()) {
            if (window.dayOfWeek == null || !validCategoryIds.contains(window.categoryId)) {
                continue;
            }
            byDay.computeIfAbsent(window.dayOfWeek, d -> new ArrayList<>())
                    .add(new CategoryWindow(window.categoryId, window.startTime, window.endTime));
        }
        cachedByDay = byDay;
    }
}

package com.autosecretary.features.task.application.config;

import com.autosecretary.features.task.data.TaskPrefSlotFactory;
import com.autosecretary.features.task.data.TaskScheduleConfig;
import com.autosecretary.features.task.data.TaskScheduleConfigDao;
import com.autosecretary.features.task.domain.scheduling.SchedulingWindowProvider;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Repository for per-day task schedule configuration with lazy-loaded caching.
 *
 * <p><strong>Purpose:</strong> Implements {@link SchedulingWindowProvider} to allow users to configure
 * different scheduling windows (start/end times) for each day of the week. During slot generation,
 * the scheduler queries this repository to obtain the allowable time boundaries for a given day.
 *
 * <p><strong>Cache behavior:</strong>
 * <ul>
 *   <li>Initial state: {@code null} (uninitialized)</li>
 *   <li>On first {@code forDay()} call: loads from DAO and caches</li>
 *   <li>On {@code loadAll()} call: reloads from DAO and updates cache</li>
 *   <li>On {@code saveAll()} call: invalidates cache (sets to {@code null})</li>
 * </ul>
 * The cache is lazily initialized and persists across calls until explicitly invalidated.
 *
 * <p><strong>Dependencies:</strong> Backed by {@link TaskScheduleConfigDao} for persistence
 * and {@link TaskPrefSlotFactory} for app-wide default time constants.
 *
 * @see SchedulingWindowProvider
 * @see TaskScheduleConfigDao
 */
public class TaskScheduleConfigRepository implements SchedulingWindowProvider {
    private static final LocalTime DEFAULT_START = TaskPrefSlotFactory.DEFAULT_START_TIME;
    private static final LocalTime DEFAULT_END = TaskPrefSlotFactory.DEFAULT_END_TIME;

    private final TaskScheduleConfigDao dao;
    private Map<DayOfWeek, TaskScheduleConfig> cachedByDay;

    public TaskScheduleConfigRepository(TaskScheduleConfigDao dao) {
        this.dao = dao;
    }

    /**
     * Loads all schedule configurations from the database and refreshes the cache.
     *
     * <p>Returns one {@link TaskScheduleConfig} per day of the week (always 7 entries),
     * ordered by {@link java.time.DayOfWeek} natural order (MONDAY … SUNDAY). Days with no
     * stored config are filled with the application default times from {@link TaskPrefSlotFactory}.
     *
     * <p>Unlike {@link #forDay}, this method always queries the DAO (it is not lazy).
     * Called by the schedule-config UI dialog to populate the editing form, where showing
     * fresh data from the database is more important than skipping the query.
     *
     * @return an ordered list of schedule configs for all 7 days; never null, never empty
     */
    public List<TaskScheduleConfig> loadAll() {
        cachedByDay = buildAsMap(dao.readAll());
        return Arrays.stream(DayOfWeek.values())
                .map(cachedByDay::get)
                .collect(Collectors.toList());
    }

    /**
     * Persists all schedule configurations and invalidates the cache.
     *
     * <p>Ensures every day of the week has a valid entry (filling missing days with defaults
     * and normalizing all configs). The cache is then set to {@code null}; it will be reloaded
     * lazily on the next {@code forDay()} call.
     *
     * @param configs The list of schedule configurations to save (may be null or contain nulls)
     */
    public void saveAll(List<TaskScheduleConfig> configs) {
        Map<DayOfWeek, TaskScheduleConfig> configMap = buildAsMap(configs != null ? configs : new ArrayList<>());
        List<TaskScheduleConfig> toSave = Arrays.stream(DayOfWeek.values())
                .map(configMap::get)
                .collect(Collectors.toList());
        dao.writeAll(toSave);
        cachedByDay = null;  // Invalidate cache; will be reloaded on next ensureCached()
    }

    /**
     * Builds a map of DayOfWeek → TaskScheduleConfig from a source list.
     *
     * <p>Filters null configs, normalizes each valid config to ensure it respects invariants
     * (times within bounds, end after start), and fills in any missing days with sensible defaults.
     * Guarantees every {@code DayOfWeek} has an entry.
     *
     * @param source The list of configs (may contain nulls)
     * @return A map with an entry for each DayOfWeek, normalized and with defaults applied
     */
    private Map<DayOfWeek, TaskScheduleConfig> buildAsMap(Iterable<TaskScheduleConfig> source) {
        Map<DayOfWeek, TaskScheduleConfig> byDay = new EnumMap<>(DayOfWeek.class);
        for (TaskScheduleConfig config : source) {
            if (config != null && config.dayOfWeek != null) {
                byDay.put(config.dayOfWeek, normalize(config.dayOfWeek, config.startTime, config.endTime));
            }
        }
        for (DayOfWeek day : DayOfWeek.values()) {
            byDay.computeIfAbsent(day, d -> new TaskScheduleConfig(d, DEFAULT_START, DEFAULT_END));
        }
        return byDay;
    }

    @Override
    public SchedulingWindow forDay(LocalDate day) {
        ensureCached();
        TaskScheduleConfig config = cachedByDay.get(day.getDayOfWeek());
        return new SchedulingWindowProvider.SchedulingWindow(
                LocalDateTime.of(day, config.startTime),
                LocalDateTime.of(day, config.endTime)
        );
    }

    /**
     * Ensures the cache is initialized, loading from DAO only if needed.
     *
     * <p>This defers database access until the first scheduling window lookup, reducing startup
     * time if the config is never queried during a session. Once loaded, the cache persists until
     * explicitly invalidated by {@code saveAll()}.
     */
    private void ensureCached() {
        if (cachedByDay == null) {
            cachedByDay = buildAsMap(dao.readAll());
        }
    }

    /**
     * Normalizes a schedule config to ensure validity and invariants.
     *
     * <p>Applies two transformations:
     * <ul>
     *   <li>Fills missing times (null) with app-wide defaults from {@link TaskPrefSlotFactory}</li>
     *   <li>If end time is not strictly after start time, defaults end to start + 1 hour,
     *       ensuring every day has a valid (non-inverted) scheduling window</li>
     * </ul>
     *
     * @param dayOfWeek The day being configured
     * @param start The desired start time (or null for default)
     * @param end The desired end time (or null for default)
     * @return A normalized, valid TaskScheduleConfig
     */
    private TaskScheduleConfig normalize(DayOfWeek dayOfWeek, LocalTime start, LocalTime end) {
        LocalTime safeStart = orDefault(start, DEFAULT_START);
        LocalTime safeEnd = orDefault(end, DEFAULT_END);
        if (!safeEnd.isAfter(safeStart)) {
            safeEnd = safeStart.plusHours(1);
        }
        return new TaskScheduleConfig(dayOfWeek, safeStart, safeEnd);
    }

    private LocalTime orDefault(LocalTime value, LocalTime defaultValue) {
        return value != null ? value : defaultValue;
    }
}

package com.autosecretary.features.task.application.config;

import com.autosecretary.features.task.data.TaskPrefSlotFactory;
import com.autosecretary.features.task.data.TaskScheduleConfig;
import com.autosecretary.features.task.data.TaskScheduleConfigDAO;
import com.autosecretary.features.task.domain.SchedulingWindowProvider;

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
 * Repository for task schedule configuration with lazy-loaded caching.
 *
 * Cache behavior:
 * - Initial state: null (uninitialized)
 * - On first {@code forDay()} call: loads from DAO and caches
 * - On {@code loadAll()} call: reloads from DAO and updates cache
 * - On {@code saveAll()} call: invalidates cache (sets to null)
 *
 * The cache is lazily initialized and persists across calls until explicitly invalidated.
 */
public class TaskScheduleConfigRepository implements SchedulingWindowProvider {
    private static final LocalTime DEFAULT_START = TaskPrefSlotFactory.DEFAULT_START_TIME;
    private static final LocalTime DEFAULT_END = TaskPrefSlotFactory.DEFAULT_END_TIME;

    private final TaskScheduleConfigDAO dao;
    private Map<DayOfWeek, TaskScheduleConfig> cachedByDay;

    public TaskScheduleConfigRepository(TaskScheduleConfigDAO dao) {
        this.dao = dao;
    }

    public List<TaskScheduleConfig> loadAll() {
        cachedByDay = buildAsMap(dao.readAll());
        return Arrays.stream(DayOfWeek.values())
                .map(cachedByDay::get)
                .collect(Collectors.toList());
    }

    public void saveAll(List<TaskScheduleConfig> configs) {
        Map<DayOfWeek, TaskScheduleConfig> configMap = buildAsMap(configs != null ? configs : new ArrayList<>());
        List<TaskScheduleConfig> toSave = Arrays.stream(DayOfWeek.values())
                .map(configMap::get)
                .collect(Collectors.toList());
        dao.writeAll(toSave);
        cachedByDay = null;
    }

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
     * Ensures the cache is initialized. Lazily loads from DAO if cache is null.
     */
    private void ensureCached() {
        if (cachedByDay == null) {
            cachedByDay = buildAsMap(dao.readAll());
        }
    }

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

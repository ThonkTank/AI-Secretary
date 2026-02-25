package com.autosecretary.features.task.application;

import com.autosecretary.features.task.data.TaskScheduleConfig;
import com.autosecretary.features.task.data.TaskScheduleConfigDao;
import com.autosecretary.features.task.domain.internal.scheduling.SchedulingWindowProvider;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class TaskScheduleConfigRepository implements SchedulingWindowProvider {
    private static final LocalTime DEFAULT_START = LocalTime.of(6, 0);
    private static final LocalTime DEFAULT_END = LocalTime.of(21, 0);

    private final TaskScheduleConfigDao dao;

    public TaskScheduleConfigRepository(TaskScheduleConfigDao dao) {
        this.dao = dao;
    }

    public List<TaskScheduleConfig> loadAll() {
        Map<DayOfWeek, TaskScheduleConfig> byDay = new EnumMap<>(DayOfWeek.class);
        for (TaskScheduleConfig config : dao.readAll()) {
            if (config != null && config.dayOfWeek != null) {
                byDay.put(config.dayOfWeek, normalize(config.dayOfWeek, config.startTime, config.endTime));
            }
        }

        List<TaskScheduleConfig> merged = new ArrayList<>();
        for (DayOfWeek day : DayOfWeek.values()) {
            TaskScheduleConfig config = byDay.get(day);
            if (config == null) {
                config = new TaskScheduleConfig(day, DEFAULT_START, DEFAULT_END);
            }
            merged.add(config);
        }
        return merged;
    }

    public void saveAll(List<TaskScheduleConfig> configs) {
        Map<DayOfWeek, TaskScheduleConfig> byDay = new EnumMap<>(DayOfWeek.class);
        if (configs != null) {
            for (TaskScheduleConfig config : configs) {
                if (config != null && config.dayOfWeek != null) {
                    byDay.put(config.dayOfWeek, normalize(config.dayOfWeek, config.startTime, config.endTime));
                }
            }
        }

        List<TaskScheduleConfig> completeWeek = new ArrayList<>();
        for (DayOfWeek day : DayOfWeek.values()) {
            TaskScheduleConfig config = byDay.get(day);
            if (config == null) {
                config = new TaskScheduleConfig(day, DEFAULT_START, DEFAULT_END);
            }
            completeWeek.add(config);
        }
        dao.writeAll(completeWeek);
    }

    @Override
    public SchedulingWindow forDay(LocalDate day) {
        DayOfWeek dayOfWeek = day.getDayOfWeek();
        for (TaskScheduleConfig config : loadAll()) {
            if (config.dayOfWeek == dayOfWeek) {
                return new SchedulingWindowProvider.SchedulingWindow(
                        LocalDateTime.of(day, config.startTime),
                        LocalDateTime.of(day, config.endTime)
                );
            }
        }
        return new SchedulingWindowProvider.SchedulingWindow(
                LocalDateTime.of(day, DEFAULT_START),
                LocalDateTime.of(day, DEFAULT_END)
        );
    }

    private TaskScheduleConfig normalize(DayOfWeek dayOfWeek, LocalTime start, LocalTime end) {
        LocalTime safeStart = start != null ? start : DEFAULT_START;
        LocalTime safeEnd = end != null ? end : DEFAULT_END;
        if (!safeEnd.isAfter(safeStart)) {
            safeEnd = safeStart.plusHours(1);
        }
        return new TaskScheduleConfig(dayOfWeek, safeStart, safeEnd);
    }
}

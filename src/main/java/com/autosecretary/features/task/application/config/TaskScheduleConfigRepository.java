package com.autosecretary.features.task.application.config;

import com.autosecretary.features.task.data.TaskScheduleConfig;
import com.autosecretary.features.task.data.TaskScheduleConfigDAO;
import com.autosecretary.features.task.domain.SchedulingWindowProvider;

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

    private final TaskScheduleConfigDAO dao;

    public TaskScheduleConfigRepository(TaskScheduleConfigDAO dao) {
        this.dao = dao;
    }

    public List<TaskScheduleConfig> loadAll() {
        return buildCompleteWeek(dao.readAll());
    }

    public void saveAll(List<TaskScheduleConfig> configs) {
        dao.writeAll(buildCompleteWeek(configs != null ? configs : new ArrayList<>()));
    }

    private List<TaskScheduleConfig> buildCompleteWeek(Iterable<TaskScheduleConfig> source) {
        Map<DayOfWeek, TaskScheduleConfig> byDay = new EnumMap<>(DayOfWeek.class);
        for (TaskScheduleConfig config : source) {
            if (config != null && config.dayOfWeek != null) {
                byDay.put(config.dayOfWeek, normalize(config.dayOfWeek, config.startTime, config.endTime));
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
        return completeWeek;
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

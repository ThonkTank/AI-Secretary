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

    private List<TaskScheduleConfig> buildCompleteWeek(Iterable<TaskScheduleConfig> source) {
        Map<DayOfWeek, TaskScheduleConfig> byDay = buildAsMap(source);
        List<TaskScheduleConfig> completeWeek = new ArrayList<>();
        for (DayOfWeek day : DayOfWeek.values()) {
            completeWeek.add(byDay.get(day));
        }
        return completeWeek;
    }

    @Override
    public SchedulingWindow forDay(LocalDate day) {
        TaskScheduleConfig config = buildAsMap(dao.readAll()).get(day.getDayOfWeek());
        return new SchedulingWindowProvider.SchedulingWindow(
                LocalDateTime.of(day, config.startTime),
                LocalDateTime.of(day, config.endTime)
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

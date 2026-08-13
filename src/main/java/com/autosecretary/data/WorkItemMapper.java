package com.autosecretary.data;

import com.autosecretary.data.entity.StepDayEntity;
import com.autosecretary.data.entity.StepEntity;
import com.autosecretary.data.entity.WorkItemEntity;
import com.autosecretary.domain.CompletionStats;
import com.autosecretary.domain.Routine;
import com.autosecretary.domain.Step;
import com.autosecretary.domain.Task;
import com.autosecretary.domain.TimePreference;
import com.autosecretary.domain.WorkItem;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class WorkItemMapper {
    WorkItem toDomain(
            WorkItemEntity source,
            List<StepEntity> stepEntities,
            List<StepDayEntity> dayEntities) {
        List<StepEntity> ownSteps = new ArrayList<>();
        Set<String> ownStepIds = new HashSet<>();
        for (StepEntity step : stepEntities) {
            if (source.id.equals(step.workItemId)) {
                ownSteps.add(step);
                ownStepIds.add(step.id);
            }
        }
        Map<String, EnumSet<DayOfWeek>> days = new HashMap<>();
        Set<String> corruptSteps = new HashSet<>();
        for (StepDayEntity day : dayEntities) {
            if (!ownStepIds.contains(day.stepId)) continue;
            try {
                days.computeIfAbsent(day.stepId, ignored -> EnumSet.noneOf(DayOfWeek.class))
                        .add(DayOfWeek.valueOf(day.dayOfWeek));
            } catch (RuntimeException corruptDay) {
                corruptSteps.add(day.stepId);
            }
        }
        List<Step> steps = new ArrayList<>();
        for (StepEntity step : ownSteps) {
            if (corruptSteps.contains(step.id)) continue;
            try {
                steps.add(new Step(step.id, step.title,
                        days.getOrDefault(step.id, EnumSet.noneOf(DayOfWeek.class)), step.position));
            } catch (RuntimeException corruptStep) {
                // One malformed child must not hide the otherwise valid parent graph.
            }
        }
        CompletionStats stats = new CompletionStats(
                source.currentStreak, source.bestStreak, source.totalCompletions);
        LocalDateTime deadline = source.deadlineAt == null ? null : LocalDateTime.parse(source.deadlineAt);
        TimePreference preference = source.timePreference == null
                ? null : TimePreference.valueOf(source.timePreference);
        LocalDateTime created = LocalDateTime.parse(source.createdAt);
        if ("ROUTINE".equals(source.kind)) {
            return new Routine(source.id, source.title, source.durationMinutes, deadline,
                    preference, source.flexible, steps, created, source.cadenceDays,
                    LocalDate.parse(source.nextDueDate), stats, source.revision);
        }
        return new Task(source.id, source.title, source.durationMinutes, deadline,
                preference, source.flexible, steps, created, source.completed, stats, source.revision);
    }

    WorkItemEntity toEntity(WorkItem source, long revision) {
        WorkItemEntity result = new WorkItemEntity();
        result.id = source.id();
        result.kind = source instanceof Routine ? "ROUTINE" : "TASK";
        result.title = source.title();
        result.durationMinutes = source.durationMinutes();
        result.deadlineAt = source.deadlineAt() == null ? null : source.deadlineAt().toString();
        result.timePreference = source.timePreference() == null ? null : source.timePreference().name();
        result.flexible = source.flexible();
        result.createdAt = source.createdAt().toString();
        result.completed = source instanceof Task task && task.completed();
        result.cadenceDays = source instanceof Routine routine ? routine.cadenceDays() : 0;
        result.nextDueDate = source instanceof Routine routine ? routine.nextDueDate().toString() : null;
        result.currentStreak = source.stats().currentStreak();
        result.bestStreak = source.stats().bestStreak();
        result.totalCompletions = source.stats().totalCompletions();
        result.revision = revision;
        return result;
    }

    List<StepEntity> stepEntities(WorkItem source) {
        List<StepEntity> result = new ArrayList<>();
        for (Step step : source.steps()) {
            StepEntity entity = new StepEntity();
            entity.id = step.id();
            entity.workItemId = source.id();
            entity.title = step.title();
            entity.position = step.position();
            result.add(entity);
        }
        return result;
    }

    List<StepDayEntity> stepDayEntities(WorkItem source) {
        List<StepDayEntity> result = new ArrayList<>();
        for (Step step : source.steps()) {
            for (DayOfWeek day : step.days()) {
                StepDayEntity entity = new StepDayEntity();
                entity.stepId = step.id();
                entity.dayOfWeek = day.name();
                result.add(entity);
            }
        }
        return result;
    }
}

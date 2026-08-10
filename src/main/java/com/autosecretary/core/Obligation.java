package com.autosecretary.core;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/** The only persisted work definition: either a one-off task or one recurring routine block. */
public final class Obligation {
    public enum Kind { TASK, ROUTINE }

    public String id = UUID.randomUUID().toString();
    public Kind kind = Kind.TASK;
    public String title = "";
    public int durationMinutes = 30;
    public LocalDateTime deadlineAt;
    public int cadenceDays;
    public LocalDate nextDueDate;
    public TimePreference timePreference;
    public List<RoutineStep> steps = new ArrayList<>();
    public LocalDateTime createdAt = LocalDateTime.now();
    public boolean completed;
    public int currentStreak;
    public int bestStreak;
    public int totalCompletions;
    public LocalDate manualOrderOn;
    public long manualOrderRank;

    public boolean isRoutine() {
        return kind == Kind.ROUTINE;
    }

    public boolean isOpenOn(LocalDate day) {
        if (!isRoutine()) {
            return !completed;
        }
        LocalDate due = nextDueDate != null ? nextDueDate : day;
        return !due.isAfter(day);
    }

    public LocalDate occurrenceDate(LocalDate day) {
        if (!isRoutine()) return null;
        return nextDueDate == null ? day : nextDueDate;
    }

    public List<RoutineStep> activeStepsFor(LocalDate day) {
        if (!isRoutine() || steps == null) {
            return Collections.emptyList();
        }
        LocalDate occurrence = occurrenceDate(day);
        return steps.stream()
                .filter(step -> step != null && step.appliesOn(occurrence.getDayOfWeek()))
                .filter(step -> step.title != null && !step.title.trim().isEmpty())
                .collect(Collectors.toList());
    }

    public List<PlanStep> planStepsFor(LocalDate day) {
        LocalDate occurrence = occurrenceDate(day);
        return activeStepsFor(day).stream()
                .map(step -> new PlanStep(step.id, step.title, step.isCompletedFor(occurrence)))
                .collect(Collectors.toList());
    }

    public boolean allActiveStepsCompleted(LocalDate day) {
        List<RoutineStep> active = activeStepsFor(day);
        LocalDate occurrence = occurrenceDate(day);
        return !active.isEmpty() && active.stream().allMatch(step -> step.isCompletedFor(occurrence));
    }
}

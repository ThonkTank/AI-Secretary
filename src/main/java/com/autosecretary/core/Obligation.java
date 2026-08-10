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
    public List<RoutineStep> steps = new ArrayList<>();
    public LocalDateTime createdAt = LocalDateTime.now();
    public boolean completed;
    public int currentStreak;
    public int bestStreak;
    public int totalCompletions;
    public LocalDate postponedOn;
    public long postponedRank;

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

    public List<String> stepTitlesFor(LocalDate day) {
        if (!isRoutine() || steps == null) {
            return Collections.emptyList();
        }
        return steps.stream()
                .filter(step -> step != null && step.appliesOn(day.getDayOfWeek()))
                .map(step -> step.title)
                .filter(title -> title != null && !title.trim().isEmpty())
                .collect(Collectors.toList());
    }
}

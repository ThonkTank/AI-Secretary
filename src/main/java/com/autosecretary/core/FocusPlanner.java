package com.autosecretary.core;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/** Deterministic, non-persisted focus planner: deadlines first, behavior second, calendar read-only. */
public final class FocusPlanner {
    private static final int TRANSITION_MINUTES = 15;

    public List<PlanItem> plan(
            List<Obligation> obligations,
            List<Completion> completions,
            List<CalendarBlock> calendar,
            LocalDateTime now,
            int limit) {
        LocalDate day = now.toLocalDate();
        BehaviorProfile behavior = BehaviorProfile.from(completions, day);
        List<Obligation> candidates = obligations.stream()
                .filter(item -> item.isOpenOn(day))
                .sorted(order(day, behavior))
                .collect(Collectors.toList());

        List<CalendarBlock> busy = calendar.stream()
                .filter(block -> block.end().isAfter(now))
                .sorted(Comparator.comparing(CalendarBlock::start))
                .collect(Collectors.toList());
        List<PlanItem> result = new ArrayList<>();
        LocalDateTime cursor = now.withSecond(0).withNano(0);

        for (Obligation obligation : candidates) {
            if (result.size() >= Math.max(0, limit)) {
                break;
            }
            LocalDateTime start = nextFree(cursor, obligation.durationMinutes, busy);
            LocalDateTime end = start == null ? null : start.plusMinutes(obligation.durationMinutes);
            result.add(new PlanItem(obligation, start, end, obligation.stepTitlesFor(day)));
            if (end != null) {
                cursor = end.plusMinutes(TRANSITION_MINUTES);
            }
        }
        return result;
    }

    private Comparator<Obligation> order(LocalDate day, BehaviorProfile behavior) {
        return Comparator
                .comparing((Obligation item) -> isPostponedToday(item, day))
                .thenComparingLong(item -> isPostponedToday(item, day) ? item.postponedRank : 0L)
                .thenComparing(Comparator.comparingInt((Obligation item) -> urgency(item, day)).reversed())
                .thenComparing(Comparator.comparingInt(
                        (Obligation item) -> behavior.transitionStrength(item.id)).reversed())
                .thenComparingInt(item -> behavior.learnedMinute(item.id))
                .thenComparing(item -> item.createdAt)
                .thenComparing(item -> item.title, String.CASE_INSENSITIVE_ORDER);
    }

    private int urgency(Obligation item, LocalDate today) {
        if (item.deadlineAt != null) {
            long days = ChronoUnit.DAYS.between(today, item.deadlineAt.toLocalDate());
            if (days < 0) return 100_000 + (int) Math.min(9_999, -days);
            if (days == 0) return 90_000;
            if (days <= 3) return 80_000 - (int) days;
            return 40_000 - (int) Math.min(30_000, days);
        }
        if (item.isRoutine()) {
            long overdue = item.nextDueDate == null
                    ? 0
                    : Math.max(0, ChronoUnit.DAYS.between(item.nextDueDate, today));
            return overdue > 0 ? 70_000 + (int) Math.min(9_999, overdue) : 60_000;
        }
        return 10_000;
    }

    private boolean isPostponedToday(Obligation item, LocalDate day) {
        return day.equals(item.postponedOn) && item.postponedRank > 0;
    }

    private LocalDateTime nextFree(
            LocalDateTime earliest,
            int durationMinutes,
            List<CalendarBlock> busy) {
        LocalDateTime cursor = earliest;
        for (CalendarBlock block : busy) {
            if (!block.start().toLocalDate().equals(earliest.toLocalDate())) {
                continue;
            }
            if (!block.end().isAfter(cursor)) {
                continue;
            }
            if (!block.start().isAfter(cursor)
                    || Duration.between(cursor, block.start()).toMinutes() < durationMinutes) {
                cursor = block.end().plusMinutes(TRANSITION_MINUTES);
            } else {
                break;
            }
        }
        LocalDateTime dayEnd = LocalDateTime.of(earliest.toLocalDate(), LocalTime.of(22, 0));
        return cursor.plusMinutes(durationMinutes).isAfter(dayEnd) ? null : cursor;
    }
}

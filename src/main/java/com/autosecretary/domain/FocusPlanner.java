package com.autosecretary.domain;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Separates urgency ordering from global placement into independently available gaps. */
public final class FocusPlanner {
    private record Gap(LocalDateTime start, LocalDateTime end) { }
    private record Placement(int gapIndex, LocalDateTime start, int score) { }

    public PlanningResult plan(
            List<WorkItem> source,
            List<CompletionEvidence> evidence,
            List<BusyInterval> calendar,
            List<PlanOrderDirective> directives,
            PlanningSettings settings,
            LocalDateTime now) {
        LocalDate today = now.toLocalDate();
        LocalDate horizonEnd = today.plusDays(settings.horizonDays());
        Behavior behavior = Behavior.from(evidence, now);
        List<WorkItem> candidates = source.stream()
                .filter(item -> isCandidate(item, today, horizonEnd))
                .sorted(order(today, behavior))
                .collect(Collectors.toCollection(ArrayList::new));
        applyDirectives(candidates, directives, today);

        List<Gap> gaps = buildGaps(calendar, settings, now);
        List<PlanAssignment> assignments = new ArrayList<>();
        List<PlanConflict> conflicts = new ArrayList<>();
        for (WorkItem item : candidates) {
            Placement placement = bestPlacement(item, gaps, settings, behavior, today);
            String occurrenceKey = item.occurrenceDate(today) == null
                    ? "TASK" : item.occurrenceDate(today).toString();
            if (placement == null) {
                PlanConflict.Reason reason = conflictReason(item, gaps, today)
                        ? PlanConflict.Reason.AFTER_DEADLINE
                        : PlanConflict.Reason.NO_CAPACITY;
                conflicts.add(new PlanConflict(item, occurrenceKey, reason,
                        "Kein freies " + item.durationMinutes() + "-Minuten-Fenster im Planungshorizont"));
                continue;
            }
            LocalDateTime end = placement.start().plusMinutes(item.durationMinutes());
            assignments.add(new PlanAssignment(item, occurrenceKey, placement.start(), end));
            consumeGap(gaps, placement.gapIndex(), placement.start(), end,
                    settings.taskTransitionMinutes());
        }
        assignments.sort(Comparator.comparing(PlanAssignment::start));
        return new PlanningResult(assignments, conflicts);
    }

    private static boolean isCandidate(WorkItem item, LocalDate today, LocalDate horizonEnd) {
        if (item instanceof Task task) return !task.completed();
        Routine routine = (Routine) item;
        return routine.nextDueDate().isBefore(horizonEnd);
    }

    private static Comparator<WorkItem> order(LocalDate today, Behavior behavior) {
        return Comparator
                .comparingInt((WorkItem item) -> urgency(item, today)).reversed()
                .thenComparing(Comparator.comparingInt(
                        (WorkItem item) -> item.flexible()
                                ? behavior.transitionStrength(item.id()) : 0).reversed())
                .thenComparing(item -> item.createdAt())
                .thenComparing(WorkItem::title, String.CASE_INSENSITIVE_ORDER);
    }

    private static int urgency(WorkItem item, LocalDate today) {
        if (item.deadlineAt() != null) {
            long days = ChronoUnit.DAYS.between(today, item.deadlineAt().toLocalDate());
            if (days < 0) return 100_000 + (int) Math.min(9_999, -days);
            if (days == 0) return 90_000;
            if (days <= 3) return 80_000 - (int) days;
            return 40_000 - (int) Math.min(30_000, days);
        }
        if (item instanceof Routine routine) {
            long overdue = Math.max(0, ChronoUnit.DAYS.between(routine.nextDueDate(), today));
            return overdue > 0 ? 70_000 + (int) Math.min(9_999, overdue) : 60_000;
        }
        return 10_000;
    }

    private static List<Gap> buildGaps(
            List<BusyInterval> calendar,
            PlanningSettings settings,
            LocalDateTime now) {
        List<Gap> result = new ArrayList<>();
        for (int offset = 0; offset < settings.horizonDays(); offset++) {
            LocalDate day = now.toLocalDate().plusDays(offset);
            LocalDateTime dayStart = day.atTime(settings.day().start());
            if (offset == 0 && now.isAfter(dayStart)) {
                dayStart = now.withSecond(0).withNano(0);
                if (dayStart.isBefore(now)) dayStart = dayStart.plusMinutes(1);
            }
            LocalDateTime dayEnd = day.atTime(settings.day().end());
            if (!dayEnd.isAfter(dayStart)) continue;
            List<BusyInterval> busy = calendar.stream()
                    .filter(block -> block.end().toLocalDate().compareTo(day) >= 0
                            && block.start().toLocalDate().compareTo(day) <= 0)
                    .map(block -> new BusyInterval(
                            block.start().minusMinutes(settings.calendarBufferBeforeMinutes()),
                            block.end().plusMinutes(settings.calendarBufferAfterMinutes()), block.title()))
                    .sorted(Comparator.comparing(BusyInterval::start))
                    .collect(Collectors.toList());
            LocalDateTime cursor = dayStart;
            for (BusyInterval block : busy) {
                LocalDateTime start = block.start().isBefore(dayStart) ? dayStart : block.start();
                LocalDateTime end = block.end().isAfter(dayEnd) ? dayEnd : block.end();
                if (!end.isAfter(cursor)) continue;
                if (start.isAfter(cursor)) result.add(new Gap(cursor, start));
                if (end.isAfter(cursor)) cursor = end;
            }
            if (dayEnd.isAfter(cursor)) result.add(new Gap(cursor, dayEnd));
        }
        return result;
    }

    private static Placement bestPlacement(
            WorkItem item,
            List<Gap> gaps,
            PlanningSettings settings,
            Behavior behavior,
            LocalDate today) {
        Placement best = null;
        LocalDate earliestDay = item instanceof Routine routine && routine.nextDueDate().isAfter(today)
                ? routine.nextDueDate() : today;
        for (int index = 0; index < gaps.size(); index++) {
            Gap gap = gaps.get(index);
            if (gap.start().toLocalDate().isBefore(earliestDay)) continue;
            if (Duration.between(gap.start(), gap.end()).toMinutes() < item.durationMinutes()) continue;
            LocalDateTime latestStart = gap.end().minusMinutes(item.durationMinutes());
            if (item.deadlineAt() != null) {
                LocalDateTime deadlineStart = item.deadlineAt().minusMinutes(item.durationMinutes());
                if (deadlineStart.isBefore(latestStart)) latestStart = deadlineStart;
            }
            if (latestStart.isBefore(gap.start())) continue;
            LocalDateTime start = preferredStart(
                    item, gap.start(), latestStart, settings, behavior);
            int score = placementScore(item, start, settings, behavior, today);
            if (best == null || score > best.score()
                    || score == best.score() && start.isBefore(best.start())) {
                best = new Placement(index, start, score);
            }
        }
        return best;
    }

    private static LocalDateTime preferredStart(
            WorkItem item,
            LocalDateTime earliest,
            LocalDateTime latest,
            PlanningSettings settings,
            Behavior behavior) {
        LocalTime target = effectivePreferredTime(item, settings, behavior);
        if (target == null) return earliest;
        LocalDateTime preferred = earliest.toLocalDate().atTime(target);
        if (preferred.isBefore(earliest)) return earliest;
        if (preferred.isAfter(latest)) return latest;
        return preferred;
    }

    /** True only when duration fits in the horizon but every such placement misses the deadline. */
    private static boolean conflictReason(
            WorkItem item,
            List<Gap> gaps,
            LocalDate today) {
        if (item.deadlineAt() == null) return false;
        LocalDate earliestDay = item instanceof Routine routine && routine.nextDueDate().isAfter(today)
                ? routine.nextDueDate() : today;
        return gaps.stream().anyMatch(gap -> !gap.start().toLocalDate().isBefore(earliestDay)
                && Duration.between(gap.start(), gap.end()).toMinutes() >= item.durationMinutes());
    }

    private static int placementScore(
            WorkItem item,
            LocalDateTime start,
            PlanningSettings settings,
            Behavior behavior,
            LocalDate today) {
        int score = -(int) ChronoUnit.DAYS.between(today, start.toLocalDate()) * 10_000;
        if (item.timePreference() != null
                && settings.preferenceWindow(item.timePreference()).contains(start.toLocalTime())) {
            score += 5_000;
        }
        LocalTime target = effectivePreferredTime(item, settings, behavior);
        if (target != null) {
            score -= Math.abs(start.toLocalTime().toSecondOfDay() - target.toSecondOfDay()) / 60;
        }
        return score;
    }

    private static LocalTime effectivePreferredTime(
            WorkItem item,
            PlanningSettings settings,
            Behavior behavior) {
        TimeWindow window = item.timePreference() == null
                ? null : settings.preferenceWindow(item.timePreference());
        Integer anchor = window == null ? null
                : (window.start().toSecondOfDay() + window.end().toSecondOfDay()) / 120;
        Integer learned = item.flexible() ? behavior.learnedMinute(item.id()) : null;
        if (learned == null) return anchor == null ? null : LocalTime.of(anchor / 60, anchor % 60);
        if (anchor == null) return LocalTime.of(learned / 60, learned % 60);
        int bounded = Math.max(anchor - 120, Math.min(anchor + 120, learned));
        int minute = (anchor * 2 + bounded) / 3;
        return LocalTime.of(minute / 60, minute % 60);
    }

    private static void consumeGap(
            List<Gap> gaps,
            int index,
            LocalDateTime start,
            LocalDateTime end,
            int transitionMinutes) {
        Gap original = gaps.remove(index);
        LocalDateTime blockedStart = start.minusMinutes(transitionMinutes);
        LocalDateTime blockedEnd = end.plusMinutes(transitionMinutes);
        if (blockedStart.isAfter(original.start())) gaps.add(new Gap(original.start(), blockedStart));
        if (original.end().isAfter(blockedEnd)) gaps.add(new Gap(blockedEnd, original.end()));
        gaps.sort(Comparator.comparing(Gap::start));
    }

    private static void applyDirectives(
            List<WorkItem> items,
            List<PlanOrderDirective> directives,
            LocalDate today) {
        directives.stream()
                .filter(value -> today.equals(value.day()))
                .sorted(Comparator.comparing(PlanOrderDirective::updatedAt))
                .forEach(value -> {
                    WorkItem item = items.stream()
                            .filter(candidate -> candidate.id().equals(value.workItemId()))
                            .findFirst().orElse(null);
                    if (item == null) return;
                    if ((value.relation() == PlanOrderDirective.Relation.BEFORE
                            || value.relation() == PlanOrderDirective.Relation.AFTER)
                            && (value.anchorWorkItemId() == null
                            || value.anchorWorkItemId().equals(value.workItemId())
                            || indexOf(items, value.anchorWorkItemId()) < 0
                            || urgencyBand(item, today) != urgencyBand(
                            items.get(indexOf(items, value.anchorWorkItemId())), today))) {
                        return;
                    }
                    items.remove(item);
                    int target = switch (value.relation()) {
                        case FIRST -> firstIndexInBand(items, urgencyBand(item, today), today);
                        case LAST -> lastIndexInBand(items, urgencyBand(item, today), today);
                        case BEFORE, AFTER -> {
                            int anchor = indexOf(items, value.anchorWorkItemId());
                            yield value.relation() == PlanOrderDirective.Relation.BEFORE
                                    ? anchor : anchor + 1;
                        }
                    };
                    items.add(Math.max(0, Math.min(target, items.size())), item);
                });
    }

    private static int indexOf(List<WorkItem> items, String id) {
        if (id == null) return -1;
        for (int index = 0; index < items.size(); index++) {
            if (id.equals(items.get(index).id())) return index;
        }
        return -1;
    }

    private static int firstIndexInBand(List<WorkItem> items, int band, LocalDate today) {
        for (int index = 0; index < items.size(); index++) {
            if (urgencyBand(items.get(index), today) <= band) return index;
        }
        return items.size();
    }

    private static int lastIndexInBand(List<WorkItem> items, int band, LocalDate today) {
        int target = firstIndexInBand(items, band, today);
        while (target < items.size() && urgencyBand(items.get(target), today) == band) target++;
        return target;
    }

    private static int urgencyBand(WorkItem item, LocalDate today) {
        if (item.deadlineAt() != null) {
            long days = ChronoUnit.DAYS.between(today, item.deadlineAt().toLocalDate());
            if (days < 0) return 6;
            if (days == 0) return 5;
            if (days <= 3) return 4;
            return 1;
        }
        if (item instanceof Routine routine) {
            return routine.nextDueDate().isBefore(today) ? 3 : 2;
        }
        return 0;
    }

    private static final class Behavior {
        private final Map<String, Integer> learnedMinutes = new HashMap<>();
        private final Map<String, Integer> transitions = new HashMap<>();
        private final Map<String, Integer> outgoing = new HashMap<>();
        private String lastCompletedToday;

        static Behavior from(List<CompletionEvidence> source, LocalDateTime now) {
            Behavior result = new Behavior();
            LocalDateTime cutoff = now.minusDays(90);
            List<CompletionEvidence> evidence = source.stream()
                    .filter(value -> !value.completedAt().isBefore(cutoff))
                    .sorted(Comparator.comparing(CompletionEvidence::completedAt))
                    .collect(Collectors.toList());
            Map<String, Integer> retainedPerItem = new HashMap<>();
            java.util.Set<Integer> retained = new java.util.HashSet<>();
            for (int index = evidence.size() - 1; index >= 0; index--) {
                CompletionEvidence value = evidence.get(index);
                if (retainedPerItem.merge(value.workItemId(), 1, Integer::sum) <= 20) {
                    retained.add(index);
                }
            }
            Map<String, List<Integer>> minutes = new HashMap<>();
            for (int index = 0; index < evidence.size(); index++) {
                CompletionEvidence value = evidence.get(index);
                if (!retained.contains(index)) continue;
                List<Integer> values = minutes.computeIfAbsent(value.workItemId(), ignored -> new ArrayList<>());
                values.add(value.completedAt().getHour() * 60 + value.completedAt().getMinute());
                CompletionEvidence previous = index == 0 ? null : evidence.get(index - 1);
                if (previous != null && retained.contains(index - 1)
                        && previous.completedAt().toLocalDate()
                        .equals(value.completedAt().toLocalDate())) {
                    String key = previous.workItemId() + "→" + value.workItemId();
                    result.transitions.merge(key, 1, Integer::sum);
                    result.outgoing.merge(previous.workItemId(), 1, Integer::sum);
                }
                if (value.completedAt().toLocalDate().equals(now.toLocalDate())) {
                    result.lastCompletedToday = value.workItemId();
                }
            }
            minutes.forEach((id, values) -> {
                if (values.size() < 3) return;
                values.sort(Integer::compareTo);
                result.learnedMinutes.put(id, values.get(values.size() / 2));
            });
            return result;
        }

        Integer learnedMinute(String id) { return learnedMinutes.get(id); }

        int transitionStrength(String id) {
            if (lastCompletedToday == null) return 0;
            int count = transitions.getOrDefault(lastCompletedToday + "→" + id, 0);
            int total = outgoing.getOrDefault(lastCompletedToday, 0);
            return count >= 3 && total > 0 && count * 100 / total >= 60 ? count : 0;
        }
    }
}

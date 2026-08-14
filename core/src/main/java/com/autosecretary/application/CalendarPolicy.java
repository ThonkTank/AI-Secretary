package com.autosecretary.application;

import com.autosecretary.domain.BusyInterval;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;

/** Product policy for which losslessly read calendar occurrences block planned work. */
public final class CalendarPolicy {
    public boolean blocksTime(CalendarOccurrence occurrence) {
        return !occurrence.allDay()
                && occurrence.availability() != CalendarAvailability.FREE
                && occurrence.status() != CalendarStatus.CANCELED
                && occurrence.participation() != CalendarParticipation.DECLINED
                && occurrence.visibility() != CalendarVisibility.HIDDEN;
    }

    public List<CalendarOccurrence> relevantOccurrences(List<CalendarOccurrence> occurrences) {
        LinkedHashMap<CalendarOccurrenceId, CalendarOccurrence> unique = new LinkedHashMap<>();
        occurrences.stream()
                .filter(this::blocksTime)
                .sorted(Comparator.comparing(CalendarOccurrence::start)
                        .thenComparing(CalendarOccurrence::end)
                        .thenComparing(value -> value.id().stableValue()))
                .forEach(value -> unique.putIfAbsent(value.id(), value));
        return List.copyOf(unique.values());
    }

    public List<BusyInterval> busyIntervals(
            List<CalendarOccurrence> occurrences, ZoneId zone) {
        List<BusyInterval> result = new ArrayList<>();
        for (CalendarOccurrence occurrence : relevantOccurrences(occurrences)) {
            LocalDateTime start = LocalDateTime.ofInstant(occurrence.start(), zone);
            LocalDateTime end = LocalDateTime.ofInstant(occurrence.end(), zone);
            if (!end.isAfter(start)) {
                end = start.plus(Duration.between(occurrence.start(), occurrence.end()));
            }
            result.add(new BusyInterval(occurrence.id().stableValue(), start, end,
                    occurrence.title().orElse(null),
                    occurrence.visibility() == CalendarVisibility.TITLE_HIDDEN
                            ? BusyInterval.TitleVisibility.HIDDEN
                            : BusyInterval.TitleVisibility.VISIBLE));
        }
        return List.copyOf(result);
    }
}

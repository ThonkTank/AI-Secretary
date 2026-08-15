package de.thonktank.autosecretary.domain.model;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;

public final class RoutineProgress {
    public final int level;
    public final int occurrenceStreak;
    public final int weekStreak;
    public final LocalDate lastCountedWeek;

    public RoutineProgress(int level, int occurrenceStreak, int weekStreak, LocalDate lastCountedWeek) {
        this.level = Math.max(1, level);
        this.occurrenceStreak = Math.max(0, occurrenceStreak);
        this.weekStreak = Math.max(0, weekStreak);
        this.lastCountedWeek = lastCountedWeek;
    }

    public RoutineProgress recordCompletion(boolean onTime, boolean previousOnTime, LocalDate completedOn) {
        if (!onTime) return new RoutineProgress(level, 0, 0, null);

        int nextOccurrenceStreak = previousOnTime ? occurrenceStreak + 1 : 1;
        int nextLevel = Math.max(level, 1 + nextOccurrenceStreak / 5);
        LocalDate week = weekStart(completedOn);
        if (lastCountedWeek == null || !previousOnTime)
            return new RoutineProgress(nextLevel, nextOccurrenceStreak, 1, week);

        long distance = ChronoUnit.WEEKS.between(lastCountedWeek, week);
        if (distance == 0) return new RoutineProgress(nextLevel, nextOccurrenceStreak, weekStreak, week);
        if (distance == 1) return new RoutineProgress(nextLevel, nextOccurrenceStreak, weekStreak + 1, week);
        return new RoutineProgress(nextLevel, nextOccurrenceStreak, 1, week);
    }

    public static LocalDate weekStart(LocalDate date) {
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }
}

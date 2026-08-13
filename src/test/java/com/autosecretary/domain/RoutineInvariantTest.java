package com.autosecretary.domain;

import static org.junit.Assert.assertThrows;

import org.junit.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class RoutineInvariantTest {
    @Test
    public void routineCannotCarryTaskDeadlineBesideItsDueDate() {
        assertThrows(IllegalArgumentException.class, () -> new Routine(
                "00000000-0000-0000-0000-000000000001", "Routine", 30,
                LocalDateTime.of(2026, 8, 20, 18, 0), null, true, List.of(),
                LocalDateTime.of(2026, 8, 1, 8, 0), 7, LocalDate.of(2026, 8, 11),
                CompletionStats.empty(), 0));
    }
}

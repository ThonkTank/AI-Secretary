package com.autosecretary.application.ai;

import static org.junit.Assert.assertThrows;

import com.autosecretary.domain.CompletionStats;
import com.autosecretary.domain.Task;

import org.junit.Test;

import java.time.LocalDateTime;
import java.util.List;

public final class BulkChangeTest {
    private static final String TARGET = "90000000-0000-0000-0000-000000000001";
    private static final String CHANGE_ONE = "90000000-0000-0000-0000-000000000101";
    private static final String CHANGE_TWO = "90000000-0000-0000-0000-000000000102";

    @Test
    public void typedChangeRejectsContradictoryShapeAndIdentity() {
        Task task = task(TARGET);

        assertThrows(IllegalArgumentException.class, () -> new BulkChange(
                CHANGE_ONE, BulkChange.Type.DELETE, TARGET, 0, task, "Löschen"));
        assertThrows(IllegalArgumentException.class, () -> new BulkChange(
                CHANGE_ONE, BulkChange.Type.UPDATE, TARGET, 0, null, "Ändern"));
        assertThrows(IllegalArgumentException.class, () -> new BulkChange(
                CHANGE_ONE, BulkChange.Type.UPDATE,
                "90000000-0000-0000-0000-000000000002", 0, task, "Ändern"));
        assertThrows(IllegalArgumentException.class, () -> new BulkChange(
                CHANGE_ONE, BulkChange.Type.ADD, TARGET, 1, task, "Neu"));
        Task revisionOne = new Task(task.id(), task.title(), task.durationMinutes(), null,
                null, true, List.of(), task.createdAt(), false, CompletionStats.empty(), 1);
        assertThrows(IllegalArgumentException.class, () -> new BulkChange(
                CHANGE_ONE, BulkChange.Type.ADD, TARGET, 0, revisionOne, "Neu"));
        assertThrows(IllegalArgumentException.class, () -> new BulkChange(
                CHANGE_ONE, BulkChange.Type.UPDATE, TARGET, 0, revisionOne, "Ändern"));
    }

    @Test
    public void proposalRejectsMultipleChangesForOneTarget() {
        Task task = task(TARGET);
        BulkChange first = new BulkChange(CHANGE_ONE, BulkChange.Type.UPDATE,
                TARGET, 0, task, "Titel ändern");
        BulkChange second = new BulkChange(CHANGE_TWO, BulkChange.Type.DELETE,
                TARGET, 0, null, "Löschen");

        assertThrows(IllegalArgumentException.class,
                () -> new BulkChangeProposal("Widerspruch", List.of(first, second)));
    }

    private static Task task(String id) {
        return new Task(id, "Aufgabe", 30, null, null, true, List.of(),
                LocalDateTime.of(2026, 8, 13, 8, 0), false,
                CompletionStats.empty(), 0);
    }
}

package com.autosecretary.ui.editor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import com.autosecretary.domain.Step;

import org.junit.Test;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

public final class ObligationEditorStateTest {
    private static final String SHOWER = "00000000-0000-0000-0000-000000000101";
    private static final String HAIR = "00000000-0000-0000-0000-000000000102";
    private static final String ONE = "00000000-0000-0000-0000-000000000201";
    private static final String TWO = "00000000-0000-0000-0000-000000000202";
    @Test
    public void insertedFirstStepDoesNotStealExistingStepIdentity() {
        List<Step> existing = List.of(
                new Step(SHOWER, "Duschen", Set.of(), 0),
                new Step(HAIR, "Haare waschen", Set.of(DayOfWeek.THURSDAY), 1));

        var task = new com.autosecretary.domain.Task(
                "00000000-0000-0000-0000-000000000001", "Pflege", 30, null, null,
                true, existing, LocalDateTime.of(2026, 8, 1, 8, 0), false,
                com.autosecretary.domain.CompletionStats.empty(), 0);
        ObligationEditorState state = ObligationEditorState.initial(
                false, task, LocalDateTime.of(2026, 8, 11, 8, 0));
        List<StepEditorState> edited = new java.util.ArrayList<>(state.steps());
        edited.add(0, StepEditorState.empty().edit("Zähne putzen", ""));
        state = state.edit(state.titleInput(), state.durationInput(), state.deadlineInput(),
                state.timePreferenceInput(), state.flexible(), state.cadenceInput(),
                state.nextDueInput(), edited).validated(LocalDateTime.of(2026, 8, 11, 8, 0));
        List<Step> parsed = state.toWorkItem().steps();

        assertEquals(SHOWER, parsed.get(1).id());
        assertEquals(HAIR, parsed.get(2).id());
        assertEquals(3, parsed.stream().map(Step::id).distinct().count());
    }

    @Test
    public void reorderedAndRenamedRowsKeepIds() {
        List<Step> existing = List.of(
                new Step(ONE, "Eins", Set.of(), 0),
                new Step(TWO, "Zwei", Set.of(DayOfWeek.MONDAY), 1));

        var task = new com.autosecretary.domain.Task(
                "00000000-0000-0000-0000-000000000002", "Reihenfolge", 30, null, null,
                true, existing, LocalDateTime.of(2026, 8, 1, 8, 0), false,
                com.autosecretary.domain.CompletionStats.empty(), 0);
        ObligationEditorState state = ObligationEditorState.initial(
                false, task, LocalDateTime.of(2026, 8, 11, 8, 0));
        state = state.moveStep(TWO, -1);
        List<StepEditorState> renamed = new java.util.ArrayList<>(state.steps());
        renamed.set(0, renamed.get(0).edit("Zwei neu", "Mo"));
        state = state.edit(state.titleInput(), state.durationInput(), state.deadlineInput(),
                state.timePreferenceInput(), state.flexible(), state.cadenceInput(),
                state.nextDueInput(), renamed).validated(LocalDateTime.of(2026, 8, 11, 8, 0));
        List<Step> parsed = state.toWorkItem().steps();

        assertEquals(List.of(TWO, ONE), parsed.stream().map(Step::id).toList());
        assertEquals("Zwei neu", parsed.get(0).title());
    }

    @Test
    public void routineLearningToggleIsIndependentFromTimePreference() {
        ObligationEditorState state = ObligationEditorState.initial(
                true, null, LocalDateTime.of(2026, 8, 11, 8, 0));

        state = state.edit("Lesen", "30", "", "MORNING", false, "7", "2026-08-11",
                List.of()).validated(LocalDateTime.of(2026, 8, 11, 8, 0));
        var routine = (com.autosecretary.domain.Routine) state.toWorkItem();

        assertEquals(com.autosecretary.domain.TimePreference.MORNING,
                routine.timePreference());
        assertFalse(routine.flexible());
    }
}

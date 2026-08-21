package de.thonktank.autosecretary;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;

import de.thonktank.autosecretary.presentation.today.RepetitionProgressUiModel;
import de.thonktank.autosecretary.presentation.today.FocusStepUiModel;

import org.junit.Test;

public final class RepetitionInputStateTest {
    @Test public void draftClampsAndKeepsTheSelectedSavedSet() {
        FocusStepUiModel step = FocusTaskFixtures.step("step", "Kniebeugen")
                .amount("3 × 12").repetition(RepetitionProgressUiModel.sets(
                        3, 12, Arrays.asList(10, 11))).build();

        RepetitionInputState state = RepetitionInputState.idle().edit(step, 0)
                .adjust(step, -20);

        assertEquals(0, state.valueFor(step));
        assertEquals(0, state.editingIndexFor(step));
        assertEquals(999, state.adjust(step, 2_000).valueFor(step));
    }
}

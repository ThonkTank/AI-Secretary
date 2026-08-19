package de.thonktank.autosecretary;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;

import de.thonktank.autosecretary.presentation.RepetitionProgressUiModel;
import de.thonktank.autosecretary.presentation.FocusStepUiModel;

import org.junit.Test;

public final class RepetitionInputStateTest {
    @Test public void draftClampsAndKeepsTheSelectedSavedSet() {
        FocusStepUiModel step = FocusStepUiModel.of("step", "Kniebeugen", "3 × 12", "",
                false, RepetitionProgressUiModel.sets(3, 12, Arrays.asList(10, 11)),
                0, 10, 0);

        RepetitionInputState state = RepetitionInputState.idle().edit(step, 0)
                .adjust(step, -20);

        assertEquals(0, state.valueFor(step));
        assertEquals(0, state.editingIndexFor(step));
        assertEquals(999, state.adjust(step, 2_000).valueFor(step));
    }
}

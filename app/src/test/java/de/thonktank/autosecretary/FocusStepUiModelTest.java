package de.thonktank.autosecretary;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import de.thonktank.autosecretary.presentation.FocusStepUiModel;
import de.thonktank.autosecretary.presentation.RepetitionProgressUiModel;

public final class FocusStepUiModelTest {
    @Test public void factoriesRejectContradictoryRepetitionPresentations() {
        RepetitionProgressUiModel complete = RepetitionProgressUiModel.sets(
                2, 12, Arrays.asList(12, 11));
        RepetitionProgressUiModel open = RepetitionProgressUiModel.sets(
                2, 12, Collections.singletonList(12));

        assertThrows(IllegalArgumentException.class, () -> FocusStepUiModel.of(
                "step", "Kniebeugen", "2 × 12", "", false, complete, 0, 10, 0));
        assertThrows(IllegalArgumentException.class, () -> FocusStepUiModel.of(
                "step", "Kniebeugen", "", "", false, open, 0, 10, 0));

        FocusStepUiModel explicitlyCompleted = FocusStepUiModel.of(
                "step", "Kniebeugen", "2 × 12", "", true, open, 0, 10, 10);
        assertEquals(Collections.singletonList(12),
                explicitlyCompleted.repetitionProgress.actualRepetitions);
    }
}

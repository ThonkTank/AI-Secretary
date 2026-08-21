package de.thonktank.autosecretary;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import de.thonktank.autosecretary.presentation.today.FocusStepUiModel;
import de.thonktank.autosecretary.presentation.today.RepetitionProgressUiModel;

public final class FocusStepUiModelTest {
    @Test public void factoriesRejectContradictoryRepetitionPresentations() {
        RepetitionProgressUiModel complete = RepetitionProgressUiModel.sets(
                2, 12, Arrays.asList(12, 11));
        RepetitionProgressUiModel open = RepetitionProgressUiModel.sets(
                2, 12, Collections.singletonList(12));

        assertThrows(IllegalArgumentException.class, () -> FocusTaskFixtures
                .step("step", "Kniebeugen").amount("2 × 12")
                .repetition(complete).build());
        assertThrows(IllegalArgumentException.class, () -> FocusTaskFixtures
                .step("step", "Kniebeugen").repetition(open).build());

        FocusStepUiModel explicitlyCompleted = FocusTaskFixtures.step("step", "Kniebeugen")
                .amount("2 × 12").done(true).repetition(open).earnedXp(10).build();
        assertEquals(Collections.singletonList(12),
                explicitlyCompleted.repetitionProgress.actualRepetitions);
    }
}

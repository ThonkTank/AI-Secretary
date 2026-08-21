package de.thonktank.autosecretary;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import de.thonktank.autosecretary.domain.model.RewardBreakdown;

public final class RewardBreakdownTest {
    @Test public void factoryOwnsZeroFractionalWholeAndThreeDigitResults() {
        assertBreakdown(RewardBreakdown.fromStage(0, 7), 0, 7, 4.5d, 0);
        assertBreakdown(RewardBreakdown.fromStage(15, 1), 15, 1, 1.5d, 23);
        assertBreakdown(RewardBreakdown.fromStage(15, 2), 15, 2, 2d, 30);
        assertBreakdown(RewardBreakdown.fromStage(25, 8), 25, 8, 5d, 125);
    }

    @Test public void halfBoundariesUseThePublishedMathRoundRule() {
        assertEquals(2, RewardBreakdown.fromStage(1, 1).resultXp);
        assertEquals(5, RewardBreakdown.fromStage(3, 1).resultXp);
        assertEquals(8, RewardBreakdown.fromStage(5, 1).resultXp);
    }

    private static void assertBreakdown(RewardBreakdown value, int base, int stage,
                                        double multiplier, int result) {
        assertEquals(base, value.baseXp);
        assertEquals(stage, value.comboStage);
        assertEquals(multiplier, value.multiplier, 0d);
        assertEquals(result, value.resultXp);
    }
}

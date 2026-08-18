package de.thonktank.autosecretary;

import static org.junit.Assert.assertEquals;

import de.thonktank.autosecretary.domain.model.ComboProgress;
import de.thonktank.autosecretary.domain.model.TaskId;
import de.thonktank.autosecretary.domain.model.XpProgress;
import de.thonktank.autosecretary.domain.model.RewardPolicy;

import org.junit.Test;

import java.time.LocalDate;

public final class ComboRulesTest {
    @Test public void triangularLevelsAndMultipliersHaveNoArtificialCap() {
        ComboProgress value = new ComboProgress("task:t", TaskId.of("t"),
                ComboProgress.Kind.TASK, 78, LocalDate.of(2026, 8, 18));
        assertEquals(12, value.level());
        assertEquals(7d, value.multiplier(), 0d);
    }

    @Test public void decayCountsOnlyFullyElapsedInactiveDaysAndIsIdempotent() {
        ComboProgress value = new ComboProgress("task:t", TaskId.of("t"),
                ComboProgress.Kind.TASK, 20, LocalDate.of(2026, 8, 15));
        assertEquals(20, value.settle(LocalDate.of(2026, 8, 16)).points);
        ComboProgress settled = value.settle(LocalDate.of(2026, 8, 18));
        assertEquals(16, settled.points);
        assertEquals(16, settled.settle(LocalDate.of(2026, 8, 18)).points);
    }

    @Test public void xpLevelResetsItsRingsAtEachThreshold() {
        XpProgress before = new XpProgress(299);
        assertEquals(2, before.level); assertEquals(199, before.inLevel);
        XpProgress next = new XpProgress(300);
        assertEquals(3, next.level); assertEquals(0, next.inLevel);
        assertEquals(300, next.required);
    }

    @Test public void rewardRoundingAndLateCapFollowThePublishedPolicy() {
        ComboProgress levelOne = new ComboProgress("step:s", TaskId.of("t"),
                ComboProgress.Kind.STEP, 1, LocalDate.of(2026, 8, 18));
        assertEquals(15, RewardPolicy.stepXp(levelOne));
        assertEquals(23, RewardPolicy.routineXp(15, levelOne));
        assertEquals(10, RewardPolicy.singleTaskBase(0));
        assertEquals(30, RewardPolicy.singleTaskBase(4));
        assertEquals(30, RewardPolicy.singleTaskBase(400));
    }

    @Test public void aFreshComboDoesNotDecayBeforeItsFirstActivity() {
        ComboProgress fresh = ComboProgress.fresh("task:t", TaskId.of("t"),
                ComboProgress.Kind.TASK);
        assertEquals(0, fresh.settle(LocalDate.of(2099, 1, 1)).points);
        assertEquals(null, fresh.settle(LocalDate.of(2099, 1, 1)).settledThroughOn);
    }
}

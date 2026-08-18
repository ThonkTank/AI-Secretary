package de.thonktank.autosecretary;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import de.thonktank.autosecretary.domain.model.RewardReceipt;

import org.junit.Test;

public final class RewardAnimationQueueTest {
    @Test public void rewardsRunStrictlyOneAtATimeInInsertionOrder() {
        RewardAnimationQueue queue = new RewardAnimationQueue();
        queue.offer(event(10)); queue.offer(event(20)); queue.offer(event(30));

        assertEquals(10, queue.startNext().rewardXp);
        assertTrue(queue.isActive());
        assertNull(queue.startNext());
        queue.finish();
        assertEquals(20, queue.startNext().rewardXp);
        queue.finish();
        assertEquals(30, queue.startNext().rewardXp);
        queue.finish();
        assertEquals(0, queue.size());
    }

    private static UiEvent event(int xp) {
        return UiEvent.reward(new RewardReceipt(xp, RewardReceipt.Target.HEAD, false),
                "complete:test");
    }
}

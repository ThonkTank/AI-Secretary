package de.thonktank.autosecretary;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import de.thonktank.autosecretary.domain.model.RewardBooking;
import de.thonktank.autosecretary.domain.model.RewardReceipt;

import java.time.LocalDate;
import java.util.Collections;

import org.junit.Test;

public final class RewardEffectQueueTest {
    @Test public void effectsRemainPendingInInsertionOrderUntilAcknowledgedById() {
        RewardEffectQueue queue = new RewardEffectQueue();
        RewardEffect first = effect(10);
        RewardEffect second = effect(20);
        RewardEffect third = effect(30);

        queue.enqueue(first);
        queue.enqueue(second);
        queue.enqueue(third);
        queue.enqueue(first);

        assertEquals(first.id, queue.snapshot().first().id);
        assertEquals(3, queue.snapshot().pending.size());
        assertEquals(second.id, queue.acknowledge(first.id).first().id);
        assertEquals(second.id, queue.acknowledge("unknown").first().id);
        assertEquals(third.id, queue.acknowledge(second.id).first().id);
        assertNull(queue.acknowledge(third.id).first());
    }

    private static RewardEffect effect(int xp) {
        RewardBooking booking = new RewardBooking("booking-" + xp, "transaction-" + xp,
                "occurrence", null, "task:test", RewardBooking.Kind.SINGLE_COMPLETION,
                RewardBooking.Target.HEAD, xp, 0, LocalDate.of(2026, 8, 18), null);
        RewardReceipt receipt = RewardReceipt.of(booking.transactionId,
                Collections.singletonList(booking), RewardReceipt.Target.HEAD);
        return RewardEffect.from(receipt,
                new UiCommand(UiCommand.Kind.COMPLETE, "occurrence"));
    }
}

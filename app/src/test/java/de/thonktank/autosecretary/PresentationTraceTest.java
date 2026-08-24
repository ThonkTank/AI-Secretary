package de.thonktank.autosecretary;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public final class PresentationTraceTest {
    @After public void clearTrace() { PresentationTrace.clear(); }

    @Test public void traceIsBoundedOrderedAndObservable() {
        PresentationTrace.clear();
        assertTrue(PresentationTrace.enabled());
        AtomicInteger observed = new AtomicInteger();
        PresentationTrace.Subscription subscription = PresentationTrace.subscribe(
                event -> observed.incrementAndGet());

        for (int index = 0; index < 300; index++)
            PresentationTrace.emit("editor", "state", "index=" + index);

        List<PresentationTrace.Event> events = PresentationTrace.snapshot();
        assertEquals(300, observed.get());
        assertEquals(256, events.size());
        for (int index = 1; index < events.size(); index++)
            assertTrue(events.get(index - 1).sequence < events.get(index).sequence);
        assertTrue(events.get(0).detail.startsWith("index="));
        assertTrue(PresentationTrace.describe().contains("editor/state"));

        subscription.close();
        PresentationTrace.emit("editor", "state", "after-close");
        assertEquals(300, observed.get());
    }

    @Test public void brokenListenerCannotAffectPresentationOrOtherListeners() {
        AtomicInteger observed = new AtomicInteger();
        PresentationTrace.Subscription broken = PresentationTrace.subscribe(event -> {
            throw new IllegalStateException("expected test failure");
        });
        PresentationTrace.Subscription healthy = PresentationTrace.subscribe(
                event -> observed.incrementAndGet());
        try {
            PresentationTrace.emit("today", "action", "BEGIN_REORDER");
            assertEquals(1, observed.get());
        } finally {
            broken.close();
            healthy.close();
        }
    }
}

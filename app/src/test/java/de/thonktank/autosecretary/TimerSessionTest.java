package de.thonktank.autosecretary;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import de.thonktank.autosecretary.timer.TimerSession;

public final class TimerSessionTest {
    @Test public void runningTimerUsesMonotonicDeadlineAndCanPauseAndResume() {
        TimerSession running = running(61_000, 1_061_000, 2_061_000);

        assertEquals(31_000, running.remainingAt(1_030_000));
        TimerSession paused = running.paused(1_030_000);
        assertEquals(TimerSession.State.PAUSED, paused.state);
        assertEquals(31_000, paused.remainingAt(9_000_000));

        TimerSession resumed = paused.resumed(4_000_000, 8_000_000);
        assertEquals(TimerSession.State.RUNNING, resumed.state);
        assertEquals(4_031_000, resumed.targetElapsedRealtime);
        assertEquals(8_031_000, resumed.targetEpochMillis);
    }

    @Test public void finishDoesNotCompleteTheTaskAndObservationIsExplicit() {
        TimerSession finished = running(60_000, 70_000, 80_000).finished();

        assertEquals(TimerSession.State.FINISHED, finished.state);
        assertEquals(0, finished.remainingAt(100_000));
        assertFalse(finished.completionObserved);
        assertTrue(finished.observed().completionObserved);
        assertSame(finished, finished.paused(100_000));
    }

    private static TimerSession running(long remaining, long elapsedTarget, long epochTarget) {
        return new TimerSession("duration:step", "step", "Laufen",
                TimerSession.Kind.DURATION, TimerSession.State.RUNNING, 61, remaining,
                elapsedTarget, epochTarget, 101, false);
    }
}

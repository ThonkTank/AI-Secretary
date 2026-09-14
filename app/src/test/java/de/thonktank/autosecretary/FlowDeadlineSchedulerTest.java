package de.thonktank.autosecretary;

import static org.junit.Assert.*;
import android.os.*;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;
import de.thonktank.autosecretary.presentation.today.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk = 35)
public final class FlowDeadlineSchedulerTest {
    @Test public void hiddenRunsWakeOnceAndRebindDoesNotLoop() {
        AtomicInteger refreshed = new AtomicInteger(); long[] now = {4_000_000};
        FlowDeadlineScheduler scheduler = new FlowDeadlineScheduler(new Handler(Looper.getMainLooper()),
                () -> now[0], refreshed::incrementAndGet);
        var runs = List.of(FlowChainFixtures.run("hidden", "Wäsche", FlowChainUiModel.Mode.COUNTDOWN, now[0] + 1000));
        scheduler.update(runs); scheduler.setForeground(true); ShadowLooper.idleMainLooper();
        assertEquals(0, refreshed.get());
        now[0] += 1000; ShadowLooper.shadowMainLooper().idleFor(Duration.ofSeconds(1));
        assertEquals(1, refreshed.get());
        scheduler.update(runs); ShadowLooper.idleMainLooper();
        assertEquals(1, refreshed.get()); scheduler.close();
    }

    @Test public void replacementAndBackgroundCancelOldWakeAndResumeCatchesUp() {
        AtomicInteger refreshed = new AtomicInteger(); long[] now = {4_000_000};
        FlowDeadlineScheduler scheduler = new FlowDeadlineScheduler(new Handler(Looper.getMainLooper()),
                () -> now[0], refreshed::incrementAndGet);
        scheduler.setForeground(true);
        scheduler.update(List.of(FlowChainFixtures.run("a", "Wäsche", FlowChainUiModel.Mode.COUNTDOWN, now[0] + 1000)));
        ShadowLooper.idleMainLooper();
        scheduler.update(List.of(FlowChainFixtures.run("a", "Wäsche", FlowChainUiModel.Mode.COUNTDOWN, now[0] + 5000)));
        ShadowLooper.idleMainLooper(); now[0] += 1000; ShadowLooper.shadowMainLooper().idleFor(Duration.ofSeconds(1));
        assertEquals(0, refreshed.get()); scheduler.setForeground(false);
        now[0] += 5000; ShadowLooper.shadowMainLooper().idleFor(Duration.ofSeconds(5)); assertEquals(0, refreshed.get());
        scheduler.setForeground(true); ShadowLooper.idleMainLooper(); assertEquals(1, refreshed.get());
        scheduler.close();
    }
}

package com.autosecretary.ui;

import static org.junit.Assert.assertEquals;

import android.app.Application;
import android.os.Looper;

import com.autosecretary.application.TimeProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicInteger;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = Application.class)
public final class TimelineRefreshSchedulerTest {
    @Test public void firesOnceAtTheCanonicalNextRefreshBoundary() {
        MutableTime time = new MutableTime(Instant.parse("2026-08-14T08:00:00Z"));
        AtomicInteger refreshes = new AtomicInteger();
        TimelineRefreshScheduler scheduler = new TimelineRefreshScheduler(
                time, refreshes::incrementAndGet);
        scheduler.update(time.now().plusSeconds(60));
        scheduler.start();

        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(59));
        assertEquals(0, refreshes.get());
        time.value = time.value.plusSeconds(60);
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));

        assertEquals(1, refreshes.get());
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(5));
        assertEquals(1, refreshes.get());
        scheduler.close();
    }

    @Test public void stopCancelsAndStartReschedulesTheRetainedBoundary() {
        MutableTime time = new MutableTime(Instant.parse("2026-08-14T08:00:00Z"));
        AtomicInteger refreshes = new AtomicInteger();
        TimelineRefreshScheduler scheduler = new TimelineRefreshScheduler(
                time, refreshes::incrementAndGet);
        scheduler.start();
        scheduler.update(time.now().plusSeconds(30));
        scheduler.stop();
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(30));
        assertEquals(0, refreshes.get());

        time.value = time.value.plusSeconds(30);
        scheduler.start();
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1));
        assertEquals(1, refreshes.get());
        scheduler.close();
    }

    private static final class MutableTime implements TimeProvider {
        Instant value;
        MutableTime(Instant value) { this.value = value; }
        @Override public Instant now() { return value; }
        @Override public ZoneId zone() { return ZoneId.of("Europe/Berlin"); }
    }
}

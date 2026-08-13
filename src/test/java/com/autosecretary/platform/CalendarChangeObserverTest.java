package com.autosecretary.platform;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.Manifest;
import android.app.Application;
import android.os.Looper;
import android.provider.CalendarContract;

import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = Application.class)
public final class CalendarChangeObserverTest {
    private Application application;

    @Before public void setUp() {
        application = ApplicationProvider.getApplicationContext();
        Shadows.shadowOf(application).denyPermissions(Manifest.permission.READ_CALENDAR);
    }

    @After public void tearDown() {
        Shadows.shadowOf(Looper.getMainLooper()).idle();
    }

    @Test
    public void denialDoesNotRegisterAndGrantRegisters() {
        CalendarChangeObserver observer = new CalendarChangeObserver(
                application, Runnable::run, () -> { });
        observer.refreshRegistration();
        assertFalse(observer.isRegistered());

        Shadows.shadowOf(application).grantPermissions(Manifest.permission.READ_CALENDAR);
        observer.refreshRegistration();
        assertTrue(observer.isRegistered());
        observer.close();
        assertFalse(observer.isRegistered());
    }

    @Test
    public void changesAreDebouncedAndCloseCancelsPendingDelivery() {
        Shadows.shadowOf(application).grantPermissions(Manifest.permission.READ_CALENDAR);
        AtomicInteger refreshes = new AtomicInteger();
        CalendarChangeObserver observer = new CalendarChangeObserver(
                application, Runnable::run, refreshes::incrementAndGet);
        observer.refreshRegistration();

        application.getContentResolver().notifyChange(CalendarContract.Events.CONTENT_URI, null);
        application.getContentResolver().notifyChange(CalendarContract.Events.CONTENT_URI, null);
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(749));
        assertEquals(0, refreshes.get());
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1));
        assertEquals(1, refreshes.get());

        application.getContentResolver().notifyChange(CalendarContract.Events.CONTENT_URI, null);
        observer.close();
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));
        assertEquals(1, refreshes.get());
    }
}

package de.thonktank.autosecretary;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.Manifest;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.provider.CalendarContract;

import androidx.test.core.app.ApplicationProvider;

import de.thonktank.autosecretary.calendar.CalendarDayWindow;
import de.thonktank.autosecretary.calendar.CalendarPolicy;
import de.thonktank.autosecretary.calendar.CalendarResult;
import de.thonktank.autosecretary.infrastructure.AppLogger;
import de.thonktank.autosecretary.presentation.AndroidUiTextProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowContentResolver;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public final class CalendarIntegrationRobolectricTest {
    private static final LocalDate DAY = LocalDate.of(2026, 8, 15);
    private Context context;
    private RecordingProvider provider;
    private CalendarPolicy policy;

    @Before public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        policy = CalendarPolicy.ALL_VISIBLE;
        provider = new RecordingProvider();
        ProviderInfo info = new ProviderInfo();
        info.authority = CalendarContract.AUTHORITY;
        provider.attachInfo(context, info);
        ShadowContentResolver.registerProviderInternal(CalendarContract.AUTHORITY, provider);
        Shadows.shadowOf((android.app.Application) context)
                .grantPermissions(Manifest.permission.READ_CALENDAR);
    }

    @After public void tearDown() {
        Shadows.shadowOf((android.app.Application) context)
                .denyPermissions(Manifest.permission.READ_CALENDAR);
    }

    @Test public void missingPermissionIsDifferentFromAnEmptyCalendar() {
        Shadows.shadowOf((android.app.Application) context)
                .denyPermissions(Manifest.permission.READ_CALENDAR);

        CalendarResult result = repository(ZoneId.of("Europe/Berlin")).loadToday();

        assertTrue(result instanceof CalendarResult.PermissionMissing);
        assertEquals(0, provider.queries);
    }

    @Test public void permissionChangeInvalidatesAPreviouslyCachedMissingResult() {
        Shadows.shadowOf((android.app.Application) context)
                .denyPermissions(Manifest.permission.READ_CALENDAR);
        CalendarRepository repository = repository(ZoneId.of("Europe/Berlin"));
        final int[] notifications = {0};
        repository.observeChanges(() -> notifications[0]++);
        assertTrue(repository.loadToday() instanceof CalendarResult.PermissionMissing);
        context.getContentResolver().notifyChange(CalendarContract.Events.CONTENT_URI, null);
        assertEquals(0, notifications[0]);

        Shadows.shadowOf((android.app.Application) context)
                .grantPermissions(Manifest.permission.READ_CALENDAR);

        assertTrue(repository.loadToday() instanceof CalendarResult.Success);
        assertEquals(1, provider.queries);
        context.getContentResolver().notifyChange(CalendarContract.Events.CONTENT_URI, null);
        assertEquals(1, notifications[0]);
        repository.loadToday();
        assertEquals(2, provider.queries);
    }

    @Test public void defaultPolicyReadsAllVisibleCalendarsAndMapsAllDayAndZone() {
        ZoneId berlin = ZoneId.of("Europe/Berlin");
        provider.add("Frühstück", ZonedDateTime.of(2026, 8, 15, 8, 30, 0, 0, berlin)
                .toInstant().toEpochMilli(), false);
        provider.add("Urlaub", ZonedDateTime.of(2026, 8, 15, 0, 0, 0, 0,
                ZoneId.of("UTC")).toInstant().toEpochMilli(), true);

        CalendarResult result = repository(berlin).loadToday();

        assertTrue(result instanceof CalendarResult.Success);
        assertEquals(2, result.events().size());
        assertEquals("ganztägig", result.events().get(0).time);
        assertEquals(0, result.events().get(0).minuteOfDay);
        assertEquals("08:30", result.events().get(1).time);
        assertTrue(!provider.selection.contains(CalendarContract.Calendars.ACCOUNT_TYPE));
        assertNull(provider.selectionArgs);
    }

    @Test public void googleOnlyIsAnExplicitPolicy() {
        policy = CalendarPolicy.GOOGLE_ONLY;

        repository(ZoneId.of("Europe/Berlin")).loadToday();

        assertTrue(provider.selection.contains(CalendarContract.Calendars.ACCOUNT_TYPE));
        assertArrayEquals(new String[]{"com.google"}, provider.selectionArgs);
    }

    @Test public void cacheIsSharedUntilAContentChangeInvalidatesIt() {
        CalendarRepository repository = repository(ZoneId.of("Europe/Berlin"));
        final int[] notifications = {0};
        repository.observeChanges(() -> notifications[0]++);

        repository.loadToday();
        repository.loadToday();
        assertEquals(1, provider.queries);

        context.getContentResolver().notifyChange(CalendarContract.Events.CONTENT_URI, null);
        repository.loadToday();

        assertEquals(1, notifications[0]);
        assertEquals(2, provider.queries);
    }

    @Test public void unavailableProviderAndUnexpectedErrorStayDistinguishable() {
        provider.returnNull = true;
        assertTrue(repository(ZoneId.of("UTC")).loadToday()
                instanceof CalendarResult.ProviderUnavailable);

        provider.returnNull = false;
        provider.failure = new IllegalStateException("broken provider");
        assertTrue(repository(ZoneId.of("UTC")).loadToday() instanceof CalendarResult.Error);
    }

    @Test public void dayWindowsRespectDstAndZoneBoundaries() {
        ZoneId berlin = ZoneId.of("Europe/Berlin");
        CalendarDayWindow spring = CalendarDayWindow.of(LocalDate.of(2026, 3, 29), berlin);
        CalendarDayWindow autumn = CalendarDayWindow.of(LocalDate.of(2026, 10, 25), berlin);
        CalendarDayWindow tokyo = CalendarDayWindow.of(DAY, ZoneId.of("Asia/Tokyo"));

        assertEquals(23, Duration.between(spring.begin, spring.endExclusive).toHours());
        assertEquals(25, Duration.between(autumn.begin, autumn.endExclusive).toHours());
        assertEquals("2026-08-14T15:00:00Z", tokyo.begin.toString());
    }

    private CalendarRepository repository(ZoneId zone) {
        Clock clock = () -> DAY;
        return new CalendarRepository(context, clock, () -> zone, () -> policy,
                new NoOpLogger(), new AndroidUiTextProvider(context));
    }

    private static final class RecordingProvider extends ContentProvider {
        final List<Object[]> rows = new ArrayList<>();
        int queries;
        String selection;
        String[] selectionArgs;
        boolean returnNull;
        RuntimeException failure;

        void add(String title, long begin, boolean allDay) {
            rows.add(new Object[]{title, begin, allDay ? 1 : 0});
        }

        @Override public boolean onCreate() { return true; }
        @Override public Cursor query(Uri uri, String[] projection, String selection,
                                      String[] selectionArgs, String sortOrder) {
            queries++;
            this.selection = selection;
            this.selectionArgs = selectionArgs;
            if (failure != null) throw failure;
            if (returnNull) return null;
            MatrixCursor cursor = new MatrixCursor(new String[]{CalendarContract.Instances.TITLE,
                    CalendarContract.Instances.BEGIN, CalendarContract.Instances.ALL_DAY});
            for (Object[] row : rows) cursor.addRow(row);
            return cursor;
        }
        @Override public String getType(Uri uri) { return null; }
        @Override public Uri insert(Uri uri, ContentValues values) { return null; }
        @Override public int delete(Uri uri, String selection, String[] selectionArgs) { return 0; }
        @Override public int update(Uri uri, ContentValues values, String selection,
                                    String[] selectionArgs) { return 0; }
    }

    private static final class NoOpLogger implements AppLogger {
        @Override public void info(String tag, String message) { }
        @Override public void error(String tag, String message, Throwable error) { }
    }
}

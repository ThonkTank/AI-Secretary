package de.thonktank.autosecretary;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract;

import de.thonktank.autosecretary.calendar.CalendarDataSource;
import de.thonktank.autosecretary.calendar.CalendarDayWindow;
import de.thonktank.autosecretary.calendar.CalendarPolicy;
import de.thonktank.autosecretary.calendar.CalendarPolicyProvider;
import de.thonktank.autosecretary.calendar.CalendarResult;
import de.thonktank.autosecretary.infrastructure.AppLogger;
import de.thonktank.autosecretary.presentation.UiTextProvider;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

/** Cached read-only projection of today's visible calendar instances. */
public final class CalendarRepository implements CalendarDataSource {
    private static final String TAG = "CalendarRepository";
    private static final String GOOGLE_ACCOUNT_TYPE = "com.google";
    private final Context context;
    private final ContentResolver resolver;
    private final Clock clock;
    private final ZoneIdProvider zones;
    private final CalendarPolicyProvider policies;
    private final AppLogger logger;
    private final UiTextProvider texts;
    private final List<Runnable> observers = new CopyOnWriteArrayList<>();
    private final Object cacheLock = new Object();
    private LocalDate cachedDate;
    private ZoneId cachedZone;
    private CalendarPolicy cachedPolicy;
    private boolean cachedPermissionGranted;
    private CalendarResult cachedResult;
    private boolean dirty = true;

    public CalendarRepository(Context context, Clock clock, ZoneIdProvider zones,
                              CalendarPolicyProvider policies, AppLogger logger,
                              UiTextProvider texts) {
        this.context = context.getApplicationContext();
        this.resolver = this.context.getContentResolver();
        this.clock = clock;
        this.zones = zones;
        this.policies = policies;
        this.logger = logger;
        this.texts = texts;
        resolver.registerContentObserver(CalendarContract.Events.CONTENT_URI, true,
                new ContentObserver(null) {
                    @Override public void onChange(boolean selfChange, Uri uri) {
                        invalidate();
                    }
                });
    }

    @Override public CalendarResult loadToday() {
        LocalDate day = clock.today();
        ZoneId zone = zones.zoneId();
        CalendarPolicy policy = policies.policy();
        boolean permissionGranted = context.checkSelfPermission(Manifest.permission.READ_CALENDAR)
                == PackageManager.PERMISSION_GRANTED;
        synchronized (cacheLock) {
            if (!dirty && day.equals(cachedDate) && zone.equals(cachedZone)
                    && policy == cachedPolicy && permissionGranted == cachedPermissionGranted
                    && cachedResult != null) return cachedResult;
        }
        CalendarResult loaded = query(day, zone, policy);
        synchronized (cacheLock) {
            cachedDate = day;
            cachedZone = zone;
            cachedPolicy = policy;
            cachedPermissionGranted = permissionGranted;
            cachedResult = loaded;
            dirty = false;
            return cachedResult;
        }
    }

    @Override public Subscription observeChanges(Runnable observer) {
        observers.add(observer);
        return () -> observers.remove(observer);
    }

    private CalendarResult query(LocalDate day, ZoneId zone, CalendarPolicy policy) {
        if (context.checkSelfPermission(Manifest.permission.READ_CALENDAR)
                != PackageManager.PERMISSION_GRANTED) return new CalendarResult.PermissionMissing();
        CalendarDayWindow window = CalendarDayWindow.of(day, zone);
        Uri.Builder builder = CalendarContract.Instances.CONTENT_URI.buildUpon();
        ContentUris.appendId(builder, window.begin.toEpochMilli());
        ContentUris.appendId(builder, window.endExclusive.toEpochMilli());
        String[] columns = {CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.ALL_DAY};
        String selection = CalendarContract.Instances.VISIBLE + "=1 AND "
                + CalendarContract.Instances.STATUS + "!=" + CalendarContract.Events.STATUS_CANCELED + " AND ("
                + CalendarContract.Instances.SELF_ATTENDEE_STATUS + " IS NULL OR "
                + CalendarContract.Instances.SELF_ATTENDEE_STATUS + "!="
                + CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED + ")";
        String[] selectionArgs = null;
        if (policy == CalendarPolicy.GOOGLE_ONLY) {
            selection += " AND " + CalendarContract.Calendars.ACCOUNT_TYPE + "=?";
            selectionArgs = new String[]{GOOGLE_ACCOUNT_TYPE};
        }
        List<CalendarEventSnapshot> result = new ArrayList<>();
        try (Cursor cursor = resolver.query(builder.build(), columns, selection, selectionArgs,
                CalendarContract.Instances.BEGIN + " ASC")) {
            if (cursor == null) return new CalendarResult.ProviderUnavailable();
            while (cursor.moveToNext())
                result.add(toSnapshot(cursor.getString(0), cursor.getLong(1),
                        cursor.getInt(2) != 0, zone, texts));
        } catch (SecurityException error) {
            logger.error(TAG, "Calendar permission disappeared during query", error);
            return new CalendarResult.PermissionMissing();
        } catch (IllegalArgumentException error) {
            logger.error(TAG, "Calendar provider is unavailable", error);
            return new CalendarResult.ProviderUnavailable();
        } catch (RuntimeException error) {
            logger.error(TAG, "Calendar provider query failed", error);
            return new CalendarResult.Error(error);
        }
        result.sort(Comparator.comparingInt(event -> event.minuteOfDay));
        return new CalendarResult.Success(result);
    }

    static CalendarEventSnapshot toSnapshot(String title, long start, boolean allDay, ZoneId zone,
                                             UiTextProvider texts) {
        java.time.ZonedDateTime local = Instant.ofEpochMilli(start).atZone(zone);
        String time = allDay ? texts.text(R.string.calendar_all_day)
                : local.format(DateTimeFormatter.ofPattern("HH:mm", Locale.GERMANY));
        return new CalendarEventSnapshot(time,
                title == null || title.trim().isEmpty() ? texts.text(R.string.calendar_untitled) : title,
                allDay ? 0 : local.getHour() * 60 + local.getMinute());
    }

    private void invalidate() {
        synchronized (cacheLock) { dirty = true; }
        for (Runnable observer : observers) observer.run();
    }
}

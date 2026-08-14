package com.autosecretary.platform;

import static org.junit.Assert.assertEquals;

import android.database.MatrixCursor;
import android.provider.CalendarContract;

import com.autosecretary.domain.BusyInterval;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZoneId;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public final class DeviceCalendarGatewayTest {
    private static final String[] PROJECTION = {
            CalendarContract.Instances.BEGIN, CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY, CalendarContract.Instances.TITLE,
            CalendarContract.Instances.AVAILABILITY, CalendarContract.Instances.STATUS,
            CalendarContract.Instances.SELF_ATTENDEE_STATUS,
            CalendarContract.Instances.VISIBLE,
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.CALENDAR_ID
    };

    @Test
    public void excludesNonBlockingInstancesAndDeduplicatesExactMatches() {
        MatrixCursor cursor = new MatrixCursor(PROJECTION);
        add(cursor, 10, 11, 0, "Beschäftigt", CalendarContract.Events.AVAILABILITY_BUSY,
                CalendarContract.Events.STATUS_CONFIRMED,
                CalendarContract.Attendees.ATTENDEE_STATUS_ACCEPTED, 1);
        add(cursor, 10, 11, 0, "Beschäftigt", CalendarContract.Events.AVAILABILITY_BUSY,
                CalendarContract.Events.STATUS_CONFIRMED,
                CalendarContract.Attendees.ATTENDEE_STATUS_ACCEPTED, 1);
        add(cursor, 0, 24, 1, "Ganztägig", CalendarContract.Events.AVAILABILITY_BUSY,
                CalendarContract.Events.STATUS_CONFIRMED, null, 1);
        add(cursor, 11, 12, 0, "Frei", CalendarContract.Events.AVAILABILITY_FREE,
                CalendarContract.Events.STATUS_CONFIRMED, null, 1);
        add(cursor, 12, 13, 0, "Abgesagt", CalendarContract.Events.AVAILABILITY_BUSY,
                CalendarContract.Events.STATUS_CANCELED, null, 1);
        add(cursor, 13, 14, 0, "Abgelehnt", CalendarContract.Events.AVAILABILITY_BUSY,
                CalendarContract.Events.STATUS_CONFIRMED,
                CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED, 1);
        add(cursor, 14, 15, 0, "Unsichtbar", CalendarContract.Events.AVAILABILITY_BUSY,
                CalendarContract.Events.STATUS_CONFIRMED, null, 0);
        add(cursor, 15, 16, 0, "Vorläufig", CalendarContract.Events.AVAILABILITY_TENTATIVE,
                CalendarContract.Events.STATUS_TENTATIVE, null, 1);
        add(cursor, 16, 17, 0, "", null, null, null, null);

        List<BusyInterval> intervals = DeviceCalendarGateway.intervals(cursor, ZoneOffset.UTC);

        assertEquals(List.of("Beschäftigt", "Vorläufig", "Kalendertermin"),
                intervals.stream().map(BusyInterval::title).toList());
    }

    @Test
    public void preservesPositiveDurationAcrossAutumnDstOverlapAndStableInstantIds() {
        MatrixCursor cursor = new MatrixCursor(PROJECTION);
        long firstStart = Instant.parse("2026-10-25T00:30:00Z").toEpochMilli();
        long firstEnd = Instant.parse("2026-10-25T01:30:00Z").toEpochMilli();
        cursor.addRow(row(firstStart, firstEnd, "Zeitumstellung", 41));
        cursor.addRow(row(firstStart, firstEnd + 60_000, "Provider-Duplikat", 41));
        long secondStart = Instant.parse("2026-10-25T01:30:00Z").toEpochMilli();
        long secondEnd = Instant.parse("2026-10-25T02:00:00Z").toEpochMilli();
        cursor.addRow(row(secondStart, secondEnd, "Zweites 02:30", 42));

        List<BusyInterval> intervals = DeviceCalendarGateway.intervals(
                cursor, ZoneId.of("Europe/Berlin"));

        assertEquals(2, intervals.size());
        BusyInterval overlap = intervals.stream()
                .filter(value -> value.id().contains(":41:"))
                .findFirst().orElseThrow();
        assertEquals(60, java.time.Duration.between(
                overlap.start(), overlap.end()).toMinutes());
        assertEquals(List.of("1:42:2026-10-25T01:30:00Z", "1:41:2026-10-25T00:30:00Z"),
                intervals.stream().map(BusyInterval::id).toList());
    }

    private static Object[] row(long start, long end, String title, long eventId) {
        return new Object[]{start, end, 0, title, CalendarContract.Events.AVAILABILITY_BUSY,
                CalendarContract.Events.STATUS_CONFIRMED,
                CalendarContract.Attendees.ATTENDEE_STATUS_ACCEPTED, 1, eventId, 1};
    }

    private static void add(MatrixCursor cursor, int startHour, int endHour, int allDay,
                            String title, Integer availability, Integer status,
                            Integer attendeeStatus, Integer visible) {
        long day = Instant.parse("2026-08-14T00:00:00Z").toEpochMilli();
        cursor.addRow(new Object[]{day + startHour * 3_600_000L,
                day + endHour * 3_600_000L, allDay, title, availability, status,
                attendeeStatus, visible, startHour, 1});
    }
}

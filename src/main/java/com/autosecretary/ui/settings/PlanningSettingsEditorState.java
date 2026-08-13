package com.autosecretary.ui.settings;

import com.autosecretary.domain.PlanningSettings;
import com.autosecretary.domain.TimeWindow;

import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.Map;

/** Raw planning settings and field errors, safe for SavedStateHandle. */
public record PlanningSettingsEditorState(
        String dayStart,
        String dayEnd,
        String morningStart,
        String morningEnd,
        String middayStart,
        String middayEnd,
        String eveningStart,
        String eveningEnd,
        String transition,
        String calendarBefore,
        String calendarAfter,
        String horizon,
        Map<String, String> errors) implements java.io.Serializable {
    public PlanningSettingsEditorState {
        errors = Map.copyOf(errors == null ? Map.of() : errors);
    }

    public static PlanningSettingsEditorState from(PlanningSettings source) {
        return new PlanningSettingsEditorState(
                source.day().start().toString(), source.day().end().toString(),
                source.morning().start().toString(), source.morning().end().toString(),
                source.midday().start().toString(), source.midday().end().toString(),
                source.evening().start().toString(), source.evening().end().toString(),
                Integer.toString(source.taskTransitionMinutes()),
                Integer.toString(source.calendarBufferBeforeMinutes()),
                Integer.toString(source.calendarBufferAfterMinutes()),
                Integer.toString(source.horizonDays()), Map.of());
    }

    public PlanningSettingsEditorState edit(
            String dayStart, String dayEnd,
            String morningStart, String morningEnd,
            String middayStart, String middayEnd,
            String eveningStart, String eveningEnd,
            String transition, String calendarBefore, String calendarAfter, String horizon) {
        return new PlanningSettingsEditorState(dayStart, dayEnd, morningStart, morningEnd,
                middayStart, middayEnd, eveningStart, eveningEnd, transition, calendarBefore,
                calendarAfter, horizon, Map.of());
    }

    public PlanningSettingsEditorState validated() {
        Map<String, String> result = new LinkedHashMap<>();
        TimeWindow day = window(dayStart, dayEnd, "day", result);
        TimeWindow morning = window(morningStart, morningEnd, "morning", result);
        TimeWindow midday = window(middayStart, middayEnd, "midday", result);
        TimeWindow evening = window(eveningStart, eveningEnd, "evening", result);
        inside(day, morning, "morning", result);
        inside(day, midday, "midday", result);
        inside(day, evening, "evening", result);
        number(transition, 0, 180, "transition", "Übergang", result);
        number(calendarBefore, 0, 180, "before", "Puffer davor", result);
        number(calendarAfter, 0, 180, "after", "Puffer danach", result);
        number(horizon, 1, 14, "horizon", "Horizont", result);
        return new PlanningSettingsEditorState(dayStart, dayEnd, morningStart, morningEnd,
                middayStart, middayEnd, eveningStart, eveningEnd, transition, calendarBefore,
                calendarAfter, horizon, result);
    }

    public boolean valid() { return errors.isEmpty(); }

    public PlanningSettings toSettings() {
        if (!valid()) throw new IllegalStateException("Einstellungen wurden nicht validiert");
        return new PlanningSettings(new TimeWindow(LocalTime.parse(dayStart), LocalTime.parse(dayEnd)),
                new TimeWindow(LocalTime.parse(morningStart), LocalTime.parse(morningEnd)),
                new TimeWindow(LocalTime.parse(middayStart), LocalTime.parse(middayEnd)),
                new TimeWindow(LocalTime.parse(eveningStart), LocalTime.parse(eveningEnd)),
                Integer.parseInt(transition), Integer.parseInt(calendarBefore),
                Integer.parseInt(calendarAfter), Integer.parseInt(horizon));
    }

    private static TimeWindow window(
            String start, String end, String key, Map<String, String> errors) {
        try { return new TimeWindow(LocalTime.parse(start.trim()), LocalTime.parse(end.trim())); }
        catch (RuntimeException error) {
            errors.put(key, "Zeiten bitte als HH:mm eingeben");
            return null;
        }
    }

    private static int number(String raw, int min, int max, String key, String label,
                              Map<String, String> errors) {
        try {
            int value = Integer.parseInt(raw.trim());
            if (value < min || value > max) throw new NumberFormatException();
            return value;
        } catch (RuntimeException error) {
            errors.put(key, label + ": " + min + "–" + max);
            return min;
        }
    }

    private static void inside(
            TimeWindow day,
            TimeWindow candidate,
            String key,
            Map<String, String> errors) {
        if (day != null && candidate != null
                && (candidate.start().isBefore(day.start()) || candidate.end().isAfter(day.end()))) {
            errors.put(key, "Fenster muss innerhalb des Planungstags liegen");
        }
    }
}

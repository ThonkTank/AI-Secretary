package com.autosecretary.platform;

import android.content.Context;
import android.content.SharedPreferences;

import com.autosecretary.application.PlanningSettingsRepository;
import com.autosecretary.domain.PlanningSettings;
import com.autosecretary.domain.TimeWindow;

import java.time.LocalTime;

public final class PreferencesPlanningSettingsRepository implements PlanningSettingsRepository {
    private static final String FILE = "planning_settings";
    private final SharedPreferences preferences;

    public PreferencesPlanningSettingsRepository(Context context) {
        preferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    @Override
    public PlanningSettings load() {
        PlanningSettings defaults = PlanningSettings.defaults();
        try {
            return new PlanningSettings(
                    window("day", defaults.day()),
                    window("morning", defaults.morning()),
                    window("midday", defaults.midday()),
                    window("evening", defaults.evening()),
                    preferences.getInt("taskTransition", defaults.taskTransitionMinutes()),
                    preferences.getInt("calendarBefore", defaults.calendarBufferBeforeMinutes()),
                    preferences.getInt("calendarAfter", defaults.calendarBufferAfterMinutes()),
                    preferences.getInt("horizon", defaults.horizonDays()));
        } catch (RuntimeException ignored) {
            return defaults;
        }
    }

    @Override
    public void save(PlanningSettings settings) {
        SharedPreferences.Editor editor = preferences.edit();
        putWindow(editor, "day", settings.day());
        putWindow(editor, "morning", settings.morning());
        putWindow(editor, "midday", settings.midday());
        putWindow(editor, "evening", settings.evening());
        editor.putInt("taskTransition", settings.taskTransitionMinutes());
        editor.putInt("calendarBefore", settings.calendarBufferBeforeMinutes());
        editor.putInt("calendarAfter", settings.calendarBufferAfterMinutes());
        editor.putInt("horizon", settings.horizonDays());
        editor.apply();
    }

    private TimeWindow window(String key, TimeWindow fallback) {
        return new TimeWindow(
                LocalTime.parse(preferences.getString(key + "Start", fallback.start().toString())),
                LocalTime.parse(preferences.getString(key + "End", fallback.end().toString())));
    }

    private static void putWindow(SharedPreferences.Editor editor, String key, TimeWindow value) {
        editor.putString(key + "Start", value.start().toString());
        editor.putString(key + "End", value.end().toString());
    }
}

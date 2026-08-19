package de.thonktank.autosecretary.data.preferences;

import android.content.Context;
import android.content.SharedPreferences;

import de.thonktank.autosecretary.infrastructure.AppLogger;
import de.thonktank.autosecretary.calendar.CalendarPolicy;

public final class UiPreferences {
    private static final String TAG = "UiPreferences";
    private static final String FILE = "forest_ui";
    private static final String THEME_MODE = "theme_mode";
    private static final String CALENDAR_ASKED = "calendar_asked";
    private static final String CALENDAR_POLICY = "calendar_policy";
    private static final String FOCUS_STEP_LIMIT = "focus_step_limit";

    private final SharedPreferences preferences;
    private final AppLogger logger;

    public UiPreferences(Context context, AppLogger logger) {
        this.preferences = context.getApplicationContext()
                .getSharedPreferences(FILE, Context.MODE_PRIVATE);
        this.logger = logger;
    }

    public UiThemeMode themeMode() {
        String stored = preferences.getString(THEME_MODE, UiThemeMode.AUTO.name());
        try {
            return UiThemeMode.valueOf(stored);
        } catch (IllegalArgumentException error) {
            logger.error(TAG, "Ignoring unsupported stored theme mode: " + stored, error);
            return UiThemeMode.AUTO;
        }
    }

    public void setThemeMode(UiThemeMode mode) {
        preferences.edit().putString(THEME_MODE, mode.name()).apply();
    }

    public FocusStepLimit focusStepLimit() {
        String stored = preferences.getString(FOCUS_STEP_LIMIT, FocusStepLimit.AUTO.name());
        try {
            return FocusStepLimit.valueOf(stored);
        } catch (IllegalArgumentException error) {
            logger.error(TAG, "Ignoring unsupported focus step limit: " + stored, error);
            return FocusStepLimit.AUTO;
        }
    }

    public void setFocusStepLimit(FocusStepLimit limit) {
        preferences.edit().putString(FOCUS_STEP_LIMIT, limit.name()).apply();
    }

    public boolean calendarPermissionAsked() {
        return preferences.getBoolean(CALENDAR_ASKED, false);
    }

    public void markCalendarPermissionAsked() {
        preferences.edit().putBoolean(CALENDAR_ASKED, true).apply();
    }

    public CalendarPolicy calendarPolicy() {
        String stored = preferences.getString(CALENDAR_POLICY, CalendarPolicy.ALL_VISIBLE.name());
        try {
            return CalendarPolicy.valueOf(stored);
        } catch (IllegalArgumentException error) {
            logger.error(TAG, "Ignoring unsupported calendar policy: " + stored, error);
            return CalendarPolicy.ALL_VISIBLE;
        }
    }

    public void setCalendarPolicy(CalendarPolicy policy) {
        preferences.edit().putString(CALENDAR_POLICY, policy.name()).apply();
    }

}

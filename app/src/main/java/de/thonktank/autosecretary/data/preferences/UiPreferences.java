package de.thonktank.autosecretary.data.preferences;

import android.content.Context;
import android.content.SharedPreferences;

import de.thonktank.autosecretary.infrastructure.AppLogger;
import de.thonktank.autosecretary.calendar.CalendarPolicy;
import de.thonktank.autosecretary.update.application.UpdatePreferences;

public final class UiPreferences implements UpdatePreferences {
    private static final String TAG = "UiPreferences";
    private static final String FILE = "forest_ui";
    private static final String THEME_MODE = "theme_mode";
    private static final String CALENDAR_ASKED = "calendar_asked";
    private static final String CALENDAR_POLICY = "calendar_policy";
    private static final String LAST_UPDATE_CHECK = "last_update_check";
    private static final String POSTPONED_UPDATE_CODE = "postponed_update_code";
    private static final String POSTPONED_UPDATE_AT = "postponed_update_at";
    private static final long UPDATE_INTERVAL_MS = 24L * 60L * 60L * 1000L;

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

    @Override public boolean shouldCheckUpdates(long nowMillis) {
        long previous = preferences.getLong(LAST_UPDATE_CHECK, 0L);
        return previous <= 0L || nowMillis < previous || nowMillis - previous >= UPDATE_INTERVAL_MS;
    }

    @Override public void markUpdateCheck(long nowMillis) {
        preferences.edit().putLong(LAST_UPDATE_CHECK, nowMillis).apply();
    }

    @Override public boolean shouldPromptForUpdate(long versionCode, long nowMillis) {
        long postponedCode = preferences.getLong(POSTPONED_UPDATE_CODE, -1L);
        long postponedAt = preferences.getLong(POSTPONED_UPDATE_AT, 0L);
        return postponedCode != versionCode || nowMillis < postponedAt
                || nowMillis - postponedAt >= UPDATE_INTERVAL_MS;
    }

    @Override public void postponeUpdate(long versionCode, long nowMillis) {
        preferences.edit().putLong(POSTPONED_UPDATE_CODE, versionCode)
                .putLong(POSTPONED_UPDATE_AT, nowMillis).apply();
    }
}

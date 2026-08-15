package de.thonktank.autosecretary.data.preferences;

import android.content.Context;
import android.content.SharedPreferences;

import de.thonktank.autosecretary.infrastructure.AppLogger;

public final class UiPreferences {
    private static final String TAG = "UiPreferences";
    private static final String FILE = "forest_ui";
    private static final String THEME_MODE = "theme_mode";
    private static final String CALENDAR_ASKED = "calendar_asked";

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
}

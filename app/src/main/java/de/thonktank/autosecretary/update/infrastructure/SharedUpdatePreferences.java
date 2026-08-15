package de.thonktank.autosecretary.update.infrastructure;

import android.content.Context;
import android.content.SharedPreferences;

import de.thonktank.autosecretary.infrastructure.AppLogger;
import de.thonktank.autosecretary.update.application.UpdatePreferences;

/** Dedicated update persistence with a one-time, lossless migration from forest_ui. */
public final class SharedUpdatePreferences implements UpdatePreferences {
    static final String FILE = "forest_updates";
    static final String LEGACY_FILE = "forest_ui";
    static final String LAST_UPDATE_CHECK = "last_update_check";
    static final String POSTPONED_UPDATE_CODE = "postponed_update_code";
    static final String POSTPONED_UPDATE_AT = "postponed_update_at";
    private static final String LEGACY_MIGRATED = "legacy_forest_ui_migrated";
    private static final String TAG = "UpdatePreferences";
    private static final long UPDATE_INTERVAL_MS = 24L * 60L * 60L * 1000L;

    private final SharedPreferences preferences;

    public SharedUpdatePreferences(Context context, AppLogger logger) {
        Context app = context.getApplicationContext();
        preferences = app.getSharedPreferences(FILE, Context.MODE_PRIVATE);
        migrateLegacyValues(app.getSharedPreferences(LEGACY_FILE, Context.MODE_PRIVATE), logger);
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

    private void migrateLegacyValues(SharedPreferences legacy, AppLogger logger) {
        if (preferences.getBoolean(LEGACY_MIGRATED, false)) {
            removeLegacyValues(legacy, logger);
            return;
        }
        SharedPreferences.Editor destination = preferences.edit();
        copyLongIfAbsent(legacy, destination, LAST_UPDATE_CHECK, logger);
        copyLongIfAbsent(legacy, destination, POSTPONED_UPDATE_CODE, logger);
        copyLongIfAbsent(legacy, destination, POSTPONED_UPDATE_AT, logger);
        destination.putBoolean(LEGACY_MIGRATED, true);
        if (!destination.commit()) {
            logger.error(TAG, "Could not persist legacy update preference migration", null);
            return;
        }
        removeLegacyValues(legacy, logger);
    }

    private void copyLongIfAbsent(SharedPreferences legacy,
                                  SharedPreferences.Editor destination, String key,
                                  AppLogger logger) {
        if (preferences.contains(key) || !legacy.contains(key)) return;
        try {
            destination.putLong(key, legacy.getLong(key, 0L));
        } catch (ClassCastException error) {
            logger.error(TAG, "Ignoring malformed legacy update preference " + key, error);
        }
    }

    private static void removeLegacyValues(SharedPreferences legacy, AppLogger logger) {
        if (!legacy.contains(LAST_UPDATE_CHECK) && !legacy.contains(POSTPONED_UPDATE_CODE)
                && !legacy.contains(POSTPONED_UPDATE_AT)) return;
        if (!legacy.edit().remove(LAST_UPDATE_CHECK).remove(POSTPONED_UPDATE_CODE)
                .remove(POSTPONED_UPDATE_AT).commit())
            logger.error(TAG, "Could not remove migrated values from UI preferences", null);
    }
}

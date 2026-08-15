package de.thonktank.autosecretary.update.infrastructure;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.test.core.app.ApplicationProvider;

import de.thonktank.autosecretary.infrastructure.AppLogger;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {26, 35})
public final class SharedUpdatePreferencesRobolectricTest {
    private Context context;

    @Before public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        context.deleteSharedPreferences(SharedUpdatePreferences.FILE);
        context.deleteSharedPreferences(SharedUpdatePreferences.LEGACY_FILE);
    }

    @After public void tearDown() {
        context.deleteSharedPreferences(SharedUpdatePreferences.FILE);
        context.deleteSharedPreferences(SharedUpdatePreferences.LEGACY_FILE);
    }

    @Test public void legacyValuesMoveWithoutTouchingUiPreferences() {
        SharedPreferences legacy = context.getSharedPreferences(
                SharedUpdatePreferences.LEGACY_FILE, Context.MODE_PRIVATE);
        legacy.edit().putString("theme_mode", "DARK")
                .putLong(SharedUpdatePreferences.LAST_UPDATE_CHECK, 123L)
                .putLong(SharedUpdatePreferences.POSTPONED_UPDATE_CODE, 44L)
                .putLong(SharedUpdatePreferences.POSTPONED_UPDATE_AT, 100L).commit();

        SharedUpdatePreferences preferences =
                new SharedUpdatePreferences(context, new NoOpLogger());

        assertFalse(preferences.shouldCheckUpdates(124L));
        assertFalse(preferences.shouldPromptForUpdate(44L, 101L));
        assertEquals("DARK", legacy.getString("theme_mode", null));
        assertFalse(legacy.contains(SharedUpdatePreferences.LAST_UPDATE_CHECK));
        assertFalse(legacy.contains(SharedUpdatePreferences.POSTPONED_UPDATE_CODE));
        assertFalse(legacy.contains(SharedUpdatePreferences.POSTPONED_UPDATE_AT));
    }

    @Test public void existingDedicatedValuesWinAndMigrationDoesNotRepeat() {
        SharedPreferences dedicated = context.getSharedPreferences(
                SharedUpdatePreferences.FILE, Context.MODE_PRIVATE);
        dedicated.edit().putLong(SharedUpdatePreferences.POSTPONED_UPDATE_CODE, 55L)
                .putLong(SharedUpdatePreferences.POSTPONED_UPDATE_AT, 1_000L).commit();
        SharedPreferences legacy = context.getSharedPreferences(
                SharedUpdatePreferences.LEGACY_FILE, Context.MODE_PRIVATE);
        legacy.edit().putLong(SharedUpdatePreferences.POSTPONED_UPDATE_CODE, 44L)
                .putLong(SharedUpdatePreferences.POSTPONED_UPDATE_AT, 100L).commit();

        SharedUpdatePreferences first = new SharedUpdatePreferences(context, new NoOpLogger());
        assertFalse(first.shouldPromptForUpdate(55L, 1_001L));

        legacy.edit().putLong(SharedUpdatePreferences.POSTPONED_UPDATE_CODE, 66L).commit();
        SharedUpdatePreferences second = new SharedUpdatePreferences(context, new NoOpLogger());
        assertFalse(second.shouldPromptForUpdate(55L, 1_001L));
        assertFalse(legacy.contains(SharedUpdatePreferences.POSTPONED_UPDATE_CODE));
    }

    private static final class NoOpLogger implements AppLogger {
        @Override public void info(String tag, String message) { }
        @Override public void error(String tag, String message, Throwable error) { }
    }
}

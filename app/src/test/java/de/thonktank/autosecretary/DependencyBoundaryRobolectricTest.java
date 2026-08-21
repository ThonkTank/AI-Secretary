package de.thonktank.autosecretary;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import de.thonktank.autosecretary.data.preferences.UiPreferences;
import de.thonktank.autosecretary.data.preferences.UiThemeMode;
import de.thonktank.autosecretary.infrastructure.AppLogger;
import de.thonktank.autosecretary.calendar.CalendarPolicy;
import de.thonktank.autosecretary.update.application.UpdateConfiguration;
import de.thonktank.autosecretary.update.infrastructure.DisabledUpdateRepository;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public final class DependencyBoundaryRobolectricTest {
    private Context context;
    private RecordingLogger logger;

    @Before public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        context.deleteSharedPreferences("forest_ui");
        context.deleteSharedPreferences("stability_refactor");
        context.deleteSharedPreferences("jetzt_state");
        logger = new RecordingLogger();
    }

    @After public void tearDown() {
        context.deleteSharedPreferences("forest_ui");
        context.deleteSharedPreferences("stability_refactor");
        context.deleteSharedPreferences("jetzt_state");
    }

    @Test public void uiPreferencesExposeTypedThemeAndPermissionState() {
        UiPreferences preferences = new UiPreferences(context, logger);

        assertEquals(UiThemeMode.AUTO, preferences.themeMode());
        assertEquals(CalendarPolicy.ALL_VISIBLE, preferences.calendarPolicy());
        assertFalse(preferences.calendarPermissionAsked());

        preferences.setThemeMode(UiThemeMode.DARK);
        preferences.setCalendarPolicy(CalendarPolicy.GOOGLE_ONLY);
        preferences.markCalendarPermissionAsked();

        assertEquals(UiThemeMode.DARK, preferences.themeMode());
        assertEquals(CalendarPolicy.GOOGLE_ONLY, preferences.calendarPolicy());
        assertTrue(preferences.calendarPermissionAsked());
    }

    @Test public void invalidStoredThemeFallsBackAndIsLogged() {
        context.getSharedPreferences("forest_ui", Context.MODE_PRIVATE).edit()
                .putString("theme_mode", "NIGHTISH").commit();

        assertEquals(UiThemeMode.AUTO, new UiPreferences(context, logger).themeMode());
        assertEquals(1, logger.errors);
    }

    @Test public void debugApplicationUsesAnExplicitNetworkFreeUpdateEnvironment() {
        AppContainer container = AutoSecretaryApplication.from(context).container();

        assertEquals(UpdateConfiguration.Environment.DEVELOPMENT,
                container.updateConfiguration.environment);
        assertFalse(container.updateConfiguration.remoteChecksEnabled);
        assertFalse(container.updateConfiguration.automaticChecksEnabled);
        assertTrue(container.updates instanceof DisabledUpdateRepository);
    }

    private static final class RecordingLogger implements AppLogger {
        int infos;
        int errors;

        @Override public void info(String tag, String message) {
            infos++;
        }

        @Override public void error(String tag, String message, Throwable error) {
            errors++;
        }
    }
}

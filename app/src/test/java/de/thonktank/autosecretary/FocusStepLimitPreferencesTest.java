package de.thonktank.autosecretary;

import static org.junit.Assert.assertEquals;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import de.thonktank.autosecretary.data.preferences.FocusStepLimit;
import de.thonktank.autosecretary.data.preferences.DisplayPreferences;
import de.thonktank.autosecretary.data.preferences.UiPreferences;
import de.thonktank.autosecretary.infrastructure.AppLogger;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public final class FocusStepLimitPreferencesTest {
    private Context context;
    private UiPreferences preferences;

    @Before public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        context.getSharedPreferences("forest_ui", Context.MODE_PRIVATE).edit().clear().commit();
        preferences = new UiPreferences(context, new AppLogger() {
            @Override public void info(String tag, String message) { }
            @Override public void error(String tag, String message, Throwable error) { }
        });
    }

    @Test public void automaticIsDefaultAndEveryManualLimitPersists() {
        assertEquals(FocusStepLimit.AUTO, preferences.focusStepLimit());
        for (FocusStepLimit limit : FocusStepLimit.values()) {
            preferences.setFocusStepLimit(limit);
            assertEquals(limit, preferences.focusStepLimit());
        }
    }

    @Test public void unsupportedStoredValueFallsBackToAutomatic() {
        context.getSharedPreferences("forest_ui", Context.MODE_PRIVATE).edit()
                .putString("focus_step_limit", "SIEBEN").commit();

        assertEquals(FocusStepLimit.AUTO, preferences.focusStepLimit());
    }

    @Test public void displayChangesAreObservableAndSurviveARepositoryRecreation() {
        List<DisplayPreferences> observed = new ArrayList<>();
        UiPreferences.Subscription subscription =
                preferences.observeDisplayPreferences(observed::add);

        preferences.setFocusStepLimit(FocusStepLimit.FIVE);

        assertEquals(2, observed.size());
        assertEquals(FocusStepLimit.FIVE, observed.get(1).focusStepLimit);
        UiPreferences recreated = new UiPreferences(context, new AppLogger() {
            @Override public void info(String tag, String message) { }
            @Override public void error(String tag, String message, Throwable error) { }
        });
        assertEquals(FocusStepLimit.FIVE, recreated.displayPreferences().focusStepLimit);

        subscription.close();
        preferences.setFocusStepLimit(FocusStepLimit.ONE);
        assertEquals(2, observed.size());
    }
}

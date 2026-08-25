package de.thonktank.autosecretary;

import static org.junit.Assert.assertEquals;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import de.thonktank.autosecretary.calendar.CalendarPolicy;
import de.thonktank.autosecretary.data.preferences.FocusStepLimit;
import de.thonktank.autosecretary.data.preferences.DisplayPreferences;
import de.thonktank.autosecretary.data.preferences.UiPreferences;
import de.thonktank.autosecretary.infrastructure.AppLogger;
import de.thonktank.autosecretary.domain.model.ComboDecayTrigger;
import de.thonktank.autosecretary.domain.model.ComboPolicy;

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

    @Test public void comboPolicyDefaultsAndPersistsAllUserOwnedValues() {
        ComboPolicy defaults = preferences.current();
        assertEquals(2, defaults.gainPoints);
        assertEquals(1, defaults.decayPoints);
        assertEquals(ComboDecayTrigger.DAILY_OVERDUE, defaults.trigger);

        preferences.setComboPolicy(new ComboPolicy(0, 4,
                ComboDecayTrigger.NEXT_SCHEDULED_OCCURRENCE));
        ComboPolicy restored = new UiPreferences(context, new AppLogger() {
            @Override public void info(String tag, String message) { }
            @Override public void error(String tag, String message, Throwable error) { }
        }).current();

        assertEquals(0, restored.gainPoints);
        assertEquals(4, restored.decayPoints);
        assertEquals(ComboDecayTrigger.NEXT_SCHEDULED_OCCURRENCE, restored.trigger);
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

    @Test public void calendarPolicyChangesStopAfterTheSubscriptionCloses() {
        List<CalendarPolicy> observed = new ArrayList<>();
        UiPreferences.Subscription subscription = preferences.observeCalendarPolicy(observed::add);

        preferences.setCalendarPolicy(CalendarPolicy.GOOGLE_ONLY);

        assertEquals(2, observed.size());
        assertEquals(CalendarPolicy.ALL_VISIBLE, observed.get(0));
        assertEquals(CalendarPolicy.GOOGLE_ONLY, observed.get(1));

        subscription.close();
        preferences.setCalendarPolicy(CalendarPolicy.ALL_VISIBLE);
        assertEquals(2, observed.size());
    }
}

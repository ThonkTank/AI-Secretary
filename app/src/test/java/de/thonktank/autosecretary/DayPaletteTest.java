package de.thonktank.autosecretary;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import org.junit.Test;

import java.time.LocalTime;

import de.thonktank.autosecretary.domain.model.TaskSlot;
import de.thonktank.autosecretary.presentation.DashboardUiMapper;
import de.thonktank.autosecretary.presentation.AndroidUiTextProvider;
import androidx.test.core.app.ApplicationProvider;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public final class DayPaletteTest {
    @Test public void greetingsSwitchAtTheEightDesignMarks() {
        assertEquals(R.string.greeting_night, DayPalette.greetingRes(LocalTime.of(3, 59)));
        assertEquals(R.string.greeting_early, DayPalette.greetingRes(LocalTime.of(4, 20)));
        assertEquals(R.string.greeting_morning, DayPalette.greetingRes(LocalTime.of(6, 30)));
        assertEquals(R.string.greeting_forenoon, DayPalette.greetingRes(LocalTime.of(9, 40)));
        assertEquals(R.string.greeting_noon, DayPalette.greetingRes(LocalTime.of(13, 5)));
        assertEquals(R.string.greeting_afternoon, DayPalette.greetingRes(LocalTime.of(17, 10)));
        assertEquals(R.string.greeting_evening, DayPalette.greetingRes(LocalTime.of(19, 35)));
        assertEquals(R.string.greeting_late, DayPalette.greetingRes(LocalTime.of(21, 40)));
        assertEquals(R.string.greeting_night, DayPalette.greetingRes(LocalTime.of(23, 50)));
    }

    @Test public void fixedModesKeepClockDrivenSunPosition() {
        DayPalette automatic = DayPalette.at(LocalTime.of(13, 5), DayPalette.Mode.AUTO);
        DayPalette light = DayPalette.at(LocalTime.of(13, 5), DayPalette.Mode.LIGHT);
        DayPalette dark = DayPalette.at(LocalTime.of(13, 5), DayPalette.Mode.DARK);
        assertEquals(automatic.sunX, light.sunX, .001f);
        assertEquals(automatic.sunX, dark.sunX, .001f);
        assertNotEquals(light.background, dark.background);
    }

    @Test public void softTimesRemainProse() {
        DashboardUiMapper mapper = new DashboardUiMapper(new AndroidUiTextProvider(
                ApplicationProvider.getApplicationContext()));
        assertEquals("um die Mittagszeit", mapper.softTime(TaskSlot.MIDDAY, false));
        assertEquals("fortlaufend, bis die Bedingung erfüllt ist", mapper.softTime(TaskSlot.LATER, true));
    }
}

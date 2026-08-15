package de.thonktank.autosecretary;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import org.junit.Test;

import java.time.LocalTime;

import de.thonktank.autosecretary.domain.model.TaskSlot;
import de.thonktank.autosecretary.presentation.DashboardUiMapper;

public final class DayPaletteTest {
    @Test public void greetingsSwitchAtTheEightDesignMarks() {
        assertEquals("Gute Nacht", DayPalette.greeting(LocalTime.of(3, 59)));
        assertEquals("Noch früh", DayPalette.greeting(LocalTime.of(4, 20)));
        assertEquals("Guten Morgen", DayPalette.greeting(LocalTime.of(6, 30)));
        assertEquals("Vormittag", DayPalette.greeting(LocalTime.of(9, 40)));
        assertEquals("Mittag", DayPalette.greeting(LocalTime.of(13, 5)));
        assertEquals("Nachmittag", DayPalette.greeting(LocalTime.of(17, 10)));
        assertEquals("Guten Abend", DayPalette.greeting(LocalTime.of(19, 35)));
        assertEquals("Es wird spät", DayPalette.greeting(LocalTime.of(21, 40)));
        assertEquals("Gute Nacht", DayPalette.greeting(LocalTime.of(23, 50)));
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
        assertEquals("um die Mittagszeit", DashboardUiMapper.softTime(TaskSlot.MIDDAY, false));
        assertEquals("fortlaufend, bis die Bedingung erfüllt ist", DashboardUiMapper.softTime(TaskSlot.LATER, true));
    }
}

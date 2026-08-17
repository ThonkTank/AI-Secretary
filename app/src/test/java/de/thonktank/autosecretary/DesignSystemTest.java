package de.thonktank.autosecretary;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.util.Locale;
import java.util.stream.Collectors;

public final class DesignSystemTest {
    @Test public void allEightAnchorPalettesMatchTheApprovedGoldenTokens() throws Exception {
        String expected;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                getClass().getResourceAsStream("/golden/day-palette-anchors.txt"),
                StandardCharsets.UTF_8))) {
            expected = reader.lines().collect(Collectors.joining("\n"));
        }
        DayPaletteInterpolator interpolator = new DayPaletteInterpolator();
        StringBuilder actual = new StringBuilder();
        for (DayPaletteAnchor anchor : DayPaletteAnchor.values()) {
            if (actual.length() > 0) actual.append('\n');
            actual.append(snapshot(anchor, interpolator.atAnchor(anchor)));
        }
        assertEquals(expected, actual.toString());
    }

    @Test public void primaryCombinationsMeetWcagContrastAcrossTheWholeDay() {
        for (DayPalette.Mode mode : DayPalette.Mode.values()) {
            for (int minute = 0; minute < 24 * 60; minute += 15) {
                DayPalette palette = DayPalette.at(LocalTime.of(minute / 60, minute % 60), mode);
                assertContrast("primary on leaf", palette.ink, palette.leaf1, mode, minute);
                assertContrast("accent content", palette.accentText, palette.accent, mode, minute);
                assertContrast("calendar content", palette.calendarInk, palette.calendar, mode, minute);
                assertContrast("status on background", palette.status, palette.background, mode, minute);
            }
        }
    }

    @Test public void fixedModesFreezeSurfacesButKeepAllSolarAttributesClockDriven() {
        LocalTime morning = LocalTime.of(8, 0);
        LocalTime evening = LocalTime.of(19, 0);
        DayPalette lightMorning = DayPalette.at(morning, DayPalette.Mode.LIGHT);
        DayPalette lightEvening = DayPalette.at(evening, DayPalette.Mode.LIGHT);
        DayPalette autoMorning = DayPalette.at(morning, DayPalette.Mode.AUTO);
        DayPalette autoEvening = DayPalette.at(evening, DayPalette.Mode.AUTO);

        assertEquals(lightMorning.background, lightEvening.background);
        assertEquals(lightMorning.tree, lightEvening.tree);
        assertEquals(autoMorning.sunX, lightMorning.sunX, .0001f);
        assertEquals(autoEvening.sunX, lightEvening.sunX, .0001f);
        assertEquals(autoMorning.sunWidth, lightMorning.sunWidth, .0001f);
        assertEquals(autoEvening.sunColor, lightEvening.sunColor);
        assertNotEquals(lightMorning.sunX, lightEvening.sunX, .0001f);
    }

    @Test public void compatibilityAliasesComeFromNamedTokenGroups() {
        DayPalette palette = DayPalette.at(LocalTime.NOON, DayPalette.Mode.AUTO);
        assertEquals(palette.surfaces.background, palette.background);
        assertEquals(palette.typography.primary, palette.ink);
        assertEquals(palette.forest.tree, palette.tree);
        assertEquals(MotionTokens.standard().deferDurationMs, palette.motion.deferDurationMs);
    }

    @Test public void approvedEdgesSolarGeometryAndMotionAreExplicitTokens() {
        DayPalette day = new DayPaletteInterpolator().atAnchor(DayPaletteAnchor.MORNING);
        DayPalette evening = new DayPaletteInterpolator().atAnchor(DayPaletteAnchor.AFTERNOON);
        DayPalette night = new DayPaletteInterpolator().atAnchor(DayPaletteAnchor.NIGHT);

        assertEquals(0x38785a1e, day.leaf1Edge);
        assertEquals(0x2b785a1e, day.leaf2Edge);
        assertEquals(0x21785a1e, day.leaf3Edge);
        assertEquals(.52f, day.sunHeight, .0001f);
        assertEquals(.50f, evening.sunHeight, .0001f);
        assertEquals(.42f, night.sunHeight, .0001f);
        assertEquals(.16f, day.shadowAlpha, .0001f);
        assertEquals(.44f, night.shadowAlpha, .0001f);
        assertEquals(180L, day.motion.dewDurationMs);
        assertEquals(240L, day.motion.stateChangeDurationMs);
        assertEquals(420L, day.motion.leafFlightDurationMs);
        assertEquals(520L, day.motion.glintDurationMs);
        assertEquals(1_000L, day.motion.afterglowDurationMs);
        assertEquals(11_000L, day.motion.forestBreathDurationMs);
        assertEquals(18f, day.motion.forestBreathDistanceDp, .0001f);
    }

    private static String snapshot(DayPaletteAnchor anchor, DayPalette p) {
        return String.format(Locale.ROOT,
                "%s|%08x|%08x|%08x|%08x|%08x|%08x|%08x|%08x|%08x|%08x|%08x|%08x|%08x|%08x|%08x|%08x|%08x|%08x|%08x|%08x|%08x|%.4f|%.4f|%.4f|%.4f|%.4f",
                anchor.name(), p.background, p.leaf1, p.leaf2, p.leaf3, p.ink, p.ink2,
                p.hint, p.muted, p.done, p.dot, p.status, p.accent, p.accentText,
                p.light, p.lightText, p.tree, p.calendar, p.calendarInk, p.calendarLabel,
                p.bad, p.sunColor, p.farAlpha, p.middleAlpha, p.frontAlpha, p.sunX, p.sunWidth);
    }

    private static void assertContrast(String label, int foreground, int background,
                                       DayPalette.Mode mode, int minute) {
        double ratio = contrast(foreground, background);
        assertTrue(label + " contrast " + ratio + " in " + mode + " at " + minute,
                ratio >= 4.5d);
    }

    private static double contrast(int first, int second) {
        double a = luminance(first), b = luminance(second);
        return (Math.max(a, b) + .05d) / (Math.min(a, b) + .05d);
    }

    private static double luminance(int color) {
        double red = channel((color >> 16) & 255);
        double green = channel((color >> 8) & 255);
        double blue = channel(color & 255);
        return .2126d * red + .7152d * green + .0722d * blue;
    }

    private static double channel(int value) {
        double normalized = value / 255d;
        return normalized <= .04045d ? normalized / 12.92d
                : Math.pow((normalized + .055d) / 1.055d, 2.4d);
    }
}

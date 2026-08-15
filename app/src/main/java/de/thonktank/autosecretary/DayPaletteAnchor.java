package de.thonktank.autosecretary;

/** The eight approved clock anchors from the visual handoff. */
public enum DayPaletteAnchor {
    PRE_DAWN(4 * 60 + 20),
    DAWN(6 * 60 + 30),
    MORNING(9 * 60 + 40),
    FORENOON(13 * 60 + 5),
    NOON(17 * 60 + 10),
    AFTERNOON(19 * 60 + 35),
    EVENING(21 * 60 + 40),
    NIGHT(23 * 60 + 50);

    public final int minuteOfDay;

    DayPaletteAnchor(int minuteOfDay) {
        this.minuteOfDay = minuteOfDay;
    }
}

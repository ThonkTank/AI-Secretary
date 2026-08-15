package de.thonktank.autosecretary;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import de.thonktank.autosecretary.domain.model.Recurrence;
import de.thonktank.autosecretary.domain.model.TaskSlot;

/** Deterministic debug-only states for layout inspectors and future preview activities. */
public final class DebugPreviewFixtures {
    private DebugPreviewFixtures() { }

    public static DashboardState busyDay() {
        TaskSnapshot focus = new TaskSnapshot("preview-morning", "preview-occurrence",
                "Morgenroutine", TaskSlot.MORNING, "heute am Morgen", "Anziehen",
                Recurrence.DAILY, Arrays.asList(
                        new TaskStepSnapshot("preview-step-1", "Duschen", true),
                        new TaskStepSnapshot("preview-step-2", "Anziehen", false),
                        new TaskStepSnapshot("preview-step-3", "Frühstück", false)),
                2, false, false, false, false, 6, 1_001_000L);
        TaskSnapshot after = new TaskSnapshot("preview-letter", "preview-letter-occurrence",
                "Brief beantworten", TaskSlot.MIDDAY, "um die Mittagszeit", "Erledigen",
                Recurrence.ONCE, Collections.emptyList(), 0, false, false, false,
                false, 0, 2_001_000L);
        return new DashboardState(120, Arrays.asList(focus, after));
    }

    public static DashboardState emptyDay() {
        return new DashboardState(0, Collections.emptyList());
    }

    public static List<CalendarEventSnapshot> calendar() {
        return Arrays.asList(new CalendarEventSnapshot("ganztägig", "Urlaub", 0),
                new CalendarEventSnapshot("10:15", "Arzttermin", 10 * 60 + 15));
    }
}

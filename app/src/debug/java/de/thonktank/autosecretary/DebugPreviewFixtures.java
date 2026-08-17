package de.thonktank.autosecretary;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import de.thonktank.autosecretary.domain.model.Recurrence;
import de.thonktank.autosecretary.domain.model.TaskSlot;

/** Deterministic debug-only states for the reference preview gallery and layout inspectors. */
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

    public static DashboardState reference(String state) {
        TaskSnapshot morning = morning(false, "step".equals(state));
        TaskSnapshot after = task("preview-after", "Abgabe Statistik-Übung", TaskSlot.MORNING,
                "voraussichtlich ab 10:15", false, false, 2_000L);
        TaskSnapshot laundry = task("preview-laundry", "Wäsche aufhängen", TaskSlot.LATER,
                "", false, false, 3_000L);
        TaskSnapshot hiddenOne = task("preview-hidden-1", "Einkauf planen", TaskSlot.LATER,
                "", false, false, 4_000L);
        TaskSnapshot hiddenTwo = task("preview-hidden-2", "Pflanzen gießen", TaskSlot.LATER,
                "", false, false, 5_000L);
        if ("empty".equals(state)) return new DashboardState(0, Collections.emptyList());
        if ("later".equals(state)) return new DashboardState(120,
                Arrays.asList(after, morning, laundry, hiddenOne, hiddenTwo));
        if ("complete".equals(state)) return new DashboardState(120,
                Arrays.asList(morning(true, true), after, laundry, hiddenOne, hiddenTwo));
        if ("evening".equals(state)) {
            TaskSnapshot ongoing = new TaskSnapshot("preview-ongoing", "preview-ongoing-occurrence",
                    "Praktikum", TaskSlot.LATER, "fortlaufend, bis es angenommen ist", "angenommen",
                    Recurrence.ONCE, Collections.emptyList(), 0, true, true, false, false, 6, 900L);
            return new DashboardState(150, Arrays.asList(ongoing, morning(true, true), laundry));
        }
        return new DashboardState(120, Arrays.asList(
                morning, after, laundry, hiddenOne, hiddenTwo));
    }

    public static List<CalendarEventSnapshot> referenceCalendar(String state) {
        if ("empty".equals(state) || "evening".equals(state)) return Collections.emptyList();
        return Collections.singletonList(new CalendarEventSnapshot("11:00", "Zahnarzt", 11 * 60));
    }

    private static TaskSnapshot morning(boolean done, boolean secondStepDone) {
        return new TaskSnapshot("preview-morning", "preview-morning-occurrence", "Morgenroutine",
                TaskSlot.MORNING, "etwa eine halbe Stunde", "Haare waschen", Recurrence.DAILY,
                Arrays.asList(new TaskStepSnapshot("preview-step-1", "Duschen", true),
                        new TaskStepSnapshot("preview-step-2", "Haare waschen", secondStepDone || done),
                        new TaskStepSnapshot("preview-step-3", "Anziehen", done),
                        new TaskStepSnapshot("preview-step-4", "Tabletten nehmen", done)),
                done ? 0 : secondStepDone ? 2 : 3, false, false, done, false, 6, 1_000L);
    }

    private static TaskSnapshot task(String id, String title, TaskSlot slot, String softTime,
                                     boolean done, boolean overdue, long order) {
        return new TaskSnapshot(id, id + "-occurrence", title, slot, softTime, "erledigen",
                Recurrence.ONCE, Collections.emptyList(), 0, false, false, done, overdue, 0, order);
    }
}

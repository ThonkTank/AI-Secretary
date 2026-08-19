package de.thonktank.autosecretary;

import de.thonktank.autosecretary.presentation.TaskStepUiModel;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import de.thonktank.autosecretary.domain.model.Recurrence;
import de.thonktank.autosecretary.domain.model.TaskSlot;
import de.thonktank.autosecretary.domain.model.XpProgress;

/** Deterministic debug-only states for the reference preview gallery and layout inspectors. */
public final class DebugPreviewFixtures {
    private DebugPreviewFixtures() { }

    public static TodayUiModel busyDay() {
        TaskSnapshot focus = new TaskSnapshot("preview-morning", "preview-occurrence",
                "Morgenroutine", TaskSlot.MORNING, "heute am Morgen", "Anziehen",
                Recurrence.DAILY, Arrays.asList(
                        new TaskStepUiModel("preview-step-1", "Duschen", true),
                        new TaskStepUiModel("preview-step-2", "Anziehen", false),
                        new TaskStepUiModel("preview-step-3", "Frühstück", false)),
                2, false, false, false, false, 6, 1_001_000L);
        TaskSnapshot after = new TaskSnapshot("preview-letter", "preview-letter-occurrence",
                "Brief beantworten", TaskSlot.MIDDAY, "um die Mittagszeit", "Erledigen",
                Recurrence.ONCE, Collections.emptyList(), 0, false, false, false,
                false, 0, 2_001_000L);
        return today(120, Arrays.asList(focus, after));
    }

    public static TodayUiModel widgetReference() {
        TaskSnapshot gym = new TaskSnapshot("preview-gym", "preview-gym-occurrence",
                "Gym Routine", TaskSlot.MORNING, "etwa eine Stunde", "Rudern",
                Recurrence.DAILY, Arrays.asList(
                        new TaskStepUiModel("preview-gym-1", "Bankdrücken",
                                "3 Sätze · 8 Wiederholungen · 60 kg", true, null, 2, 10, 10),
                        new TaskStepUiModel("preview-gym-2", "Rudern",
                                "3 Sätze · 10 Wiederholungen · 45 kg", false, null, 1, 10, 0),
                        new TaskStepUiModel("preview-gym-3", "Plank",
                                "60 Sekunden · ruhig atmen", false, null, 0, 10, 0)),
                2, false, false, false, false, 4, 1_000L);
        TaskSnapshot after = task("preview-letter", "Brief beantworten", TaskSlot.MIDDAY,
                "um die Mittagszeit", false, false, 2_000L);
        return today(120, Arrays.asList(gym, after));
    }

    public static TodayUiModel emptyDay() {
        return today(0, Collections.emptyList());
    }

    public static List<CalendarEventSnapshot> calendar() {
        return Arrays.asList(new CalendarEventSnapshot("ganztägig", "Urlaub", 0),
                new CalendarEventSnapshot("10:15", "Arzttermin", 10 * 60 + 15));
    }

    public static TodayUiModel reference(String state) {
        if ("empty-vessel".equals(state)) return today(70,
                Collections.singletonList(vesselTask(0, false)));
        if ("partial-vessel".equals(state)) return today(70,
                Collections.singletonList(vesselTask(1, false)));
        if ("harvest-ready".equals(state)) return today(70,
                Collections.singletonList(vesselTask(3, true)));
        if ("three-digit".equals(state)) return today(70,
                Collections.singletonList(threeDigitTask()));
        TaskSnapshot morning = morning(false, "step".equals(state));
        TaskSnapshot after = task("preview-after", "Abgabe Statistik-Übung", TaskSlot.MORNING,
                "voraussichtlich ab 10:15", false, false, 2_000L);
        TaskSnapshot laundry = task("preview-laundry", "Wäsche aufhängen", TaskSlot.LATER,
                "", false, false, 3_000L);
        TaskSnapshot hiddenOne = task("preview-hidden-1", "Einkauf planen", TaskSlot.LATER,
                "", false, false, 4_000L);
        TaskSnapshot hiddenTwo = task("preview-hidden-2", "Pflanzen gießen", TaskSlot.LATER,
                "", false, false, 5_000L);
        if ("empty".equals(state)) return today(0, Collections.emptyList());
        if ("later".equals(state)) return today(120,
                Arrays.asList(after, morning, laundry, hiddenOne, hiddenTwo));
        if ("complete".equals(state) || "harvested".equals(state)) return today(120,
                Arrays.asList(morning(true, true), after, laundry, hiddenOne, hiddenTwo));
        if ("evening".equals(state)) {
            TaskSnapshot ongoing = new TaskSnapshot("preview-ongoing", "preview-ongoing-occurrence",
                    "Praktikum", TaskSlot.LATER, "fortlaufend, bis es angenommen ist", "angenommen",
                    Recurrence.ONCE, Collections.emptyList(), 0, true, true, false, false, 6, 900L);
            return today(150, Arrays.asList(ongoing, morning(true, true), laundry));
        }
        return today(120, Arrays.asList(
                morning, after, laundry, hiddenOne, hiddenTwo));
    }

    public static List<CalendarEventSnapshot> referenceCalendar(String state) {
        if ("empty".equals(state) || "evening".equals(state)) return Collections.emptyList();
        return Collections.singletonList(new CalendarEventSnapshot("11:00", "Zahnarzt", 11 * 60));
    }

    private static TaskSnapshot morning(boolean done, boolean secondStepDone) {
        return new TaskSnapshot("preview-morning", "preview-morning-occurrence", "Morgenroutine",
                TaskSlot.MORNING, "etwa eine halbe Stunde", "Haare waschen", Recurrence.DAILY,
                Arrays.asList(new TaskStepUiModel("preview-step-1", "Duschen", true),
                        new TaskStepUiModel("preview-step-2", "Haare waschen", secondStepDone || done),
                        new TaskStepUiModel("preview-step-3", "Anziehen", done),
                        new TaskStepUiModel("preview-step-4", "Tabletten nehmen", done)),
                done ? 0 : secondStepDone ? 2 : 3, false, false, done, false, 6, 1_000L);
    }

    private static TaskSnapshot task(String id, String title, TaskSlot slot, String softTime,
                                     boolean done, boolean overdue, long order) {
        return new TaskSnapshot(id, id + "-occurrence", title, slot, softTime, "erledigen",
                Recurrence.ONCE, Collections.emptyList(), 0, false, false, done, overdue, 0, order);
    }

    private static TaskSnapshot vesselTask(int completed, boolean ready) {
        List<TaskStepUiModel> steps = Arrays.asList(
                previewStep("vessel-1", "Duschen", completed >= 1, 10, 2),
                previewStep("vessel-2", "Haare waschen", completed >= 2, 15, 3),
                previewStep("vessel-3", "Anziehen", completed >= 3, 20, 5));
        int collected = completed >= 1 ? 10 : 0;
        if (completed >= 2) collected += 15;
        if (completed >= 3) collected += 20;
        return new TaskSnapshot("preview-vessel", "preview-vessel-occurrence",
                "Morgenroutine", TaskSlot.MORNING, "", "erledigen", Recurrence.DAILY,
                steps, 3 - completed, false, false, false, false, 5, 1_000L,
                68, collected, 0, ready);
    }

    private static TaskStepUiModel previewStep(String id, String title, boolean done,
                                                int value, int combo) {
        return new TaskStepUiModel(id, title, "", done, null, combo, value,
                done ? value : 0);
    }

    private static TaskSnapshot threeDigitTask() {
        return new TaskSnapshot("preview-three-digit", "preview-three-digit-occurrence",
                "Steuerunterlagen abgeben", TaskSlot.MORNING, "", "erledigen",
                Recurrence.ONCE, Collections.emptyList(), 0, false, false, false,
                true, 12, 1_000L, 125, 0, 0, false);
    }

    private static TodayUiModel today(int xp, List<TaskSnapshot> tasks) {
        TaskSnapshot focus = null;
        for (TaskSnapshot task : tasks) if (!task.done) { focus = task; break; }
        return new TodayUiModel(xp, new XpProgress(xp), tasks, focus);
    }
}

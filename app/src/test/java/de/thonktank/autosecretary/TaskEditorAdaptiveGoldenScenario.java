package de.thonktank.autosecretary;

import java.time.LocalTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Explicit night and compact scenario catalog for the adaptive editor contract. */
final class TaskEditorAdaptiveGoldenScenario {
    static final List<TaskEditorAdaptiveGoldenScenario> ALL = Collections.unmodifiableList(
            Arrays.asList(
                    new TaskEditorAdaptiveGoldenScenario("11-nacht-uebersicht", 7,
                            412, 892, 1f, LocalTime.of(23, 50), DayPalette.Mode.DARK),
                    new TaskEditorAdaptiveGoldenScenario("12-kompakt-titel", 0,
                            320, 640, 1.5f, LocalTime.of(9, 40), DayPalette.Mode.LIGHT),
                    new TaskEditorAdaptiveGoldenScenario("13-kompakt-rhythmus", 3,
                            320, 640, 1.5f, LocalTime.of(9, 40), DayPalette.Mode.LIGHT),
                    new TaskEditorAdaptiveGoldenScenario("14-kompakt-schritte", 5,
                            320, 640, 1.5f, LocalTime.of(9, 40), DayPalette.Mode.LIGHT),
                    new TaskEditorAdaptiveGoldenScenario("15-kompakt-uebersicht", 7,
                            320, 640, 1.5f, LocalTime.of(9, 40), DayPalette.Mode.LIGHT)));

    final String id;
    final int standardScenarioIndex;
    final int widthDp;
    final int heightDp;
    final float fontScale;
    final LocalTime time;
    final DayPalette.Mode paletteMode;

    private TaskEditorAdaptiveGoldenScenario(String id, int standardScenarioIndex,
                                             int widthDp, int heightDp, float fontScale,
                                             LocalTime time, DayPalette.Mode paletteMode) {
        this.id = id;
        this.standardScenarioIndex = standardScenarioIndex;
        this.widthDp = widthDp;
        this.heightDp = heightDp;
        this.fontScale = fontScale;
        this.time = time;
        this.paletteMode = paletteMode;
    }

    EditorUiState state() {
        return TaskEditorGoldenScenario.ALL.get(standardScenarioIndex).state();
    }
}

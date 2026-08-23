package de.thonktank.autosecretary;

import android.os.Bundle;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import de.thonktank.autosecretary.domain.model.Recurrence;
import de.thonktank.autosecretary.domain.model.StepAmount;
import de.thonktank.autosecretary.domain.model.StepAmountKind;
import de.thonktank.autosecretary.domain.model.TaskBoundKind;
import de.thonktank.autosecretary.domain.model.TaskSlot;
import de.thonktank.autosecretary.domain.model.TimeOfDay;

/** Explicit catalog behind the ten task-editor regression baselines. */
final class TaskEditorGoldenScenario {
    enum Steps { NONE, BASIC, DETAIL, ROUTINE }

    static final LocalDate TODAY = LocalDate.of(2026, 8, 17);
    static final List<TaskEditorGoldenScenario> ALL = Collections.unmodifiableList(Arrays.asList(
            new TaskEditorGoldenScenario("01-titel-zeitraum", "Morgenroutine",
                    Recurrence.DAILY, 1, 0, TaskBoundKind.FOR_WEEKS, 6, 30, "",
                    Steps.NONE, EditorUiState.Page.TITLE, EditorUiState.Prompt.NONE, false),
            new TaskEditorGoldenScenario("02-titel-fehler", "", Recurrence.DAILY, 1, 0,
                    TaskBoundKind.FOREVER, null, 30, "", Steps.NONE,
                    EditorUiState.Page.TITLE, EditorUiState.Prompt.NONE, false),
            new TaskEditorGoldenScenario("03-titel-einmalig", "Abgabe Statistik-Übung",
                    Recurrence.ONCE, 1, 0, TaskBoundKind.FOREVER, null, 30, "",
                    Steps.NONE, EditorUiState.Page.TITLE, EditorUiState.Prompt.NONE, false),
            new TaskEditorGoldenScenario("04-rhythmus-wochentage", "Morgenroutine",
                    Recurrence.WEEKDAYS, 1, 31, TaskBoundKind.FOREVER, null, 30, "",
                    Steps.NONE, EditorUiState.Page.SCHEDULE, EditorUiState.Prompt.NONE, false),
            new TaskEditorGoldenScenario("05-rhythmus-intervall", "Pflanzen gießen",
                    Recurrence.INTERVAL, 3, 0, TaskBoundKind.FOREVER, null, 30, "",
                    Steps.NONE, EditorUiState.Page.SCHEDULE, EditorUiState.Prompt.NONE, false),
            new TaskEditorGoldenScenario("06-schritte", "Kraft üben", Recurrence.DAILY,
                    1, 0, TaskBoundKind.FOREVER, null, 30, "", Steps.BASIC,
                    EditorUiState.Page.STEPS, EditorUiState.Prompt.NONE, false),
            new TaskEditorGoldenScenario("07-schritt-detail", "Kraft üben", Recurrence.DAILY,
                    1, 0, TaskBoundKind.FOREVER, null, 30, "", Steps.DETAIL,
                    EditorUiState.Page.STEPS, EditorUiState.Prompt.NONE, false),
            new TaskEditorGoldenScenario("08-uebersicht", "Morgenroutine",
                    Recurrence.WEEKDAYS, 1, 31, TaskBoundKind.FOR_WEEKS, 6, 20,
                    "Tabletten liegen im Bad, nicht in der Küche.", Steps.ROUTINE,
                    EditorUiState.Page.SUMMARY, EditorUiState.Prompt.NONE, true),
            new TaskEditorGoldenScenario("09-abbrechen-rueckfrage", "Morgenroutine",
                    Recurrence.WEEKDAYS, 1, 31, TaskBoundKind.FOR_WEEKS, 6, 20,
                    "Tabletten liegen im Bad, nicht in der Küche.", Steps.ROUTINE,
                    EditorUiState.Page.TITLE, EditorUiState.Prompt.DISCARD, false),
            new TaskEditorGoldenScenario("10-loeschen-rueckfrage", "Morgenroutine",
                    Recurrence.WEEKDAYS, 1, 31, TaskBoundKind.FOR_WEEKS, 6, 20,
                    "Tabletten liegen im Bad, nicht in der Küche.", Steps.ROUTINE,
                    EditorUiState.Page.SUMMARY, EditorUiState.Prompt.DELETE, true)));

    final String id;
    final String title;
    final Recurrence recurrence;
    final int intervalDays;
    final int weekdayMask;
    final TaskBoundKind boundKind;
    final Integer boundWeeks;
    final Integer duration;
    final String note;
    final Steps steps;
    final EditorUiState.Page page;
    final EditorUiState.Prompt prompt;
    final boolean edit;

    private TaskEditorGoldenScenario(String id, String title, Recurrence recurrence,
                                     int intervalDays, int weekdayMask,
                                     TaskBoundKind boundKind, Integer boundWeeks,
                                     Integer duration, String note, Steps steps,
                                     EditorUiState.Page page, EditorUiState.Prompt prompt,
                                     boolean edit) {
        this.id = id; this.title = title; this.recurrence = recurrence;
        this.intervalDays = intervalDays; this.weekdayMask = weekdayMask;
        this.boundKind = boundKind; this.boundWeeks = boundWeeks; this.duration = duration;
        this.note = note; this.steps = steps; this.page = page; this.prompt = prompt;
        this.edit = edit;
    }

    EditorUiState state() {
        List<EditorStepState> stepStates = steps();
        String expanded = steps == Steps.DETAIL ? stepStates.get(0).id : null;
        int times = recurrence == Recurrence.ONCE ? 0 : TimeOfDay.MORNING.bit;
        EditorUiState state = EditorUiState.create().draft(title, TaskSlot.MORNING, duration,
                recurrence, intervalDays, weekdayMask, times, boundKind,
                boundKind == TaskBoundKind.FOR_WEEKS ? TODAY.plusWeeks(boundWeeks) : null,
                boundWeeks, null, recurrence == Recurrence.ONCE ? TODAY.plusDays(12) : null,
                note, stepStates, expanded, stepStates.size() + 1).withPage(page, false);
        if (title.isEmpty()) state = state.withFeedback(
                Collections.singleton(ValidationIssue.task(ValidationIssue.Field.TITLE)),
                EditorUiState.Prompt.NONE, "");
        if (edit) {
            Bundle bundle = state.toBundle(); bundle.putString("task_id", "edit-task");
            state = EditorUiState.fromBundle(bundle);
        }
        return prompt == EditorUiState.Prompt.NONE ? state : state.withFeedback(
                state.issues, prompt, state.storageError);
    }

    private List<EditorStepState> steps() {
        if (steps == Steps.NONE) return Collections.emptyList();
        List<EditorStepState> result = new ArrayList<>();
        String[] labels = steps == Steps.ROUTINE
                ? new String[]{"Haare waschen", "Tabletten", "Dehnen"}
                : new String[]{"Liegestütze", "Kniebeugen", "Planke"};
        for (int index = 0; index < labels.length; index++) {
            StepAmountKind kind = steps == Steps.DETAIL
                    ? index < 2 ? StepAmountKind.SETS_REPS : StepAmountKind.DURATION
                    : StepAmountKind.NONE;
            result.add(new EditorStepState("s" + index, labels[index], index == 1 ? 1 | 8 : 0,
                    kind == StepAmountKind.SETS_REPS
                            ? StepAmount.setsReps(3, 12 + index * 3)
                            : kind == StepAmountKind.DURATION ? StepAmount.duration(45)
                            : StepAmount.none(),
                    steps == Steps.DETAIL && index == 0 ? "23 kg, Sitz 5" : ""));
        }
        return result;
    }
}

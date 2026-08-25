package de.thonktank.autosecretary.presentation;

import static org.junit.Assert.assertEquals;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;

import de.thonktank.autosecretary.EditorStepState;
import de.thonktank.autosecretary.EditorUiState;
import de.thonktank.autosecretary.StepCadenceMode;
import de.thonktank.autosecretary.domain.model.Recurrence;
import de.thonktank.autosecretary.domain.model.StepAmount;
import de.thonktank.autosecretary.domain.model.TaskBoundKind;
import de.thonktank.autosecretary.domain.model.TaskSlot;
import de.thonktank.autosecretary.domain.model.TimeOfDay;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "de")
public final class TaskEditorTextFormatterRobolectricTest {
    private final Context context = ApplicationProvider.getApplicationContext();
    private final TaskEditorTextFormatter formatter = new TaskEditorTextFormatter(
            new AndroidUiTextProvider(context));

    @Test public void formatsSummaryFieldsWithoutViewOwnedCopy() {
        EditorStepState first = new EditorStepState("1", "Dehnen", 0,
                StepAmount.none(), "");
        EditorStepState second = new EditorStepState("2", "Laufen", 0,
                StepAmount.none(), "");
        EditorUiState state = EditorUiState.create().draft("Training", TaskSlot.MORNING, 30,
                Recurrence.WEEKDAYS, 1, 31, TimeOfDay.MORNING.bit,
                TaskBoundKind.FOR_WEEKS, LocalDate.of(2026, 9, 6), 2, null, null, "",
                Arrays.asList(first, second), null, 1);

        assertEquals("Mo–Fr morgens · etwa 30 Minuten · 2 Schritte",
                formatter.summaryLine(state));
        assertEquals("Mo · Di · Mi · Do · Fr", formatter.rhythm(state));
        assertEquals("30 Minuten", formatter.duration(state));
        assertEquals("für 2 Wochen · endet 06.09.", formatter.bound(state));
        assertEquals("Dehnen · Laufen", formatter.steps(state));
    }

    @Test public void formatsStepCadenceAndAmountMetadata() {
        EditorStepState weekdays = new EditorStepState("1", "Kniebeugen",
                StepCadenceMode.WEEKDAYS, 5, null, StepAmount.setsReps(3, 12), "");
        EditorStepState interval = new EditorStepState("2", "Wiegen",
                StepCadenceMode.INTERVAL, 0, 4, StepAmount.none(), "");

        assertEquals("Mo · Mi · 3 × 12 Wdh. · Pause: App-Standard",
                formatter.stepMeta(weekdays));
        assertEquals("alle 4 Tage", formatter.stepMeta(interval));
        assertEquals("—", formatter.steps(EditorUiState.create().draft("", TaskSlot.MORNING,
                null, Recurrence.ONCE, 1, 0, 0, TaskBoundKind.FOREVER, null, null,
                null, null, "", Collections.emptyList(), null, 1)));
    }
}

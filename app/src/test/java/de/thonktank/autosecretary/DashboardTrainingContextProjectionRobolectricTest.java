package de.thonktank.autosecretary;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import de.thonktank.autosecretary.domain.model.Dashboard;
import de.thonktank.autosecretary.domain.model.DashboardTask;
import de.thonktank.autosecretary.domain.model.Occurrence;
import de.thonktank.autosecretary.domain.model.OccurrenceState;
import de.thonktank.autosecretary.domain.model.OccurrenceStep;
import de.thonktank.autosecretary.domain.model.Recurrence;
import de.thonktank.autosecretary.domain.model.ResistanceLoad;
import de.thonktank.autosecretary.domain.model.StepAmount;
import de.thonktank.autosecretary.domain.model.Task;
import de.thonktank.autosecretary.domain.model.TaskBoundKind;
import de.thonktank.autosecretary.domain.model.TaskId;
import de.thonktank.autosecretary.domain.model.TaskSlot;
import de.thonktank.autosecretary.domain.model.TrainingAdjustment;
import de.thonktank.autosecretary.domain.model.TrainingAssistantState;
import de.thonktank.autosecretary.domain.model.TrainingContext;
import de.thonktank.autosecretary.domain.model.TrainingDecision;
import de.thonktank.autosecretary.domain.model.TrainingHistoryEntry;
import de.thonktank.autosecretary.domain.model.TrainingLoadRequest;
import de.thonktank.autosecretary.presentation.AndroidUiTextProvider;
import de.thonktank.autosecretary.presentation.DashboardUiMapper;
import de.thonktank.autosecretary.presentation.today.FocusStepUiModel;
import de.thonktank.autosecretary.presentation.today.TodayUiModel;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public final class DashboardTrainingContextProjectionRobolectricTest {
    @Test public void mapperProjectsOnlyTheOpenTodayQuestion() {
        LocalDate today = LocalDate.of(2026, 8, 31);
        TaskId taskId = TaskId.of("gym");
        Task task = Task.restore(taskId, "Gym", Recurrence.DAILY, 1, 0,
                false, "", false, false, today, null, null, today, 1, false,
                null, TaskBoundKind.FOREVER, null, null, null, null, "");
        Occurrence occurrence = new Occurrence("gym-today", taskId, today, TaskSlot.MORNING,
                OccurrenceState.OPEN, 0, null);
        OccurrenceStep step = de.thonktank.autosecretary.testing.StepTestFixtures.occurrence(
                "press-today", occurrence.id, 0, "Beinpresse", false,
                StepAmount.setsReps(3, 12), "50 kg", Collections.emptyList(),
                "press-template", "step:press-template");
        ResistanceLoad load = ResistanceLoad.numeric(ResistanceLoad.Mode.EXTERNAL,
                ResistanceLoad.Unit.KG, 50_000);
        TrainingAdjustment adjustment = new TrainingAdjustment("adjustment", "press-template",
                step.id, TrainingDecision.Reason.REPETITIONS_INCREASED,
                (StepAmount.SetsReps) StepAmount.setsReps(3, 11), load,
                (StepAmount.SetsReps) StepAmount.setsReps(3, 12), load, today,
                TrainingAdjustment.State.UNDONE, 1, TrainingDecision.RULE_VERSION);
        TrainingLoadRequest request = TrainingLoadRequest.open("request", "press-template",
                step.id, TrainingDecision.LoadDirection.PROGRESS, load, today, 2,
                TrainingDecision.RULE_VERSION);
        TrainingContext training = new TrainingContext("press-template",
                new TrainingAssistantState(TrainingAssistantState.Status.CALIBRATING,
                        2, 0, 0), request, adjustment,
                Arrays.asList(TrainingHistoryEntry.request(request),
                        TrainingHistoryEntry.adjustment(adjustment)), false);
        Dashboard dashboard = new Dashboard(0, Collections.singletonList(new DashboardTask(
                task, occurrence, Collections.singletonList(step), false,
                Collections.emptyMap(), 0, TaskSlot.MORNING)), Collections.emptyMap(),
                Collections.emptyList(), Collections.singletonMap("press-template", training));
        Context android = ApplicationProvider.getApplicationContext();

        TodayUiModel todayModel = new DashboardUiMapper(
                new AndroidUiTextProvider(android)).map(dashboard, today);
        FocusStepUiModel mapped = todayModel.focus.steps.get(0);

        assertNotNull(mapped.trainingPrompt);
        assertEquals("press-template", mapped.trainingPrompt.templateId);
        assertEquals(TrainingDecision.LoadDirection.PROGRESS,
                mapped.trainingPrompt.direction);
        assertEquals(load, mapped.trainingPrompt.currentLoad);
    }
}

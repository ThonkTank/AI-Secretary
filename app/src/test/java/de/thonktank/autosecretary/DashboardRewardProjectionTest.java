package de.thonktank.autosecretary;

import static org.junit.Assert.assertEquals;

import de.thonktank.autosecretary.domain.model.ComboProgress;
import de.thonktank.autosecretary.domain.model.Dashboard;
import de.thonktank.autosecretary.domain.model.DashboardTask;
import de.thonktank.autosecretary.domain.model.Occurrence;
import de.thonktank.autosecretary.domain.model.OccurrenceState;
import de.thonktank.autosecretary.domain.model.OccurrenceStep;
import de.thonktank.autosecretary.domain.model.Recurrence;
import de.thonktank.autosecretary.domain.model.Task;
import de.thonktank.autosecretary.domain.model.TaskBoundKind;
import de.thonktank.autosecretary.domain.model.TaskId;
import de.thonktank.autosecretary.domain.model.TaskSlot;
import de.thonktank.autosecretary.presentation.DashboardUiMapper;
import de.thonktank.autosecretary.presentation.UiTextProvider;
import de.thonktank.autosecretary.presentation.today.TodayUiModel;
import de.thonktank.autosecretary.presentation.today.FocusTaskUiModel;

import org.junit.Test;

import java.time.LocalDate;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class DashboardRewardProjectionTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 21);

    @Test public void stepDewAndRoutineVesselExposeTheirMultipliedResults() {
        TaskId taskId = TaskId.of("routine");
        Task task = Task.restore(taskId, "Routine", Recurrence.DAILY, 1, 0,
                false, "", false, false, TODAY, null, null, TODAY, 1, false,
                null, TaskBoundKind.FOREVER, null, null, null, null, "");
        Occurrence occurrence = new Occurrence("today", taskId, TODAY, TaskSlot.MORNING,
                OccurrenceState.OPEN, 1, null);
        OccurrenceStep done = new OccurrenceStep("done", occurrence.id, 0, "Fertig", true,
                de.thonktank.autosecretary.domain.model.StepAmount.none(), "",
                Collections.emptyList(), "template-done", "step:done");
        OccurrenceStep open = new OccurrenceStep("open", occurrence.id, 1, "Offen", false,
                de.thonktank.autosecretary.domain.model.StepAmount.none(), "",
                Collections.emptyList(), "template-open", "step:open");
        Map<String, Integer> earned = Collections.singletonMap(done.id, 15);
        DashboardTask item = new DashboardTask(task, occurrence,
                java.util.Arrays.asList(done, open), false, earned, 0, TaskSlot.MORNING);
        Map<String, ComboProgress> combos = new HashMap<>();
        combos.put(done.comboOwnerId, new ComboProgress(done.comboOwnerId, taskId,
                ComboProgress.Kind.STEP, 1, TODAY));
        combos.put(open.comboOwnerId, new ComboProgress(open.comboOwnerId, taskId,
                ComboProgress.Kind.STEP, 1, TODAY));
        combos.put(ComboProgress.taskOwner(taskId), new ComboProgress(
                ComboProgress.taskOwner(taskId), taskId, ComboProgress.Kind.TASK, 1, TODAY));
        Occurrence completedOccurrence = new Occurrence("completed", taskId, TODAY,
                TaskSlot.MORNING, OccurrenceState.COMPLETED, 0, TODAY);
        DashboardTask completed = new DashboardTask(task, completedOccurrence,
                Collections.emptyList(), true, Collections.emptyMap(), 23, TaskSlot.MORNING);
        DashboardTask sameOccurrenceDifferentObject = new DashboardTask(task, occurrence,
                java.util.Arrays.asList(done, open), false, earned, 0, TaskSlot.MORNING);

        TodayUiModel model = new DashboardUiMapper(new ResourceNames()).map(
                new Dashboard(0, java.util.Arrays.asList(
                        completed, item, sameOccurrenceDifferentObject), combos), TODAY);

        FocusTaskUiModel focus = model.focus;
        assertEquals(15, focus.reward.baseXp);
        assertEquals(23, focus.reward.resultXp);
        assertEquals(1.5d, focus.reward.multiplier, 0d);
        assertEquals(15, focus.steps.get(1).reward.resultXp);
        assertEquals("today", focus.occurrenceId());
        assertEquals(1, model.completedToday.size());
        assertEquals("completed", model.completedToday.get(0).occurrenceId);
        assertEquals(0, model.timeline.size());
    }

    private static final class ResourceNames implements UiTextProvider {
        @Override public String text(int resourceId, Object... arguments) {
            return String.valueOf(resourceId);
        }
    }
}

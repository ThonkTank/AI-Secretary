package de.thonktank.autosecretary.domain.steps;

import static org.junit.Assert.assertEquals;

import de.thonktank.autosecretary.domain.model.ComboProgress;
import de.thonktank.autosecretary.domain.model.Occurrence;
import de.thonktank.autosecretary.domain.model.OccurrenceStep;
import de.thonktank.autosecretary.domain.model.Recurrence;
import de.thonktank.autosecretary.domain.model.StepAmount;
import de.thonktank.autosecretary.domain.model.Task;
import de.thonktank.autosecretary.domain.model.TaskBoundKind;
import de.thonktank.autosecretary.domain.model.TaskId;
import de.thonktank.autosecretary.domain.model.TaskSlot;
import de.thonktank.autosecretary.domain.model.TaskStepId;
import de.thonktank.autosecretary.domain.model.TaskStepTemplate;
import de.thonktank.autosecretary.domain.repository.TransactionalRepository;

import org.junit.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class MoveTaskStepTest {
    @Test public void focusedDoubleExposesOnlyTheStepOrganizationPort() {
        StepDouble store = new StepDouble();
        Task source = task("source");
        Task target = task("target");
        store.tasks.put(source.id, source);
        store.tasks.put(target.id, target);
        store.templates.put("step", de.thonktank.autosecretary.testing.StepTestFixtures.template("step", source.id, 0,
                "Schritt", 0, StepAmount.none(), ""));

        StepTransferResult result = new MoveTaskStep(store).execute(new StepMoveRequest(
                TaskStepId.of("step"), target.id, Optional.empty()));

        assertEquals(StepTransferResult.DEFINITION_ONLY_FOR_FUTURE, result);
        assertEquals(target.id, store.templates.get("step").taskId);
    }

    private static Task task(String id) {
        return Task.restore(TaskId.of(id), id, Recurrence.DAILY, 1, 0,
                false, "", false, false, LocalDate.of(2026, 8, 21), null, null,
                LocalDate.of(2026, 8, 21), 1_024, false, null, TaskBoundKind.FOREVER,
                null, null, null,
                null, "");
    }

    private static final class StepDouble implements StepOrganizationRepository {
        final Map<TaskId, Task> tasks = new LinkedHashMap<>();
        final Map<String, TaskStepTemplate> templates = new LinkedHashMap<>();
        final Map<String, OccurrenceStep> snapshots = new LinkedHashMap<>();
        final Map<String, ComboProgress> combos = new LinkedHashMap<>();

        @Override public <T> T inTransaction(TransactionalRepository.Transaction<T> operation) {
            return operation.execute();
        }
        @Override public Task findTask(TaskId id) { return tasks.get(id); }
        @Override public TaskStepTemplate findTemplate(String id) { return templates.get(id); }
        @Override public List<TaskStepTemplate> templates(TaskId taskId) {
            List<TaskStepTemplate> result = new ArrayList<>();
            for (TaskStepTemplate value : templates.values())
                if (value.taskId.equals(taskId)) result.add(value);
            result.sort((left, right) -> Integer.compare(left.position, right.position));
            return result;
        }
        @Override public void insertTemplates(List<TaskStepTemplate> values) {
            for (TaskStepTemplate value : values) templates.put(value.id, value);
        }
        @Override public List<Occurrence> openOccurrences(TaskId taskId) {
            return Collections.emptyList();
        }
        @Override public Occurrence openOccurrence(TaskId taskId, TaskSlot slot) { return null; }
        @Override public List<OccurrenceStep> occurrenceSteps(String occurrenceId) {
            List<OccurrenceStep> result = new ArrayList<>();
            for (OccurrenceStep value : snapshots.values())
                if (value.occurrenceId.equals(occurrenceId)) result.add(value);
            return result;
        }
        @Override public void updateOccurrenceStep(OccurrenceStep step) {
            snapshots.put(step.id, step);
        }
        @Override public void assignRewardBookings(String stepId, String occurrenceId) { }
        @Override public ComboProgress combo(String ownerId) { return combos.get(ownerId); }
        @Override public void putCombo(ComboProgress combo) { combos.put(combo.ownerId, combo); }
    }
}

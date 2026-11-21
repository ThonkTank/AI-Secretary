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
import de.thonktank.autosecretary.domain.transaction.TransactionRunner;
import de.thonktank.autosecretary.domain.repository.CatalogRepository;
import de.thonktank.autosecretary.domain.repository.StepRepository;
import de.thonktank.autosecretary.domain.repository.TodayRepository;

import org.junit.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public final class MoveTaskStepTest {
    @Test public void focusedDoubleExposesOnlyTheStepOrganizationPort() {
        StepDouble store = new StepDouble();
        Task source = task("source");
        Task target = task("target");
        store.tasks.put(source.id, source);
        store.tasks.put(target.id, target);
        store.templates.put("step", de.thonktank.autosecretary.testing.StepTestFixtures.template("step", source.id, 0,
                "Schritt", 0, StepAmount.none(), ""));

        StepTransferResult result = new MoveTaskStep(store.catalog, store.steps, store.today,
                store.transactions).execute(new StepMoveRequest(
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

    private static final class StepDouble {
        final CatalogRepository catalog = adapter(CatalogRepository.class);
        final StepRepository steps = adapter(StepRepository.class);
        final TodayRepository today = adapter(TodayRepository.class);
        final TransactionRunner transactions = adapter(TransactionRunner.class);
        final Map<TaskId, Task> tasks = new LinkedHashMap<>();
        final Map<String, TaskStepTemplate> templates = new LinkedHashMap<>();
        final Map<String, OccurrenceStep> snapshots = new LinkedHashMap<>();
        final Map<String, ComboProgress> combos = new LinkedHashMap<>();

        private <T> T adapter(Class<T> port) {
            return port.cast(Proxy.newProxyInstance(port.getClassLoader(), new Class<?>[]{port},
                    (proxy, method, arguments) -> invoke(port, method, arguments)));
        }

        private Object invoke(Class<?> port, Method method, Object[] arguments) throws Throwable {
            try {
                return getClass().getMethod(method.getName(), method.getParameterTypes())
                        .invoke(this, arguments);
            } catch (NoSuchMethodException missing) {
                throw new AssertionError("Unexpected " + port.getSimpleName() + " call: "
                        + method.getName());
            } catch (InvocationTargetException failure) {
                throw failure.getCause();
            }
        }

        public <T> T inTransaction(TransactionRunner.Transaction<T> operation) {
            return operation.execute();
        }
        public Task findTask(TaskId id) { return tasks.get(id); }
        public TaskStepTemplate findTemplate(String id) { return templates.get(id); }
        public List<TaskStepTemplate> templates(TaskId taskId) {
            List<TaskStepTemplate> result = new ArrayList<>();
            for (TaskStepTemplate value : templates.values())
                if (value.taskId.equals(taskId)) result.add(value);
            result.sort((left, right) -> Integer.compare(left.position, right.position));
            return result;
        }
        public void insertTemplates(List<TaskStepTemplate> values) {
            for (TaskStepTemplate value : values) templates.put(value.id, value);
        }
        public List<Occurrence> openOccurrences(TaskId taskId) {
            return Collections.emptyList();
        }
        public Occurrence openOccurrence(TaskId taskId, TaskSlot slot) { return null; }
        public List<OccurrenceStep> occurrenceSteps(String occurrenceId) {
            List<OccurrenceStep> result = new ArrayList<>();
            for (OccurrenceStep value : snapshots.values())
                if (value.occurrenceId.equals(occurrenceId)) result.add(value);
            return result;
        }
        public void updateOccurrenceStep(OccurrenceStep step) {
            snapshots.put(step.id, step);
        }
        public void assignRewardBookings(String stepId, String occurrenceId) { }
        public ComboProgress combo(String ownerId) { return combos.get(ownerId); }
        public void putCombo(ComboProgress combo) { combos.put(combo.ownerId, combo); }
    }
}

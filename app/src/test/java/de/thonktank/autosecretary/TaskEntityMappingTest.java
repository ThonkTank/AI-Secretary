package de.thonktank.autosecretary;

import de.thonktank.autosecretary.data.local.TaskEntity;
import de.thonktank.autosecretary.data.local.TaskStepEntity;

import static org.junit.Assert.assertEquals;

import de.thonktank.autosecretary.data.local.TaskEntityMapper;
import de.thonktank.autosecretary.domain.model.Recurrence;
import de.thonktank.autosecretary.domain.model.Task;
import de.thonktank.autosecretary.domain.model.TaskId;
import de.thonktank.autosecretary.domain.model.StepAmount;
import de.thonktank.autosecretary.domain.model.TaskStepTemplate;

import org.junit.Test;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

public final class TaskEntityMappingTest {
    @Test public void entityMapperKeepsRoomCodesOutOfTheDomainModel() {
        TaskEntity entity = new TaskEntity("id", "Aufgabe", "DAILY", 1, 0,
                false, "", false, false, "2026-08-15", "2026-08-15", "", "",
                1_001_000L,
                true, null, "FOREVER", "", null, null, "", "");
        TaskEntityMapper mapper = new TaskEntityMapper();

        Task task = mapper.toDomain(entity);
        TaskEntity roundTrip = mapper.toEntity(task);

        assertEquals(Recurrence.DAILY, task.recurrence);
        assertEquals(LocalDate.of(2026, 8, 15), task.cadenceAnchorOn);
        assertEquals("DAILY", roundTrip.recurrence);
        assertEquals(1_001_000L, roundTrip.catalogOrder);
    }

    @Test public void everyTypedStepAmountRoundTripsThroughTheUnchangedRoomColumns() {
        TaskEntityMapper mapper = new TaskEntityMapper();
        List<StepAmount> amounts = Arrays.asList(StepAmount.none(),
                StepAmount.setsReps(3, 12), StepAmount.repetitions(20),
                StepAmount.duration(90));

        for (int index = 0; index < amounts.size(); index++) {
            StepAmount amount = amounts.get(index);
            TaskStepTemplate template = de.thonktank.autosecretary.testing.StepTestFixtures.template("step-" + index,
                    TaskId.of("task"), index, "Schritt " + index, 0, amount, "Notiz");

            TaskStepEntity stored = mapper.toEntity(template);
            TaskStepTemplate restored = mapper.toDomain(stored);

            assertEquals(amount, restored.prescription.amount);
            assertEquals(amount.kind().storageCode(), stored.amountKind);
            if (amount instanceof StepAmount.SetsReps) {
                assertEquals(Integer.valueOf(3), stored.plannedSets);
                assertEquals(Integer.valueOf(12), stored.plannedReps);
            } else if (amount instanceof StepAmount.Repetitions) {
                assertEquals(Integer.valueOf(20), stored.plannedReps);
            } else if (amount instanceof StepAmount.Duration) {
                assertEquals(Integer.valueOf(90), stored.plannedDurationSeconds);
            }
        }
    }
}

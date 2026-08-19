package de.thonktank.autosecretary;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import de.thonktank.autosecretary.data.local.TaskEntityMapper;
import de.thonktank.autosecretary.domain.model.Recurrence;
import de.thonktank.autosecretary.domain.model.Task;
import de.thonktank.autosecretary.domain.model.TaskId;
import de.thonktank.autosecretary.domain.model.TaskSlot;
import de.thonktank.autosecretary.domain.model.StepAmount;
import de.thonktank.autosecretary.domain.model.TaskStepTemplate;
import de.thonktank.autosecretary.domain.model.OccurrenceStep;
import de.thonktank.autosecretary.domain.model.RepetitionProgressCodec;

import org.junit.Test;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

public final class DomainModelTest {
    @Test public void taskSlotsUseOnlyStableStorageCodes() {
        assertEquals(TaskSlot.MORNING, TaskSlot.fromStorage("MORNING"));
        assertThrows(IllegalArgumentException.class, () -> TaskSlot.fromStorage("Morgen"));
        assertThrows(IllegalArgumentException.class, () -> TaskSlot.fromStorage("unbekannt"));
        assertEquals("MIDDAY", TaskSlot.MIDDAY.storageCode);
    }

    @Test public void invalidDomainCombinationsAreRejectedAtCreation() {
        assertThrows(IllegalArgumentException.class, () -> Task.create(
                TaskId.of("weekday"), "Routine", TaskSlot.MORNING, Recurrence.WEEKDAYS,
                1, 0, false, "", LocalDate.of(2026, 8, 15), 1_001_000L));
        assertThrows(IllegalArgumentException.class, () -> Task.create(
                TaskId.of("ongoing"), "Praktikum", TaskSlot.LATER, Recurrence.ONCE,
                1, 0, true, "", LocalDate.of(2026, 8, 15), 4_001_000L));
    }

    @Test public void entityMapperKeepsRoomCodesOutOfTheDomainModel() {
        TaskEntity entity = new TaskEntity("id", "Aufgabe", "MORNING", "DAILY", 1, 0,
                false, "", false, false, "2026-08-15", "", "", 1_001_000L,
                true, null, 1, "FOREVER", "", null, null, "", "");
        TaskEntityMapper mapper = new TaskEntityMapper();

        Task task = mapper.toDomain(entity);
        TaskEntity roundTrip = mapper.toEntity(task);

        assertEquals(TaskSlot.MORNING, task.slot);
        assertEquals(Recurrence.DAILY, task.recurrence);
        assertEquals("MORNING", roundTrip.slot);
        assertEquals("DAILY", roundTrip.recurrence);
        assertEquals(1_001_000L, roundTrip.displayOrder);
    }

    @Test public void everyTypedStepAmountRoundTripsThroughTheUnchangedRoomColumns() {
        TaskEntityMapper mapper = new TaskEntityMapper();
        List<StepAmount> amounts = Arrays.asList(StepAmount.none(),
                StepAmount.setsReps(3, 12), StepAmount.repetitions(20),
                StepAmount.duration(90));

        for (int index = 0; index < amounts.size(); index++) {
            StepAmount amount = amounts.get(index);
            TaskStepTemplate template = new TaskStepTemplate("step-" + index,
                    TaskId.of("task"), index, "Schritt " + index, 0, amount, "Notiz");

            TaskStepEntity stored = mapper.toEntity(template);
            TaskStepTemplate restored = mapper.toDomain(stored);

            assertEquals(amount, restored.amount);
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

    @Test public void repetitionProgressAcceptsZeroForSetsAndSingleValues() {
        OccurrenceStep sets = new OccurrenceStep("sets", "occ", 0, "Kniebeugen", false,
                StepAmount.setsReps(2, 12), "", java.util.Collections.emptyList());
        OccurrenceStep first = sets.recordRepetitionResult(0);
        OccurrenceStep second = first.recordRepetitionResult(999);
        OccurrenceStep single = new OccurrenceStep("single", "occ", 1, "Liegestütze", false,
                StepAmount.repetitions(12), "", java.util.Collections.emptyList())
                .recordRepetitionResult(0);

        assertEquals(java.util.Arrays.asList(0, 999),
                second.repetitionProgress.actualRepetitions);
        assertEquals(true, second.done);
        assertEquals(java.util.Collections.singletonList(0),
                single.repetitionProgress.actualRepetitions);
        assertEquals(true, single.done);
        assertEquals("0,999", RepetitionProgressCodec.encode(
                second.repetitionProgress.actualRepetitions));
        assertEquals(second.repetitionProgress.actualRepetitions,
                RepetitionProgressCodec.decode("0,999"));
        assertThrows(IllegalArgumentException.class, () -> first.recordRepetitionResult(1000));
    }

    @Test public void legacyRepetitionValuesAboveTheNewInputLimitRemainReadable() {
        OccurrenceStep legacy = new OccurrenceStep("legacy", "occ", 0, "Beinpresse", false,
                StepAmount.setsReps(2, 12), "", Arrays.asList(1_200));

        assertEquals(Arrays.asList(1_200), legacy.repetitionProgress.actualRepetitions);
        assertEquals(Arrays.asList(1_200), RepetitionProgressCodec.decode("1200"));
        assertEquals("1200", RepetitionProgressCodec.encode(
                legacy.repetitionProgress.actualRepetitions));
    }
}

package de.thonktank.autosecretary;

import de.thonktank.autosecretary.data.local.TaskEntity;
import de.thonktank.autosecretary.data.local.TaskStepEntity;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import de.thonktank.autosecretary.data.local.TaskEntityMapper;
import de.thonktank.autosecretary.domain.model.Recurrence;
import de.thonktank.autosecretary.domain.model.Task;
import de.thonktank.autosecretary.domain.model.TaskBoundKind;
import de.thonktank.autosecretary.domain.model.TaskId;
import de.thonktank.autosecretary.domain.model.TaskSchedule;
import de.thonktank.autosecretary.domain.model.TaskScheduleEntry;
import de.thonktank.autosecretary.domain.model.TaskSlot;
import de.thonktank.autosecretary.domain.model.StepAmount;
import de.thonktank.autosecretary.domain.model.TaskStepTemplate;
import de.thonktank.autosecretary.domain.model.OccurrenceStep;

import org.junit.Test;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

public final class DomainModelTest {
    @Test public void taskScheduleRejectsDuplicateIdsAndTaskSlotPlacements() {
        TaskId first = TaskId.of("first");
        TaskId second = TaskId.of("second");
        TaskScheduleEntry morning = new TaskScheduleEntry(
                "placement", first, TaskSlot.MORNING, 1_024L);

        assertThrows(IllegalArgumentException.class, () -> new TaskSchedule(Arrays.asList(
                morning,
                new TaskScheduleEntry("placement", second, TaskSlot.MIDDAY, 1_024L))));
        assertThrows(IllegalArgumentException.class, () -> new TaskSchedule(Arrays.asList(
                morning,
                new TaskScheduleEntry("other", first, TaskSlot.MORNING, 2_048L))));
    }

    @Test public void taskScheduleOwnsPrimaryPlacementAndDeterministicMoveOrdering() {
        TaskId first = TaskId.of("first");
        TaskId second = TaskId.of("second");
        TaskSchedule schedule = new TaskSchedule(Arrays.asList(
                new TaskScheduleEntry("first-evening", first, TaskSlot.EVENING, 9_999L),
                new TaskScheduleEntry("second-morning", second, TaskSlot.MORNING, 9_999L),
                new TaskScheduleEntry("first-morning", first, TaskSlot.MORNING, 8_888L)));

        assertEquals("first-morning", schedule.primary(first).id);
        TaskSchedule.Mutation moved = schedule.move(
                "first-evening", TaskSlot.EVENING, null);
        assertEquals(Arrays.asList(1_024L), moved.schedule.placements(first).stream()
                .filter(value -> value.slot == TaskSlot.EVENING)
                .map(value -> value.displayOrder).collect(java.util.stream.Collectors.toList()));

        TaskSchedule.Mutation reordered = schedule.move(
                "second-morning", TaskSlot.MORNING, "first-morning");
        List<TaskScheduleEntry> morning = new java.util.ArrayList<>();
        for (TaskScheduleEntry entry : reordered.schedule.entries())
            if (entry.slot == TaskSlot.MORNING) morning.add(entry);
        assertEquals(Arrays.asList("second-morning", "first-morning"), morning.stream()
                .map(value -> value.id).collect(java.util.stream.Collectors.toList()));
        assertEquals(Arrays.asList(1_024L, 2_048L), morning.stream()
                .map(value -> value.displayOrder).collect(java.util.stream.Collectors.toList()));
    }

    @Test public void taskSlotsUseOnlyStableStorageCodes() {
        assertEquals(TaskSlot.MORNING, TaskSlot.fromStorage("MORNING"));
        assertThrows(IllegalArgumentException.class, () -> TaskSlot.fromStorage("Morgen"));
        assertThrows(IllegalArgumentException.class, () -> TaskSlot.fromStorage("unbekannt"));
        assertEquals("MIDDAY", TaskSlot.MIDDAY.storageCode);
    }

    @Test public void invalidDomainCombinationsAreRejectedAtCreation() {
        assertThrows(IllegalArgumentException.class, () -> Task.restore(
                TaskId.of("weekday"), "Routine", Recurrence.WEEKDAYS, 1, 0, false, "",
                false, false, LocalDate.of(2026, 8, 15), null, null,
                LocalDate.of(2026, 8, 15), 1_001_000L, false,
                null, TaskBoundKind.FOREVER, null, null, null, null, ""));
        assertThrows(IllegalArgumentException.class, () -> Task.restore(
                TaskId.of("ongoing"), "Praktikum", Recurrence.ONCE, 1, 0, true, "",
                false, false, LocalDate.of(2026, 8, 15), null, null,
                LocalDate.of(2026, 8, 15), 4_001_000L, false,
                null, TaskBoundKind.FOREVER, null, null, null, null, ""));
    }

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
        OccurrenceStep sets = de.thonktank.autosecretary.testing.StepTestFixtures.occurrence("sets", "occ", 0, "Kniebeugen", false,
                StepAmount.setsReps(2, 12), "", java.util.Collections.emptyList());
        OccurrenceStep first = sets.recordRepetitionResult(0);
        OccurrenceStep second = first.recordRepetitionResult(999);
        OccurrenceStep single = de.thonktank.autosecretary.testing.StepTestFixtures.occurrence("single", "occ", 1, "Liegestütze", false,
                StepAmount.repetitions(12), "", java.util.Collections.emptyList())
                .recordRepetitionResult(0);

        assertEquals(java.util.Arrays.asList(0, 999),
                second.repetitionProgress.actualRepetitions);
        assertEquals(true, second.done);
        assertEquals(java.util.Collections.singletonList(0),
                single.repetitionProgress.actualRepetitions);
        assertEquals(true, single.done);
        assertThrows(IllegalArgumentException.class, () -> first.recordRepetitionResult(1000));
    }

    @Test public void legacyRepetitionValuesAboveTheNewInputLimitRemainReadable() {
        OccurrenceStep legacy = de.thonktank.autosecretary.testing.StepTestFixtures.occurrence("legacy", "occ", 0, "Beinpresse", false,
                StepAmount.setsReps(2, 12), "", Arrays.asList(1_200));

        assertEquals(Arrays.asList(1_200), legacy.repetitionProgress.actualRepetitions);
    }
}

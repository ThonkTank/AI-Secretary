package de.thonktank.autosecretary;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import de.thonktank.autosecretary.domain.model.ComboDecayTrigger;
import de.thonktank.autosecretary.domain.model.ComboObligation;
import de.thonktank.autosecretary.domain.model.ComboPolicy;
import de.thonktank.autosecretary.domain.model.ComboProgress;
import de.thonktank.autosecretary.domain.model.Occurrence;
import de.thonktank.autosecretary.domain.model.OccurrenceStep;
import de.thonktank.autosecretary.domain.model.Recurrence;
import de.thonktank.autosecretary.domain.model.TaskDefinition;
import de.thonktank.autosecretary.domain.model.TaskSlot;
import de.thonktank.autosecretary.domain.model.TaskBoundKind;
import de.thonktank.autosecretary.domain.model.TimeOfDay;
import de.thonktank.autosecretary.domain.model.MissedOccurrenceMode;
import de.thonktank.autosecretary.domain.repository.ComboPolicySource;
import de.thonktank.autosecretary.domain.usecase.ApplyComboDecay;
import de.thonktank.autosecretary.domain.usecase.CreateTask;
import de.thonktank.autosecretary.domain.usecase.CompleteOccurrence;
import de.thonktank.autosecretary.domain.usecase.MaterializeDueOccurrences;
import de.thonktank.autosecretary.domain.usecase.ToggleStep;
import de.thonktank.autosecretary.domain.usecase.LoadDashboard;
import de.thonktank.autosecretary.testing.InMemoryExecutionRepository;

import org.junit.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collections;

public final class ScheduleAwareComboDecayTest {
    private static final LocalDate MONDAY = LocalDate.of(2026, 8, 24);

    @Test public void plannedOffDayDoesNotDecayAResolvedIntervalStep() {
        Fixture fixture = new Fixture(ComboPolicy.defaults());
        OccurrenceStep step = fixture.createIntervalTaskAndMaterialize();
        new ToggleStep(fixture.repository, fixture.clock).execute(step.id);

        fixture.clock.date = MONDAY.plusDays(1);
        fixture.materialize.execute();
        fixture.decay.execute();

        assertEquals(2, fixture.repository.combo(step.comboOwnerId).points);
        assertEquals(ComboObligation.State.RESOLVED,
                fixture.repository.comboObligations().stream()
                        .filter(value -> value.ownerId.equals(step.comboOwnerId))
                        .findFirst().orElseThrow().state);
    }

    @Test public void carryForwardDoesNotCreateAnotherGenuineObligation() {
        Fixture fixture = new Fixture(ComboPolicy.defaults());
        OccurrenceStep step = fixture.createIntervalTaskAndMaterialize();
        fixture.repository.putCombo(new ComboProgress(step.comboOwnerId,
                fixture.repository.findOccurrence(step.occurrenceId).taskId,
                ComboProgress.Kind.STEP, 10, MONDAY));

        fixture.clock.date = MONDAY.plusDays(1);
        fixture.materialize.execute();
        fixture.decay.execute();

        assertEquals(1, fixture.repository.comboObligations().stream()
                .filter(value -> value.ownerId.equals(step.comboOwnerId)).count());
        assertEquals(9, fixture.repository.combo(step.comboOwnerId).points);
    }

    @Test public void missedOccurrenceModeBooksOnceInsteadOfEveryOverdueDay() {
        Fixture fixture = new Fixture(new ComboPolicy(2, 1,
                ComboDecayTrigger.MISSED_OCCURRENCE));
        OccurrenceStep step = fixture.createIntervalTaskAndMaterialize();
        fixture.repository.putCombo(new ComboProgress(step.comboOwnerId,
                fixture.repository.findOccurrence(step.occurrenceId).taskId,
                ComboProgress.Kind.STEP, 10, MONDAY));

        fixture.clock.date = MONDAY.plusDays(1);
        fixture.materialize.execute();
        fixture.decay.execute();
        fixture.clock.date = MONDAY.plusDays(2);
        fixture.materialize.execute();
        fixture.decay.execute();

        assertEquals(9, fixture.repository.combo(step.comboOwnerId).points);
    }

    @Test public void nextScheduledModeWaitsUntilTheNextGenuineDueDate() {
        Fixture fixture = new Fixture(new ComboPolicy(2, 1,
                ComboDecayTrigger.NEXT_SCHEDULED_OCCURRENCE));
        OccurrenceStep step = fixture.createIntervalTaskAndMaterialize();
        fixture.repository.putCombo(new ComboProgress(step.comboOwnerId,
                fixture.repository.findOccurrence(step.occurrenceId).taskId,
                ComboProgress.Kind.STEP, 10, MONDAY));

        fixture.clock.date = MONDAY.plusDays(1);
        fixture.materialize.execute();
        fixture.decay.execute();
        assertEquals(10, fixture.repository.combo(step.comboOwnerId).points);

        fixture.clock.date = MONDAY.plusDays(2);
        fixture.materialize.execute();
        fixture.decay.execute();
        assertEquals(9, fixture.repository.combo(step.comboOwnerId).points);
    }

    @Test public void decayLedgerEntryDoesNotMasqueradeAsACompletionReward() {
        Fixture fixture = new Fixture(ComboPolicy.defaults());
        new CreateTask(fixture.repository, fixture.repository, fixture.clock, fixture.ids).execute(
                TaskDefinition.basic("Einmalig", TaskSlot.MORNING, Recurrence.ONCE,
                        1, 0, Collections.emptyList()));
        fixture.materialize.execute();
        Occurrence first = fixture.repository.openOccurrences().get(0);
        fixture.repository.putCombo(new ComboProgress(ComboProgress.taskOwner(first.taskId),
                first.taskId, ComboProgress.Kind.TASK, 10, MONDAY));

        fixture.clock.date = MONDAY.plusDays(1);
        fixture.materialize.execute();
        fixture.decay.execute();
        Occurrence carried = fixture.repository.openOccurrences().get(0);

        assertTrue(new CompleteOccurrence(fixture.repository, fixture.clock)
                .execute(carried.id).xp > 0);
        assertEquals(11, fixture.repository.combo(ComboProgress.taskOwner(first.taskId)).points);
    }

    @Test public void accumulateKeepsEveryDueDateButDashboardAdvancesOneAtATime() {
        Fixture fixture = new Fixture(ComboPolicy.defaults());
        new CreateTask(fixture.repository, fixture.repository, fixture.clock, fixture.ids).execute(
                new TaskDefinition("Gym", null, TaskSlot.EVENING, Recurrence.DAILY,
                        1, 0, TimeOfDay.EVENING.bit, TaskBoundKind.FOREVER,
                        null, null, null, null, "", MissedOccurrenceMode.ACCUMULATE,
                        Collections.emptyList()));
        fixture.materialize.execute();
        fixture.clock.date = MONDAY.plusDays(2);
        fixture.materialize.execute();

        assertEquals(3, fixture.repository.openOccurrences().size());
        de.thonktank.autosecretary.domain.model.Dashboard first =
                new LoadDashboard(fixture.repository).execute(fixture.clock.today());
        assertEquals(1, first.tasks.size());
        assertEquals(MONDAY, first.tasks.get(0).occurrence.scheduledOn);
        assertEquals(2, first.tasks.get(0).backlogCount);

        new CompleteOccurrence(fixture.repository, fixture.clock)
                .execute(first.tasks.get(0).occurrence.id);
        de.thonktank.autosecretary.domain.model.Dashboard next =
                new LoadDashboard(fixture.repository).execute(fixture.clock.today());
        assertEquals(MONDAY.plusDays(1), next.tasks.stream().filter(value -> !value.done)
                .findFirst().orElseThrow().occurrence.scheduledOn);
        assertEquals(1, next.tasks.stream().filter(value -> !value.done)
                .findFirst().orElseThrow().backlogCount);
    }

    private static final class Fixture {
        final InMemoryExecutionRepository repository = new InMemoryExecutionRepository();
        final MutableClock clock = new MutableClock(MONDAY);
        final IncrementingIds ids = new IncrementingIds();
        final MaterializeDueOccurrences materialize =
                new MaterializeDueOccurrences(repository, clock, ids);
        final ApplyComboDecay decay;

        Fixture(ComboPolicy policy) {
            ComboPolicySource source = () -> policy;
            decay = new ApplyComboDecay(repository, clock, source);
        }

        OccurrenceStep createIntervalTaskAndMaterialize() {
            new CreateTask(repository, repository, clock, ids).execute(
                    TaskDefinition.basic("Peeling", TaskSlot.EVENING, Recurrence.INTERVAL,
                            2, 0, Collections.singletonList("Anwenden")));
            materialize.execute();
            Occurrence occurrence = repository.openOccurrences().get(0);
            return repository.occurrenceSteps(occurrence.id).get(0);
        }
    }

    private static final class MutableClock implements Clock {
        LocalDate date;
        MutableClock(LocalDate date) { this.date = date; }
        @Override public LocalDate today() { return date; }
        @Override public LocalTime time() { return LocalTime.NOON; }
    }

    private static final class IncrementingIds
            implements de.thonktank.autosecretary.domain.usecase.IdGenerator {
        int next;
        @Override public String nextId() { return "id-" + ++next; }
    }
}

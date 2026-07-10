package com.autosecretary.features.task;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.autosecretary.database.AppDatabase;
import com.autosecretary.features.budget.data.repository.BudgetRoomRepository;
import com.autosecretary.features.task.application.CheckOffTaskUseCase;
import com.autosecretary.features.task.application.internal.completion.TaskTransitionRecorder;
import com.autosecretary.features.task.application.listmodel.TaskListItem;
import com.autosecretary.features.task.application.listmodel.TaskListItemMapper;
import com.autosecretary.features.task.data.TaskDao;
import com.autosecretary.features.task.data.TaskTransitionStat;
import com.autosecretary.features.task.domain.TaskLifecycleManager;
import com.autosecretary.features.task.domain.model.Task;
import com.autosecretary.features.task.domain.model.TaskPrerequisite;
import com.autosecretary.features.task.domain.model.TaskSlot;
import com.autosecretary.shared.Period;
import com.autosecretary.testing.AutoSecretaryRobolectricTest;
import com.autosecretary.testing.BudgetFixtures;
import com.autosecretary.testing.CallbackProbe;
import com.autosecretary.testing.TaskCheckOffFixture;
import com.autosecretary.testing.TaskFixtures;
import com.autosecretary.testing.TestDatabases;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Characterizes four behavior invariants documented in CLAUDE.md that previously had no test:
 * carryover-debt accumulation, task→budget expense booking, adaptive prerequisite-gap learning,
 * and A→B transition recording for scheduler follow-up learning.
 */
public final class TaskInvariantsCharacterizationTest extends AutoSecretaryRobolectricTest {
    private AppDatabase db;
    private TaskDao taskDao;

    @Before
    public void setUp() {
        db = TestDatabases.inMemory();
        taskDao = db.taskDao();
    }

    @After
    public void tearDown() {
        db.close();
    }

    /**
     * Invariant: with {@code completeFirst=true}, unmet reps from an expired period accumulate
     * into {@code carryoverDebt}, and that counter survives a Room round-trip.
     */
    @Test
    public void carryoverDebtAccumulatesFromMissedPeriodWhenCompleteFirst() {
        LocalDate today = LocalDate.now();
        Task task = TaskFixtures.taskWithSlot("Carryover", today);
        task.core.repetition.reps = 2;
        task.core.repetition.periodUnit = Period.DAY;
        task.core.repetition.periodStart = today.minusDays(1);
        task.core.repetition.periodCompletions = 0;
        task.core.repetition.completeFirst = true;
        task.core.repetition.carryoverDebt = 0;
        taskDao.write(task);

        Task reloaded = taskDao.read(task.core.id);
        new TaskLifecycleManager().advancePeriods(reloaded, today);
        assertEquals(2, reloaded.core.repetition.carryoverDebt);

        taskDao.write(reloaded);
        Task persisted = taskDao.read(task.core.id);
        assertEquals(2, persisted.core.repetition.carryoverDebt);
    }

    /**
     * Invariant: completing a task with {@code budgetRequiredCents > 0} books an expense against
     * the linked account, deducting the stored balance.
     */
    @Test
    public void completingTaskWithBudgetRequirementBooksExpense() {
        BudgetRoomRepository budget = BudgetFixtures.budgetRepository(db);
        budget.insertAccount(BudgetFixtures.account("Giro"));
        String accountId = budget.findDefaultActiveAccountId();
        assertNotNull(accountId);
        long balanceBefore = budget.getCurrentBalanceCents(accountId);

        LocalDate today = LocalDate.now();
        Task task = TaskFixtures.taskWithSlot("Einkauf", today);
        task.core.budgetRequiredCents = 500;
        task.core.budgetAccountId = accountId;
        taskDao.write(task);

        completeTask(TaskCheckOffFixture.create(db), task.core.id);

        assertEquals(balanceBefore - 500, budget.getCurrentBalanceCents(accountId));
        assertEquals(1, budget.findTransactionsForAccount(accountId).size());
    }

    /**
     * Invariant: for an adaptive task, the prerequisite {@code minGapMinutes} is nudged toward the
     * observed gap via EMA (alpha=0.2), rounded to 5 minutes. Gap 60→observed 30 yields 55.
     */
    @Test
    public void adaptivePrerequisiteGapMovesTowardObservedGap() {
        TaskPrerequisite prereq = new TaskPrerequisite();
        prereq.minGapMinutes = 60;

        TaskSlot prereqSlot = new TaskSlot();
        prereqSlot.realEnd = LocalTime.of(9, 0);
        TaskSlot dependentSlot = new TaskSlot();
        dependentSlot.realStart = LocalTime.of(9, 30);

        new TaskLifecycleManager().adaptPrerequisiteGap(prereq, prereqSlot, dependentSlot);

        assertEquals(55, prereq.minGapMinutes);
    }

    /**
     * Invariant: starting task B after task A already acted earlier the same day records an A→B
     * transition stat (the learning input the scorer later uses for follow-up boosts).
     */
    @Test
    public void transitionFromPriorTaskIsRecordedOnStart() {
        LocalDate today = LocalDate.now();

        Task a = TaskFixtures.taskWithSlot("A", today);
        TaskSlot aSlot = a.slots.get(0);
        aSlot.start = LocalTime.of(9, 0);
        aSlot.realStart = LocalTime.of(9, 0);
        aSlot.realEnd = LocalTime.of(9, 30);
        aSlot.completed = true;
        taskDao.write(a);

        Task b = TaskFixtures.taskWithSlot("B", today);
        TaskSlot bSlot = b.slots.get(0);
        bSlot.start = LocalTime.of(10, 0);
        bSlot.realStart = LocalTime.of(10, 0);
        taskDao.write(b);

        new TaskTransitionRecorder(taskDao, db.taskTransitionStatDao()).recordStarted(bSlot);

        List<TaskTransitionStat> stats = db.taskTransitionStatDao().readAll();
        assertEquals(1, stats.size());
        assertEquals(a.core.id, stats.get(0).fromTaskId);
        assertEquals(b.core.id, stats.get(0).toTaskId);
    }

    private void completeTask(CheckOffTaskUseCase useCase, String taskId) {
        TaskListItem item = currentItem(taskId);
        CallbackProbe<Void> startProbe = new CallbackProbe<>();
        useCase.execute(item, startProbe.runnable());
        startProbe.assertCalled();

        Task afterStart = taskDao.read(taskId);
        TaskSlot slot = afterStart.findSlot(item.slotId);
        slot.realStart = LocalTime.now().minusMinutes(10);
        taskDao.writeSlot(slot);

        CallbackProbe<Void> doneProbe = new CallbackProbe<>();
        useCase.execute(currentItem(taskId), doneProbe.runnable());
        doneProbe.assertCalled();
    }

    private TaskListItem currentItem(String taskId) {
        return new TaskListItemMapper().map(List.of(taskDao.read(taskId))).get(0);
    }
}

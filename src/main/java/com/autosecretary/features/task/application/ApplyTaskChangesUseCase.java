package com.autosecretary.features.task.application;

import androidx.room.RoomDatabase;

import com.autosecretary.features.task.data.TaskCategoryDao;
import com.autosecretary.features.task.data.TaskCategoryWindowDao;
import com.autosecretary.features.task.data.TaskDao;
import com.autosecretary.features.task.domain.assistant.ChangeOp;
import com.autosecretary.features.task.domain.assistant.TaskAssistantProposal;
import com.autosecretary.features.task.domain.assistant.TaskAssistantProposal.CategoryChange;
import com.autosecretary.features.task.domain.assistant.TaskAssistantProposal.TaskChange;
import com.autosecretary.features.task.domain.model.Task;
import com.autosecretary.features.task.domain.model.TaskCategory;
import com.autosecretary.features.task.domain.model.TaskCategoryWindow;
import com.autosecretary.features.task.domain.model.TaskCore;
import com.autosecretary.features.task.domain.model.TaskPrefSlot;
import com.autosecretary.features.task.domain.model.TaskPrefSlotFactory;
import com.autosecretary.shared.Period;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

/**
 * Applies an approved {@link TaskAssistantProposal} atomically and records an undo snapshot.
 *
 * <p>Before mutating anything it validates that every UPDATE/DELETE id exists and every created
 * category has a name (unknown-id proposals are rejected without any DB write). It then captures the
 * prior state (updated/deleted tasks and categories, plus tasks whose category is being deleted, and
 * the deleted categories' windows), applies all changes inside a single transaction, and — only on
 * success — pushes one {@link TaskChangeUndoHolder.Snapshot}.
 *
 * <p>Note: on UPDATE, a null field means "unchanged" (the assistant omits unchanged fields), so a
 * task's category cannot be cleared via an UPDATE; use the editor for that.
 */
public class ApplyTaskChangesUseCase {

    private final RoomDatabase database;
    private final TaskDao taskDao;
    private final TaskCategoryDao categoryDao;
    private final TaskCategoryWindowDao windowDao;
    private final TaskChangeUndoHolder undoHolder;
    private final ExecutorService dbExecutor;
    private final Executor callbackDispatcher;

    public ApplyTaskChangesUseCase(RoomDatabase database,
                                   TaskDao taskDao,
                                   TaskCategoryDao categoryDao,
                                   TaskCategoryWindowDao windowDao,
                                   TaskChangeUndoHolder undoHolder,
                                   ExecutorService dbExecutor,
                                   Executor callbackDispatcher) {
        this.database = database;
        this.taskDao = taskDao;
        this.categoryDao = categoryDao;
        this.windowDao = windowDao;
        this.undoHolder = undoHolder;
        this.dbExecutor = dbExecutor;
        this.callbackDispatcher = callbackDispatcher;
    }

    public void apply(TaskAssistantProposal proposal, Runnable onApplied, Consumer<String> onError) {
        dbExecutor.execute(() -> {
            try {
                applyOnDbThread(proposal);
                callbackDispatcher.execute(onApplied);
            } catch (ValidationException e) {
                callbackDispatcher.execute(() -> onError.accept(e.getMessage()));
            } catch (RuntimeException e) {
                callbackDispatcher.execute(() -> onError.accept(
                        "Änderungen konnten nicht angewendet werden: " + e.getMessage()));
            }
        });
    }

    private void applyOnDbThread(TaskAssistantProposal proposal) {
        Map<String, Task> tasksById = new LinkedHashMap<>();
        for (Task task : taskDao.readAll()) {
            tasksById.put(task.core.id, task);
        }
        Set<String> categoryIds = new HashSet<>();
        for (TaskCategory category : categoryDao.readAll()) {
            categoryIds.add(category.id);
        }

        validate(proposal, tasksById.keySet(), categoryIds);

        // Pre-build created entities so their ids are known before the transaction (for the undo snapshot).
        List<TaskCategory> createdCategories = new ArrayList<>();
        List<String> createdCategoryIds = new ArrayList<>();
        for (CategoryChange change : proposal.categoryChanges()) {
            if (change.op() == ChangeOp.CREATE) {
                TaskCategory category = new TaskCategory();
                category.name = change.name();
                if (change.icon() != null) category.icon = change.icon();
                if (change.colorHex() != null) category.colorHex = change.colorHex();
                createdCategories.add(category);
                createdCategoryIds.add(category.id);
            }
        }
        List<Task> createdTasks = new ArrayList<>();
        List<String> createdTaskIds = new ArrayList<>();
        for (TaskChange change : proposal.taskChanges()) {
            if (change.op() == ChangeOp.CREATE) {
                Task task = new Task();
                applyEditableFields(task, change, true);
                createdTasks.add(task);
                createdTaskIds.add(task.core.id);
            }
        }

        TaskChangeUndoHolder.Snapshot snapshot =
                captureUndo(proposal, tasksById, createdTaskIds, createdCategoryIds);

        database.runInTransaction(() -> {
            applyCategoryChanges(proposal, createdCategories);
            applyTaskChanges(proposal, createdTasks);
        });

        undoHolder.push(snapshot);
    }

    private void validate(TaskAssistantProposal proposal, Set<String> taskIds, Set<String> categoryIds) {
        for (CategoryChange change : proposal.categoryChanges()) {
            switch (change.op()) {
                case CREATE -> {
                    if (change.name() == null) {
                        throw new ValidationException("Vorschlag enthält eine neue Kategorie ohne Namen.");
                    }
                }
                case UPDATE, DELETE -> {
                    if (change.id() == null || !categoryIds.contains(change.id())) {
                        throw new ValidationException("Unbekannte Kategorie-id im Vorschlag: " + change.id());
                    }
                }
            }
        }
        for (TaskChange change : proposal.taskChanges()) {
            switch (change.op()) {
                case CREATE -> {
                    if (change.title() == null) {
                        throw new ValidationException("Vorschlag enthält eine neue Task ohne Titel.");
                    }
                }
                case UPDATE, DELETE -> {
                    if (change.id() == null || !taskIds.contains(change.id())) {
                        throw new ValidationException("Unbekannte Task-id im Vorschlag: " + change.id());
                    }
                }
            }
        }
    }

    private TaskChangeUndoHolder.Snapshot captureUndo(TaskAssistantProposal proposal,
                                                      Map<String, Task> tasksById,
                                                      List<String> createdTaskIds,
                                                      List<String> createdCategoryIds) {
        Set<String> deletedCategoryIds = new HashSet<>();
        for (CategoryChange change : proposal.categoryChanges()) {
            if (change.op() == ChangeOp.DELETE) {
                deletedCategoryIds.add(change.id());
            }
        }

        // Tasks whose prior state we must keep: those directly updated/deleted, plus those whose
        // category is being deleted (their categoryId is cascade-cleared on apply).
        Set<String> affectedTaskIds = new HashSet<>();
        for (TaskChange change : proposal.taskChanges()) {
            if (change.op() != ChangeOp.CREATE) {
                affectedTaskIds.add(change.id());
            }
        }
        for (Task task : tasksById.values()) {
            if (task.core.categoryId != null && deletedCategoryIds.contains(task.core.categoryId)) {
                affectedTaskIds.add(task.core.id);
            }
        }

        List<Task> priorTasks = new ArrayList<>();
        for (String id : affectedTaskIds) {
            Task prior = taskDao.read(id); // fresh copy kept untouched for undo
            if (prior != null) {
                priorTasks.add(prior);
            }
        }

        List<TaskCategory> priorCategories = new ArrayList<>();
        for (CategoryChange change : proposal.categoryChanges()) {
            if (change.op() != ChangeOp.CREATE) {
                TaskCategory prior = categoryDao.read(change.id());
                if (prior != null) {
                    priorCategories.add(prior);
                }
            }
        }

        List<TaskCategoryWindow> priorWindows = new ArrayList<>();
        if (!deletedCategoryIds.isEmpty()) {
            for (TaskCategoryWindow window : windowDao.readAll()) {
                if (deletedCategoryIds.contains(window.categoryId)) {
                    priorWindows.add(window);
                }
            }
        }

        return new TaskChangeUndoHolder.Snapshot(
                priorTasks, new ArrayList<>(createdTaskIds),
                priorCategories, new ArrayList<>(createdCategoryIds), priorWindows);
    }

    private void applyCategoryChanges(TaskAssistantProposal proposal, List<TaskCategory> createdCategories) {
        int createdIndex = 0;
        for (CategoryChange change : proposal.categoryChanges()) {
            switch (change.op()) {
                case CREATE -> categoryDao.write(createdCategories.get(createdIndex++));
                case UPDATE -> {
                    TaskCategory category = categoryDao.read(change.id());
                    if (category == null) break;
                    if (change.name() != null) category.name = change.name();
                    if (change.icon() != null) category.icon = change.icon();
                    if (change.colorHex() != null) category.colorHex = change.colorHex();
                    categoryDao.write(category);
                }
                case DELETE -> {
                    categoryDao.clearCategoryFromTasks(change.id());
                    windowDao.deleteByCategory(change.id());
                    categoryDao.delete(change.id());
                }
            }
        }
    }

    private void applyTaskChanges(TaskAssistantProposal proposal, List<Task> createdTasks) {
        int createdIndex = 0;
        for (TaskChange change : proposal.taskChanges()) {
            switch (change.op()) {
                case CREATE -> taskDao.write(createdTasks.get(createdIndex++));
                case UPDATE -> {
                    Task task = taskDao.read(change.id());
                    if (task == null) break;
                    applyEditableFields(task, change, false);
                    taskDao.write(task);
                }
                case DELETE -> taskDao.deleteTaskGraph(change.id());
            }
        }
    }

    /**
     * Writes every non-null field of {@code change} onto {@code task}, mirroring the manual editor's
     * save mapping ({@code TaskEditStateMapper.toTask}). A null field means "leave unchanged". The
     * {@code repetition}/{@code progress} objects and {@code prefSlots} list are applied as a group
     * only when present. Must run repetition before pref slots (the default-slot seed reads reps).
     */
    private static void applyEditableFields(Task task, TaskChange change, boolean isCreate) {
        TaskCore core = task.core;
        if (change.title() != null) core.title = change.title();
        if (change.description() != null) core.description = change.description();
        if (change.categoryId() != null) core.categoryId = change.categoryId();
        if (change.priority() != null) core.priority = change.priority();
        if (change.leisure() != null) core.leisure = change.leisure();
        if (change.adaptive() != null) core.adaptive = change.adaptive();
        if (change.minDuration() != null) core.minDuration = change.minDuration();
        if (change.maxDuration() != null) core.maxDuration = change.maxDuration();
        if (change.cooldown() != null) core.cooldown = change.cooldown();
        if (change.schedulingType() != null) core.schedulingType = change.schedulingType();
        if (change.startDate() != null) core.startDate = change.startDate();
        if (change.deadline() != null) core.deadline = change.deadline();
        if (change.closeOnMiss() != null) core.closeOnMiss = change.closeOnMiss();
        if (change.fixedDate() != null) core.fixedDate = change.fixedDate();
        if (change.fixedStart() != null) core.fixedStart = change.fixedStart();
        if (change.fixedEnd() != null) core.fixedEnd = change.fixedEnd();
        if (change.fixedDuration() != null) core.fixedDuration = change.fixedDuration();
        if (change.budgetRequiredCents() != null) core.budgetRequiredCents = change.budgetRequiredCents();
        if (change.budgetAccountId() != null) core.budgetAccountId = change.budgetAccountId();
        if (change.budgetCategoryId() != null) core.budgetCategoryId = change.budgetCategoryId();

        applyRepetition(core, change.repetition(), isCreate);
        applyProgress(core.progress, change.progress());
        applyPrefSlots(task, change.prefSlots(), isCreate);
    }

    private static void applyRepetition(TaskCore core, TaskAssistantProposal.RepetitionChange rep,
                                        boolean isCreate) {
        if (rep == null) {
            return;
        }
        TaskCore.Repetition target = core.repetition;
        int oldReps = target.reps;
        int oldPerPeriod = target.perPeriod;
        Period oldUnit = target.periodUnit;
        if (rep.reps() != null) target.reps = rep.reps();
        if (rep.perPeriod() != null) target.perPeriod = rep.perPeriod();
        if (rep.periodUnit() != null) target.periodUnit = rep.periodUnit();
        if (rep.completeFirst() != null) target.completeFirst = rep.completeFirst();

        boolean patternChanged = target.reps != oldReps || target.perPeriod != oldPerPeriod
                || target.periodUnit != oldUnit;
        if (isCreate || patternChanged || target.periodStart == null) {
            target.periodStart = LocalDate.now();
            target.periodCompletions = 0;
            target.carryoverDebt = 0;
        }
    }

    private static void applyProgress(TaskCore.Progress progress,
                                      TaskAssistantProposal.ProgressChange change) {
        if (change == null) {
            return;
        }
        if (change.unit() != null) progress.unit = change.unit();
        if (change.target() != null) progress.target = change.target();
        if (change.current() != null) progress.current = change.current();
        if (change.resetPerRep() != null) progress.resetPerRep = change.resetPerRep();
        if (change.minPerRep() != null) progress.minPerRep = change.minPerRep();
        if (change.maxPerRep() != null) progress.maxPerRep = change.maxPerRep();
    }

    private static void applyPrefSlots(Task task, List<TaskAssistantProposal.PrefSlotChange> prefSlots,
                                       boolean isCreate) {
        if (prefSlots != null) {
            List<TaskPrefSlot> slots = new ArrayList<>();
            for (TaskAssistantProposal.PrefSlotChange change : prefSlots) {
                TaskPrefSlot slot = new TaskPrefSlot();
                slot.taskId = task.core.id;
                slot.days = change.days();
                slot.start = change.start();
                slots.add(slot);
            }
            task.prefSlots = slots;
            return;
        }
        // A freshly created recurring task needs at least one preferred slot to be schedulable, matching
        // the manual editor's default; existing/non-recurring tasks keep whatever they already have.
        if (isCreate && task.core.repetition.reps > 0
                && (task.prefSlots == null || task.prefSlots.isEmpty())) {
            List<TaskPrefSlot> slots = new ArrayList<>();
            slots.add(TaskPrefSlotFactory.createDefault(task.core.id));
            task.prefSlots = slots;
        }
    }

    /** Thrown when the proposal references unknown ids or misses required fields. */
    private static final class ValidationException extends RuntimeException {
        ValidationException(String message) {
            super(message);
        }
    }
}

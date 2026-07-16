package com.autosecretary.features.task.application;

import androidx.room.RoomDatabase;

import com.autosecretary.features.task.application.config.TaskCategoryWindowRepository;
import com.autosecretary.features.task.data.TaskCategoryDao;
import com.autosecretary.features.task.data.TaskCategoryWindowDao;
import com.autosecretary.features.task.data.TaskDao;
import com.autosecretary.features.task.domain.assistant.ChangeOp;
import com.autosecretary.features.task.domain.assistant.TaskAssistantProposal;
import com.autosecretary.features.task.domain.assistant.TaskAssistantProposal.CategoryChange;
import com.autosecretary.features.task.domain.assistant.TaskAssistantProposal.TaskChange;
import com.autosecretary.features.task.domain.assistant.TaskAssistantProposal.WindowChange;
import com.autosecretary.features.task.domain.model.Task;
import com.autosecretary.features.task.domain.model.TaskCategory;
import com.autosecretary.features.task.domain.model.TaskCategoryWindow;
import com.autosecretary.features.task.domain.model.TaskCore;
import com.autosecretary.features.task.domain.model.TaskPrefSlot;
import com.autosecretary.features.task.domain.model.TaskPrefSlotFactory;
import com.autosecretary.shared.Period;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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
    private final TaskCategoryWindowRepository windowRepository;
    private final TaskChangeUndoHolder undoHolder;
    private final ScheduleReplanCoordinator scheduleReplanCoordinator;
    private final ExecutorService dbExecutor;
    private final Executor callbackDispatcher;

    public ApplyTaskChangesUseCase(RoomDatabase database,
                                   TaskDao taskDao,
                                   TaskCategoryDao categoryDao,
                                   TaskCategoryWindowDao windowDao,
                                   TaskCategoryWindowRepository windowRepository,
                                   TaskChangeUndoHolder undoHolder,
                                   ScheduleReplanCoordinator scheduleReplanCoordinator,
                                   ExecutorService dbExecutor,
                                   Executor callbackDispatcher) {
        this.database = database;
        this.taskDao = taskDao;
        this.categoryDao = categoryDao;
        this.windowDao = windowDao;
        this.windowRepository = windowRepository;
        this.undoHolder = undoHolder;
        this.scheduleReplanCoordinator = scheduleReplanCoordinator;
        this.dbExecutor = dbExecutor;
        this.callbackDispatcher = callbackDispatcher;
    }

    public void apply(TaskAssistantProposal proposal, Runnable onApplied, Consumer<String> onError) {
        dbExecutor.execute(() -> {
            try {
                applyOnDbThread(proposal);
                // Assistant changed tasks and/or reserved windows — re-plan so the schedule reflects them.
                scheduleReplanCoordinator.requestReplan();
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
        // Normalised category name -> real id, so a task change can reference its category by name
        // (existing or one created in the same proposal) and never has to carry an invented id.
        Map<String, String> categoryIdByName = new HashMap<>();
        for (TaskCategory category : categoryDao.readAll()) {
            categoryIds.add(category.id);
            if (category.name != null) categoryIdByName.put(normalizeName(category.name), category.id);
        }
        Map<String, TaskCategoryWindow> windowsById = new LinkedHashMap<>();
        for (TaskCategoryWindow window : windowDao.readAll()) {
            windowsById.put(window.id, window);
        }

        // Pre-build created entities so their ids are known before the transaction (for the undo snapshot).
        // Categories first: their real ids feed name resolution for tasks created in the same proposal.
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
                if (category.name != null) categoryIdByName.put(normalizeName(category.name), category.id);
            }
        }

        validate(proposal, tasksById.keySet(), categoryIds, categoryIdByName.keySet(), windowsById);

        List<Task> createdTasks = new ArrayList<>();
        List<String> createdTaskIds = new ArrayList<>();
        for (TaskChange change : proposal.taskChanges()) {
            if (change.op() == ChangeOp.CREATE) {
                Task task = new Task();
                applyEditableFields(task, change, true, categoryIdByName);
                createdTasks.add(task);
                createdTaskIds.add(task.core.id);
            }
        }
        List<TaskCategoryWindow> createdWindows = new ArrayList<>();
        List<String> createdWindowIds = new ArrayList<>();
        for (WindowChange change : proposal.windowChanges()) {
            if (change.op() == ChangeOp.CREATE) {
                TaskCategoryWindow window = new TaskCategoryWindow(
                        change.dayOfWeek(), change.categoryId(), change.startTime(), change.endTime());
                createdWindows.add(window);
                createdWindowIds.add(window.id);
            }
        }

        TaskChangeUndoHolder.Snapshot snapshot = captureUndo(
                proposal, tasksById, windowsById, createdTaskIds, createdCategoryIds, createdWindowIds);

        database.runInTransaction(() -> {
            applyCategoryChanges(proposal, createdCategories);
            applyTaskChanges(proposal, createdTasks, categoryIdByName);
            applyWindowChanges(proposal, createdWindows, windowsById);
        });

        undoHolder.push(snapshot);
        // The scheduler shares the window repository's cache; refresh it after any window mutation
        // (including the category-delete cascade above).
        windowRepository.invalidateCache();
    }

    private void validate(TaskAssistantProposal proposal, Set<String> taskIds, Set<String> categoryIds,
                          Set<String> knownCategoryNames, Map<String, TaskCategoryWindow> windowsById) {
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
            // A referenced category must exist (or be created in this proposal); a name that resolves
            // to nothing would silently leave the task uncategorised, so reject it instead.
            if (change.op() != ChangeOp.DELETE && change.categoryName() != null
                    && !change.categoryName().isBlank()
                    && !knownCategoryNames.contains(normalizeName(change.categoryName()))) {
                throw new ValidationException("Unbekannte Kategorie im Vorschlag: " + change.categoryName());
            }
        }
        for (WindowChange change : proposal.windowChanges()) {
            switch (change.op()) {
                case CREATE -> {
                    if (change.dayOfWeek() == null || change.startTime() == null || change.endTime() == null) {
                        throw new ValidationException(
                                "Neue Zeitreservierung braucht Wochentag, Start- und Endzeit.");
                    }
                    if (change.categoryId() == null || !categoryIds.contains(change.categoryId())) {
                        throw new ValidationException(
                                "Unbekannte Kategorie-id für Zeitreservierung: " + change.categoryId());
                    }
                    if (!change.endTime().isAfter(change.startTime())) {
                        throw new ValidationException("Endzeit muss nach der Startzeit liegen.");
                    }
                }
                case UPDATE, DELETE -> {
                    TaskCategoryWindow prior = change.id() == null ? null : windowsById.get(change.id());
                    if (prior == null) {
                        throw new ValidationException(
                                "Unbekannte Zeitreservierungs-id im Vorschlag: " + change.id());
                    }
                    if (change.categoryId() != null && !categoryIds.contains(change.categoryId())) {
                        throw new ValidationException(
                                "Unbekannte Kategorie-id für Zeitreservierung: " + change.categoryId());
                    }
                    if (change.op() == ChangeOp.UPDATE) {
                        LocalTime start = change.startTime() != null ? change.startTime() : prior.startTime;
                        LocalTime end = change.endTime() != null ? change.endTime() : prior.endTime;
                        if (!end.isAfter(start)) {
                            throw new ValidationException("Endzeit muss nach der Startzeit liegen.");
                        }
                    }
                }
            }
        }
    }

    private TaskChangeUndoHolder.Snapshot captureUndo(TaskAssistantProposal proposal,
                                                      Map<String, Task> tasksById,
                                                      Map<String, TaskCategoryWindow> windowsById,
                                                      List<String> createdTaskIds,
                                                      List<String> createdCategoryIds,
                                                      List<String> createdWindowIds) {
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

        // Windows whose prior row we must keep: those directly updated/deleted, plus those bound to a
        // category being deleted (the category-delete cascade removes them). windowsById holds the
        // untouched pre-apply rows.
        Set<String> priorWindowIds = new HashSet<>();
        for (WindowChange change : proposal.windowChanges()) {
            if (change.op() != ChangeOp.CREATE) {
                priorWindowIds.add(change.id());
            }
        }
        if (!deletedCategoryIds.isEmpty()) {
            for (TaskCategoryWindow window : windowsById.values()) {
                if (deletedCategoryIds.contains(window.categoryId)) {
                    priorWindowIds.add(window.id);
                }
            }
        }
        List<TaskCategoryWindow> priorWindows = new ArrayList<>();
        for (String id : priorWindowIds) {
            TaskCategoryWindow prior = windowsById.get(id);
            if (prior != null) {
                priorWindows.add(prior);
            }
        }

        return new TaskChangeUndoHolder.Snapshot(
                priorTasks, new ArrayList<>(createdTaskIds),
                priorCategories, new ArrayList<>(createdCategoryIds),
                priorWindows, new ArrayList<>(createdWindowIds));
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

    private void applyTaskChanges(TaskAssistantProposal proposal, List<Task> createdTasks,
                                  Map<String, String> categoryIdByName) {
        int createdIndex = 0;
        for (TaskChange change : proposal.taskChanges()) {
            switch (change.op()) {
                case CREATE -> taskDao.write(createdTasks.get(createdIndex++));
                case UPDATE -> {
                    Task task = taskDao.read(change.id());
                    if (task == null) break;
                    applyEditableFields(task, change, false, categoryIdByName);
                    taskDao.write(task);
                }
                case DELETE -> taskDao.deleteTaskGraph(change.id());
            }
        }
    }

    /**
     * Applies reserved category-window changes. UPDATE builds a fresh row (same id, non-null fields
     * merged over the prior row) so the untouched {@code windowsById} instance stays valid for undo.
     */
    private void applyWindowChanges(TaskAssistantProposal proposal, List<TaskCategoryWindow> createdWindows,
                                    Map<String, TaskCategoryWindow> windowsById) {
        int createdIndex = 0;
        for (WindowChange change : proposal.windowChanges()) {
            switch (change.op()) {
                case CREATE -> windowDao.write(createdWindows.get(createdIndex++));
                case UPDATE -> {
                    TaskCategoryWindow prior = windowsById.get(change.id());
                    if (prior == null) break;
                    TaskCategoryWindow updated = new TaskCategoryWindow(
                            change.dayOfWeek() != null ? change.dayOfWeek() : prior.dayOfWeek,
                            change.categoryId() != null ? change.categoryId() : prior.categoryId,
                            change.startTime() != null ? change.startTime() : prior.startTime,
                            change.endTime() != null ? change.endTime() : prior.endTime);
                    updated.id = prior.id;
                    windowDao.write(updated);
                }
                case DELETE -> windowDao.delete(change.id());
            }
        }
    }

    /**
     * Writes every non-null field of {@code change} onto {@code task}, mirroring the manual editor's
     * save mapping ({@code TaskEditStateMapper.toTask}). A null field means "leave unchanged". The
     * {@code repetition}/{@code progress} objects and {@code prefSlots} list are applied as a group
     * only when present. Must run repetition before pref slots (the default-slot seed reads reps).
     */
    /** Category-name key for case/whitespace-insensitive matching (assistant supplies names, not ids). */
    private static String normalizeName(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }

    private static void applyEditableFields(Task task, TaskChange change, boolean isCreate,
                                            Map<String, String> categoryIdByName) {
        TaskCore core = task.core;
        if (change.title() != null) core.title = change.title();
        if (change.description() != null) core.description = change.description();
        // Resolve the category by name to a real id (validated to exist). A blank name is treated as
        // "no change"; an unresolved name leaves the category untouched rather than clearing it.
        if (change.categoryName() != null && !change.categoryName().isBlank()) {
            String resolved = categoryIdByName.get(normalizeName(change.categoryName()));
            if (resolved != null) core.categoryId = resolved;
        }
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

        // The assistant omits periodUnit for one-off tasks ("reps:0"); never persist a null/invalid
        // period — it would make the whole scheduler NPE. Fall back to a valid, non-schedulable default.
        if (target.periodUnit == null) target.periodUnit = Period.DAY;
        if (target.perPeriod <= 0) target.perPeriod = 1;

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

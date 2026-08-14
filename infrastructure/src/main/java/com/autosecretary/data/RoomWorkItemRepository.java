package com.autosecretary.data;

import com.autosecretary.application.CompletionRecord;
import com.autosecretary.application.DayPlanDirective;
import com.autosecretary.application.FocusSnapshot;
import com.autosecretary.application.StepCompletion;
import com.autosecretary.application.WorkItemRepository;
import com.autosecretary.application.ai.BulkChange;
import com.autosecretary.data.entity.CompletionEntity;
import com.autosecretary.data.entity.DayPlanDirectiveEntity;
import com.autosecretary.data.entity.StepCompletionEntity;
import com.autosecretary.data.entity.StepDayEntity;
import com.autosecretary.data.entity.StepEntity;
import com.autosecretary.data.entity.UndoJournalEntity;
import com.autosecretary.data.entity.WorkItemEntity;
import com.autosecretary.domain.CompletionStats;
import com.autosecretary.domain.Routine;
import com.autosecretary.domain.Step;
import com.autosecretary.domain.Task;
import com.autosecretary.domain.WorkItem;

import org.json.JSONObject;
import org.json.JSONArray;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/** Room adapter containing the atomic persistence operations of the focus core. */
public final class RoomWorkItemRepository implements WorkItemRepository {
    private final FocusDatabase database;
    private final FocusDao dao;
    private final WorkItemMapper mapper = new WorkItemMapper();
    private final WorkItemJson workItemJson = new WorkItemJson();

    public RoomWorkItemRepository(FocusDatabase database) {
        this.database = database;
        this.dao = database.focusDao();
    }

    @Override
    public FocusSnapshot loadSnapshot() {
        List<StepEntity> steps = dao.readSteps();
        List<StepDayEntity> days = dao.readStepDays();
        List<WorkItem> items = new ArrayList<>();
        for (WorkItemEntity item : dao.readWorkItems()) {
            try { items.add(mapper.toDomain(item, steps, days)); }
            catch (RuntimeException corruptRecord) {
                // A malformed row is quarantined from this snapshot; other work remains usable.
            }
        }
        List<CompletionRecord> completions = new ArrayList<>();
        for (CompletionEntity item : dao.readCompletions()) {
            try {
                completions.add(new CompletionRecord(item.id, item.workItemId, item.occurrenceKey,
                        LocalDateTime.parse(item.completedAt)));
            } catch (RuntimeException corruptRecord) {
                // One corrupt evidence row must not make the entire dashboard unavailable.
            }
        }
        List<StepCompletion> stepCompletions = new ArrayList<>();
        for (StepCompletionEntity item : dao.readStepCompletions()) {
            try {
                stepCompletions.add(new StepCompletion(item.stepId, item.occurrenceKey,
                        LocalDateTime.parse(item.completedAt)));
            } catch (RuntimeException corruptRecord) {
                // Keep all other step evidence readable.
            }
        }
        return new FocusSnapshot(items, completions, stepCompletions);
    }

    @Override
    public WorkItem find(String id) {
        WorkItemEntity item = dao.readWorkItem(id);
        if (item == null) return null;
        try { return mapper.toDomain(item, dao.readSteps(id), dao.readStepDays()); }
        catch (RuntimeException corruptRecord) { return null; }
    }

    @Override
    public void save(WorkItem item) {
        database.runInTransaction(() -> saveChecked(item));
    }

    @Override
    public void delete(String id) {
        database.runInTransaction(() -> dao.deleteWorkItem(id));
    }

    @Override
    public void deleteAll(List<String> ids) {
        database.runInTransaction(() -> ids.forEach(dao::deleteWorkItem));
    }

    @Override
    public WorkItem complete(String id, LocalDateTime at) {
        return transactionResult(() -> completeInternal(id, at));
    }

    @Override
    public WorkItem setStepCompleted(
            String workItemId,
            String stepId,
            boolean completed,
            LocalDateTime at) {
        return transactionResult(() -> {
            WorkItem item = find(workItemId);
            if (item == null || !item.isOpenOn(at.toLocalDate())) return null;
            Step step = item.steps().stream()
                    .filter(candidate -> candidate.id().equals(stepId))
                    .findFirst().orElse(null);
            if (step == null || !step.appliesOn(item.occurrenceDate(at.toLocalDate()) == null
                    ? at.getDayOfWeek() : item.occurrenceDate(at.toLocalDate()).getDayOfWeek())) return null;
            String occurrenceKey = occurrenceKey(item, at.toLocalDate());
            if (completed) {
                StepCompletionEntity entity = new StepCompletionEntity();
                entity.id = UUID.randomUUID().toString();
                entity.stepId = stepId;
                entity.occurrenceKey = occurrenceKey;
                entity.completedAt = at.toString();
                dao.upsertStepCompletion(entity);
            } else {
                dao.deleteStepCompletion(stepId, occurrenceKey);
            }
            if (completed && allActiveStepsCompleted(item, occurrenceKey, at.toLocalDate())) {
                return completeInternal(workItemId, at);
            }
            // Step state is part of the revisioned graph even though it lives in its own table.
            // Bumping the parent revision makes a later AI undo detect this intervening mutation.
            saveChecked(item);
            return find(workItemId);
        });
    }

    @Override
    public List<DayPlanDirective> directives(LocalDate day) {
        return transactionResult(() -> {
            List<StepEntity> steps = dao.readSteps();
            List<StepDayEntity> stepDays = dao.readStepDays();
            Map<String, WorkItem> open = new java.util.HashMap<>();
            for (WorkItemEntity entity : dao.readWorkItems()) {
                try {
                    WorkItem item = mapper.toDomain(entity, steps, stepDays);
                    if (item.isOpenOn(day)) open.put(item.id(), item);
                } catch (RuntimeException corruptRecord) {
                    // A directive to a corrupt item is invalid and is removed below.
                }
            }
            List<DayPlanDirective> valid = new ArrayList<>();
            for (DayPlanDirectiveEntity entity : dao.readDirectives(day.toString())) {
                boolean relative = "BEFORE".equals(entity.relation) || "AFTER".equals(entity.relation);
                boolean invalid = !open.containsKey(entity.workItemId)
                        || relative && (entity.anchorWorkItemId == null
                        || entity.anchorWorkItemId.equals(entity.workItemId)
                        || !open.containsKey(entity.anchorWorkItemId));
                if (invalid) {
                    dao.deleteDirective(entity.day, entity.workItemId);
                } else {
                    try { valid.add(toDirective(entity)); }
                    catch (RuntimeException corruptRecord) {
                        dao.deleteDirective(entity.day, entity.workItemId);
                    }
                }
            }
            return valid;
        });
    }

    @Override
    public void saveDirective(DayPlanDirective directive, String undoLabel) {
        database.runInTransaction(() -> {
            DayPlanDirectiveEntity before = dao.readDirective(
                    directive.day().toString(), directive.workItemId());
            JSONObject payload = new JSONObject();
            try {
                payload.put("type", "directive");
                payload.put("day", directive.day().toString());
                payload.put("workItemId", directive.workItemId());
                payload.put("before", before == null ? JSONObject.NULL : directiveJson(before));
                payload.put("after", directiveJson(toEntity(directive)));
            } catch (Exception error) {
                throw new IllegalStateException("Undo konnte nicht vorbereitet werden", error);
            }
            dao.upsertDirective(toEntity(directive));
            insertUndo("DAY_PLAN", undoLabel, payload.toString(), directive.updatedAt());
        });
    }

    @Override
    public String latestUndoLabel() {
        UndoJournalEntity undo = dao.readLatestUndo();
        return undo == null ? null : undo.label;
    }

    @Override
    public boolean undoLatest(LocalDateTime at) {
        return transactionResult(() -> {
            UndoJournalEntity undo = dao.readLatestUndo();
            if (undo == null) return false;
            try {
                JSONObject payload = new JSONObject(undo.payloadJson);
                if ("changeSet".equals(payload.optString("type"))) {
                    undoChangeSet(payload);
                    return dao.markUndone(undo.id, at.toString()) == 1;
                }
                if (!"directive".equals(payload.optString("type"))) return false;
                String day = payload.getString("day");
                String workItemId = payload.getString("workItemId");
                DayPlanDirectiveEntity current = dao.readDirective(day, workItemId);
                if (current == null || !sameDirective(
                        current, payload.getJSONObject("after"))) {
                    throw new IllegalStateException(
                            "Tagesreihenfolge wurde seitdem verändert");
                }
                if (payload.isNull("before")) {
                    dao.deleteDirective(day, workItemId);
                } else {
                    dao.upsertDirective(directiveFromJson(payload.getJSONObject("before")));
                }
                return dao.markUndone(undo.id, at.toString()) == 1;
            } catch (IllegalStateException conflict) {
                throw conflict;
            } catch (Exception error) {
                throw new IllegalStateException("Undo ist beschädigt", error);
            }
        });
    }

    @Override
    public void applyChangeSet(
            List<BulkChange> changes,
            String label,
            LocalDateTime at) {
        database.runInTransaction(() -> {
            if (changes == null || changes.isEmpty()) {
                throw new IllegalStateException("Changeset enthält keine Änderungen");
            }
            Set<String> changeIds = new HashSet<>();
            Set<String> changeTargets = new HashSet<>();
            for (BulkChange change : changes) {
                if (!changeIds.add(change.changeId())) {
                    throw new IllegalStateException("Changeset enthält eine Change-ID mehrfach");
                }
                if (!changeTargets.add(change.targetId())) {
                    throw new IllegalStateException("Changeset enthält ein Ziel mehrfach");
                }
            }
            JSONObject payload = new JSONObject();
            JSONArray before = new JSONArray();
            JSONArray created = new JSONArray();
            JSONArray appliedChangeIds = new JSONArray();
            JSONArray expectedRevisions = new JSONArray();
            JSONArray completionHistory = new JSONArray();
            JSONArray stepCompletionHistory = new JSONArray();
            Set<String> capturedHistory = new HashSet<>();
            try {
                for (BulkChange change : changes) {
                    appliedChangeIds.put(change.changeId());
                    WorkItemEntity stored = dao.readWorkItem(change.targetId());
                    WorkItem existing = find(change.targetId());
                    switch (change.type()) {
                        case ADD -> {
                            if (stored != null) {
                                throw new IllegalStateException("KI-Hinzufügung existiert bereits");
                            }
                            created.put(change.targetId());
                            expectedRevisions.put(new JSONObject()
                                    .put("id", change.targetId()).put("revision", 0));
                        }
                        case UPDATE -> {
                            if (existing == null
                                    || existing.revision() != change.expectedRevision()) {
                                throw new IllegalStateException("KI-Vorschlag ist nicht mehr aktuell");
                            }
                            before.put(workItemJson.encode(existing));
                            captureHistory(existing, completionHistory, stepCompletionHistory,
                                    capturedHistory);
                            expectedRevisions.put(new JSONObject().put("id", change.targetId())
                                    .put("revision", existing.revision() + 1));
                        }
                        case DELETE -> {
                            if (existing == null) {
                                throw new IllegalStateException("Zu löschendes Work Item fehlt");
                            }
                            if (existing.revision() != change.expectedRevision()) {
                                throw new IllegalStateException("KI-Löschung ist nicht mehr aktuell");
                            }
                            before.put(workItemJson.encode(existing));
                            captureHistory(existing, completionHistory, stepCompletionHistory,
                                    capturedHistory);
                            expectedRevisions.put(new JSONObject()
                                    .put("id", change.targetId()).put("deleted", true));
                        }
                    }
                }
                payload.put("type", "changeSet").put("before", before)
                        .put("changeIds", appliedChangeIds)
                        .put("created", created).put("expected", expectedRevisions)
                        .put("completionHistory", completionHistory)
                        .put("stepCompletionHistory", stepCompletionHistory);
            } catch (Exception error) {
                throw new IllegalStateException("KI-Undo konnte nicht vorbereitet werden", error);
            }
            for (BulkChange change : changes) {
                if (change.type() == BulkChange.Type.DELETE) {
                    dao.deleteWorkItem(change.targetId());
                } else {
                    saveChecked(change.upsert());
                }
            }
            insertUndo("AI_CHANGE_SET", label, payload.toString(), at);
        });
    }

    private void undoChangeSet(JSONObject payload) throws Exception {
        JSONArray expected = payload.getJSONArray("expected");
        for (int index = 0; index < expected.length(); index++) {
            JSONObject value = expected.getJSONObject(index);
            WorkItem current = find(value.getString("id"));
            if (value.optBoolean("deleted")) {
                if (current != null) throw new IllegalStateException("Undo-Konflikt nach Löschung");
            } else if (current == null || current.revision() != value.getLong("revision")) {
                throw new IllegalStateException("Work Item wurde seit der KI-Änderung bearbeitet");
            }
        }
        JSONArray created = payload.getJSONArray("created");
        for (int index = 0; index < created.length(); index++) {
            dao.deleteWorkItem(created.getString(index));
        }
        JSONArray before = payload.getJSONArray("before");
        for (int index = 0; index < before.length(); index++) {
            WorkItem item = workItemJson.decode(before.getJSONObject(index));
            dao.upsertGraph(mapper.toEntity(item, item.revision()),
                    mapper.stepEntities(item), mapper.stepDayEntities(item));
        }
        JSONArray completionHistory = payload.optJSONArray("completionHistory");
        if (completionHistory != null) {
            for (int index = 0; index < completionHistory.length(); index++) {
                dao.upsertCompletion(completionFromJson(
                        completionHistory.getJSONObject(index)));
            }
        }
        JSONArray stepCompletionHistory = payload.optJSONArray("stepCompletionHistory");
        if (stepCompletionHistory != null) {
            for (int index = 0; index < stepCompletionHistory.length(); index++) {
                dao.upsertStepCompletion(stepCompletionFromJson(
                        stepCompletionHistory.getJSONObject(index)));
            }
        }
    }

    private void captureHistory(
            WorkItem item,
            JSONArray completions,
            JSONArray stepCompletions,
            Set<String> capturedItems) throws Exception {
        if (!capturedItems.add(item.id())) return;
        for (CompletionEntity completion : dao.readCompletions()) {
            if (item.id().equals(completion.workItemId)) {
                completions.put(completionJson(completion));
            }
        }
        Set<String> stepIds = item.steps().stream().map(Step::id).collect(Collectors.toSet());
        for (StepCompletionEntity completion : dao.readStepCompletions()) {
            if (stepIds.contains(completion.stepId)) {
                stepCompletions.put(stepCompletionJson(completion));
            }
        }
    }

    private WorkItem completeInternal(String id, LocalDateTime at) {
        WorkItem item = find(id);
        if (item == null || !item.isOpenOn(at.toLocalDate())) return null;
        WorkItem completed;
        String occurrenceKey = occurrenceKey(item, at.toLocalDate());
        if (item instanceof Task task) {
            CompletionStats stats = new CompletionStats(0, task.stats().bestStreak(),
                    task.stats().totalCompletions() + 1);
            completed = new Task(task.id(), task.title(), task.durationMinutes(), task.deadlineAt(),
                    task.timePreference(), task.flexible(), task.steps(), task.createdAt(), true,
                    stats, task.revision());
        } else {
            Routine routine = (Routine) item;
            long daysLate = Math.max(0, ChronoUnit.DAYS.between(routine.nextDueDate(), at.toLocalDate()));
            int streak = daysLate < routine.cadenceDays() ? routine.stats().currentStreak() + 1 : 1;
            long periods = daysLate / routine.cadenceDays() + 1;
            LocalDate next = routine.nextDueDate().plusDays(
                    Math.multiplyExact(periods, (long) routine.cadenceDays()));
            CompletionStats stats = new CompletionStats(streak,
                    Math.max(streak, routine.stats().bestStreak()),
                    routine.stats().totalCompletions() + 1);
            completed = new Routine(routine.id(), routine.title(), routine.durationMinutes(),
                    routine.deadlineAt(), routine.timePreference(), routine.flexible(), routine.steps(),
                    routine.createdAt(), routine.cadenceDays(), next, stats, routine.revision());
        }
        for (Step step : activeSteps(item, at.toLocalDate())) {
            StepCompletionEntity entity = new StepCompletionEntity();
            entity.id = UUID.randomUUID().toString();
            entity.stepId = step.id();
            entity.occurrenceKey = occurrenceKey;
            entity.completedAt = at.toString();
            dao.upsertStepCompletion(entity);
        }
        saveChecked(completed);
        CompletionEntity evidence = new CompletionEntity();
        evidence.id = UUID.randomUUID().toString();
        evidence.workItemId = id;
        evidence.occurrenceKey = occurrenceKey;
        evidence.completedAt = at.toString();
        dao.upsertCompletion(evidence);
        return find(id);
    }

    private boolean allActiveStepsCompleted(WorkItem item, String occurrenceKey, LocalDate day) {
        List<Step> active = activeSteps(item, day);
        if (active.isEmpty()) return false;
        Map<String, StepCompletionEntity> completed = dao.readStepCompletions().stream()
                .filter(value -> occurrenceKey.equals(value.occurrenceKey))
                .collect(Collectors.toMap(value -> value.stepId, value -> value, (left, right) -> right));
        return active.stream().allMatch(step -> completed.containsKey(step.id()));
    }

    private static List<Step> activeSteps(WorkItem item, LocalDate day) {
        LocalDate occurrence = item.occurrenceDate(day);
        LocalDate effective = occurrence == null ? day : occurrence;
        return item.steps().stream().filter(step -> step.appliesOn(effective.getDayOfWeek()))
                .collect(Collectors.toList());
    }

    private static String occurrenceKey(WorkItem item, LocalDate day) {
        LocalDate occurrence = item.occurrenceDate(day);
        return occurrence == null ? "TASK" : occurrence.toString();
    }

    private void saveChecked(WorkItem item) {
        WorkItemEntity existing = dao.readWorkItem(item.id());
        if (existing == null && item.revision() != 0) {
            throw new IllegalStateException("Neues Work Item hat eine ungültige Revision");
        }
        if (existing != null && existing.revision != item.revision()) {
            throw new IllegalStateException("Work Item wurde zwischenzeitlich geändert");
        }
        for (Step step : item.steps()) {
            StepEntity stored = dao.readStep(step.id());
            if (stored != null && !stored.workItemId.equals(item.id())) {
                throw new IllegalStateException("Schritt-ID gehört zu einem anderen Work Item");
            }
        }
        long nextRevision = existing == null ? 0 : existing.revision + 1;
        dao.upsertGraph(mapper.toEntity(item, nextRevision),
                mapper.stepEntities(item), mapper.stepDayEntities(item));
    }

    private void insertUndo(
            String kind, String label, String payload, LocalDateTime createdAt) {
        UndoJournalEntity undo = new UndoJournalEntity();
        undo.id = UUID.randomUUID().toString();
        undo.kind = kind;
        undo.label = label == null ? "Änderung rückgängig machen" : label;
        undo.payloadJson = payload;
        undo.createdAt = createdAt.toString();
        dao.insertUndo(undo);
        dao.trimUndoJournal();
    }

    private DayPlanDirective toDirective(DayPlanDirectiveEntity entity) {
        return new DayPlanDirective(entity.id, LocalDate.parse(entity.day), entity.workItemId,
                DayPlanDirective.Relation.valueOf(entity.relation), entity.anchorWorkItemId,
                LocalDateTime.parse(entity.updatedAt));
    }

    private DayPlanDirectiveEntity toEntity(DayPlanDirective directive) {
        DayPlanDirectiveEntity entity = new DayPlanDirectiveEntity();
        entity.id = directive.id();
        entity.day = directive.day().toString();
        entity.workItemId = directive.workItemId();
        entity.relation = directive.relation().name();
        entity.anchorWorkItemId = directive.anchorWorkItemId();
        entity.updatedAt = directive.updatedAt().toString();
        return entity;
    }

    private static JSONObject directiveJson(DayPlanDirectiveEntity value) throws Exception {
        return new JSONObject()
                .put("id", value.id)
                .put("day", value.day)
                .put("workItemId", value.workItemId)
                .put("relation", value.relation)
                .put("anchorWorkItemId", value.anchorWorkItemId == null
                        ? JSONObject.NULL : value.anchorWorkItemId)
                .put("updatedAt", value.updatedAt);
    }

    private static DayPlanDirectiveEntity directiveFromJson(JSONObject value) throws Exception {
        DayPlanDirectiveEntity result = new DayPlanDirectiveEntity();
        result.id = value.getString("id");
        result.day = value.getString("day");
        result.workItemId = value.getString("workItemId");
        result.relation = value.getString("relation");
        result.anchorWorkItemId = value.isNull("anchorWorkItemId")
                ? null : value.getString("anchorWorkItemId");
        result.updatedAt = value.getString("updatedAt");
        return result;
    }

    private static boolean sameDirective(
            DayPlanDirectiveEntity current,
            JSONObject expected) throws Exception {
        return current.id.equals(expected.getString("id"))
                && current.day.equals(expected.getString("day"))
                && current.workItemId.equals(expected.getString("workItemId"))
                && current.relation.equals(expected.getString("relation"))
                && java.util.Objects.equals(current.anchorWorkItemId,
                        expected.isNull("anchorWorkItemId")
                                ? null : expected.getString("anchorWorkItemId"))
                && current.updatedAt.equals(expected.getString("updatedAt"));
    }

    private static JSONObject completionJson(CompletionEntity value) throws Exception {
        return new JSONObject().put("id", value.id)
                .put("workItemId", value.workItemId)
                .put("occurrenceKey", value.occurrenceKey)
                .put("completedAt", value.completedAt);
    }

    private static CompletionEntity completionFromJson(JSONObject value) throws Exception {
        CompletionEntity result = new CompletionEntity();
        result.id = value.getString("id");
        result.workItemId = value.getString("workItemId");
        result.occurrenceKey = value.getString("occurrenceKey");
        result.completedAt = value.getString("completedAt");
        return result;
    }

    private static JSONObject stepCompletionJson(StepCompletionEntity value) throws Exception {
        return new JSONObject().put("id", value.id)
                .put("stepId", value.stepId)
                .put("occurrenceKey", value.occurrenceKey)
                .put("completedAt", value.completedAt);
    }

    private static StepCompletionEntity stepCompletionFromJson(JSONObject value) throws Exception {
        StepCompletionEntity result = new StepCompletionEntity();
        result.id = value.getString("id");
        result.stepId = value.getString("stepId");
        result.occurrenceKey = value.getString("occurrenceKey");
        result.completedAt = value.getString("completedAt");
        return result;
    }

    private <T> T transactionResult(Supplier<T> supplier) {
        final Object[] result = new Object[1];
        database.runInTransaction(() -> result[0] = supplier.get());
        @SuppressWarnings("unchecked") T value = (T) result[0];
        return value;
    }
}

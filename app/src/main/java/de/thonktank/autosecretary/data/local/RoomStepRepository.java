package de.thonktank.autosecretary.data.local;

import de.thonktank.autosecretary.AppDatabase;
import de.thonktank.autosecretary.domain.model.OccurrenceStep;
import de.thonktank.autosecretary.domain.model.ResistanceLoad;
import de.thonktank.autosecretary.domain.model.SetResult;
import de.thonktank.autosecretary.domain.model.TaskId;
import de.thonktank.autosecretary.domain.model.TaskStepTemplate;
import de.thonktank.autosecretary.domain.model.TrainingObservation;
import de.thonktank.autosecretary.domain.repository.StepRepository;
import de.thonktank.autosecretary.domain.transaction.TransactionRunner;
import de.thonktank.autosecretary.domain.today.TodayStepPositionUpdate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Focused Room adapter for reusable and materialized step rows and their atomic set results. */
public final class RoomStepRepository implements StepRepository {
    private final StepDao dao;
    private final TaskEntityMapper mapper;
    private final TransactionRunner transactions;

    public RoomStepRepository(AppDatabase database, TransactionRunner transactions) {
        this(database, new TaskEntityMapper(), transactions);
    }

    RoomStepRepository(AppDatabase database, TaskEntityMapper mapper,
                       TransactionRunner transactions) {
        this.dao = database.steps();
        this.mapper = mapper;
        this.transactions = transactions;
    }

    @Override public void insertTemplates(List<TaskStepTemplate> steps) {
        List<TaskStepEntity> entities = new ArrayList<>();
        for (TaskStepTemplate step : steps) entities.add(mapper.toEntity(step));
        if (!entities.isEmpty()) dao.insertTemplates(entities);
    }

    @Override public void updateTemplate(TaskStepTemplate template) {
        dao.updateTemplate(mapper.toEntity(template));
    }

    @Override public void deleteTemplates(TaskId taskId) { dao.deleteTemplates(taskId.value); }

    @Override public void deleteTemplate(String id) { dao.deleteTemplate(id); }

    @Override public List<TaskStepTemplate> templates(TaskId taskId) {
        List<TaskStepTemplate> result = new ArrayList<>();
        for (TaskStepEntity entity : dao.templates(taskId.value)) result.add(mapper.toDomain(entity));
        return result;
    }

    @Override public TaskStepTemplate findTemplate(String id) {
        TaskStepEntity entity = dao.template(id);
        return entity == null ? null : mapper.toDomain(entity);
    }

    @Override public List<TaskStepTemplate> templatesFor(List<TaskId> taskIds) {
        if (taskIds.isEmpty()) return new ArrayList<>();
        List<String> values = new ArrayList<>();
        for (TaskId id : taskIds) values.add(id.value);
        List<TaskStepTemplate> result = new ArrayList<>();
        for (TaskStepEntity entity : dao.templatesFor(values)) result.add(mapper.toDomain(entity));
        return result;
    }

    @Override public void insertOccurrenceSteps(List<OccurrenceStep> steps) {
        transactions.inTransaction(() -> {
            List<OccurrenceStepEntity> entities = new ArrayList<>();
            for (OccurrenceStep step : steps) entities.add(mapper.toEntity(step));
            if (!entities.isEmpty()) dao.insertOccurrenceSteps(entities);
            List<RepetitionResultEntity> results = new ArrayList<>();
            for (OccurrenceStep step : steps) {
                List<Integer> values = step.repetitionProgress == null
                        ? java.util.Collections.emptyList()
                        : step.repetitionProgress.repetitions();
                for (int index = 0; index < values.size(); index++)
                    results.add(new RepetitionResultEntity(step.id, index, values.get(index)));
            }
            if (!results.isEmpty()) dao.putRepetitionResults(results);
            return null;
        });
    }

    @Override public List<OccurrenceStep> occurrenceSteps(String occurrenceId) {
        return mapOccurrenceSteps(dao.occurrenceSteps(occurrenceId));
    }

    @Override public List<OccurrenceStep> occurrenceStepsFor(List<String> occurrenceIds) {
        if (occurrenceIds.isEmpty()) return new ArrayList<>();
        return mapOccurrenceSteps(dao.occurrenceStepsFor(occurrenceIds));
    }

    @Override public OccurrenceStep findOccurrenceStep(String id) {
        OccurrenceStepEntity entity = dao.occurrenceStep(id);
        return entity == null ? null : mapper.toDomain(entity, results(id));
    }

    @Override public void updateOccurrenceStep(OccurrenceStep step) {
        transactions.inTransaction(() -> {
            dao.updateOccurrenceStep(mapper.toEntity(step));
            syncResults(step);
            return null;
        });
    }

    @Override public void deleteOccurrenceStep(String id) { dao.deleteOccurrenceStep(id); }

    @Override public void updateOccurrenceStepPositions(List<TodayStepPositionUpdate> updates) {
        for (TodayStepPositionUpdate update : updates)
            dao.updateOccurrenceStepPosition(update.stepId, update.position);
    }

    private List<OccurrenceStep> mapOccurrenceSteps(List<OccurrenceStepEntity> entities) {
        if (entities.isEmpty()) return new ArrayList<>();
        List<String> ids = new ArrayList<>();
        Map<String, List<SetResult>> results = new LinkedHashMap<>();
        for (OccurrenceStepEntity entity : entities) {
            ids.add(entity.id);
            results.put(entity.id, new ArrayList<>());
        }
        for (RepetitionResultEntity result : dao.repetitionResultsFor(ids)) {
            List<SetResult> values = results.get(result.stepId);
            if (values == null) continue;
            if (result.slotIndex == values.size()) values.add(setResult(result));
            else android.util.Log.w("RoomStepRepository", "Ignoring non-contiguous result for "
                    + result.stepId + " at slot " + result.slotIndex);
        }
        List<OccurrenceStep> result = new ArrayList<>();
        for (OccurrenceStepEntity entity : entities)
            result.add(mapper.toDomain(entity, results.get(entity.id)));
        return result;
    }

    private List<SetResult> results(String stepId) {
        List<SetResult> result = new ArrayList<>();
        for (RepetitionResultEntity value : dao.repetitionResults(stepId)) {
            if (value.slotIndex == result.size()) result.add(setResult(value));
            else android.util.Log.w("RoomStepRepository", "Ignoring non-contiguous result for "
                    + stepId + " at slot " + value.slotIndex);
        }
        return result;
    }

    private void syncResults(OccurrenceStep step) {
        List<SetResult> desired = step.repetitionProgress == null
                ? java.util.Collections.emptyList() : step.repetitionProgress.results;
        List<RepetitionResultEntity> stored = dao.repetitionResults(step.id);
        for (int index = 0; index < desired.size(); index++) {
            SetResult value = desired.get(index);
            if (index >= stored.size() || stored.get(index).slotIndex != index
                    || !value.equals(setResult(stored.get(index))))
                dao.putRepetitionResult(entity(step.id, index, value));
        }
        dao.deleteRepetitionResultsFrom(step.id, desired.size());
    }

    private static SetResult setResult(RepetitionResultEntity value) {
        ResistanceLoad load = ResistanceLoad.restore(value.loadMode, value.loadUnit,
                value.loadMilli);
        TrainingObservation.Origin origin = enumValue(TrainingObservation.Origin.class,
                value.source, TrainingObservation.Origin.LEGACY);
        TrainingObservation.Safety safety = enumValue(TrainingObservation.Safety.class,
                value.safetyFlag, TrainingObservation.Safety.NONE);
        TrainingObservation observation = load.mode == ResistanceLoad.Mode.UNSPECIFIED
                && value.rir == null && origin == TrainingObservation.Origin.LEGACY
                && safety == TrainingObservation.Safety.NONE ? null
                : new TrainingObservation(load, value.rir, safety, origin);
        return SetResult.restore(value.actualRepetitions, observation);
    }

    private static RepetitionResultEntity entity(String stepId, int index, SetResult value) {
        if (value.training == null)
            return new RepetitionResultEntity(stepId, index, value.repetitions);
        TrainingObservation training = value.training;
        return new RepetitionResultEntity(stepId, index, value.repetitions,
                training.load.mode.name(), training.load.unit.name(), training.load.milliUnits,
                training.rir, training.origin.name(), training.safety.name());
    }

    private static <T extends Enum<T>> T enumValue(Class<T> type, String value, T fallback) {
        try { return Enum.valueOf(type, value); }
        catch (RuntimeException invalid) { return fallback; }
    }
}

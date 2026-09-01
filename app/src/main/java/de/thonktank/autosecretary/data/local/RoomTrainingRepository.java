package de.thonktank.autosecretary.data.local;

import de.thonktank.autosecretary.AppDatabase;
import de.thonktank.autosecretary.domain.model.ResistanceLoad;
import de.thonktank.autosecretary.domain.model.StepAmount;
import de.thonktank.autosecretary.domain.model.TrainingAdjustment;
import de.thonktank.autosecretary.domain.model.TrainingDecision;
import de.thonktank.autosecretary.domain.model.TrainingLoadRequest;
import de.thonktank.autosecretary.domain.model.TrainingMuscleGroup;
import de.thonktank.autosecretary.domain.repository.TrainingRepository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Focused Room adapter for training prescriptions, decisions and audit history. */
public final class RoomTrainingRepository implements TrainingRepository {
    private final TrainingDao dao;

    public RoomTrainingRepository(AppDatabase database) {
        this.dao = database.training();
    }

    @Override public double effectiveSetsSince(TrainingMuscleGroup muscle, LocalDate start,
                                               LocalDate end) {
        if (muscle == null) return 0;
        String first = start.toString();
        String last = end.toString();
        return dao.effectivePrimarySets(muscle.name(), first, last)
                + dao.effectiveSecondarySets(muscle.name(), first, last) * 0.5;
    }

    @Override public void insertTrainingAdjustment(TrainingAdjustment adjustment) {
        dao.insertTrainingAdjustment(adjustment(adjustment));
    }

    @Override public TrainingAdjustment latestTrainingAdjustment(String templateId) {
        TrainingAdjustmentEntity value = dao.latestTrainingAdjustment(templateId);
        return value == null ? null : adjustment(value);
    }

    @Override public List<TrainingAdjustment> recentTrainingAdjustments(String templateId,
                                                                        int limit) {
        List<TrainingAdjustment> result = new ArrayList<>();
        for (TrainingAdjustmentEntity value : dao.recentTrainingAdjustments(templateId, limit))
            result.add(adjustment(value));
        return result;
    }

    @Override public void updateTrainingAdjustment(TrainingAdjustment adjustment) {
        dao.updateTrainingAdjustment(adjustment(adjustment));
    }

    @Override public long nextTrainingAuditOrder() {
        return Math.max(dao.maximumTrainingAdjustmentOrder(),
                dao.maximumTrainingLoadRequestOrder()) + 1;
    }

    @Override public void insertTrainingLoadRequest(TrainingLoadRequest request) {
        dao.insertTrainingLoadRequest(loadRequest(request));
    }

    @Override public TrainingLoadRequest openTrainingLoadRequest(String templateId) {
        TrainingLoadRequestEntity value = dao.openTrainingLoadRequest(templateId);
        return value == null ? null : loadRequest(value);
    }

    @Override public List<TrainingLoadRequest> recentTrainingLoadRequests(String templateId,
                                                                          int limit) {
        List<TrainingLoadRequest> result = new ArrayList<>();
        for (TrainingLoadRequestEntity value : dao.recentTrainingLoadRequests(templateId, limit))
            result.add(loadRequest(value));
        return result;
    }

    @Override public void updateTrainingLoadRequest(TrainingLoadRequest request) {
        dao.updateTrainingLoadRequest(loadRequest(request));
    }

    private static TrainingAdjustmentEntity adjustment(TrainingAdjustment value) {
        return new TrainingAdjustmentEntity(value.id, value.templateId,
                value.sourceOccurrenceStepId, value.reason.name(), value.before.sets,
                value.before.repetitions, value.beforeLoad.mode.name(),
                value.beforeLoad.unit.name(), value.beforeLoad.milliUnits, value.after.sets,
                value.after.repetitions, value.afterLoad.mode.name(), value.afterLoad.unit.name(),
                value.afterLoad.milliUnits, value.createdOn.toString(), value.state.name(),
                value.auditOrder, value.ruleVersion);
    }

    private static TrainingAdjustment adjustment(TrainingAdjustmentEntity value) {
        return new TrainingAdjustment(value.id, value.templateId, value.sourceOccurrenceStepId,
                TrainingDecision.Reason.valueOf(value.reason),
                (StepAmount.SetsReps) StepAmount.setsReps(value.beforeSets, value.beforeReps),
                ResistanceLoad.restore(value.beforeLoadMode, value.beforeLoadUnit,
                        value.beforeLoadMilli),
                (StepAmount.SetsReps) StepAmount.setsReps(value.afterSets, value.afterReps),
                ResistanceLoad.restore(value.afterLoadMode, value.afterLoadUnit,
                        value.afterLoadMilli), LocalDate.parse(value.createdOn),
                TrainingAdjustment.State.valueOf(value.state), value.auditOrder,
                value.ruleVersion);
    }

    private static TrainingLoadRequestEntity loadRequest(TrainingLoadRequest value) {
        return new TrainingLoadRequestEntity(value.id, value.templateId,
                value.sourceOccurrenceStepId, value.direction.name(),
                value.currentLoad.mode.name(), value.currentLoad.unit.name(),
                value.currentLoad.milliUnits, value.createdOn.toString(), value.auditOrder,
                value.ruleVersion, value.state.name(), value.resolution.name(),
                value.resolvedOn == null ? null : value.resolvedOn.toString());
    }

    private static TrainingLoadRequest loadRequest(TrainingLoadRequestEntity value) {
        return new TrainingLoadRequest(value.id, value.templateId,
                value.sourceOccurrenceStepId,
                TrainingDecision.LoadDirection.valueOf(value.direction),
                ResistanceLoad.restore(value.currentLoadMode, value.currentLoadUnit,
                        value.currentLoadMilli), LocalDate.parse(value.createdOn),
                value.auditOrder, value.ruleVersion,
                TrainingLoadRequest.State.valueOf(value.state),
                TrainingLoadRequest.Resolution.valueOf(value.resolution),
                value.resolvedOn == null ? null : LocalDate.parse(value.resolvedOn));
    }
}

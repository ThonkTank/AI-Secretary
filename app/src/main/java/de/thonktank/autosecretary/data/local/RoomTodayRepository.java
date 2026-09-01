package de.thonktank.autosecretary.data.local;

import de.thonktank.autosecretary.AppDatabase;
import de.thonktank.autosecretary.domain.model.ComboDecayEvent;
import de.thonktank.autosecretary.domain.model.ComboObligation;
import de.thonktank.autosecretary.domain.model.ComboProgress;
import de.thonktank.autosecretary.domain.model.Occurrence;
import de.thonktank.autosecretary.domain.model.OccurrenceState;
import de.thonktank.autosecretary.domain.model.RewardBooking;
import de.thonktank.autosecretary.domain.model.TaskId;
import de.thonktank.autosecretary.domain.model.TaskSlot;
import de.thonktank.autosecretary.domain.repository.TodayRepository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Room adapter for occurrences, materialization, rewards, combos and obligations. */
public final class RoomTodayRepository implements TodayRepository {
    private final TodayDao dao;
    private final TaskEntityMapper mapper = new TaskEntityMapper();

    public RoomTodayRepository(AppDatabase database) { this.dao = database.today(); }

    @Override public void insertOccurrence(Occurrence value) {
        dao.insertOccurrence(mapper.toEntity(value));
    }
    @Override public void updateOccurrence(Occurrence value) {
        dao.updateOccurrence(mapper.toEntity(value));
    }
    @Override public void deleteOccurrence(String id) { dao.deleteOccurrence(id); }
    @Override public Occurrence findOccurrence(String id) {
        OccurrenceEntity value = dao.occurrence(id);
        return value == null ? null : mapper.toDomain(value);
    }
    @Override public Occurrence findOccurrence(TaskId taskId, LocalDate scheduledOn,
                                               TaskSlot slot) {
        OccurrenceEntity value = dao.occurrence(taskId.value, scheduledOn.toString(),
                slot.storageCode);
        return value == null ? null : mapper.toDomain(value);
    }
    @Override public List<Occurrence> openOccurrences(TaskId taskId, LocalDate scheduledOn) {
        return mapOccurrences(dao.occurrences(taskId.value, scheduledOn.toString(),
                OccurrenceState.OPEN.storageCode()));
    }
    @Override public List<Occurrence> openOccurrences(TaskId taskId) {
        return mapOccurrences(dao.openOccurrencesForTask(taskId.value,
                OccurrenceState.OPEN.storageCode()));
    }
    @Override public Occurrence openOccurrence(TaskId taskId, TaskSlot slot) {
        OccurrenceEntity value = dao.openForTaskSlot(taskId.value, slot.storageCode,
                OccurrenceState.OPEN.storageCode());
        return value == null ? null : mapper.toDomain(value);
    }
    @Override public Occurrence openOccurrence(TaskId taskId) {
        OccurrenceEntity value = dao.openForTask(taskId.value, OccurrenceState.OPEN.storageCode());
        return value == null ? null : mapper.toDomain(value);
    }
    @Override public List<Occurrence> openOccurrences() {
        return mapOccurrences(dao.occurrencesByState(OccurrenceState.OPEN.storageCode()));
    }
    @Override public List<Occurrence> openOccurrences(TaskSlot slot) {
        return mapOccurrences(dao.occurrencesByStateAndSlot(OccurrenceState.OPEN.storageCode(),
                slot.storageCode));
    }
    @Override public List<Occurrence> allOccurrences() {
        return mapOccurrences(dao.allOccurrences());
    }
    @Override public List<Occurrence> occurrences(TaskId taskId) {
        return mapOccurrences(dao.occurrencesForTask(taskId.value));
    }
    @Override public Occurrence earliestOpenOccurrence(TaskId taskId) {
        OccurrenceEntity value = dao.earliestOccurrence(taskId.value,
                OccurrenceState.OPEN.storageCode());
        return value == null ? null : mapper.toDomain(value);
    }
    @Override public Occurrence latestCompletedOccurrence(TaskId taskId) {
        OccurrenceEntity value = dao.latestCompletedOccurrence(taskId.value, harvestedStates());
        return value == null ? null : mapper.toDomain(value);
    }
    @Override public List<Occurrence> completedOccurrences(LocalDate date) {
        return mapOccurrences(dao.completedOccurrences(harvestedStates(), date.toString()));
    }
    @Override public void assignRewardBookings(String occurrenceStepId, String occurrenceId) {
        List<RewardAssignmentEntity> assignments = new ArrayList<>();
        for (String bookingId : dao.rewardBookingIds(occurrenceStepId))
            assignments.add(new RewardAssignmentEntity(bookingId, occurrenceId));
        if (!assignments.isEmpty()) dao.putRewardAssignments(assignments);
    }
    @Override public int xp() {
        StatsEntity value = dao.stats();
        return value == null ? 0 : value.xp;
    }
    @Override public void setXp(int xp) { dao.putStats(new StatsEntity(Math.max(0, xp))); }
    @Override public ComboProgress combo(String ownerId) {
        ComboEntity value = dao.combo(ownerId);
        return value == null ? null : combo(value);
    }
    @Override public void putCombo(ComboProgress value) {
        dao.putCombo(new ComboEntity(value.ownerId, value.taskId.value, value.kind.name(),
                value.points, value.settledThroughOn == null ? ""
                : value.settledThroughOn.toString()));
    }
    @Override public List<ComboProgress> combos() {
        List<ComboProgress> result = new ArrayList<>();
        for (ComboEntity value : dao.allCombos()) result.add(combo(value));
        return result;
    }
    @Override public void insertRewardBooking(RewardBooking value) {
        dao.insertRewardBooking(new RewardBookingEntity(value.id, value.transactionId,
                value.occurrenceId, value.occurrenceStepId, value.ownerId, value.kind.name(),
                value.target.name(), value.xpDelta, value.comboPointDelta,
                value.bookedOn.toString(), value.reversesBookingId, value.plannedXp));
    }
    @Override public List<RewardBooking> rewardBookings(String occurrenceId) {
        return mapBookings(dao.rewardBookings(occurrenceId));
    }
    @Override public List<RewardBooking> rewardBookings(List<String> occurrenceIds) {
        if (occurrenceIds.isEmpty()) return new ArrayList<>();
        return mapBookings(dao.rewardBookings(occurrenceIds));
    }
    @Override public List<ComboObligation> comboObligations() {
        List<ComboObligation> result = new ArrayList<>();
        for (ComboObligationEntity value : dao.comboObligations())
            result.add(new ComboObligation(value.id, value.ownerId, TaskId.of(value.taskId),
                    ComboProgress.Kind.valueOf(value.kind), TaskSlot.fromStorage(value.slot),
                    LocalDate.parse(value.scheduledOn), value.occurrenceId,
                    ComboObligation.State.valueOf(value.state),
                    value.resolvedOn == null ? null : LocalDate.parse(value.resolvedOn)));
        return result;
    }
    @Override public void insertComboObligations(List<ComboObligation> obligations) {
        List<ComboObligationEntity> values = new ArrayList<>();
        for (ComboObligation obligation : obligations) values.add(obligation(obligation));
        if (!values.isEmpty()) dao.insertComboObligations(values);
    }
    @Override public void updateComboObligation(ComboObligation value) {
        dao.updateComboObligation(obligation(value));
    }
    @Override public ComboDecayEvent comboDecayEvent(String ownerId, LocalDate eventOn) {
        ComboDecayEventEntity value = dao.comboDecayEvent(ownerId, eventOn.toString());
        return value == null ? null : new ComboDecayEvent(value.ownerId,
                LocalDate.parse(value.eventOn), value.bookingId);
    }
    @Override public void insertComboDecayEvent(ComboDecayEvent value) {
        dao.insertComboDecayEvent(new ComboDecayEventEntity(value.ownerId,
                value.eventOn.toString(), value.bookingId));
    }

    private static List<String> harvestedStates() {
        List<String> result = new ArrayList<>();
        result.add(OccurrenceState.COMPLETED.storageCode());
        result.add(OccurrenceState.HARVESTED_WITH_MISSED_STEPS.storageCode());
        return result;
    }
    private List<Occurrence> mapOccurrences(List<OccurrenceEntity> values) {
        List<Occurrence> result = new ArrayList<>();
        for (OccurrenceEntity value : values) result.add(mapper.toDomain(value));
        return result;
    }
    private static ComboProgress combo(ComboEntity value) {
        return new ComboProgress(value.ownerId, TaskId.of(value.taskId),
                ComboProgress.Kind.valueOf(value.kind), Math.max(0, value.points),
                value.settledThroughOn.isEmpty() ? null : LocalDate.parse(value.settledThroughOn));
    }
    private static List<RewardBooking> mapBookings(List<RewardBookingEntity> values) {
        List<RewardBooking> result = new ArrayList<>();
        for (RewardBookingEntity value : values)
            result.add(new RewardBooking(value.id, value.transactionId, value.occurrenceId,
                    value.occurrenceStepId, value.ownerId, RewardBooking.Kind.valueOf(value.kind),
                    RewardBooking.Target.valueOf(value.target), value.xpDelta,
                    value.comboPointDelta, LocalDate.parse(value.bookedOn),
                    value.reversesBookingId, value.plannedXp));
        return result;
    }
    private static ComboObligationEntity obligation(ComboObligation value) {
        return new ComboObligationEntity(value.id, value.ownerId, value.taskId.value,
                value.kind.name(), value.slot.storageCode, value.scheduledOn.toString(),
                value.occurrenceId, value.state.name(),
                value.resolvedOn == null ? null : value.resolvedOn.toString());
    }
}

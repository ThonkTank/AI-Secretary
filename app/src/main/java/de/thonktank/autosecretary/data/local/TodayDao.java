package de.thonktank.autosecretary.data.local;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface TodayDao {
    @Query("DELETE FROM today_placements WHERE "
            + "(kind = 'OCCURRENCE' AND NOT EXISTS (SELECT 1 FROM occurrences o JOIN tasks t "
            + "ON t.id=o.taskId WHERE o.id=today_placements.id AND o.state='OPEN' "
            + "AND t.archived=0 AND t.conditionDone=0)) OR "
            + "(kind = 'TASK' AND NOT EXISTS (SELECT 1 FROM tasks t WHERE t.id=today_placements.id "
            + "AND t.archived=0 AND t.conditionDone=0)) OR "
            + "(kind = 'FLOW_TASK_SHEET' AND NOT EXISTS (SELECT 1 FROM flow_task_sheet_placements p "
            + "JOIN tasks t ON t.id=p.taskId WHERE p.id=today_placements.id AND t.archived=0 "
            + "AND t.conditionDone=0 AND (EXISTS (SELECT 1 FROM flow_candidates c "
            + "WHERE c.taskId=p.taskId AND c.slot=p.slot) OR EXISTS (SELECT 1 FROM step_flow_runs r "
            + "WHERE r.taskId=p.taskId AND r.slot=p.slot AND r.collected=0 AND r.cancelled=0))))")
    void pruneTodayPlacements();
    @androidx.room.Transaction
    default List<TodayPlacementEntity> activeTodayPlacements() {
        List<TodayPlacementEntity> current = todayPlacements();
        if (current.isEmpty()) return current;
        pruneTodayPlacements();
        return todayPlacements();
    }
    @Query("SELECT * FROM today_placements") List<TodayPlacementEntity> todayPlacements();
    @Insert(onConflict = OnConflictStrategy.REPLACE) void putTodayPlacement(TodayPlacementEntity value);
    @Query("DELETE FROM today_placements WHERE kind = :kind AND id = :id")
    void deleteTodayPlacement(String kind, String id);
    @Insert(onConflict = OnConflictStrategy.IGNORE) void insertOccurrence(OccurrenceEntity value);
    @Update void updateOccurrence(OccurrenceEntity occurrence);
    @Query("DELETE FROM occurrences WHERE id = :id") void deleteOccurrence(String id);
    @Query("SELECT * FROM occurrences WHERE id = :id LIMIT 1") OccurrenceEntity occurrence(String id);
    @Query("SELECT * FROM occurrences WHERE taskId = :taskId AND scheduledOn = :scheduledOn "
            + "AND slot = :slot LIMIT 1")
    OccurrenceEntity occurrence(String taskId, String scheduledOn, String slot);
    @Query("SELECT * FROM occurrences WHERE taskId = :taskId AND state = :state "
            + "AND kind != 'FLOW_STEP' LIMIT 1")
    OccurrenceEntity openForTask(String taskId, String state);
    @Query("SELECT * FROM occurrences WHERE taskId = :taskId AND slot = :slot "
            + "AND state = :state AND kind != 'FLOW_STEP' ORDER BY scheduledOn LIMIT 1")
    OccurrenceEntity openForTaskSlot(String taskId, String slot, String state);
    @Query("SELECT * FROM occurrences WHERE taskId = :taskId AND state = :state "
            + "AND kind != 'FLOW_STEP' ORDER BY scheduledOn, slot")
    List<OccurrenceEntity> openOccurrencesForTask(String taskId, String state);
    @Query("SELECT * FROM occurrences WHERE taskId = :taskId AND scheduledOn = :scheduledOn "
            + "AND state = :state AND kind != 'FLOW_STEP'")
    List<OccurrenceEntity> occurrences(String taskId, String scheduledOn, String state);
    @Query("SELECT * FROM occurrences WHERE state = :state")
    List<OccurrenceEntity> occurrencesByState(String state);
    @Query("SELECT * FROM occurrences WHERE state = :state AND slot = :slot "
            + "ORDER BY sortOrder, scheduledOn, id")
    List<OccurrenceEntity> occurrencesByStateAndSlot(String state, String slot);
    @Query("SELECT * FROM occurrences") List<OccurrenceEntity> allOccurrences();
    @Query("SELECT * FROM occurrences WHERE taskId = :taskId")
    List<OccurrenceEntity> occurrencesForTask(String taskId);
    @Query("SELECT * FROM occurrences WHERE taskId = :taskId AND state = :state "
            + "AND kind != 'FLOW_STEP' ORDER BY scheduledOn ASC LIMIT 1")
    OccurrenceEntity earliestOccurrence(String taskId, String state);
    @Query("SELECT * FROM occurrences WHERE taskId = :taskId AND state IN (:states) "
            + "AND kind != 'FLOW_STEP' ORDER BY completedOn DESC, scheduledOn DESC LIMIT 1")
    OccurrenceEntity latestCompletedOccurrence(String taskId, List<String> states);
    @Query("SELECT * FROM occurrences WHERE state IN (:states) AND completedOn = :date")
    List<OccurrenceEntity> completedOccurrences(List<String> states, String date);
    @Insert(onConflict = OnConflictStrategy.REPLACE) void putStats(StatsEntity stats);
    @Query("SELECT * FROM stats WHERE id = 1") StatsEntity stats();
    @Insert(onConflict = OnConflictStrategy.REPLACE) void putCombo(ComboEntity combo);
    @Query("SELECT * FROM combo_progress WHERE ownerId = :ownerId LIMIT 1")
    ComboEntity combo(String ownerId);
    @Query("SELECT * FROM combo_progress") List<ComboEntity> allCombos();
    @Insert(onConflict = OnConflictStrategy.ABORT)
    void insertRewardBooking(RewardBookingEntity booking);
    @Query("SELECT * FROM reward_bookings WHERE id = :id LIMIT 1")
    RewardBookingEntity ledgerRewardBooking(String id);
    @Query("SELECT id FROM reward_bookings WHERE occurrenceStepId = :occurrenceStepId")
    List<String> rewardBookingIds(String occurrenceStepId);
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void putRewardAssignments(List<RewardAssignmentEntity> assignments);
    @Query("SELECT rb.id,rb.transactionId,"
            + "COALESCE(ra.occurrenceId,rb.occurrenceId) AS occurrenceId,"
            + "rb.occurrenceStepId,rb.ownerId,rb.kind,rb.target,rb.xpDelta,"
            + "rb.comboPointDelta,rb.bookedOn,rb.reversesBookingId,rb.plannedXp "
            + "FROM reward_bookings rb LEFT JOIN reward_assignments ra ON ra.bookingId=rb.id "
            + "WHERE COALESCE(ra.occurrenceId,rb.occurrenceId)=:occurrenceId "
            + "ORDER BY rb.bookedOn,rb.id")
    List<RewardBookingEntity> rewardBookings(String occurrenceId);
    @Query("SELECT rb.id,rb.transactionId,"
            + "COALESCE(ra.occurrenceId,rb.occurrenceId) AS occurrenceId,"
            + "rb.occurrenceStepId,rb.ownerId,rb.kind,rb.target,rb.xpDelta,"
            + "rb.comboPointDelta,rb.bookedOn,rb.reversesBookingId,rb.plannedXp "
            + "FROM reward_bookings rb LEFT JOIN reward_assignments ra ON ra.bookingId=rb.id "
            + "WHERE COALESCE(ra.occurrenceId,rb.occurrenceId) IN (:occurrenceIds) "
            + "ORDER BY rb.bookedOn,rb.id")
    List<RewardBookingEntity> rewardBookings(List<String> occurrenceIds);
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insertComboObligations(List<ComboObligationEntity> obligations);
    @Update void updateComboObligation(ComboObligationEntity obligation);
    @Query("SELECT * FROM combo_obligations ORDER BY scheduledOn,slot,ownerId")
    List<ComboObligationEntity> comboObligations();
    @Query("SELECT * FROM combo_decay_events WHERE ownerId = :ownerId "
            + "AND eventOn = :eventOn LIMIT 1")
    ComboDecayEventEntity comboDecayEvent(String ownerId, String eventOn);
    @Insert(onConflict = OnConflictStrategy.ABORT)
    void insertComboDecayEvent(ComboDecayEventEntity event);
}

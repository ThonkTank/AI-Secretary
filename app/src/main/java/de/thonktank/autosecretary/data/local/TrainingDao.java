package de.thonktank.autosecretary.data.local;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface TrainingDao {
    @Query("SELECT COUNT(*) FROM repetition_results rr JOIN occurrence_steps os ON os.id=rr.stepId "
            + "JOIN occurrences o ON o.id=os.occurrenceId "
            + "JOIN task_steps ts ON ts.id=os.sourceTemplateId "
            + "WHERE rr.source='USER' AND ts.primaryMuscle=:muscle "
            + "AND o.scheduledOn BETWEEN :start AND :end")
    int effectivePrimarySets(String muscle, String start, String end);
    @Query("SELECT COUNT(*) FROM repetition_results rr JOIN occurrence_steps os ON os.id=rr.stepId "
            + "JOIN occurrences o ON o.id=os.occurrenceId "
            + "JOIN task_steps ts ON ts.id=os.sourceTemplateId WHERE rr.source='USER' "
            + "AND (',' || ts.secondaryMuscles || ',') LIKE ('%,' || :muscle || ',%') "
            + "AND o.scheduledOn BETWEEN :start AND :end")
    int effectiveSecondarySets(String muscle, String start, String end);
    @Insert(onConflict = OnConflictStrategy.ABORT)
    void insertTrainingAdjustment(TrainingAdjustmentEntity adjustment);
    @Update void updateTrainingAdjustment(TrainingAdjustmentEntity adjustment);
    @Query("SELECT * FROM training_adjustments WHERE templateId=:templateId "
            + "ORDER BY auditOrder DESC LIMIT 1")
    TrainingAdjustmentEntity latestTrainingAdjustment(String templateId);
    @Query("SELECT * FROM training_adjustments WHERE templateId=:templateId "
            + "ORDER BY auditOrder DESC LIMIT :limit")
    List<TrainingAdjustmentEntity> recentTrainingAdjustments(String templateId, int limit);
    @Query("SELECT COALESCE(MAX(auditOrder),0) FROM training_adjustments")
    long maximumTrainingAdjustmentOrder();
    @Insert(onConflict = OnConflictStrategy.ABORT)
    void insertTrainingLoadRequest(TrainingLoadRequestEntity request);
    @Update void updateTrainingLoadRequest(TrainingLoadRequestEntity request);
    @Query("SELECT * FROM training_load_requests WHERE templateId=:templateId "
            + "AND state='OPEN' ORDER BY auditOrder DESC LIMIT 1")
    TrainingLoadRequestEntity openTrainingLoadRequest(String templateId);
    @Query("SELECT * FROM training_load_requests WHERE templateId=:templateId "
            + "ORDER BY auditOrder DESC LIMIT :limit")
    List<TrainingLoadRequestEntity> recentTrainingLoadRequests(String templateId, int limit);
    @Query("SELECT COALESCE(MAX(auditOrder),0) FROM training_load_requests")
    long maximumTrainingLoadRequestOrder();
}

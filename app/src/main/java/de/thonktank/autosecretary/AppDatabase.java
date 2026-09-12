package de.thonktank.autosecretary;

import de.thonktank.autosecretary.data.local.ComboEntity;
import de.thonktank.autosecretary.data.local.OccurrenceEntity;
import de.thonktank.autosecretary.data.local.OccurrenceStepEntity;
import de.thonktank.autosecretary.data.local.RepetitionResultEntity;
import de.thonktank.autosecretary.data.local.RewardAssignmentEntity;
import de.thonktank.autosecretary.data.local.RewardBookingEntity;
import de.thonktank.autosecretary.data.local.StatsEntity;
import de.thonktank.autosecretary.data.local.CatalogDao;
import de.thonktank.autosecretary.data.local.FlowDao;
import de.thonktank.autosecretary.data.local.StepDao;
import de.thonktank.autosecretary.data.local.TodayDao;
import de.thonktank.autosecretary.data.local.TrainingDao;
import de.thonktank.autosecretary.data.local.TaskEntity;
import de.thonktank.autosecretary.data.local.TaskScheduleEntity;
import de.thonktank.autosecretary.data.local.TaskStepEntity;
import de.thonktank.autosecretary.data.local.TimerSessionEntity;
import de.thonktank.autosecretary.data.local.TimerSessionDao;
import de.thonktank.autosecretary.data.local.ComboObligationEntity;
import de.thonktank.autosecretary.data.local.ComboDecayEventEntity;
import de.thonktank.autosecretary.data.local.CapacityResourceEntity;
import de.thonktank.autosecretary.data.local.FlowRunResourceEntity;
import de.thonktank.autosecretary.data.local.FlowRunStepEntity;
import de.thonktank.autosecretary.data.local.FlowCandidateEntity;
import de.thonktank.autosecretary.data.local.FlowTaskSheetPlacementEntity;
import de.thonktank.autosecretary.data.local.StepFlowRunEntity;
import de.thonktank.autosecretary.data.local.StepResourceLeaseEntity;
import de.thonktank.autosecretary.data.local.StepTransitionEntity;
import de.thonktank.autosecretary.data.local.TrainingAdjustmentEntity;
import de.thonktank.autosecretary.data.local.TrainingLoadRequestEntity;

import androidx.room.Database;
import androidx.room.RoomDatabase;

@Database(entities = {de.thonktank.autosecretary.data.local.MigrationRecoveryEntity.class, de.thonktank.autosecretary.data.local.TodayPlacementEntity.class, TaskEntity.class, TaskStepEntity.class, OccurrenceEntity.class,
        OccurrenceStepEntity.class, StatsEntity.class, ComboEntity.class,
        RewardBookingEntity.class, RewardAssignmentEntity.class,
        RepetitionResultEntity.class, TaskScheduleEntity.class,
        TimerSessionEntity.class, ComboObligationEntity.class, ComboDecayEventEntity.class,
        CapacityResourceEntity.class, de.thonktank.autosecretary.data.local.FlowDefinitionEdgeEntity.class,
        de.thonktank.autosecretary.data.local.FlowStepWaitEntity.class,
        de.thonktank.autosecretary.data.local.FlowEditorSaveEntity.class,
        StepResourceLeaseEntity.class, de.thonktank.autosecretary.data.local.GraphRunEntity.class,
        de.thonktank.autosecretary.data.local.GraphRunStepEntity.class,
        de.thonktank.autosecretary.data.local.GraphRunResourceEntity.class,
        de.thonktank.autosecretary.data.local.GraphRunEdgeEntity.class, FlowCandidateEntity.class,
        FlowTaskSheetPlacementEntity.class, TrainingAdjustmentEntity.class,
        TrainingLoadRequestEntity.class},
        version = DatabaseContract.VERSION,
        exportSchema = true)
public abstract class AppDatabase extends RoomDatabase {
    public abstract CatalogDao catalog();
    public abstract StepDao steps();
    public abstract TodayDao today();
    public abstract FlowDao flows();
    public abstract TrainingDao training();
    public abstract TimerSessionDao timers();
}

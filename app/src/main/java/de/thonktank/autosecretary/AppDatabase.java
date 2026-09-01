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
import de.thonktank.autosecretary.data.local.StepFlowRunEntity;
import de.thonktank.autosecretary.data.local.StepResourceLeaseEntity;
import de.thonktank.autosecretary.data.local.StepTransitionEntity;
import de.thonktank.autosecretary.data.local.TrainingAdjustmentEntity;
import de.thonktank.autosecretary.data.local.TrainingLoadRequestEntity;

import androidx.room.Database;
import androidx.room.RoomDatabase;

@Database(entities = {TaskEntity.class, TaskStepEntity.class, OccurrenceEntity.class,
        OccurrenceStepEntity.class, StatsEntity.class, ComboEntity.class,
        RewardBookingEntity.class, RewardAssignmentEntity.class,
        RepetitionResultEntity.class, TaskScheduleEntity.class,
        TimerSessionEntity.class, ComboObligationEntity.class, ComboDecayEventEntity.class,
        CapacityResourceEntity.class, StepTransitionEntity.class,
        StepResourceLeaseEntity.class, StepFlowRunEntity.class,
        FlowRunStepEntity.class, FlowRunResourceEntity.class, TrainingAdjustmentEntity.class,
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

package com.autosecretary.data;

import androidx.room.Database;
import androidx.room.RoomDatabase;

import com.autosecretary.data.entity.CompletionEntity;
import com.autosecretary.data.entity.DayPlanDirectiveEntity;
import com.autosecretary.data.entity.StepCompletionEntity;
import com.autosecretary.data.entity.StepDayEntity;
import com.autosecretary.data.entity.StepEntity;
import com.autosecretary.data.entity.UndoJournalEntity;
import com.autosecretary.data.entity.WorkItemEntity;

/** Current pre-stable Room schema. Targeted migrations start once this format is declared stable. */
@Database(
        entities = {
                WorkItemEntity.class,
                StepEntity.class,
                StepDayEntity.class,
                StepCompletionEntity.class,
                CompletionEntity.class,
                DayPlanDirectiveEntity.class,
                UndoJournalEntity.class
        },
        version = FocusDatabase.VERSION,
        exportSchema = true)
public abstract class FocusDatabase extends RoomDatabase {
    public static final String NAME = "autosecretary.db";
    public static final int VERSION = 35;

    public abstract FocusDao focusDao();
}

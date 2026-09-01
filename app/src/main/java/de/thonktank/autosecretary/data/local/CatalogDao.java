package de.thonktank.autosecretary.data.local;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface CatalogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) void insertTask(TaskEntity task);
    @Update void updateTask(TaskEntity task);
    @Query("SELECT * FROM tasks WHERE archived = 0 AND conditionDone = 0")
    List<TaskEntity> activeTasks();
    @Query("SELECT * FROM tasks") List<TaskEntity> allTasks();
    @Query("SELECT * FROM tasks WHERE id = :id LIMIT 1") TaskEntity task(String id);
    @Query("DELETE FROM tasks WHERE id = :id") void deleteTask(String id);
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void putScheduleEntries(List<TaskScheduleEntity> entries);
    @Query("DELETE FROM task_schedule_entries WHERE id = :id")
    void deleteScheduleEntry(String id);
    @Query("SELECT * FROM task_schedule_entries ORDER BY slot, displayOrder, id")
    List<TaskScheduleEntity> scheduleEntries();
    @Query("SELECT * FROM task_schedule_entries WHERE taskId = :taskId "
            + "ORDER BY slot, displayOrder, id")
    List<TaskScheduleEntity> scheduleEntries(String taskId);
    @Query("SELECT * FROM task_schedule_entries WHERE id = :id LIMIT 1")
    TaskScheduleEntity scheduleEntry(String id);
    @Query("SELECT * FROM task_schedule_entries WHERE slot = :slot ORDER BY displayOrder, id")
    List<TaskScheduleEntity> scheduleEntriesInSlot(String slot);
    @Query("SELECT * FROM task_schedule_entries WHERE taskId IN (:taskIds) "
            + "ORDER BY slot, displayOrder, id")
    List<TaskScheduleEntity> scheduleEntriesFor(List<String> taskIds);
}

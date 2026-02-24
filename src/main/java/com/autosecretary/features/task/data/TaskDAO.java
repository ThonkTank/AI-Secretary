package com.autosecretary.features.task.data;

import androidx.room.Dao; 
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;

import java.util.List;

@Dao
public interface TaskDAO {

    // ============== READ ==============
    @Transaction
    @Query("SELECT * FROM task_core WHERE id = :id")
    Task read(String id);
    @Transaction
    @Query("SELECT * FROM task_core")
    List<Task> readAll();

    // ============== Write ==============
    //Transactions
    @Transaction
    default void writeList(List<Task> tasks) {
        for (Task task : tasks) {
            writeCore(task.core);
        }
        for (Task task : tasks) {
            writeSlots(task.slots);
            writePrefSlots(task.prefSlots);
            writePrerequisites(task.prerequisites);
            for (Task child : task.children) {
                writeRelation(new TaskRelation(task.core.id, child.core.id));
            }
        }
    }

    @Transaction
    default void write(Task task) {
        writeCore(task.core);
        writeSlots(task.slots);
        writePrefSlots(task.prefSlots);
        writePrerequisites(task.prerequisites);
        for (Task child : task.children) {
            writeRelation(new TaskRelation(task.core.id, child.core.id));
        }
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void writeCore(TaskCore core);

    //Pref Slots
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void writePrefSlots(List<TaskPrefSlot> prefSlots);

    //Prerequisites
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void writePrerequisites(List<TaskPrerequisite> prerequisites);

    //Parent
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void writeRelation(TaskRelation relation);

    //Task Slots
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void writeSlots(List<TaskSlot> slots);
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void writeSlot(TaskSlot slots);

    // ============== Delete ==============
    @Query("DELETE FROM task_core WHERE id = :id")
    void deleteCore(String id);
    @Query("DELETE FROM task_core")
    void deleteAllCore();
}

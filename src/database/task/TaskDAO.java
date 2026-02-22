package database.task;

import androidx.room.Dao; 
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;

import java.time.LocalDate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Dao
public interface TaskDAO {

    // ============== READ ==============
    @Transaction
    @Query("SELECT * FROM task_core WHERE id = :id")
    Task read(String id);
    @Transaction
    @Query("SELECT DISTINCT tc.* FROM task_core tc INNER JOIN task_slots ts ON tc.id = ts.taskId WHERE ts.day = :day ORDER BY ts.start")
    List<Task> readByDue(LocalDate day);
    @Transaction
    @Query("SELECT * FROM task_core")
    List<Task> readAll();

    // ============== Write ==============
    //Transactions
       @Transaction
    default void writeList(List<Task> tasks) {
        tasks = Task.flatten(tasks);
        for (Task task : tasks) {
            writeCore(task.core);
        }
        for (Task task : tasks) {
            writeSlots(task.slots);
            writeFollowUps(task.followUps);
            writePrefSlots(task.prefSlots);
            for (Task child : task.children) {
                writeRelation(new TaskRelation(task.core.id, child.core.id));
            }
        }
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void writeCore(TaskCore core);

    //Follow Ups
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void writeFollowUps(List<TaskFollowUp> followUps);

    //Pref Slots
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void writePrefSlots(List<TaskPrefSlot> prefSlots);

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

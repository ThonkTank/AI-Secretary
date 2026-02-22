package database.task;

import androidx.room.Dao; 
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;

import java.time.LocalDate;
import views.models.ViewSlot;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Dao
public interface TaskDAO {

    // ============== READ ==============
    @Transaction
    @Query("SELECT * FROM task_core WHERE id = :id")
    Task read(Long id);
    @Transaction
    @Query("SELECT DISTINCT tc.* FROM task_core tc INNER JOIN task_slots ts ON tc.id = ts.taskId WHERE ts.day = :day ORDER BY ts.start")
    List<Task> readByDue(LocalDate day);
    @Transaction
    @Query("SELECT * FROM task_core")
    List<Task> readAll();

    // ============== Write ==============
    //Transactions
    @Transaction
    default void writeList(List<Task> roots) {
        // Flatten: alle einzigartigen Tasks sammeln
        List<Task> all = Task.flatten(roots);

        // Pass 1: Cores
        for (Task task : all) {
            long id = writeCore(task.core);
            task.setId(id);
        }

        // Pass 2: Parent-Links (IDs existieren jetzt)
        for (Task task : all) {
            for (Task child : task.children) {
                writeRelation(new TaskRelation(task.core.id, child.core.id));
            }
        }

        // Pass 3: Rest
        for (Task task : all) {
            writeFollowUps(task.followUps);
            writePrefSlots(task.prefSlots);
            long[] slotIds = writeSlots(task.slots);
            for (int i = 0; i < task.slots.size(); i++) {
                task.slots.get(i).id = slotIds[i];
            }
        }
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long writeCore(TaskCore core);

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
    long[] writeSlots(List<TaskSlot> slots);
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void writeSlot(TaskSlot slots);

    // ============== Delete ==============
    @Query("DELETE FROM task_core WHERE id = :id")
    void deleteCore(long id);
    @Query("DELETE FROM task_core")
    void deleteAllCore();
}

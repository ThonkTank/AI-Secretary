package database.task;

import androidx.room.Dao; 
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;

import java.time.LocalDate;
import views.models.ViewSlot;
import java.util.List;

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

    //ViewSlot
    @Transaction
    @Query("SELECT * FROM task_slots WHERE day = :day ORDER BY start")
    List<ViewSlot> readSlotsForDay(LocalDate day);

    // ============== Write ==============
    //Transactions
    @Transaction
    default void write(Task task) {
        long id = writeCore(task.core);
        task.setId(id);
        writeFollowUps(task.followUps);
        writePrefSlots(task.prefSlots);
        long[] slotIds = writeSlots(task.slots);
        for (int i = 0; i <task.slots.size(); i++) {
            task.slots.get(i).id = slotIds[i];
        }
        for (Task child : task.children) {
            for (TaskSlot slot : child.slots) {
                if (slot.parentSlot != null) {
                    slot.parentSlotId = slot.parentSlot.id;
                }
            }
            write(child);
        }
    }

    @Transaction
    default void writeList(List<Task> tasks) {
        for (Task task : tasks) {
            write(task);
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

    //Task Slots
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long[] writeSlots(List<TaskSlot> slots);

    // ============== Delete ==============
    @Query("DELETE FROM task_core WHERE id = :id")
    void deleteCore(long id);
    @Query("DELETE FROM task_core")
    void deleteAllCore();
}

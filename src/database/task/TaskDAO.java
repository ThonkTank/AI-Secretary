package database.task;

import androidx.room.Dao; 
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;

import java.time.LocalDate;
import java.util.List;

@Dao
public interface TaskDAO {

    @Transaction
    @Query("SELECT * FROM task_core WHERE id = :id")
    Task read(Long id);
    @Transaction
    @Query("SELECT DISTINCT tc.* FROM task_core tc INNER JOIN task_slots ts ON tc.id = ts.taskId WHERE ts.day = :day ORDER BY ts.start")
    List<Task> readByDue(LocalDate day);
    @Transaction
    @Query("SELECT * FROM task_core")
    List<Task> readAll();
    @Transaction
    @Query("SELECT * FROM task_core WHERE title = :title")
    Task readTitle(String title);
    @Transaction
    @Query("SELECT * FROM task_core")
    List<Task> readAllCore();
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long writeCore(TaskCore core);
    @Query("DELETE FROM task_core WHERE id = :id")
    void deleteCore(long id);
    @Query("DELETE FROM task_core")
    void deleteAllCore();

    //Follow Ups
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void writeFollowUps(List<TaskFollowUp> followUps);
    @Query("DELETE FROM task_follow_ups WHERE taskId = :taskId")
    void deleteFollowUps(long taskId);
    @Query("DELETE FROM task_follow_ups")
    void deleteAllFollowUps();

    //Pref Slots
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void writePrefSlots(List<TaskPrefSlot> prefSlots);
    @Query("DELETE FROM task_pref_slots WHERE taskId = :taskId")
    void deletePrefSlots(long taskId);
    @Query("DELETE FROM task_pref_slots")
    void deleteAllPrefSlots();

    //Task Slots
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void writeSlots(List<TaskSlot> slots);
    @Query("DELETE FROM task_slots WHERE taskId = :taskId")
    void deleteSlots(long taskId);
    @Query("DELETE FROM task_slots")
    void deleteAllSlots();

    //Transactions

    @Transaction
    default void write(Task task) {
        if (task.core.id != null) {
            delete(task);
        } 
        long id = writeCore(task.core);
        task.setId(id);
        for (Task child : task.children) {
            write(child);
        }
        writeFollowUps(task.followUps);
        writePrefSlots(task.prefSlots);
        writeSlots(task.slots);
    }

    @Transaction
    default void writeList(List<Task> tasks) {
        for (Task task : tasks) {
            write(task);
        }
    }


    @Transaction
    default void delete(Task task){
        deleteCore(task.core.id);
        deleteFollowUps(task.core.id);
        deletePrefSlots(task.core.id);
        deleteSlots(task.core.id);
    }

    @Transaction
    default void deleteAll(){
        deleteAllCore();
        deleteAllFollowUps();
        deleteAllPrefSlots();
        deleteAllSlots();
    }
}

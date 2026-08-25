package de.thonktank.autosecretary.data.local;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface TimerSessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) void put(TimerSessionEntity session);
    @Query("SELECT * FROM timer_sessions ORDER BY notificationId")
    List<TimerSessionEntity> all();
    @Query("SELECT * FROM timer_sessions WHERE id = :id LIMIT 1")
    TimerSessionEntity find(String id);
    @Query("SELECT * FROM timer_sessions WHERE stepId = :stepId ORDER BY notificationId")
    List<TimerSessionEntity> forStep(String stepId);
    @Query("DELETE FROM timer_sessions WHERE id = :id") void delete(String id);
    @Query("DELETE FROM timer_sessions WHERE stepId = :stepId") void deleteForStep(String stepId);
}

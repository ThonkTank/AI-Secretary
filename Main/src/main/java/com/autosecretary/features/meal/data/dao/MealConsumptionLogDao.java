package com.autosecretary.features.meal.data.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.time.LocalDate;
import java.util.List;

import com.autosecretary.features.meal.data.entity.MealConsumptionLogEntity;

@Dao
public interface MealConsumptionLogDao {

    @Query("SELECT * FROM consumption_log WHERE date BETWEEN :from AND :to ORDER BY date")
    List<MealConsumptionLogEntity> findConsumptionLogsByDateRange(LocalDate from, LocalDate to);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertConsumptionLog(MealConsumptionLogEntity entity);
}

package com.autosecretary.features.meal.data.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.autosecretary.features.meal.data.entity.MealWeeklyFoodTargetEntity;

@Dao
public interface MealWeeklyFoodTargetDao {

    @Query("SELECT * FROM weekly_food_target WHERE periodKey = :periodKey LIMIT 1")
    MealWeeklyFoodTargetEntity findWeeklyFoodTargetByPeriodKey(String periodKey);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertWeeklyFoodTarget(MealWeeklyFoodTargetEntity entity);
}

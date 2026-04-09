package com.autosecretary.features.meal.data.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.time.LocalDate;
import java.util.List;

import com.autosecretary.features.meal.data.entity.MealPlanEntity;

@Dao
public interface MealPlanDao {

    @Query("SELECT * FROM meal_plan WHERE date BETWEEN :from AND :to ORDER BY date, mealType")
    List<MealPlanEntity> findByDateRange(LocalDate from, LocalDate to);

    @Query("SELECT * FROM meal_plan WHERE id = :id LIMIT 1")
    MealPlanEntity findById(String id);

    @Query("SELECT * FROM meal_plan WHERE itemId = :taskId LIMIT 1")
    MealPlanEntity findByItemId(String taskId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(MealPlanEntity entity);

    @Query("DELETE FROM meal_plan WHERE id = :id")
    void deleteById(String id);
}

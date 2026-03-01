package com.autosecretary.features.meal.data.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.autosecretary.features.meal.data.entity.MealCookingPreferencesEntity;

@Dao
public interface MealCookingPreferencesDao {

    @Query("SELECT * FROM cooking_preferences WHERE id = '1' LIMIT 1")
    MealCookingPreferencesEntity findCookingPreferences();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertCookingPreferences(MealCookingPreferencesEntity entity);
}

package com.autosecretary.features.meal.data.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

import com.autosecretary.features.meal.data.entity.MealHouseholdMemberEntity;

@Dao
public interface MealHouseholdMemberDao {

    @Query("SELECT * FROM household_member ORDER BY name")
    List<MealHouseholdMemberEntity> findAllHouseholdMembers();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertHouseholdMember(MealHouseholdMemberEntity entity);

    @Query("DELETE FROM household_member WHERE id = :id")
    void deleteHouseholdMemberById(String id);
}

package com.autosecretary.features.budget.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface BudgetRecurringTemplateDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(BudgetRecurringTemplateEntity template);

    @Query("SELECT * FROM budget_recurring_template WHERE accountId = :accountId AND active = 1")
    List<BudgetRecurringTemplateEntity> findActiveTemplates(String accountId);
}

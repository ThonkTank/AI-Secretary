package com.autosecretary.features.budget.data.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.time.LocalDate;

import com.autosecretary.features.budget.data.entity.BudgetImportEntity;

@Dao
public interface BudgetImportDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(BudgetImportEntity importEntity);

    @Query("""
            UPDATE budget_import
            SET status = 'COMPLETED',
                totalTransactions = :total,
                importedTransactions = :imported,
                autoCategorized = :autoCategorized,
                periodStart = :periodStart,
                periodEnd = :periodEnd
            WHERE id = :id
            """)
    void markCompleted(String id, int total, int imported, int autoCategorized,
                       LocalDate periodStart, LocalDate periodEnd);

    @Query("""
            UPDATE budget_import
            SET status = 'FAILED',
                errorMessage = :errorMessage
            WHERE id = :id
            """)
    void markFailed(String id, String errorMessage);
}

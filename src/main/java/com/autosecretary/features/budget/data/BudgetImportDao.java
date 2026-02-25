package com.autosecretary.features.budget.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.time.LocalDate;

@Dao
public interface BudgetImportDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(BudgetImportEntity importEntity);

    @Query("SELECT * FROM budget_import WHERE fileHash = :fileHash LIMIT 1")
    BudgetImportEntity findByFileHash(String fileHash);

    @Query("UPDATE budget_import SET status = 'COMPLETED', " +
            "totalTransactions = :total, importedTransactions = :imported, " +
            "autoCategorized = :autoCat, periodStart = :periodStart, " +
            "periodEnd = :periodEnd WHERE id = :id")
    void markCompleted(String id, int total, int imported, int autoCat,
                       LocalDate periodStart, LocalDate periodEnd);

    @Query("UPDATE budget_import SET status = 'FAILED', errorMessage = :errorMessage WHERE id = :id")
    void markFailed(String id, String errorMessage);
}

package com.autosecretary.features.budget.data.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.time.LocalDate;

import com.autosecretary.features.budget.data.entity.BudgetImportEntity;
import com.autosecretary.features.budget.data.entity.ImportStatus;

@Dao
public interface BudgetImportDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(BudgetImportEntity importEntity);

    @Query("SELECT * FROM budget_import WHERE fileHash = :fileHash LIMIT 1")
    BudgetImportEntity findByFileHash(String fileHash);

    @Query("UPDATE budget_import SET status = :status, " +
            "totalTransactions = :total, importedTransactions = :imported, " +
            "autoCategorized = :autoCat, periodStart = :periodStart, " +
            "periodEnd = :periodEnd WHERE id = :id")
    void markCompleted(String id, ImportStatus status, int total, int imported, int autoCat,
                       LocalDate periodStart, LocalDate periodEnd);

    @Query("UPDATE budget_import SET status = :status, errorMessage = :errorMessage WHERE id = :id")
    void markFailed(String id, ImportStatus status, String errorMessage);
}

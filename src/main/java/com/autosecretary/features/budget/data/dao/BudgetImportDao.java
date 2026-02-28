package com.autosecretary.features.budget.data.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.time.LocalDate;

import com.autosecretary.features.budget.data.entity.BudgetImportEntity;

/**
 * DAO for managing import records and their lifecycle.
 *
 * Tracks the state of transaction imports from CSV and PDF files:
 * - insert: create a new import record in PENDING state
 * - markCompleted: transition to COMPLETED after processing transactions
 * - markFailed: transition to FAILED if import encountered an error
 *
 * Used by BudgetImportRepository and the import application service to coordinate
 * multi-step imports (parse → detect patterns → upsert transactions → update templates).
 */
@Dao
public interface BudgetImportDao {

    /**
     * Inserts a new import record (typically in PENDING state).
     *
     * If an import with the same ID already exists, replaces it.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(BudgetImportEntity importEntity);

    /**
     * Marks an import as COMPLETED and records its summary statistics.
     *
     * Typically called after all transactions have been persisted and recurring patterns
     * have been detected and stored.
     *
     * @param id the import ID
     * @param total total transactions found in the imported file
     * @param imported transactions successfully added to the database
     * @param autoCategorized transactions auto-categorized (not manually assigned)
     * @param periodStart earliest transaction date in the import
     * @param periodEnd latest transaction date in the import
     */
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

    /**
     * Marks an import as FAILED and stores the error message.
     *
     * Called when parsing, categorization, or database operations fail during import.
     *
     * @param id the import ID
     * @param errorMessage human-readable error description for user display
     */
    @Query("""
            UPDATE budget_import
            SET status = 'FAILED',
                errorMessage = :errorMessage
            WHERE id = :id
            """)
    void markFailed(String id, String errorMessage);
}

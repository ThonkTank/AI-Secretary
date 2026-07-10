package com.autosecretary.features.budget.data.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.time.LocalDate;

import com.autosecretary.features.budget.data.entity.BudgetImportEntity;
import com.autosecretary.features.budget.domain.importing.ImportStatus;

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
    void write(BudgetImportEntity importEntity);

    /**
     * Updates an import record with a new status and optional summary statistics.
     *
     * Typically called to transition from PENDING to COMPLETED (with summary stats) or FAILED (with error message).
     *
     * @param id the import ID
     * @param status the new import status (COMPLETED or FAILED)
     * @param total total transactions found in the imported file (used for COMPLETED status)
     * @param imported transactions successfully added to the database (used for COMPLETED status)
     * @param autoCategorized transactions auto-categorized (used for COMPLETED status)
     * @param periodStart earliest transaction date in the import (used for COMPLETED status)
     * @param periodEnd latest transaction date in the import (used for COMPLETED status)
     * @param errorMessage error description for user display (used for FAILED status)
     */
    @Query("""
            UPDATE budget_import
            SET status = :status,
                totalTransactions = :total,
                importedTransactions = :imported,
                autoCategorized = :autoCategorized,
                periodStart = :periodStart,
                periodEnd = :periodEnd,
                errorMessage = :errorMessage
            WHERE id = :id
            """)
    void writeImportStatus(String id, ImportStatus status, int total, int imported, int autoCategorized,
                            LocalDate periodStart, LocalDate periodEnd, String errorMessage);

    /**
     * Marks an import as COMPLETED and records its summary statistics.
     *
     * Convenience method that calls {@link #writeImportStatus(String, ImportStatus, int, int, int, LocalDate, LocalDate, String)}.
     *
     * @param id the import ID
     * @param total total transactions found in the imported file
     * @param imported transactions successfully added to the database
     * @param autoCategorized transactions auto-categorized (not manually assigned)
     * @param periodStart earliest transaction date in the import
     * @param periodEnd latest transaction date in the import
     */
    default void markCompleted(String id, int total, int imported, int autoCategorized,
                               LocalDate periodStart, LocalDate periodEnd) {
        writeImportStatus(id, ImportStatus.COMPLETED, total, imported, autoCategorized,
                periodStart, periodEnd, null);
    }

    /**
     * Marks an import as FAILED and stores the error message.
     *
     * Convenience method that calls {@link #writeImportStatus(String, ImportStatus, int, int, int, LocalDate, LocalDate, String)}.
     *
     * @param id the import ID
     * @param errorMessage human-readable error description for user display
     */
    default void markFailed(String id, String errorMessage) {
        writeImportStatus(id, ImportStatus.FAILED, 0, 0, 0, null, null, errorMessage);
    }
}

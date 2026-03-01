package com.autosecretary.features.budget.data.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import java.time.LocalDate;
import java.util.UUID;
import com.autosecretary.features.budget.domain.importing.ImportStatus;

/**
 * Represents a single bank statement import session (CSV or PDF file).
 *
 * When a user uploads a statement file, a BudgetImportEntity is created to track:
 * - The import's progress (total transactions, how many imported, how many auto-categorized)
 * - The import's status (PENDING, IN_PROGRESS, COMPLETED, FAILED)
 * - The statement's time period (periodStart to periodEnd)
 * - Deduplication (fileHash to prevent re-importing the same statement)
 *
 * The import is associated with a single account. Each transaction in the file becomes
 * a BudgetTransactionEntity with a reference to this import's ID.
 */
@Entity(
        tableName = "budget_import",
        foreignKeys = @ForeignKey(
                entity = BudgetAccount.class,
                parentColumns = "id",
                childColumns = "accountId",
                onDelete = ForeignKey.RESTRICT,
                onUpdate = ForeignKey.CASCADE
        ),
        indices = {
                @Index("accountId"),
                @Index("fileHash")
        }
)
public class BudgetImportEntity {

    @PrimaryKey
    @NonNull
    public String id = UUID.randomUUID().toString();

    @NonNull
    public String accountId;

    @NonNull
    public String fileName;

    /**
     * SHA-256 hash of the raw file bytes, used to detect and reject duplicate imports.
     * Computed by the import application service before creating this record.
     * If an import with the same hash already exists, re-import is blocked without user prompting.
     */
    @NonNull
    public String fileHash;

    /**
     * Start of the statement period (e.g., first transaction date or file header date).
     * May be null if the file does not specify a period.
     */
    public LocalDate periodStart;

    /**
     * End of the statement period (e.g., last transaction date or file header date).
     * May be null if the file does not specify a period.
     */
    public LocalDate periodEnd;

    /**
     * Progress counters: these three fields always move together and track the import's progress.
     * - totalTransactions: total transactions found in the file
     * - importedTransactions: how many were successfully imported into the database
     * - autoCategorized: how many of the imported transactions were auto-assigned a category
     * Use these to display a progress bar or completion percentage to the user.
     */
    public int totalTransactions;

    public int importedTransactions;

    public int autoCategorized;

    /**
     * Status of this import: PENDING, IN_PROGRESS, COMPLETED, or FAILED.
     * Use this to filter active imports, determine if UI should show an error message, etc.
     */
    @NonNull
    public ImportStatus status = ImportStatus.PENDING;

    /**
     * Human-readable error message if status = FAILED.
     * null if status is not FAILED.
     */
    public String errorMessage;

    public BudgetImportEntity(@NonNull String accountId, @NonNull String fileName, @NonNull String fileHash) {
        this.accountId = accountId;
        this.fileName = fileName;
        this.fileHash = fileHash;
    }
}

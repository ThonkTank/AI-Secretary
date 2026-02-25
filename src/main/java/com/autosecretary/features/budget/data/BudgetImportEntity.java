package com.autosecretary.features.budget.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import java.time.LocalDate;
import java.util.UUID;

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

    @NonNull
    public String fileHash;

    public LocalDate periodStart;

    public LocalDate periodEnd;

    public int totalTransactions;

    public int importedTransactions;

    public int autoCategorized;

    @NonNull
    public String status = "PENDING";

    public String errorMessage;

    public BudgetImportEntity(@NonNull String accountId, @NonNull String fileName, @NonNull String fileHash) {
        this.accountId = accountId;
        this.fileName = fileName;
        this.fileHash = fileHash;
    }
}

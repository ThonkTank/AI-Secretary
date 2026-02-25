package com.autosecretary.features.budget.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity(tableName = "imports")
public class Import {
    @PrimaryKey(autoGenerate = true)
    public Long id;
    public Long accountId;
    public String fileName;
    public String fileHash;
    public LocalDateTime importDate;
    public LocalDate periodStart;
    public LocalDate periodEnd;
    public int totalTransactions;
    public int newTransactions;
    public int autoCategorized;
    public String claudeModel;
    public Integer claudePromptTokens;
    public Integer claudeResponseTokens;
    public Integer processingTimeMs;
    public String rawContent;
    public String parsedJson;
    public ImportStatus status;
    public String errorMessage;

    public enum ImportStatus {
        PENDING,
        PROCESSING,
        COMPLETED,
        FAILED
    }
}

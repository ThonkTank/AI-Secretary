package com.autosecretary.data.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "migration_reports")
public final class MigrationReportEntity {
    @PrimaryKey(autoGenerate = true) public long id;
    public int sourceVersion;
    @NonNull public String completedAt = "";
    public int importedItems;
    public int importedCompletions;
    public int candidateItems;
    @NonNull public String warningsJson = "[]";
    public boolean acknowledged;
}

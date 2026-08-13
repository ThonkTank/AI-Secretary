package com.autosecretary.data.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "migration_candidates")
public final class MigrationCandidateEntity {
    @PrimaryKey @NonNull public String id = "";
    @NonNull public String title = "";
    public int durationMinutes;
    public String deadlineAt;
    @NonNull public String reason = "UNSUPPORTED_RECURRENCE";
    @NonNull public String legacyPayloadJson = "{}";
}

package de.thonktank.autosecretary.data.local;

import androidx.annotation.NonNull;
import androidx.room.Entity;

/** Permanent forensic copies, independent of live task and run deletion. */
@Entity(tableName = "migration_recovery", primaryKeys = {"sourceSchema", "sourceTable", "sourceId"})
public final class MigrationRecoveryEntity {
    public final int sourceSchema;
    @NonNull public final String sourceTable;
    @NonNull public final String sourceId;
    @NonNull public final String reason;
    @NonNull public final String payload;

    public MigrationRecoveryEntity(int sourceSchema, @NonNull String sourceTable,
            @NonNull String sourceId, @NonNull String reason, @NonNull String payload) {
        this.sourceSchema = sourceSchema;
        this.sourceTable = sourceTable;
        this.sourceId = sourceId;
        this.reason = reason;
        this.payload = payload;
    }
}

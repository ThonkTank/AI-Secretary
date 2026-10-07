package de.thonktank.autosecretary.data.local;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/** Serialized immutable proposal and its local resolution state. */
@Entity(tableName = "agent_proposals", indices = @Index("sourceMessageId"))
public final class AgentProposalEntity {
    @PrimaryKey @NonNull public final String id;
    @NonNull public final String sourceMessageId;
    @NonNull public final String status;
    @NonNull public final String payloadJson;
    public final long createdAtEpochMillis;
    public final long resolvedAtEpochMillis;

    public AgentProposalEntity(@NonNull String id, @NonNull String sourceMessageId,
                               @NonNull String status, @NonNull String payloadJson,
                               long createdAtEpochMillis, long resolvedAtEpochMillis) {
        this.id = id;
        this.sourceMessageId = sourceMessageId;
        this.status = status;
        this.payloadJson = payloadJson;
        this.createdAtEpochMillis = createdAtEpochMillis;
        this.resolvedAtEpochMillis = resolvedAtEpochMillis;
    }
}

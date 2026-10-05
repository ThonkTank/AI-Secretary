package de.thonktank.autosecretary.data.local;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/** Durable local chat message. Content is never used as a persistence command. */
@Entity(tableName = "agent_messages", indices = @Index(value = "sequence", unique = true))
public final class AgentMessageEntity {
    @PrimaryKey @NonNull public final String id;
    public final long sequence;
    @NonNull public final String role;
    @NonNull public final String body;
    public final long createdAtEpochMillis;

    public AgentMessageEntity(@NonNull String id, long sequence, @NonNull String role,
                              @NonNull String body, long createdAtEpochMillis) {
        this.id = id;
        this.sequence = sequence;
        this.role = role;
        this.body = body;
        this.createdAtEpochMillis = createdAtEpochMillis;
    }
}

package de.thonktank.autosecretary.data.local;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.Index;

@Entity(tableName = "combo_decay_events", primaryKeys = {"ownerId", "eventOn"},
        indices = @Index("bookingId"))
public final class ComboDecayEventEntity {
    @NonNull public String ownerId;
    @NonNull public String eventOn;
    @Nullable public String bookingId;

    public ComboDecayEventEntity(@NonNull String ownerId, @NonNull String eventOn,
                                 @Nullable String bookingId) {
        this.ownerId = ownerId;
        this.eventOn = eventOn;
        this.bookingId = bookingId;
    }
}

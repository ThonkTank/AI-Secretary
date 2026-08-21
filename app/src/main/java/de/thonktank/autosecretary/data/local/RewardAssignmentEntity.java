package de.thonktank.autosecretary.data.local;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/** Current reporting assignment for an immutable reward booking. */
@Entity(tableName = "reward_assignments",
        foreignKeys = {
                @ForeignKey(entity = RewardBookingEntity.class, parentColumns = "id",
                        childColumns = "bookingId", onDelete = ForeignKey.CASCADE),
                @ForeignKey(entity = OccurrenceEntity.class, parentColumns = "id",
                        childColumns = "occurrenceId", onDelete = ForeignKey.CASCADE)
        }, indices = {@Index("occurrenceId")})
public final class RewardAssignmentEntity {
    @PrimaryKey @NonNull public final String bookingId;
    @NonNull public final String occurrenceId;

    public RewardAssignmentEntity(@NonNull String bookingId, @NonNull String occurrenceId) {
        this.bookingId = bookingId;
        this.occurrenceId = occurrenceId;
    }
}

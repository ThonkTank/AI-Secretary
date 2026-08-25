package de.thonktank.autosecretary.data.local;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "reward_bookings",
        foreignKeys = {
                @ForeignKey(entity = OccurrenceEntity.class, parentColumns = "id",
                        childColumns = "occurrenceId", onDelete = ForeignKey.CASCADE),
                @ForeignKey(entity = OccurrenceStepEntity.class, parentColumns = "id",
                        childColumns = "occurrenceStepId", onDelete = ForeignKey.CASCADE)
        },
        indices = {@Index("transactionId"), @Index("occurrenceId"),
                @Index("occurrenceStepId"), @Index("ownerId"),
                @Index(value = "reversesBookingId", unique = true)})
public final class RewardBookingEntity {
    @PrimaryKey @NonNull public final String id;
    @NonNull public final String transactionId;
    @NonNull public final String occurrenceId;
    @Nullable public final String occurrenceStepId;
    @NonNull public final String ownerId;
    @NonNull public final String kind;
    @NonNull public final String target;
    public final int xpDelta;
    public final int comboPointDelta;
    @NonNull public final String bookedOn;
    @Nullable public final String reversesBookingId;
    @Nullable public final Integer plannedXp;

    public RewardBookingEntity(@NonNull String id, @NonNull String transactionId,
                               @NonNull String occurrenceId, @Nullable String occurrenceStepId,
                               @NonNull String ownerId, @NonNull String kind,
                               @NonNull String target, int xpDelta, int comboPointDelta,
                               @NonNull String bookedOn, @Nullable String reversesBookingId,
                               @Nullable Integer plannedXp) {
        this.id = id; this.transactionId = transactionId; this.occurrenceId = occurrenceId;
        this.occurrenceStepId = occurrenceStepId; this.ownerId = ownerId; this.kind = kind;
        this.target = target; this.xpDelta = xpDelta; this.comboPointDelta = comboPointDelta;
        this.bookedOn = bookedOn; this.reversesBookingId = reversesBookingId;
        this.plannedXp = plannedXp;
    }
}

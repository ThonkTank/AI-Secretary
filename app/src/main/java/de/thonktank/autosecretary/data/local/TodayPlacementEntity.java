package de.thonktank.autosecretary.data.local;

import androidx.annotation.NonNull;
import androidx.room.Entity;

@Entity(tableName = "today_placements", primaryKeys = {"kind", "id"})
public final class TodayPlacementEntity {
    @NonNull public final String kind;
    @NonNull public final String id;
    @NonNull public final String displayOn;
    @NonNull public final String slot;
    public final int position;
    public TodayPlacementEntity(@NonNull String kind, @NonNull String id,
                                @NonNull String displayOn, @NonNull String slot, int position) {
        this.kind = kind; this.id = id; this.displayOn = displayOn;
        this.slot = slot; this.position = position;
    }
}

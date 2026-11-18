package de.thonktank.autosecretary.data.local;

import androidx.annotation.NonNull;
import androidx.room.Entity;

@Entity(tableName = "today_hidden_items", primaryKeys = {"kind", "id"})
public final class TodayHiddenItemEntity {
    @NonNull public final String kind;
    @NonNull public final String id;
    @NonNull public final String hiddenOn;

    public TodayHiddenItemEntity(@NonNull String kind, @NonNull String id, @NonNull String hiddenOn) {
        this.kind = kind;
        this.id = id;
        this.hiddenOn = hiddenOn;
    }
}

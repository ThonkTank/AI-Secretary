package de.thonktank.autosecretary.data.local;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "capacity_resources",
        indices = @Index(value = "normalizedName", unique = true))
public class CapacityResourceEntity {
    @PrimaryKey @NonNull public String id;
    @NonNull public String name;
    @NonNull public String normalizedName;
    public int capacity;

    public CapacityResourceEntity(@NonNull String id, @NonNull String name,
                                  @NonNull String normalizedName, int capacity) {
        this.id = id;
        this.name = name;
        this.normalizedName = normalizedName;
        this.capacity = capacity;
    }
}

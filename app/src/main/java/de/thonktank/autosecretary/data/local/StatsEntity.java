package de.thonktank.autosecretary.data.local;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "stats")
public class StatsEntity {
    @PrimaryKey public int id = 1;
    public int xp;
    public StatsEntity(int xp) { this.xp = xp; }
}

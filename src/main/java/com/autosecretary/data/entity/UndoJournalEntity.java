package com.autosecretary.data.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "undo_journal", indices = @Index("createdAt"))
public final class UndoJournalEntity {
    @PrimaryKey @NonNull public String id = "";
    @NonNull public String kind = "";
    @NonNull public String label = "";
    @NonNull public String payloadJson = "";
    @NonNull public String createdAt = "";
    public String undoneAt;
}

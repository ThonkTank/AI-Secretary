package de.thonktank.autosecretary.data.local;

import androidx.annotation.NonNull;
import androidx.room.*;

/** A request receipt survives explicit task deletion, preventing accidental resurrection. */
@Entity(tableName = "flow_editor_saves")
public final class FlowEditorSaveEntity {
    @PrimaryKey @NonNull public String requestKey = "";
    @NonNull public String taskId = "";
}

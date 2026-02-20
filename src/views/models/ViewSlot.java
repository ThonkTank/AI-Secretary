package views.models;

import androidx.room.Embedded;
import androidx.room.Relation;
import androidx.room.Ignore;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;

import database.task.*;

public class ViewSlot {
    @Embedded public TaskSlot slot;
    @Relation(parentColumn = "taskId", entityColumn = "id")
    public TaskCore core;
    @Ignore 
    public int indent;

    public static void assignIndents(List<ViewSlot> viewSlots) {
        Map<Long, Integer> indents = new HashMap<>();
        for (ViewSlot vs : viewSlots) {
            vs.indent = 0;
            if (vs.slot.parentSlotId != null) {
                vs.indent = indents.get(vs.slot.parentSlotId) + 1;
            }
            indents.put(vs.slot.id, vs.indent);
        }
    }
}
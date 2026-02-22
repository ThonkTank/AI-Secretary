package views.models;

import java.util.List;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.HashMap;
import java.util.HashSet;
import java.util.function.Predicate;

import database.task.*;

public class ViewSlotList {
    private List<ViewSlot> viewSlots;
    public List<ViewSlot> displaySlots;

    public static class ViewSlot {
        public Task task;
        public TaskSlot slot;
        public int depth;

        private List<ViewSlot> children;

        public ViewSlot (Task task, TaskSlot slot) {
            this.task = task;
            this.slot = slot;
        }
    }

    public void fromList(List<Task> tasks) {
        viewSlots = new ArrayList<>();
        for (Task task : tasks) {
            int nrSlots = 0;
            for (TaskSlot slot : task.slots) {
                viewSlots.add(new ViewSlot(task, slot));
                nrSlots++;
            }
            if (nrSlots == 0) {
                ViewSlot vs = new ViewSlot(task, new TaskSlot());
                vs.slot.day = LocalDate.now();
                viewSlots.add(vs);
            }
        }
    }

    //ViewModel ruft filter auf
    public void filter(Predicate<ViewSlot> predicate) {
        displaySlots = new ArrayList<>();
        for (ViewSlot vs : viewSlots) {
            if (predicate.test(vs)) {
                displaySlots.add(vs);
            }
        }
    }

    //ViewModel ruft sort auf
    public void sort(boolean byTaskParent, Comparator<ViewSlot> comparator) {
        buildTree(byTaskParent);
        sortTree(displaySlots, comparator);

        Set<ViewSlot> visited = new HashSet<>();
        displaySlots = flatten(displaySlots, visited, 0);
    }

    private void buildTree(boolean byTaskParent) {
        Map<Long, ViewSlot> mappedVS = new HashMap<>();
        List<ViewSlot> vsTree = new ArrayList<>();
        
        for (ViewSlot vs : displaySlots) {
            Long id = byTaskParent ? vs.task.core.id : vs.slot.id;
            mappedVS.put(id, vs);
        }

        for (ViewSlot vs : displaySlots) {
            int nrParents = 0;
            List<Long> parentIDs = new ArrayList<>();

            if (byTaskParent) {
                for (TaskParent parent : vs.task.parents) {
                    parentIDs.add(parent.parent);
                    nrParents++;
                }
            } else if (vs.slot.parent != null) {
                parentIDs.add(vs.slot.parent);
                nrParents++;
            }

            if (nrParents == 0) {
                vsTree.add(vs);
            } else {
                for (Long parent : parentIDs) {
                    mappedVS.get(parent).children.add(vs);
                }
            }
            
        }
        displaySlots = vsTree;
    }

    private void sortTree(List<ViewSlot> vsSlots, Comparator<ViewSlot> comparator) {
        if (vsSlots.isEmpty()) return;
        vsSlots.sort(comparator);
        for (ViewSlot vs : vsSlots) {
            sortTree(vs.children, comparator);
        }
    }

    private List<ViewSlot> flatten(List<ViewSlot> viewSlots, Set<ViewSlot> visited, int depth) {
        List<ViewSlot> result = new ArrayList<>();
        for (ViewSlot vs : viewSlots) {
            vs.depth = depth;
            if (!visited.add(vs)) return result;  // schon besucht
            result.add(vs);
            result.addAll(flatten(vs.children, visited, depth+1));
        }
        return result;
    }
}
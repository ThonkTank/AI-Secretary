package activities.inApp.tasksTab.editorModal.fields;

import android.content.Context;
import android.view.View;
import android.widget.EditText;
import android.widget.Spinner;

import static activities.generic.ViewHelper.*;

import com.autosecretary.R;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

import controller.taskTab.EditorManager;
import controller.taskTab.EditorManager.TreeEntry;
import entities.TrackedItem;
import entities.TrackedItem.ItemType;

/**
 * Domain-Gruppe: Parent-Spinner, Predecessor-Spinner + Delay.
 * Haelt Referenz-Listen (availableParents, availablePredecessors)
 * und exponiert ID-Aufloesung fuer apply().
 */
public class HierarchyFields implements FieldGroup {

    private final Context context;
    private final BooleanSupplier suppressCheck;
    private final Runnable onVisibilityTrigger;
    private final EditorManager manager;

    private Spinner parentSpinner;
    private Spinner predecessorSpinner;
    private EditText predecessorDelayField;
    private View parentRow;
    private View predecessorRow;
    private View predecessorDelayRow;

    private List<TrackedItem> availableParents = new ArrayList<>();
    private List<TrackedItem> availablePredecessors = new ArrayList<>();

    private TrackedItem editingItem;
    private ItemType lastType = null;

    public HierarchyFields(Context context, View root, BooleanSupplier suppressCheck,
                    Runnable onVisibilityTrigger, EditorManager manager) {
        this.context = context;
        this.suppressCheck = suppressCheck;
        this.onVisibilityTrigger = onVisibilityTrigger;
        this.manager = manager;
        bind(root);
    }

    private void bind(View root) {
        parentSpinner = root.findViewById(R.id.spinner_parent);
        predecessorSpinner = root.findViewById(R.id.spinner_predecessor);
        predecessorDelayField = root.findViewById(R.id.field_predecessor_delay);
        parentRow = root.findViewById(R.id.row_parent);
        predecessorRow = root.findViewById(R.id.row_predecessor);
        predecessorDelayRow = root.findViewById(R.id.row_predecessor_delay);

        onSpinnerSelected(parentSpinner, suppressCheck, pos -> {
            refreshPredecessorSpinner(pos);
            onVisibilityTrigger.run();
        });
        onSpinnerSelected(predecessorSpinner, suppressCheck,
            pos -> onVisibilityTrigger.run());
    }

    // ========================================================================
    // FieldGroup
    // ========================================================================

    @Override
    public void populate(TrackedItem item) {
        editingItem = item;
        ItemType type = item != null ? item.type : ItemType.TASK;
        lastType = type;
        refreshParentSpinner(type);

        int parentIdx = 0;
        int predIdx = 0;
        if (item != null) {
            parentIdx = findOffsetIdx(availableParents, item.parent, i -> i.id);
            refreshPredecessorSpinner(parentIdx);
            predIdx = findOffsetIdx(availablePredecessors, item.predecessor, i -> i.id);
            predecessorDelayField.setText(
                item.predecessorDelay > 0 ? String.valueOf(item.predecessorDelay) : "");
        } else {
            refreshPredecessorSpinner(0);
            predecessorDelayField.setText("");
        }

        if (parentIdx < parentSpinner.getCount()) parentSpinner.setSelection(parentIdx);
        if (predIdx < predecessorSpinner.getCount()) predecessorSpinner.setSelection(predIdx);
    }

    @Override
    public void apply(TrackedItem.Builder builder) {
        int parentIdx = parentSpinner.getSelectedItemPosition();
        Long parentId = resolveAtOffset(availableParents, parentIdx, i -> i.id);
        if (parentIdx > 0 && parentId != null) {
            builder.parent(parentId);
        }

        int predIdx = predecessorSpinner.getSelectedItemPosition();
        Long predId = resolveAtOffset(availablePredecessors, predIdx, i -> i.id);
        if (predIdx > 0 && predId != null) {
            String delayStr = predecessorDelayField.getText().toString().trim();
            if (!delayStr.isEmpty()) {
                try {
                    int delay = Integer.parseInt(delayStr);
                    if (delay > 0) {
                        builder.delayAfter(predId, delay);
                        return;
                    }
                } catch (NumberFormatException ignored) {}
            }
            builder.chainAfter(predId);
        }
    }

    @Override
    public void updateVisibility(VisibilityFlags flags) {
        parentRow.setVisibility(flags.showParent() ? View.VISIBLE : View.GONE);
        predecessorRow.setVisibility(flags.showPredecessor() ? View.VISIBLE : View.GONE);
        predecessorDelayRow.setVisibility(flags.showPredecessorDelay() ? View.VISIBLE : View.GONE);

        if (flags.type() != lastType) {
            lastType = flags.type();
            refreshParentSpinner(lastType);
        }
    }

    // ========================================================================
    // GETTER
    // ========================================================================

    public int getPredecessorIdx() { return predecessorSpinner.getSelectedItemPosition(); }

    // ========================================================================
    // SPINNER-REFRESH
    // ========================================================================

    private void refreshParentSpinner(ItemType type) {
        availableParents = manager.getAvailableParents(type);
        List<String> parentNames = new ArrayList<>();
        parentNames.add("(kein Parent)");
        for (TrackedItem p : availableParents) {
            parentNames.add(p.title);
        }
        parentSpinner.setAdapter(spinnerAdapter(context, parentNames));
    }

    private void refreshPredecessorSpinner(int parentIdx) {
        availablePredecessors.clear();
        List<String> names = new ArrayList<>();
        names.add("(kein Vorg\u00e4nger)");

        if (lastType == ItemType.TASK && parentIdx > 0 && parentIdx <= availableParents.size()) {
            Long goalId = availableParents.get(parentIdx - 1).id;
            for (TreeEntry entry : manager.getAllItems()) {
                if (entry.item().type == ItemType.TASK
                        && goalId.equals(entry.item().parent)
                        && (editingItem == null || !entry.item().id.equals(editingItem.id))) {
                    availablePredecessors.add(entry.item());
                    names.add(entry.item().title);
                }
            }
        }
        predecessorSpinner.setAdapter(spinnerAdapter(context, names));
    }
}

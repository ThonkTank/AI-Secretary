package activities.inApp.tasksTab;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import static activities.generic.ViewHelper.*;

import androidx.core.content.ContextCompat;

import com.autosecretary.R;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import activities.generic.ViewBuilder;
import activities.inApp.tasksTab.editorModal.ItemEditorModal;
import controller.taskTab.EditorManager;
import controller.taskTab.EditorManager.TreeEntry;
import entities.TrackedItem;
import entities.TrackedItem.ItemType;

/**
 * Verwalten-SubTab: Hierarchischer Baum (Project→Goal→Task) + Editor-Modal.
 * Verwaltet Expand/Collapse, Suche, Typ-Filter und delegiert Editing an ItemEditorModal.
 */
public class TaskManagerView implements ViewBuilder {

    private static final int TREE_INDENT_PER_LEVEL_DP = 16;
    private static final int TREE_BASE_PADDING_DP = 8;

    private final Context context;
    private final EditorManager manager;

    private LinearLayout treeContainer;
    private ItemEditorModal modal;

    // Baum-Zustand
    private Map<Long, Boolean> expandedState = new HashMap<>();
    private Set<ItemType> activeFilters = EnumSet.allOf(ItemType.class);
    private String searchQuery = "";

    public TaskManagerView(Context context, EditorManager manager) {
        this.context = context;
        this.manager = manager;
    }

    // ========================================================================
    // BUILDVIEW - Layout aufbauen, Modal + Toolbar + Baum initialisieren
    // ========================================================================

    @Override
    public View buildView() {
        FrameLayout root = (FrameLayout) LayoutInflater.from(context)
            .inflate(R.layout.view_edit_item, null);

        treeContainer = root.findViewById(R.id.tree_container);
        View modalOverlay = root.findViewById(R.id.modal_overlay);

        // ItemEditorModal: orchestriert 8 Field-Gruppen direkt
        modal = new ItemEditorModal(context, manager, modalOverlay, this::refreshTree);
        modal.init();

        // Toolbar: Suchleiste
        EditText searchField = root.findViewById(R.id.search_field);
        searchField.addTextChangedListener(afterTextChanged(
            () -> setSearchQuery(searchField.getText().toString())));

        // Toolbar: Filter-Button
        Button filterBtn = root.findViewById(R.id.btn_filter);
        filterBtn.setOnClickListener(v -> cycleFilter());

        // Toolbar: Create-Button
        Button createBtn = root.findViewById(R.id.btn_create);
        createBtn.setBackground(roundedBg(context, ContextCompat.getColor(context, R.color.accent), 4));
        createBtn.setOnClickListener(v -> modal.openCreateModal());

        // Baum initial aufbauen
        buildTree();

        return root;
    }

    /**
     * Oeffentliche API um das Create-Modal von aussen zu oeffnen.
     * Wird von TaskView aufgerufen (Widget "+" Button).
     */
    public void openCreateModal() {
        modal.openCreateModal();
    }

    // ========================================================================
    // BUILDTREE - Baum-Zeilen aus manager.getAllItems() rendern
    // ========================================================================

    private void buildTree() {
        List<TreeEntry> entries = manager.getAllItems();
        for (TreeEntry entry : entries) {
            View row = buildTreeRow(entry);
            row.setTag(entry);
            treeContainer.addView(row);
        }
        applyFilter();
    }

    private View buildTreeRow(TreeEntry entry) {
        TrackedItem item = entry.item();
        int depth = entry.depth();

        LinearLayout row = (LinearLayout) LayoutInflater.from(context)
            .inflate(R.layout.row_tree_item, treeContainer, false);

        // Dynamisches Padding links basierend auf Tiefe
        row.setPaddingRelative(dp(context, TREE_INDENT_PER_LEVEL_DP * depth + TREE_BASE_PADDING_DP),
            row.getPaddingTop(), row.getPaddingEnd(), row.getPaddingBottom());

        // Trenn-Margin
        LinearLayout.LayoutParams rowParams = (LinearLayout.LayoutParams) row.getLayoutParams();
        rowParams.setMargins(0, dp(context, 1), 0, 0);

        // Badge: Label + Farbe nach Typ
        TextView badge = row.findViewById(R.id.tree_badge);
        String badgeLabel = switch (item.type) {
            case PROJECT -> "P";
            case GOAL -> "G";
            case TASK -> "T";
        };
        int badgeColor = switch (item.type) {
            case PROJECT -> ContextCompat.getColor(context, R.color.badge_project);
            case GOAL -> ContextCompat.getColor(context, R.color.badge_goal);
            case TASK -> ContextCompat.getColor(context, R.color.badge_task);
        };
        badge.setText(badgeLabel);
        badge.setBackground(roundedBg(context, badgeColor, 3));

        // Titel
        TextView title = row.findViewById(R.id.tree_title);
        title.setText(item.title);

        // Expand-Pfeil (nur bei Items mit Kindern)
        boolean hasChildren = item.children != null && !item.children.isEmpty();
        if (hasChildren) {
            TextView arrow = row.findViewById(R.id.tree_arrow);
            arrow.setVisibility(View.VISIBLE);
            Boolean expanded = expandedState.getOrDefault(item.id, true);
            arrow.setText(expanded ? "\u25BC" : "\u25B6");
        }

        // Klick-Handler
        row.setOnClickListener(v -> {
            if (hasChildren) {
                boolean currentlyExpanded = expandedState.getOrDefault(item.id, true);
                expandedState.put(item.id, !currentlyExpanded);
                refreshTree();
            } else {
                modal.showModal(item);
            }
        });

        // Long-Press: immer Edit
        row.setOnLongClickListener(v -> {
            modal.showModal(item);
            return true;
        });

        return row;
    }

    // ========================================================================
    // REFRESHTREE - Baum-Ansicht neu aufbauen
    // ========================================================================

    private void refreshTree() {
        treeContainer.removeAllViews();
        buildTree();
    }

    // ========================================================================
    // FILTER - Suche und Typ-Filter
    // ========================================================================

    private void setSearchQuery(String query) {
        this.searchQuery = query.toLowerCase();
        applyFilter();
    }

    private void cycleFilter() {
        if (activeFilters.size() == 3) {
            activeFilters = EnumSet.of(ItemType.PROJECT);
        } else if (activeFilters.contains(ItemType.PROJECT) && activeFilters.size() == 1) {
            activeFilters = EnumSet.of(ItemType.GOAL);
        } else if (activeFilters.contains(ItemType.GOAL) && activeFilters.size() == 1) {
            activeFilters = EnumSet.of(ItemType.TASK);
        } else {
            activeFilters = EnumSet.allOf(ItemType.class);
        }
        applyFilter();
    }

    private void applyFilter() {
        // Lookup-Map: itemId -> TreeEntry fuer Ancestor-Traversal
        Map<Long, TreeEntry> entryById = new HashMap<>();
        for (int i = 0; i < treeContainer.getChildCount(); i++) {
            Object tag = treeContainer.getChildAt(i).getTag();
            if (tag instanceof TreeEntry te && te.item().id != null) {
                entryById.put(te.item().id, te);
            }
        }

        for (int i = 0; i < treeContainer.getChildCount(); i++) {
            View child = treeContainer.getChildAt(i);
            Object tag = child.getTag();
            if (!(tag instanceof TreeEntry entry)) continue;

            TrackedItem item = entry.item();
            boolean matchesSearch = searchQuery.isEmpty()
                || (item.title != null && item.title.toLowerCase().contains(searchQuery));
            boolean matchesFilter = activeFilters.contains(item.type);

            boolean ancestorCollapsed = isAnyAncestorCollapsed(item, entryById);

            child.setVisibility(matchesSearch && matchesFilter && !ancestorCollapsed
                ? View.VISIBLE : View.GONE);
        }
    }

    private boolean isAnyAncestorCollapsed(TrackedItem item, Map<Long, TreeEntry> entryById) {
        Long currentParentId = item.parent;
        while (currentParentId != null) {
            Boolean expanded = expandedState.getOrDefault(currentParentId, true);
            if (!expanded) return true;

            TreeEntry parentEntry = entryById.get(currentParentId);
            if (parentEntry == null) break;
            currentParentId = parentEntry.item().parent;
        }
        return false;
    }
}

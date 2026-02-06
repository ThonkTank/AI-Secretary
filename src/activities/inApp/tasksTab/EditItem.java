package activities.inApp.tasksTab;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import static activities.generic.ViewHelper.*;

import androidx.core.content.ContextCompat;

import com.autosecretary.R;

import activities.generic.ViewBuilder;
import controller.EditorManager;

/**
 * Duenner Orchestrator fuer den "Verwalten"-Tab.
 * Delegiert an TreeRenderer (Baum), FieldManager (Formular-Felder)
 * und ItemEditorModal (Modal-Lebenszyklus).
 */
public class EditItem implements ViewBuilder {

    private final Context context;
    private final EditorManager manager;

    private TreeRenderer treeRenderer;
    private FieldManager fieldManager;
    private ItemEditorModal modal;

    public EditItem(Context context, EditorManager manager) {
        this.context = context;
        this.manager = manager;
    }

    @Override
    public View buildView() {
        FrameLayout root = (FrameLayout) LayoutInflater.from(context)
            .inflate(R.layout.view_edit_item, null);

        // Komponenten aus Layout holen
        LinearLayout treeContainer = root.findViewById(R.id.tree_container);
        View modalOverlay = root.findViewById(R.id.modal_overlay);

        // FieldManager: bindet alle Formular-Felder
        fieldManager = new FieldManager(context, manager, root);

        // TreeRenderer: Baum-Ansicht mit Edit-Callback
        treeRenderer = new TreeRenderer(context, manager, treeContainer,
            item -> modal.showModal(item));

        // ItemEditorModal: Modal-Lebenszyklus mit Save-Callback
        modal = new ItemEditorModal(context, manager, modalOverlay,
            fieldManager, () -> treeRenderer.refreshTree());
        modal.init();

        // Toolbar: Suchleiste
        EditText searchField = root.findViewById(R.id.search_field);
        searchField.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                treeRenderer.setSearchQuery(s.toString());
            }
        });

        // Toolbar: Filter-Button
        Button filterBtn = root.findViewById(R.id.btn_filter);
        filterBtn.setOnClickListener(v -> treeRenderer.cycleFilter());

        // Toolbar: Create-Button
        Button createBtn = root.findViewById(R.id.btn_create);
        createBtn.setBackground(roundedBg(context, ContextCompat.getColor(context, R.color.accent), 4));
        createBtn.setOnClickListener(v -> modal.openCreateModal());

        // Baum initial aufbauen
        treeRenderer.buildTree();

        return root;
    }

    /**
     * Oeffentliche API um das Create-Modal von aussen zu oeffnen.
     * Wird von TaskView aufgerufen (Widget "+" Button).
     */
    public void openCreateModal() {
        modal.openCreateModal();
    }
}

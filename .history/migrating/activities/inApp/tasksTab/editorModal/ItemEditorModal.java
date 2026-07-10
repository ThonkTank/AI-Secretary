package activities.inApp.tasksTab.editorModal;

import android.content.Context;
import android.view.View;
import android.widget.Button;

import static activities.generic.ViewHelper.*;

import androidx.core.content.ContextCompat;

import com.autosecretary.R;

import activities.inApp.tasksTab.editorModal.fields.BudgetFields;
import activities.inApp.tasksTab.editorModal.fields.CoreFields;
import activities.inApp.tasksTab.editorModal.fields.DurationProgressFields;
import activities.inApp.tasksTab.editorModal.fields.FieldGroup;
import activities.inApp.tasksTab.editorModal.fields.GoalAppearanceFields;
import activities.inApp.tasksTab.editorModal.fields.HierarchyFields;
import activities.inApp.tasksTab.editorModal.fields.PrefScheduleEditor;
import activities.inApp.tasksTab.editorModal.fields.RepetitionFields;
import activities.inApp.tasksTab.editorModal.fields.SchedulingFields;
import activities.inApp.tasksTab.editorModal.fields.VisibilityFlags;
import controller.taskTab.EditorManager;
import entities.TrackedItem;

/**
 * Schlanker Orchestrator fuer das Editor-Modal.
 * Erstellt alle 8 Field-Gruppen, koordiniert populate/save/visibility.
 * Kein zentraler State-Layer — Gruppen lesen/schreiben Views direkt.
 */
public class ItemEditorModal {

    private final View modalOverlay;
    private final EditorManager manager;
    private final Runnable onSaved;

    // Typisierte Referenzen fuer Getter-Zugriff
    private final CoreFields coreFields;
    private final SchedulingFields schedulingFields;
    private final RepetitionFields repetitionFields;
    private final DurationProgressFields durationProgressFields;
    private final HierarchyFields hierarchyFields;
    private final BudgetFields budgetFields;
    private final GoalAppearanceFields goalAppearanceFields;
    private final PrefScheduleEditor prefScheduleEditor;

    private final FieldGroup[] allGroups;

    private TrackedItem editingItem = null;
    private boolean suppressListeners = false;

    // ========================================================================
    // KONSTRUKTOR
    // ========================================================================

    public ItemEditorModal(Context context, EditorManager manager,
                           View modalOverlay, Runnable onSaved) {
        this.modalOverlay = modalOverlay;
        this.manager = manager;
        this.onSaved = onSaved;

        Runnable visTrigger = this::updateVisibility;

        coreFields = new CoreFields(context, modalOverlay, this::isSuppressed, visTrigger);
        schedulingFields = new SchedulingFields(context, modalOverlay, this::isSuppressed, visTrigger);
        repetitionFields = new RepetitionFields(context, modalOverlay, this::isSuppressed, visTrigger);
        durationProgressFields = new DurationProgressFields(context, modalOverlay, this::isSuppressed, visTrigger);
        hierarchyFields = new HierarchyFields(context, modalOverlay, this::isSuppressed, visTrigger, manager);
        budgetFields = new BudgetFields(context, modalOverlay, this::isSuppressed, visTrigger, manager);
        goalAppearanceFields = new GoalAppearanceFields(context, modalOverlay, this::isSuppressed);
        prefScheduleEditor = new PrefScheduleEditor(context, modalOverlay, this::isSuppressed);

        allGroups = new FieldGroup[] {
            coreFields, schedulingFields, repetitionFields, durationProgressFields,
            hierarchyFields, budgetFields, goalAppearanceFields, prefScheduleEditor
        };
    }

    private boolean isSuppressed() { return suppressListeners; }

    // ========================================================================
    // INIT — Save/Cancel-Buttons und Overlay-Dismiss binden
    // ========================================================================

    public void init() {
        Context ctx = modalOverlay.getContext();
        setupModalOverlay(modalOverlay, this::hideModal);

        Button saveBtn = modalOverlay.findViewById(R.id.btn_save);
        saveBtn.setBackground(roundedBg(ctx, ContextCompat.getColor(ctx, R.color.accent), 4));
        saveBtn.setOnClickListener(v -> saveItem());

        Button cancelBtn = modalOverlay.findViewById(R.id.btn_cancel);
        cancelBtn.setBackground(roundedBg(ctx, ContextCompat.getColor(ctx, R.color.button_inactive), 4));
        cancelBtn.setOnClickListener(v -> hideModal());
    }

    // ========================================================================
    // SHOWMODAL — Modal sichtbar machen und Felder befuellen
    // ========================================================================

    public void showModal(TrackedItem item) {
        editingItem = item;
        coreFields.hideError();

        suppressListeners = true;
        for (FieldGroup g : allGroups) g.populate(item);
        suppressListeners = false;

        updateVisibility();
        modalOverlay.setVisibility(View.VISIBLE);
    }

    // ========================================================================
    // HIDEMODAL
    // ========================================================================

    public void hideModal() {
        modalOverlay.setVisibility(View.GONE);
        editingItem = null;
    }

    // ========================================================================
    // OPENCREATEMODAL
    // ========================================================================

    public void openCreateModal() {
        showModal(null);
    }

    // ========================================================================
    // UPDATEVISIBILITY — berechnet Flags und dispatcht an alle Gruppen
    // ========================================================================

    private void updateVisibility() {
        if (suppressListeners) return;

        VisibilityFlags flags = VisibilityFlags.compute(
            coreFields.getType(),
            schedulingFields.getSchedulingType(),
            repetitionFields.getRepType(),
            repetitionFields.getRepUnit(),
            repetitionFields.getRepValue(),
            durationProgressFields.isProgressEnabled(),
            budgetFields.isBudgetEnabled(),
            hierarchyFields.getPredecessorIdx()
        );

        for (FieldGroup g : allGroups) g.updateVisibility(flags);
    }

    // ========================================================================
    // SAVEITEM — Validation + Builder-Aufbau + Persist
    // ========================================================================

    private void saveItem() {
        String title = coreFields.getTitle();
        if (title.isEmpty()) {
            coreFields.showError("Titel darf nicht leer sein");
            return;
        }

        TrackedItem.Builder builder = new TrackedItem.Builder(
            coreFields.getType(), title, coreFields.getPriority());

        // Uniform: jede Gruppe traegt ihre Werte bei oder guardet sich selbst
        for (FieldGroup g : allGroups) g.apply(builder);

        TrackedItem newItem = builder.build();
        if (editingItem != null) {
            newItem.preserveState(editingItem);
            manager.updateItem(newItem);
        } else {
            manager.createItem(newItem);
        }

        hideModal();
        onSaved.run();
    }
}

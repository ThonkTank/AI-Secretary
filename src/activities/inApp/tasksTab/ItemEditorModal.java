package activities.inApp.tasksTab;

import android.content.Context;
import android.view.View;
import android.widget.Button;

import static activities.generic.ViewHelper.*;

import androidx.core.content.ContextCompat;

import com.autosecretary.R;

import controller.EditorManager;
import entities.TrackedItem;

/**
 * Koordiniert den Modal-Lebenszyklus (Show/Hide) und die
 * Speicher-Logik (Validierung, Builder, Create/Update).
 * Delegiert Feld-Operationen an FieldManager.
 */
class ItemEditorModal {

    private final Context context;
    private final EditorManager manager;
    private final View modalOverlay;
    private final FieldManager fieldManager;
    private final Runnable onSaved;

    private TrackedItem editingItem = null;  // null = Create-Modus

    // ========================================================================
    // KONSTRUKTOR
    // ========================================================================

    ItemEditorModal(Context context, EditorManager manager, View modalOverlay,
                    FieldManager fieldManager, Runnable onSaved) {
        this.context = context;
        this.manager = manager;
        this.modalOverlay = modalOverlay;
        this.fieldManager = fieldManager;
        this.onSaved = onSaved;
    }

    // ========================================================================
    // INIT - Save/Cancel-Buttons und Overlay-Dismiss binden
    // ========================================================================

    void init() {
        setupModalOverlay(modalOverlay, this::hideModal);

        Button saveBtn = modalOverlay.findViewById(R.id.btn_save);
        saveBtn.setBackground(roundedBg(context, ContextCompat.getColor(context, R.color.accent), 4));
        saveBtn.setOnClickListener(v -> saveItem());

        Button cancelBtn = modalOverlay.findViewById(R.id.btn_cancel);
        cancelBtn.setBackground(roundedBg(context, ContextCompat.getColor(context, R.color.button_inactive), 4));
        cancelBtn.setOnClickListener(v -> hideModal());
    }

    // ========================================================================
    // SHOWMODAL - Modal sichtbar machen und Felder befuellen
    // ========================================================================

    void showModal(TrackedItem item) {
        editingItem = item;
        fieldManager.hideError();

        if (item == null) {
            fieldManager.populateForCreate();
        } else {
            fieldManager.populateForEdit(item);
        }

        fieldManager.updateFieldVisibility(fieldManager.getSelectedType());
        fieldManager.refreshParentSpinner();

        // Parent-Spinner auf aktuellen Parent setzen
        fieldManager.selectParent(item);

        // Vorgaenger-Spinner befuellen und setzen
        fieldManager.refreshPredecessorSpinner(editingItem);
        fieldManager.selectPredecessor(item);

        // Budget-Spinner befuellen und setzen
        fieldManager.refreshBudgetAccountSpinner();
        fieldManager.selectBudgetAccount(item);
        fieldManager.selectBudgetCategory(item);

        fieldManager.updateRepDetailsVisibility();
        fieldManager.updateWeekdayVisibility();
        fieldManager.updateProgressPerRepButton();
        fieldManager.updateCompleteFirstButton();
        modalOverlay.setVisibility(View.VISIBLE);
    }

    // ========================================================================
    // HIDEMODAL - Modal ausblenden
    // ========================================================================

    void hideModal() {
        modalOverlay.setVisibility(View.GONE);
        editingItem = null;
    }

    // ========================================================================
    // OPENCREATEMODAL - Create-Modal von aussen oeffnen
    // ========================================================================

    void openCreateModal() {
        showModal(null);
    }

    // ========================================================================
    // SAVEITEM - Validierung + Create/Update + Refresh
    // ========================================================================

    private void saveItem() {
        String title = fieldManager.getTitleText();
        if (title.isEmpty()) {
            fieldManager.showError("Titel darf nicht leer sein.");
            return;
        }
        fieldManager.hideError();

        TrackedItem.Builder builder = new TrackedItem.Builder(
            fieldManager.getSelectedType(), title, fieldManager.getSelectedPriority());

        String desc = fieldManager.getDescriptionText();
        if (!desc.isEmpty()) builder.description(desc);

        fieldManager.applyDurationFields(builder);
        fieldManager.applyDeadlineFields(builder);
        fieldManager.applyCooldownField(builder);
        fieldManager.applyProgressFields(builder);
        fieldManager.applyGoalFields(builder);
        fieldManager.applyParentField(builder);
        fieldManager.applyPrefScheduleFields(builder);
        fieldManager.applyPredecessorFields(builder);
        builder.completeFirst(fieldManager.isCompleteFirstEnabled());
        fieldManager.applyBudgetFields(builder);
        fieldManager.applyRepetitionFields(builder);

        TrackedItem newItem = builder.build();
        persistItem(newItem);

        hideModal();
        onSaved.run();
    }

    // ========================================================================
    // PERSISTITEM - Create oder Update ausfuehren
    // ========================================================================

    private void persistItem(TrackedItem newItem) {
        if (editingItem != null) {
            newItem.id = editingItem.id;
            newItem.created = editingItem.created;
            newItem.children = editingItem.children;
            newItem.lastCompletion = editingItem.lastCompletion;
            newItem.completions = editingItem.completions;
            newItem.isCompleted = editingItem.isCompleted;
            newItem.scheduled = editingItem.scheduled;
            newItem.currentStreak = editingItem.currentStreak;
            newItem.averageStreak = editingItem.averageStreak;
            newItem.nrOfStreaks = editingItem.nrOfStreaks;
            newItem.totalCompletions = editingItem.totalCompletions;
            newItem.followUps = editingItem.followUps;
            manager.updateItem(newItem);
        } else {
            manager.createItem(newItem);
        }
    }
}

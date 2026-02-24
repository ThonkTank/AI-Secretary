package activities.inApp.tasksTab.editorModal.fields;

import android.content.Context;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import static activities.generic.ViewHelper.*;

import com.autosecretary.R;

import java.util.function.BooleanSupplier;

import entities.TrackedItem;
import entities.TrackedItem.ItemType;
import entities.TrackedItem.Priority;

/**
 * Core-Felder: Title, Description, Type-Buttons, Priority-Buttons, Cooldown, Error-Text.
 */
public class CoreFields implements FieldGroup {

    private static final ItemType[] TYPES = {ItemType.TASK, ItemType.GOAL, ItemType.PROJECT};
    private static final Priority[] PRIORITIES = {Priority.LOW, Priority.MODERATE, Priority.HIGH, Priority.CRITICAL};

    private final Context context;
    private final BooleanSupplier suppressCheck;

    private EditText titleField;
    private EditText descriptionField;
    private EditText cooldownField;
    private TextView errorText;
    private Button[] typeButtons = new Button[3];
    private Button[] priorityButtons = new Button[4];
    private View cooldownRow;
    private View priorityRow;

    private ItemType selectedType = ItemType.TASK;
    private Priority selectedPriority = Priority.MODERATE;

    public CoreFields(Context context, View root, BooleanSupplier suppressCheck,
               Runnable onVisibilityTrigger) {
        this.context = context;
        this.suppressCheck = suppressCheck;
        bind(root, onVisibilityTrigger);
    }

    private void bind(View root, Runnable onVisibilityTrigger) {
        titleField = root.findViewById(R.id.field_title);
        descriptionField = root.findViewById(R.id.field_description);
        cooldownField = root.findViewById(R.id.field_cooldown);
        errorText = root.findViewById(R.id.text_error);
        cooldownRow = root.findViewById(R.id.row_cooldown);
        priorityRow = root.findViewById(R.id.row_priority);

        typeButtons[0] = root.findViewById(R.id.btn_type_task);
        typeButtons[1] = root.findViewById(R.id.btn_type_goal);
        typeButtons[2] = root.findViewById(R.id.btn_type_project);
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            typeButtons[i].setOnClickListener(v -> {
                if (suppressCheck.getAsBoolean()) return;
                selectedType = TYPES[idx];
                updateButtonGroup(context, typeButtons, idx);
                onVisibilityTrigger.run();
            });
        }

        priorityButtons[0] = root.findViewById(R.id.btn_prio_low);
        priorityButtons[1] = root.findViewById(R.id.btn_prio_moderate);
        priorityButtons[2] = root.findViewById(R.id.btn_prio_high);
        priorityButtons[3] = root.findViewById(R.id.btn_prio_critical);
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            priorityButtons[i].setOnClickListener(v -> {
                if (suppressCheck.getAsBoolean()) return;
                selectedPriority = PRIORITIES[idx];
                updateButtonGroup(context, priorityButtons, idx);
            });
        }
    }

    // ========================================================================
    // FieldGroup
    // ========================================================================

    @Override
    public void populate(TrackedItem item) {
        if (item != null) {
            titleField.setText(item.title);
            descriptionField.setText(item.description != null ? item.description : "");
            cooldownField.setText(item.cooldown > 0 ? String.valueOf(item.cooldown) : "");
            selectedType = item.type;
            selectedPriority = item.priority != null ? item.priority : Priority.MODERATE;
        } else {
            titleField.setText("");
            descriptionField.setText("");
            cooldownField.setText("");
            selectedType = ItemType.TASK;
            selectedPriority = Priority.MODERATE;
        }
        updateButtonGroup(context, typeButtons, selectedType.ordinal());
        updateButtonGroup(context, priorityButtons, selectedPriority.ordinal());
        for (Button btn : typeButtons) btn.setEnabled(item == null);
    }

    @Override
    public void apply(TrackedItem.Builder builder) {
        String desc = descriptionField.getText().toString().trim();
        if (!desc.isEmpty()) builder.description(desc);

        String cd = cooldownField.getText().toString().trim();
        if (!cd.isEmpty()) {
            try {
                int cooldown = Integer.parseInt(cd);
                if (cooldown > 0) builder.cooldown(cooldown);
            } catch (NumberFormatException ignored) {}
        }
    }

    @Override
    public void updateVisibility(VisibilityFlags flags) {
        cooldownRow.setVisibility(flags.showCooldown() ? View.VISIBLE : View.GONE);
        priorityRow.setVisibility(flags.showPriority() ? View.VISIBLE : View.GONE);
    }

    // ========================================================================
    // GETTER + ERROR
    // ========================================================================

    public ItemType getType() { return selectedType; }
    public Priority getPriority() { return selectedPriority; }
    public String getTitle() { return titleField.getText().toString().trim(); }

    public void showError(String message) {
        errorText.setText(message);
        errorText.setVisibility(View.VISIBLE);
    }

    public void hideError() {
        errorText.setVisibility(View.GONE);
    }
}

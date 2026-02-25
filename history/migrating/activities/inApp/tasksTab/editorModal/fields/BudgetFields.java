package activities.inApp.tasksTab.editorModal.fields;

import android.content.Context;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;

import static activities.generic.ViewHelper.*;

import com.autosecretary.R;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

import controller.taskTab.EditorManager;
import entities.Account;
import entities.Category;
import entities.TrackedItem;

/**
 * Domain-Gruppe: Budget-Toggle, Betrag, Konto-Spinner, Kategorie-Spinner.
 * Haelt Referenz-Listen und exponiert ID-Aufloesung fuer apply().
 */
public class BudgetFields implements FieldGroup {

    private final Context context;
    private final BooleanSupplier suppressCheck;
    private final EditorManager manager;

    private CheckBox budgetCheckbox;
    private EditText budgetAmountField;
    private Spinner budgetAccountSpinner;
    private Spinner budgetCategorySpinner;
    private View budgetToggleRow;
    private View budgetRow;
    private View budgetAccountRow;
    private View budgetCategoryRow;

    private List<Account> availableAccounts = new ArrayList<>();
    private List<Category> expenseCategories = new ArrayList<>();
    private boolean budgetEnabled = false;

    public BudgetFields(Context context, View root, BooleanSupplier suppressCheck,
                 Runnable onVisibilityTrigger, EditorManager manager) {
        this.context = context;
        this.suppressCheck = suppressCheck;
        this.manager = manager;
        bind(root, onVisibilityTrigger);
    }

    private void bind(View root, Runnable onVisibilityTrigger) {
        budgetToggleRow = root.findViewById(R.id.row_budget_toggle);
        budgetCheckbox = root.findViewById(R.id.cb_budget);
        budgetRow = root.findViewById(R.id.row_budget);
        budgetAmountField = root.findViewById(R.id.field_budget_amount);
        budgetAccountRow = root.findViewById(R.id.row_budget_account);
        budgetAccountSpinner = root.findViewById(R.id.spinner_budget_account);
        budgetCategoryRow = root.findViewById(R.id.row_budget_category);
        budgetCategorySpinner = root.findViewById(R.id.spinner_budget_category);

        budgetCheckbox.setOnCheckedChangeListener((btn, checked) -> {
            if (suppressCheck.getAsBoolean()) return;
            budgetEnabled = checked;
            onVisibilityTrigger.run();
        });

        refreshAccountSpinner();
        refreshCategorySpinner();
    }

    // ========================================================================
    // FieldGroup
    // ========================================================================

    @Override
    public void populate(TrackedItem item) {
        refreshAccountSpinner();
        refreshCategorySpinner();

        if (item != null) {
            budgetEnabled = item.budgetRequirementCents > 0;
            budgetAmountField.setText(
                budgetEnabled ? String.format("%.2f", item.budgetRequirementCents / 100.0) : "");
            int accIdx = findOffsetIdx(availableAccounts, item.budgetAccountId, a -> a.id);
            int catIdx = findOffsetIdx(expenseCategories, item.budgetCategoryId, c -> c.id);
            if (accIdx < budgetAccountSpinner.getCount()) budgetAccountSpinner.setSelection(accIdx);
            if (catIdx < budgetCategorySpinner.getCount()) budgetCategorySpinner.setSelection(catIdx);
        } else {
            budgetEnabled = false;
            budgetAmountField.setText("");
            budgetAccountSpinner.setSelection(0);
            budgetCategorySpinner.setSelection(0);
        }
        budgetCheckbox.setChecked(budgetEnabled);
    }

    @Override
    public void apply(TrackedItem.Builder builder) {
        if (!budgetEnabled) return;

        String amountStr = budgetAmountField.getText().toString().trim();
        if (!amountStr.isEmpty()) {
            try {
                double euros = Double.parseDouble(amountStr.replace(",", "."));
                builder.budgetRequirement((int) (euros * 100));
            } catch (NumberFormatException ignored) {}
        }

        int accIdx = budgetAccountSpinner.getSelectedItemPosition();
        Long accId = resolveAtOffset(availableAccounts, accIdx, a -> a.id);
        if (accIdx > 0 && accId != null) builder.budgetAccount(accId);

        int catIdx = budgetCategorySpinner.getSelectedItemPosition();
        Long catId = resolveAtOffset(expenseCategories, catIdx, c -> c.id);
        if (catIdx > 0 && catId != null) builder.budgetCategory(catId);
    }

    @Override
    public void updateVisibility(VisibilityFlags flags) {
        budgetToggleRow.setVisibility(flags.showBudgetToggle() ? View.VISIBLE : View.GONE);
        int fieldVis = flags.showBudgetFields() ? View.VISIBLE : View.GONE;
        budgetRow.setVisibility(fieldVis);
        budgetAccountRow.setVisibility(fieldVis);
        budgetCategoryRow.setVisibility(fieldVis);
    }

    // ========================================================================
    // GETTER
    // ========================================================================

    public boolean isBudgetEnabled() { return budgetEnabled; }

    // ========================================================================
    // SPINNER-REFRESH
    // ========================================================================

    private void refreshAccountSpinner() {
        availableAccounts.clear();
        List<String> accountNames = new ArrayList<>();
        accountNames.add("(beliebig)");
        for (Account acc : manager.getActiveAccounts()) {
            availableAccounts.add(acc);
            accountNames.add(acc.name);
        }
        budgetAccountSpinner.setAdapter(spinnerAdapter(context, accountNames));
    }

    private void refreshCategorySpinner() {
        expenseCategories.clear();
        List<String> categoryNames = new ArrayList<>();
        categoryNames.add("(keine)");
        for (Category cat : manager.getExpenseCategories()) {
            expenseCategories.add(cat);
            String displayName = (cat.icon != null ? cat.icon + " " : "") + cat.name;
            categoryNames.add(displayName);
        }
        budgetCategorySpinner.setAdapter(spinnerAdapter(context, categoryNames));
    }
}

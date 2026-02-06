package activities.inApp.budgetTab;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import static activities.generic.ViewHelper.dp;
import static activities.generic.ViewHelper.roundedBg;
import static activities.generic.ViewHelper.showEmptyState;
import static activities.generic.ViewHelper.FAB_CORNER_RADIUS_DP;
import static activities.generic.ViewHelper.FAB_SECONDARY_CORNER_RADIUS_DP;

import androidx.core.content.ContextCompat;

import com.autosecretary.R;

import java.util.List;

import activities.generic.ViewBuilder;
import controller.BudgetManager;
import controller.BudgetManager.*;
import data.BudgetDisplayData;
import entities.Account;
import entities.Transaction;

/**
 * Budget View - UI fuer Budget-Tab
 *
 * Zeigt:
 * - Gesamtuebersicht (Saldo + monatliche Einnahmen/Ausgaben)
 * - Konten als horizontale Karten
 * - Budget-Limits als Fortschrittsbalken
 * - Letzte Transaktionen
 *
 * Delegiert Modals an TransactionModal, ImportModal, RecurringSuggestionsModal.
 */
public class BudgetView implements BudgetListener, ViewBuilder {

    private final Context context;
    private final BudgetManager manager;

    // UI References
    private View root;
    private TextView totalBalance;
    private TextView monthlyIncome;
    private TextView monthlyExpenses;
    private TextView monthlyNet;
    private TextView budgetSectionTitle;
    private LinearLayout accountsContainer;
    private LinearLayout budgetBarsContainer;
    private LinearLayout transactionsContainer;
    private FrameLayout fabAddTransaction;
    private FrameLayout fabImport;

    // Extracted Modals
    private final TransactionModal transactionModal;
    private final ImportModal importModal;
    private final RecurringSuggestionsModal recurringModal;

    public BudgetView(Context context, BudgetManager manager) {
        this.context = context;
        this.manager = manager;
        this.manager.setListener(this);

        transactionModal = new TransactionModal(context, manager);
        importModal = new ImportModal(context, manager);
        recurringModal = new RecurringSuggestionsModal(context, manager);

        importModal.setOnImportSuccess((accountId, candidates) ->
            recurringModal.show(candidates, accountId));
    }

    @Override
    public View buildView() {
        root = LayoutInflater.from(context).inflate(R.layout.view_budget, null);

        // Bind Views
        totalBalance = root.findViewById(R.id.total_balance);
        monthlyIncome = root.findViewById(R.id.monthly_income);
        monthlyExpenses = root.findViewById(R.id.monthly_expenses);
        monthlyNet = root.findViewById(R.id.monthly_net);
        budgetSectionTitle = root.findViewById(R.id.budget_section_title);
        accountsContainer = root.findViewById(R.id.accounts_container);
        budgetBarsContainer = root.findViewById(R.id.budget_bars_container);
        transactionsContainer = root.findViewById(R.id.transactions_container);
        fabAddTransaction = root.findViewById(R.id.fab_add_transaction);
        fabImport = root.findViewById(R.id.fab_import);

        // Setup FABs
        fabAddTransaction.setBackground(roundedBg(context,
            ContextCompat.getColor(context, R.color.accent), FAB_CORNER_RADIUS_DP));
        fabAddTransaction.setOnClickListener(v -> transactionModal.show(null));

        fabImport.setBackground(roundedBg(context,
            ContextCompat.getColor(context, R.color.button_inactive), FAB_SECONDARY_CORNER_RADIUS_DP));
        fabImport.setOnClickListener(v -> importModal.show());

        // Init Modals
        FrameLayout modalOverlay = root.findViewById(R.id.modal_overlay);
        FrameLayout importModalOverlay = root.findViewById(R.id.import_modal_overlay);
        FrameLayout recurringModalOverlay = root.findViewById(R.id.recurring_modal_overlay);

        transactionModal.init(modalOverlay, root);
        importModal.init(importModalOverlay, root);
        recurringModal.init(recurringModalOverlay);

        // Initial Render
        render();

        return root;
    }

    public void setFilePickerCallback(ImportModal.FilePickerCallback callback) {
        importModal.setFilePickerCallback(callback);
    }

    public void showTransactionModal(Transaction tx) {
        transactionModal.show(tx);
    }

    public void onFileSelected(String fileName, byte[] fileBytes, String mimeType) {
        importModal.onFileSelected(fileName, fileBytes, mimeType);
    }

    // ============================================================================
    // RENDER METHODS
    // ============================================================================

    private void render() {
        String yearMonth = manager.getCurrentYearMonth();

        renderSummary(yearMonth);
        renderAccounts();
        renderBudgetBars(yearMonth);
        renderTransactions();
    }

    private void renderSummary(String yearMonth) {
        BudgetSummary summary = manager.provideSummary(yearMonth);

        totalBalance.setText(summary.formattedTotal());
        monthlyIncome.setText("+" + BudgetDisplayData.formatCents(summary.monthlyIncomeCents()));
        monthlyExpenses.setText("-" + BudgetDisplayData.formatCents(summary.monthlyExpensesCents()));
        monthlyNet.setText(summary.formattedNet());

        int netColor = summary.monthlyNetCents() >= 0
            ? ContextCompat.getColor(context, R.color.budget_income)
            : ContextCompat.getColor(context, R.color.budget_expense);
        monthlyNet.setTextColor(netColor);

        budgetSectionTitle.setText("Budget " + BudgetDisplayData.formatYearMonth(yearMonth));
    }

    private void renderAccounts() {
        accountsContainer.removeAllViews();
        List<AccountEntry> accountsList = manager.provideAccounts();

        LayoutInflater inflater = LayoutInflater.from(context);
        for (AccountEntry acc : accountsList) {
            View card = inflater.inflate(R.layout.item_account_card, accountsContainer, false);

            TextView icon = card.findViewById(R.id.account_icon);
            TextView name = card.findViewById(R.id.account_name);
            TextView balance = card.findViewById(R.id.account_balance);
            TextView type = card.findViewById(R.id.account_type);

            icon.setText(acc.icon() != null ? acc.icon() : "");
            name.setText(acc.name());
            balance.setText(acc.formatted());
            type.setText(getAccountTypeName(acc.type()));

            if (acc.color() != null) {
                int color;
                try {
                    color = Color.parseColor(acc.color());
                } catch (IllegalArgumentException e) {
                    color = ContextCompat.getColor(context, R.color.accent);
                }
                card.setBackground(roundedBg(context, color, 4));
                name.setTextColor(Color.WHITE);
                balance.setTextColor(Color.WHITE);
                type.setTextColor(Color.argb(180, 255, 255, 255));
                icon.setTextColor(Color.WHITE);
            }

            accountsContainer.addView(card);
        }
    }

    private void renderBudgetBars(String yearMonth) {
        budgetBarsContainer.removeAllViews();
        List<BudgetEntry> budgets = manager.provideBudgetLimits(yearMonth);

        if (budgets.isEmpty()) {
            showEmptyState(budgetBarsContainer, "Keine Budget-Limits definiert");
            return;
        }

        LayoutInflater inflater = LayoutInflater.from(context);
        for (BudgetEntry budget : budgets) {
            View bar = inflater.inflate(R.layout.item_budget_bar, budgetBarsContainer, false);

            TextView icon = bar.findViewById(R.id.budget_icon);
            TextView category = bar.findViewById(R.id.budget_category);
            TextView amounts = bar.findViewById(R.id.budget_amounts);
            View progress = bar.findViewById(R.id.budget_progress);
            TextView warning = bar.findViewById(R.id.budget_warning);

            icon.setText(budget.categoryIcon());
            category.setText(budget.categoryLabel());
            amounts.setText(budget.formattedSpent() + " / " + budget.formattedLimit());

            double percent = Math.min(budget.percentUsed(), 1.0);
            progress.post(() -> {
                int parentWidth = ((View) progress.getParent()).getWidth();
                FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    (int) (parentWidth * percent),
                    FrameLayout.LayoutParams.MATCH_PARENT
                );
                progress.setLayoutParams(params);
            });

            int barColor;
            if (budget.percentUsed() > 1.0) {
                barColor = ContextCompat.getColor(context, R.color.budget_bar_danger);
            } else if (budget.percentUsed() > 0.8) {
                barColor = ContextCompat.getColor(context, R.color.budget_bar_warning);
            } else {
                barColor = ContextCompat.getColor(context, R.color.budget_bar_safe);
            }
            progress.setBackgroundColor(barColor);

            if (budget.isOverBudget()) {
                warning.setVisibility(View.VISIBLE);
            }

            budgetBarsContainer.addView(bar);
        }
    }

    private void renderTransactions() {
        transactionsContainer.removeAllViews();
        List<TransactionEntry> transactions = manager.provideRecentTransactions(5);

        if (transactions.isEmpty()) {
            showEmptyState(transactionsContainer, "Keine Transaktionen vorhanden");
            return;
        }

        LayoutInflater inflater = LayoutInflater.from(context);
        for (TransactionEntry tx : transactions) {
            View row = inflater.inflate(R.layout.item_transaction_row, transactionsContainer, false);

            TextView icon = row.findViewById(R.id.tx_icon);
            TextView payee = row.findViewById(R.id.tx_payee);
            TextView category = row.findViewById(R.id.tx_category);
            TextView amount = row.findViewById(R.id.tx_amount);
            TextView date = row.findViewById(R.id.tx_date);
            TextView recurring = row.findViewById(R.id.tx_recurring);

            icon.setText(tx.categoryIcon());
            payee.setText(tx.payee() != null && !tx.payee().isEmpty() ? tx.payee() : tx.categoryLabel());
            category.setText(tx.categoryLabel());
            amount.setText(tx.formatted());
            date.setText(tx.dateFormatted());

            int amountColor = tx.isIncome()
                ? ContextCompat.getColor(context, R.color.budget_income)
                : ContextCompat.getColor(context, R.color.budget_expense);
            amount.setTextColor(amountColor);

            if (tx.isRecurring()) {
                recurring.setVisibility(View.VISIBLE);
            }

            row.setOnClickListener(v -> {
                // TODO: Edit-Modal öffnen mit dieser Transaktion
            });

            transactionsContainer.addView(row);
        }
    }

    // ============================================================================
    // HELPERS
    // ============================================================================

    private String getAccountTypeName(Account.AccountType type) {
        return switch (type) {
            case CHECKING -> "Girokonto";
            case SAVINGS -> "Sparkonto";
            case CASH -> "Bargeld";
            case CREDIT -> "Kreditkarte";
        };
    }

    // ============================================================================
    // BUDGET LISTENER
    // ============================================================================

    @Override
    public void onDataUpdated() {
        if (root != null) {
            root.post(this::render);
        }
    }
}

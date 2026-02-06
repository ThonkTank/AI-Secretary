package activities.inApp;

import android.app.DatePickerDialog;
import android.content.Context;
import android.graphics.Color;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;

import static activities.generic.ViewHelper.dp;
import static activities.generic.ViewHelper.roundedBg;

import androidx.core.content.ContextCompat;

import com.autosecretary.R;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import activities.generic.ViewBuilder;
import controller.ApiKeyManager;
import controller.ImportProcessor;
import controller.RecurringPatternDetector;
import controller.budgetManager;
import controller.budgetManager.*;
import data.BudgetDisplayData;
import entities.Account;
import entities.Transaction;

/**
 * ══════════════════════════════════════════════════════════════════════════════
 * BUDGET VIEW - UI für Budget-Tab
 * ══════════════════════════════════════════════════════════════════════════════
 *
 * Zeigt:
 * - Gesamtübersicht (Saldo + monatliche Einnahmen/Ausgaben)
 * - Konten als horizontale Karten
 * - Budget-Limits als Fortschrittsbalken
 * - Letzte Transaktionen
 *
 * Implementiert BudgetListener für automatische UI-Updates.
 */
public class budgetView implements BudgetListener, ViewBuilder {

    private Context context;
    private budgetManager manager;

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
    private FrameLayout modalOverlay;
    private FrameLayout importModalOverlay;

    // Modal References (Transaction)
    private TextView modalTitle;
    private TextView btnExpense;
    private TextView btnIncome;
    private EditText inputAmount;
    private Spinner spinnerAccount;
    private Spinner spinnerCategory;
    private TextView inputDate;
    private EditText inputDescription;
    private EditText inputPayee;
    private TextView errorText;
    private TextView btnCancel;
    private TextView btnSave;

    // Modal References (Import)
    private Spinner importSpinnerAccount;
    private TextView btnSelectFile;
    private TextView textFilename;
    private EditText inputApiKey;
    private TextView btnToggleKey;
    private CheckBox checkboxSaveKey;
    private LinearLayout progressSection;
    private ProgressBar progressBar;
    private TextView progressText;
    private LinearLayout resultSection;
    private TextView resultTitle;
    private TextView resultDetails;
    private TextView importErrorText;
    private TextView btnImportCancel;
    private TextView btnImport;

    // State
    private boolean isIncomeMode = false;
    private LocalDate selectedDate = LocalDate.now();
    private Transaction editingTransaction = null;
    private List<AccountEntry> accountsList = new ArrayList<>();
    private List<CategoryOption> categoriesList = new ArrayList<>();

    // Import State
    private byte[] selectedFileBytes = null;
    private String selectedFileName = null;
    private String selectedFileMimeType = null;
    private boolean apiKeyVisible = false;
    private Long importAccountId = null; // Speichert AccountId fuer Recurring-Template-Erstellung

    // Modal References (Recurring Suggestions)
    private FrameLayout recurringModalOverlay;
    private TextView recurringCountBadge;
    private LinearLayout candidatesContainer;
    private TextView textSelectionInfo;
    private TextView btnRecurringSkip;
    private TextView btnRecurringCreate;

    // Recurring State
    private List<RecurringPatternDetector.RecurringCandidate> currentCandidates = new ArrayList<>();
    private List<Boolean> candidateSelections = new ArrayList<>();

    // Callback Interface for File Picker
    public interface FilePickerCallback {
        void requestFilePicker();
    }
    private FilePickerCallback filePickerCallback;

    public budgetView(Context context, budgetManager manager) {
        this.context = context;
        this.manager = manager;
        this.manager.setListener(this);
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
        modalOverlay = root.findViewById(R.id.modal_overlay);

        // Bind Modal Views
        modalTitle = root.findViewById(R.id.modal_title);
        btnExpense = root.findViewById(R.id.btn_expense);
        btnIncome = root.findViewById(R.id.btn_income);
        inputAmount = root.findViewById(R.id.input_amount);
        spinnerAccount = root.findViewById(R.id.spinner_account);
        spinnerCategory = root.findViewById(R.id.spinner_category);
        inputDate = root.findViewById(R.id.input_date);
        inputDescription = root.findViewById(R.id.input_description);
        inputPayee = root.findViewById(R.id.input_payee);
        errorText = root.findViewById(R.id.error_text);
        btnCancel = root.findViewById(R.id.btn_cancel);
        btnSave = root.findViewById(R.id.btn_save);

        // Setup FABs
        fabAddTransaction.setBackground(roundedBg(context,
            ContextCompat.getColor(context, R.color.accent), 28));
        fabAddTransaction.setOnClickListener(v -> showTransactionModal(null));

        fabImport = root.findViewById(R.id.fab_import);
        fabImport.setBackground(roundedBg(context,
            ContextCompat.getColor(context, R.color.button_inactive), 24));
        fabImport.setOnClickListener(v -> showImportModal());

        // Setup Import Modal Overlay
        importModalOverlay = root.findViewById(R.id.import_modal_overlay);

        // Setup Recurring Modal Overlay
        recurringModalOverlay = root.findViewById(R.id.recurring_modal_overlay);

        // Setup Modals
        setupModal();
        setupImportModal();
        setupRecurringModal();

        // Initial Render
        render();

        return root;
    }

    public void setFilePickerCallback(FilePickerCallback callback) {
        this.filePickerCallback = callback;
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

        // Netto-Farbe: grün wenn positiv, rot wenn negativ
        int netColor = summary.monthlyNetCents() >= 0
            ? ContextCompat.getColor(context, R.color.budget_income)
            : ContextCompat.getColor(context, R.color.budget_expense);
        monthlyNet.setTextColor(netColor);

        // Budget-Section-Titel mit Monat
        budgetSectionTitle.setText("Budget " + BudgetDisplayData.formatYearMonth(yearMonth));
    }

    private void renderAccounts() {
        accountsContainer.removeAllViews();
        accountsList = manager.provideAccounts();

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

            // Farbiger Rand basierend auf Konto-Farbe
            if (acc.color() != null) {
                try {
                    int color = Color.parseColor(acc.color());
                    card.setBackground(roundedBg(context, color, 4));
                    // Text auf Karte anpassen für bessere Lesbarkeit
                    name.setTextColor(Color.WHITE);
                    balance.setTextColor(Color.WHITE);
                    type.setTextColor(Color.argb(180, 255, 255, 255));
                    icon.setTextColor(Color.WHITE);
                } catch (IllegalArgumentException ignored) {}
            }

            accountsContainer.addView(card);
        }
    }

    private void renderBudgetBars(String yearMonth) {
        budgetBarsContainer.removeAllViews();
        List<BudgetEntry> budgets = manager.provideBudgetLimits(yearMonth);

        if (budgets.isEmpty()) {
            TextView empty = new TextView(context);
            empty.setText("Keine Budget-Limits definiert");
            empty.setTextColor(ContextCompat.getColor(context, R.color.text_secondary));
            empty.setPadding(0, dp(context, 8), 0, dp(context, 8));
            budgetBarsContainer.addView(empty);
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

            // Progress-Breite berechnen (max 100%)
            double percent = Math.min(budget.percentUsed(), 1.0);
            progress.post(() -> {
                int parentWidth = ((View) progress.getParent()).getWidth();
                FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    (int) (parentWidth * percent),
                    FrameLayout.LayoutParams.MATCH_PARENT
                );
                progress.setLayoutParams(params);
            });

            // Farbe basierend auf Prozent
            int barColor;
            if (budget.percentUsed() > 1.0) {
                barColor = ContextCompat.getColor(context, R.color.budget_bar_danger);
            } else if (budget.percentUsed() > 0.8) {
                barColor = ContextCompat.getColor(context, R.color.budget_bar_warning);
            } else {
                barColor = ContextCompat.getColor(context, R.color.budget_bar_safe);
            }
            progress.setBackgroundColor(barColor);

            // Warnung wenn überschritten
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
            TextView empty = new TextView(context);
            empty.setText("Keine Transaktionen vorhanden");
            empty.setTextColor(ContextCompat.getColor(context, R.color.text_secondary));
            empty.setPadding(0, dp(context, 8), 0, dp(context, 8));
            transactionsContainer.addView(empty);
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

            // Farbe für Betrag
            int amountColor = tx.isIncome()
                ? ContextCompat.getColor(context, R.color.budget_income)
                : ContextCompat.getColor(context, R.color.budget_expense);
            amount.setTextColor(amountColor);

            // Recurring-Indikator
            if (tx.isRecurring()) {
                recurring.setVisibility(View.VISIBLE);
            }

            // Click-Listener für Edit
            row.setOnClickListener(v -> {
                // TODO: Edit-Modal öffnen mit dieser Transaktion
            });

            transactionsContainer.addView(row);
        }
    }

    // ============================================================================
    // MODAL HANDLING
    // ============================================================================

    private void setupModal() {
        // Type Toggle
        btnExpense.setOnClickListener(v -> setIncomeMode(false));
        btnIncome.setOnClickListener(v -> setIncomeMode(true));

        // Date Picker
        inputDate.setOnClickListener(v -> {
            DatePickerDialog picker = new DatePickerDialog(context,
                (view, year, month, day) -> {
                    selectedDate = LocalDate.of(year, month + 1, day);
                    inputDate.setText(BudgetDisplayData.formatDate(selectedDate));
                },
                selectedDate.getYear(),
                selectedDate.getMonthValue() - 1,
                selectedDate.getDayOfMonth()
            );
            picker.show();
        });

        // Cancel Button
        btnCancel.setOnClickListener(v -> hideTransactionModal());

        // Save Button
        btnSave.setOnClickListener(v -> saveTransaction());

        // Modal Overlay click closes modal
        modalOverlay.setOnClickListener(v -> hideTransactionModal());

        // Modal Card click should not close
        View modalCard = root.findViewById(R.id.modal_card);
        modalCard.setOnClickListener(v -> {}); // Consume click
    }

    public void showTransactionModal(Transaction tx) {
        editingTransaction = tx;
        errorText.setVisibility(View.GONE);

        // Load data for spinners
        accountsList = manager.provideAccounts();
        categoriesList = isIncomeMode
            ? manager.provideCategories().stream().filter(CategoryOption::isIncome).toList()
            : manager.provideCategories().stream().filter(c -> !c.isIncome()).toList();

        // Account Spinner
        List<String> accountNames = new ArrayList<>();
        for (AccountEntry acc : accountsList) {
            accountNames.add(acc.name());
        }
        ArrayAdapter<String> accountAdapter = new ArrayAdapter<>(context,
            android.R.layout.simple_spinner_dropdown_item, accountNames);
        spinnerAccount.setAdapter(accountAdapter);

        // Category Spinner
        updateCategorySpinner();

        if (tx == null) {
            // Create mode
            modalTitle.setText("Neue Transaktion");
            setIncomeMode(false);
            inputAmount.setText("");
            selectedDate = LocalDate.now();
            inputDate.setText(BudgetDisplayData.formatDate(selectedDate));
            inputDescription.setText("");
            inputPayee.setText("");
            spinnerAccount.setSelection(0);
            spinnerCategory.setSelection(0);
        } else {
            // Edit mode
            modalTitle.setText("Transaktion bearbeiten");
            setIncomeMode(tx.isIncome);
            inputAmount.setText(String.format("%.2f", Math.abs(tx.amountCents) / 100.0));
            selectedDate = tx.transactionDate;
            inputDate.setText(BudgetDisplayData.formatDate(selectedDate));
            inputDescription.setText(tx.description != null ? tx.description : "");
            inputPayee.setText(tx.payee != null ? tx.payee : "");

            // Select correct account
            for (int i = 0; i < accountsList.size(); i++) {
                if (accountsList.get(i).accountId().equals(tx.accountId)) {
                    spinnerAccount.setSelection(i);
                    break;
                }
            }

            // Select correct category
            for (int i = 0; i < categoriesList.size(); i++) {
                if (categoriesList.get(i).categoryId() != null &&
                    categoriesList.get(i).categoryId().equals(tx.categoryId)) {
                    spinnerCategory.setSelection(i);
                    break;
                }
            }
        }

        modalOverlay.setVisibility(View.VISIBLE);
    }

    private void hideTransactionModal() {
        modalOverlay.setVisibility(View.GONE);
        editingTransaction = null;
    }

    private void setIncomeMode(boolean income) {
        isIncomeMode = income;

        if (income) {
            btnIncome.setBackgroundColor(ContextCompat.getColor(context, R.color.budget_income));
            btnIncome.setTextColor(Color.WHITE);
            btnExpense.setBackgroundColor(ContextCompat.getColor(context, R.color.button_inactive));
            btnExpense.setTextColor(ContextCompat.getColor(context, R.color.text_primary));
        } else {
            btnExpense.setBackgroundColor(ContextCompat.getColor(context, R.color.budget_expense));
            btnExpense.setTextColor(Color.WHITE);
            btnIncome.setBackgroundColor(ContextCompat.getColor(context, R.color.button_inactive));
            btnIncome.setTextColor(ContextCompat.getColor(context, R.color.text_primary));
        }

        // Update category spinner for income/expense categories
        updateCategorySpinner();
    }

    private void updateCategorySpinner() {
        categoriesList = isIncomeMode
            ? manager.provideCategories().stream().filter(CategoryOption::isIncome).toList()
            : manager.provideCategories().stream().filter(c -> !c.isIncome()).toList();

        List<String> categoryNames = new ArrayList<>();
        for (CategoryOption cat : categoriesList) {
            categoryNames.add(cat.icon() + " " + cat.label());
        }
        ArrayAdapter<String> categoryAdapter = new ArrayAdapter<>(context,
            android.R.layout.simple_spinner_dropdown_item, categoryNames);
        spinnerCategory.setAdapter(categoryAdapter);
    }

    private void saveTransaction() {
        // Validate
        String amountStr = inputAmount.getText().toString().replace(",", ".");
        double amount;
        try {
            amount = Double.parseDouble(amountStr);
        } catch (NumberFormatException e) {
            showError("Bitte gültigen Betrag eingeben");
            return;
        }

        if (amount <= 0) {
            showError("Betrag muss größer als 0 sein");
            return;
        }

        if (accountsList.isEmpty()) {
            showError("Kein Konto verfügbar");
            return;
        }

        if (categoriesList.isEmpty()) {
            showError("Keine Kategorie verfügbar");
            return;
        }

        // Get selected values
        int accountIndex = spinnerAccount.getSelectedItemPosition();
        int categoryIndex = spinnerCategory.getSelectedItemPosition();

        if (accountIndex < 0 || accountIndex >= accountsList.size()) {
            showError("Bitte Konto auswählen");
            return;
        }

        if (categoryIndex < 0 || categoryIndex >= categoriesList.size()) {
            showError("Bitte Kategorie auswählen");
            return;
        }

        Long accountId = accountsList.get(accountIndex).accountId();
        Long categoryId = categoriesList.get(categoryIndex).categoryId();

        // Amount in cents (negative for expenses)
        int amountCents = (int) Math.round(amount * 100);
        if (!isIncomeMode) {
            amountCents = -amountCents;
        }

        // Build Transaction
        Transaction tx = new Transaction.Builder(accountId, amountCents, selectedDate, categoryId)
            .description(inputDescription.getText().toString().trim())
            .payee(inputPayee.getText().toString().trim())
            .build();

        if (editingTransaction != null) {
            tx.id = editingTransaction.id;
            manager.updateTransaction(tx);
        } else {
            manager.createTransaction(tx);
        }

        hideTransactionModal();
    }

    private void showError(String message) {
        errorText.setText(message);
        errorText.setVisibility(View.VISIBLE);
    }

    // ============================================================================
    // IMPORT MODAL HANDLING
    // ============================================================================

    private void setupImportModal() {
        // Bind Import Modal Views
        importSpinnerAccount = importModalOverlay.findViewById(R.id.spinner_account);
        btnSelectFile = importModalOverlay.findViewById(R.id.btn_select_file);
        textFilename = importModalOverlay.findViewById(R.id.text_filename);
        inputApiKey = importModalOverlay.findViewById(R.id.input_api_key);
        btnToggleKey = importModalOverlay.findViewById(R.id.btn_toggle_key);
        checkboxSaveKey = importModalOverlay.findViewById(R.id.checkbox_save_key);
        progressSection = importModalOverlay.findViewById(R.id.progress_section);
        progressBar = importModalOverlay.findViewById(R.id.progress_bar);
        progressText = importModalOverlay.findViewById(R.id.progress_text);
        resultSection = importModalOverlay.findViewById(R.id.result_section);
        resultTitle = importModalOverlay.findViewById(R.id.result_title);
        resultDetails = importModalOverlay.findViewById(R.id.result_details);
        importErrorText = importModalOverlay.findViewById(R.id.error_text);
        btnImportCancel = importModalOverlay.findViewById(R.id.btn_cancel);
        btnImport = importModalOverlay.findViewById(R.id.btn_import);

        // File Selection
        btnSelectFile.setOnClickListener(v -> {
            if (filePickerCallback != null) {
                filePickerCallback.requestFilePicker();
            }
        });

        // API Key Toggle Visibility
        btnToggleKey.setOnClickListener(v -> {
            apiKeyVisible = !apiKeyVisible;
            if (apiKeyVisible) {
                inputApiKey.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                btnToggleKey.setText("🙈");
            } else {
                inputApiKey.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                btnToggleKey.setText("👁");
            }
            // Cursor ans Ende setzen
            inputApiKey.setSelection(inputApiKey.getText().length());
        });

        // Cancel Button
        btnImportCancel.setOnClickListener(v -> hideImportModal());

        // Import Button
        btnImport.setOnClickListener(v -> startImport());

        // Modal Overlay click closes modal
        importModalOverlay.setOnClickListener(v -> hideImportModal());

        // Modal Card click should not close
        View importModalCard = importModalOverlay.findViewById(R.id.modal_card);
        importModalCard.setOnClickListener(v -> {}); // Consume click
    }

    public void showImportModal() {
        // Reset state
        selectedFileBytes = null;
        selectedFileName = null;
        selectedFileMimeType = null;
        textFilename.setText("Keine Datei ausgewaehlt");
        importErrorText.setVisibility(View.GONE);
        progressSection.setVisibility(View.GONE);
        resultSection.setVisibility(View.GONE);
        btnImport.setEnabled(true);
        btnImport.setBackgroundColor(ContextCompat.getColor(context, R.color.accent));

        // Load accounts for spinner
        accountsList = manager.provideAccounts();
        List<String> accountNames = new ArrayList<>();
        for (AccountEntry acc : accountsList) {
            accountNames.add(acc.icon() + " " + acc.name());
        }
        ArrayAdapter<String> accountAdapter = new ArrayAdapter<>(context,
            android.R.layout.simple_spinner_dropdown_item, accountNames);
        importSpinnerAccount.setAdapter(accountAdapter);

        // Load saved API key if available
        String savedKey = ApiKeyManager.getApiKey(context);
        if (savedKey != null) {
            inputApiKey.setText(savedKey);
            checkboxSaveKey.setChecked(true);
        } else {
            inputApiKey.setText("");
            checkboxSaveKey.setChecked(false);
        }

        importModalOverlay.setVisibility(View.VISIBLE);
    }

    public void hideImportModal() {
        importModalOverlay.setVisibility(View.GONE);
        selectedFileBytes = null;
        selectedFileName = null;
        selectedFileMimeType = null;
    }

    /**
     * Called from mainActivity when a file is selected via the file picker.
     */
    public void onFileSelected(String fileName, byte[] fileBytes, String mimeType) {
        this.selectedFileName = fileName;
        this.selectedFileBytes = fileBytes;
        this.selectedFileMimeType = mimeType;
        textFilename.setText(fileName);
    }

    private void startImport() {
        // Validate
        if (selectedFileBytes == null) {
            showImportError("Bitte Datei auswaehlen");
            return;
        }

        String apiKey = inputApiKey.getText().toString().trim();
        if (apiKey.isEmpty()) {
            showImportError("Bitte API-Key eingeben");
            return;
        }

        if (!ApiKeyManager.isValidFormat(apiKey)) {
            showImportError("Ungueltiges API-Key Format (erwartet: sk-ant-...)");
            return;
        }

        if (accountsList.isEmpty()) {
            showImportError("Kein Konto verfuegbar");
            return;
        }

        int accountIndex = importSpinnerAccount.getSelectedItemPosition();
        if (accountIndex < 0 || accountIndex >= accountsList.size()) {
            showImportError("Bitte Konto auswaehlen");
            return;
        }

        Long accountId = accountsList.get(accountIndex).accountId();

        // Save API key if checkbox is checked
        if (checkboxSaveKey.isChecked()) {
            ApiKeyManager.saveApiKey(context, apiKey);
        }

        // Show progress
        importErrorText.setVisibility(View.GONE);
        resultSection.setVisibility(View.GONE);
        progressSection.setVisibility(View.VISIBLE);
        progressText.setText("Initialisiere...");
        btnImport.setEnabled(false);
        btnImport.setBackgroundColor(ContextCompat.getColor(context, R.color.button_inactive));

        // Start import
        ImportProcessor processor = new ImportProcessor(context);
        processor.processImportAsync(accountId, selectedFileName, selectedFileBytes,
            selectedFileMimeType, apiKey, new ImportProcessor.ImportCallback() {
                @Override
                public void onProgress(String message) {
                    progressText.setText(message);
                }

                @Override
                public void onSuccess(ImportProcessor.ImportResult result) {
                    progressSection.setVisibility(View.GONE);
                    resultSection.setVisibility(View.VISIBLE);
                    resultSection.setBackgroundColor(ContextCompat.getColor(context, R.color.budget_positive_bg));
                    resultTitle.setText("Import abgeschlossen");
                    resultTitle.setTextColor(ContextCompat.getColor(context, R.color.budget_positive));
                    resultDetails.setText(result.newTransactions() + " neue Transaktionen\n" +
                        result.duplicates() + " Duplikate uebersprungen");
                    btnImportCancel.setText("Schliessen");
                    btnImport.setVisibility(View.GONE);

                    // Wenn wiederkehrende Muster erkannt wurden, Modal anzeigen
                    if (result.recurringCandidates() != null && !result.recurringCandidates().isEmpty()) {
                        // Account-ID speichern fuer Template-Erstellung
                        importAccountId = accountId;
                        // Import-Modal schliessen und Recurring-Modal nach kurzer Verzoegerung anzeigen
                        root.postDelayed(() -> {
                            hideImportModal();
                            showRecurringSuggestionsModal(result.recurringCandidates());
                        }, 1500);
                    }
                }

                @Override
                public void onError(String errorMessage) {
                    progressSection.setVisibility(View.GONE);
                    showImportError(errorMessage);
                    btnImport.setEnabled(true);
                    btnImport.setBackgroundColor(ContextCompat.getColor(context, R.color.accent));
                }
            });
    }

    private void showImportError(String message) {
        importErrorText.setText(message);
        importErrorText.setVisibility(View.VISIBLE);
    }

    // ============================================================================
    // RECURRING SUGGESTIONS MODAL
    // ============================================================================

    private void setupRecurringModal() {
        // Bind Recurring Modal Views
        recurringCountBadge = recurringModalOverlay.findViewById(R.id.text_count_badge);
        candidatesContainer = recurringModalOverlay.findViewById(R.id.candidates_container);
        textSelectionInfo = recurringModalOverlay.findViewById(R.id.text_selection_info);
        btnRecurringSkip = recurringModalOverlay.findViewById(R.id.btn_skip);
        btnRecurringCreate = recurringModalOverlay.findViewById(R.id.btn_create);

        // Skip Button
        btnRecurringSkip.setOnClickListener(v -> hideRecurringModal());

        // Create Button
        btnRecurringCreate.setOnClickListener(v -> createSelectedTemplates());

        // Modal Overlay click closes modal
        recurringModalOverlay.setOnClickListener(v -> hideRecurringModal());

        // Modal Card click should not close
        View recurringModalCard = recurringModalOverlay.findViewById(R.id.modal_card);
        recurringModalCard.setOnClickListener(v -> {}); // Consume click
    }

    private void showRecurringSuggestionsModal(List<RecurringPatternDetector.RecurringCandidate> candidates) {
        currentCandidates = candidates;
        candidateSelections = new ArrayList<>();

        // Alle standardmaessig ausgewaehlt
        for (int i = 0; i < candidates.size(); i++) {
            candidateSelections.add(true);
        }

        // Badge aktualisieren
        recurringCountBadge.setText(String.valueOf(candidates.size()));

        // Container leeren und neu befuellen
        candidatesContainer.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(context);

        for (int i = 0; i < candidates.size(); i++) {
            final int index = i;
            RecurringPatternDetector.RecurringCandidate candidate = candidates.get(i);

            View row = inflater.inflate(R.layout.item_recurring_candidate, candidatesContainer, false);

            ImageView checkbox = row.findViewById(R.id.candidate_checkbox);
            TextView payeeText = row.findViewById(R.id.candidate_payee);
            TextView patternText = row.findViewById(R.id.candidate_pattern);
            TextView countText = row.findViewById(R.id.candidate_count);
            TextView confidenceText = row.findViewById(R.id.candidate_confidence);
            TextView amountText = row.findViewById(R.id.candidate_amount);

            // Daten setzen
            payeeText.setText(candidate.displayPayee());
            patternText.setText(getPatternDescription(candidate));
            countText.setText(candidate.transactionIds().size() + " Transaktionen");
            confidenceText.setText(String.format("%.0f%% sicher", candidate.confidenceScore() * 100));
            amountText.setText(BudgetDisplayData.formatCents(candidate.avgAmountCents()));

            // Betrag-Farbe
            int amountColor = candidate.avgAmountCents() >= 0
                ? ContextCompat.getColor(context, R.color.budget_income)
                : ContextCompat.getColor(context, R.color.budget_expense);
            amountText.setTextColor(amountColor);

            // Confidence-Farbe
            int confidenceColor;
            if (candidate.confidenceScore() >= 0.7) {
                confidenceColor = ContextCompat.getColor(context, R.color.status_success);
            } else if (candidate.confidenceScore() >= 0.5) {
                confidenceColor = ContextCompat.getColor(context, R.color.budget_bar_warning);
            } else {
                confidenceColor = ContextCompat.getColor(context, R.color.text_muted);
            }
            confidenceText.setTextColor(confidenceColor);

            // Checkbox-Status
            updateCheckboxIcon(checkbox, candidateSelections.get(index));

            // Click-Listener fuer gesamte Zeile
            row.setOnClickListener(v -> {
                candidateSelections.set(index, !candidateSelections.get(index));
                updateCheckboxIcon(checkbox, candidateSelections.get(index));
                updateSelectionInfo();
            });

            candidatesContainer.addView(row);
        }

        updateSelectionInfo();
        recurringModalOverlay.setVisibility(View.VISIBLE);
    }

    private void hideRecurringModal() {
        recurringModalOverlay.setVisibility(View.GONE);
        currentCandidates = new ArrayList<>();
        candidateSelections = new ArrayList<>();
    }

    private void updateCheckboxIcon(ImageView checkbox, boolean selected) {
        checkbox.setImageResource(selected
            ? android.R.drawable.checkbox_on_background
            : android.R.drawable.checkbox_off_background);
    }

    private void updateSelectionInfo() {
        int selected = 0;
        for (Boolean sel : candidateSelections) {
            if (sel) selected++;
        }
        textSelectionInfo.setText(selected + " von " + candidateSelections.size() + " ausgewaehlt");
        btnRecurringCreate.setText("Erstellen (" + selected + ")");
        btnRecurringCreate.setEnabled(selected > 0);
        btnRecurringCreate.setBackgroundColor(selected > 0
            ? ContextCompat.getColor(context, R.color.accent)
            : ContextCompat.getColor(context, R.color.button_inactive));
    }

    private String getPatternDescription(RecurringPatternDetector.RecurringCandidate candidate) {
        if (candidate.suggestedType() == null) return "Unbekanntes Muster";

        String amount = BudgetDisplayData.formatCents(Math.abs(candidate.avgAmountCents()));

        return switch (candidate.suggestedType()) {
            case MONTHLY_DAY -> "Monatlich am " + candidate.suggestedValue() + ". \u2022 ~" + amount;
            case MONTHLY_LAST -> "Monatlich am Monatsende \u2022 ~" + amount;
            case WEEKLY -> "Woechentlich " + getDayName(candidate.suggestedDayOfWeek()) + " \u2022 ~" + amount;
            case INTERVAL -> "Alle " + candidate.suggestedValue() + " Tage \u2022 ~" + amount;
        };
    }

    private String getDayName(java.time.DayOfWeek dow) {
        if (dow == null) return "";
        return switch (dow) {
            case MONDAY -> "Mo";
            case TUESDAY -> "Di";
            case WEDNESDAY -> "Mi";
            case THURSDAY -> "Do";
            case FRIDAY -> "Fr";
            case SATURDAY -> "Sa";
            case SUNDAY -> "So";
        };
    }

    private void createSelectedTemplates() {
        if (importAccountId == null) {
            hideRecurringModal();
            return;
        }

        int created = 0;
        for (int i = 0; i < currentCandidates.size(); i++) {
            if (candidateSelections.get(i)) {
                RecurringPatternDetector.RecurringCandidate candidate = currentCandidates.get(i);

                // Template erstellen
                Long templateId = manager.createRecurringTemplate(candidate, importAccountId);

                // Transaktionen verknuepfen
                if (templateId != null) {
                    manager.linkTransactionsToTemplate(candidate.transactionIds(), templateId);
                    created++;
                }
            }
        }

        // UI aktualisieren
        manager.notifyDataUpdated();
        hideRecurringModal();

        // Erfolgs-Toast koennte hier angezeigt werden (optional)
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

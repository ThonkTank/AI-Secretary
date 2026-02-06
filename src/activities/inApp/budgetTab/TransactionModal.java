package activities.inApp.budgetTab;

import static activities.generic.ViewHelper.setupModalOverlay;
import static activities.generic.ViewHelper.spinnerAdapter;

import android.app.DatePickerDialog;
import android.content.Context;
import android.graphics.Color;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.autosecretary.R;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import controller.BudgetManager;
import controller.BudgetManager.AccountEntry;
import controller.BudgetManager.CategoryOption;
import data.BudgetDisplayData;
import entities.Transaction;

public class TransactionModal {

    private final Context context;
    private final BudgetManager manager;

    private FrameLayout overlay;
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

    private boolean isIncomeMode = false;
    private LocalDate selectedDate = LocalDate.now();
    private Transaction editingTransaction = null;
    private List<AccountEntry> accountsList = new ArrayList<>();
    private List<CategoryOption> categoriesList = new ArrayList<>();

    public TransactionModal(Context context, BudgetManager manager) {
        this.context = context;
        this.manager = manager;
    }

    public void init(FrameLayout overlay, View root) {
        this.overlay = overlay;

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

        setup();
    }

    private void setup() {
        btnExpense.setOnClickListener(v -> setIncomeMode(false));
        btnIncome.setOnClickListener(v -> setIncomeMode(true));

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

        btnCancel.setOnClickListener(v -> hide());
        btnSave.setOnClickListener(v -> saveTransaction());

        setupModalOverlay(overlay, this::hide);
    }

    public void show(Transaction tx) {
        editingTransaction = tx;
        errorText.setVisibility(View.GONE);

        accountsList = manager.provideAccounts();

        List<String> accountNames = new ArrayList<>();
        for (AccountEntry acc : accountsList) {
            accountNames.add(acc.name());
        }
        spinnerAccount.setAdapter(spinnerAdapter(context, accountNames));

        updateCategorySpinner();

        if (tx == null) {
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
            modalTitle.setText("Transaktion bearbeiten");
            setIncomeMode(tx.isIncome);
            inputAmount.setText(String.format("%.2f", Math.abs(tx.amountCents) / 100.0));
            selectedDate = tx.transactionDate;
            inputDate.setText(BudgetDisplayData.formatDate(selectedDate));
            inputDescription.setText(tx.description != null ? tx.description : "");
            inputPayee.setText(tx.payee != null ? tx.payee : "");

            for (int i = 0; i < accountsList.size(); i++) {
                if (accountsList.get(i).accountId().equals(tx.accountId)) {
                    spinnerAccount.setSelection(i);
                    break;
                }
            }

            for (int i = 0; i < categoriesList.size(); i++) {
                if (categoriesList.get(i).categoryId() != null &&
                    categoriesList.get(i).categoryId().equals(tx.categoryId)) {
                    spinnerCategory.setSelection(i);
                    break;
                }
            }
        }

        overlay.setVisibility(View.VISIBLE);
    }

    public void hide() {
        overlay.setVisibility(View.GONE);
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
        spinnerCategory.setAdapter(spinnerAdapter(context, categoryNames));
    }

    private void saveTransaction() {
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

        int amountCents = (int) Math.round(amount * 100);
        if (!isIncomeMode) {
            amountCents = -amountCents;
        }

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

        hide();
    }

    private void showError(String message) {
        errorText.setText(message);
        errorText.setVisibility(View.VISIBLE);
    }
}

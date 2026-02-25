package com.autosecretary.features.budget.ui;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.autosecretary.R;
import com.autosecretary.app.AppCompositionRoot;
import com.autosecretary.app.AutoSecretaryApplication;
import com.google.android.material.textfield.TextInputEditText;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.List;

public class BudgetFragment extends Fragment {

    private BudgetViewModel budgetViewModel;
    private ActivityResultLauncher<String[]> csvPickerLauncher;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        csvPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(),
                uri -> {
                    if (uri == null || budgetViewModel == null) return;
                    String fileName = getFileName(uri);
                    String mimeType = requireContext().getContentResolver().getType(uri);
                    try {
                        byte[] bytes = readUriBytes(uri);
                        budgetViewModel.importFromCsv(fileName, bytes, mimeType);
                    } catch (IOException e) {
                        budgetViewModel.onImportReadFailed();
                    }
                }
        );
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.budget_overview_fragment, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        AppCompositionRoot compositionRoot =
                AutoSecretaryApplication.from(requireContext()).getAppCompositionRoot();
        BudgetViewModelFactory factory = compositionRoot.createBudgetViewModelFactory();
        budgetViewModel = new ViewModelProvider(this, factory).get(BudgetViewModel.class);

        TextView title = view.findViewById(R.id.BudgetTitle);
        TextView summary = view.findViewById(R.id.BudgetSummary);
        TextView status = view.findViewById(R.id.BudgetStatusMessage);
        Button addTransaction = view.findViewById(R.id.BudgetAddTransactionButton);
        Button importStatement = view.findViewById(R.id.BudgetImportStatementButton);
        Button retry = view.findViewById(R.id.BudgetRetryButton);
        LinearLayout transactionList = view.findViewById(R.id.BudgetTransactionList);
        ProgressBar loading = view.findViewById(R.id.BudgetLoading);

        budgetViewModel.getTitle().observe(getViewLifecycleOwner(), title::setText);
        budgetViewModel.getSummary().observe(getViewLifecycleOwner(), summary::setText);
        budgetViewModel.getStatusMessage().observe(getViewLifecycleOwner(), status::setText);

        budgetViewModel.getTransactions().observe(getViewLifecycleOwner(),
                rows -> renderTransactions(rows, transactionList));

        budgetViewModel.getUiState().observe(getViewLifecycleOwner(), state -> {
            boolean isLoading = state == BudgetViewModel.BudgetUiState.LOADING;
            boolean isError = state == BudgetViewModel.BudgetUiState.ERROR;
            loading.setVisibility(isLoading ? View.VISIBLE : View.GONE);
            retry.setVisibility(isError ? View.VISIBLE : View.GONE);
            transactionList.setVisibility(state == BudgetViewModel.BudgetUiState.CONTENT
                    ? View.VISIBLE : View.GONE);
        });

        addTransaction.setOnClickListener(v -> showAddTransactionDialog());
        importStatement.setOnClickListener(v ->
                csvPickerLauncher.launch(new String[]{"text/csv", "text/plain", "*/*"}));
        retry.setOnClickListener(v -> budgetViewModel.retry());
    }

    private void showAddTransactionDialog() {
        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.budget_add_transaction_dialog, null);
        TextInputEditText amountInput = dialogView.findViewById(R.id.BudgetDialogAmount);
        RadioButton expenseRadio = dialogView.findViewById(R.id.BudgetDialogTypeExpense);
        TextInputEditText noteInput = dialogView.findViewById(R.id.BudgetDialogNote);

        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.budget_dialog_title)
                .setView(dialogView)
                .setPositiveButton(R.string.budget_dialog_save, (dialog, which) -> {
                    String amountStr = amountInput.getText() != null
                            ? amountInput.getText().toString().trim() : "";
                    boolean isExpense = expenseRadio.isChecked();
                    String note = noteInput.getText() != null
                            ? noteInput.getText().toString().trim() : "";
                    budgetViewModel.addTransaction(amountStr, isExpense,
                            note.isEmpty() ? null : note, LocalDate.now());
                })
                .setNegativeButton(R.string.budget_dialog_cancel, null)
                .show();
    }

    private void renderTransactions(List<BudgetViewModel.BudgetTransactionRow> rows,
            LinearLayout container) {
        container.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(container.getContext());

        for (BudgetViewModel.BudgetTransactionRow row : rows) {
            View rowView = inflater.inflate(R.layout.budget_transaction_item, container, false);
            TextView label = rowView.findViewById(R.id.BudgetTransactionLabel);
            TextView amount = rowView.findViewById(R.id.BudgetTransactionAmount);
            label.setText(row.getLabel());
            amount.setText(row.getAmount());
            rowView.setContentDescription(
                    getString(R.string.budget_transaction_content_description,
                            row.getLabel(), row.getAmount()));
            container.addView(rowView);
        }
    }

    private String getFileName(Uri uri) {
        String result = null;
        if ("content".equals(uri.getScheme())) {
            try (Cursor cursor = requireContext().getContentResolver()
                    .query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (nameIndex >= 0) {
                        result = cursor.getString(nameIndex);
                    }
                }
            }
        }
        if (result == null) {
            result = uri.getLastPathSegment();
        }
        return result != null ? result : "import.csv";
    }

    private byte[] readUriBytes(Uri uri) throws IOException {
        ContentResolver cr = requireContext().getContentResolver();
        try (InputStream is = cr.openInputStream(uri)) {
            if (is == null) throw new IOException("Dateistream konnte nicht geöffnet werden: " + uri);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = is.read(buffer)) != -1) {
                baos.write(buffer, 0, read);
            }
            return baos.toByteArray();
        }
    }
}

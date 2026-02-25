package com.autosecretary.features.budget.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.autosecretary.R;

import java.util.List;

public class BudgetFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.budget_overview_fragment, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        BudgetViewModel budgetViewModel = new ViewModelProvider(this).get(BudgetViewModel.class);
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

        addTransaction.setOnClickListener(v -> budgetViewModel.addQuickTransaction());
        importStatement.setOnClickListener(v -> budgetViewModel.importStatement());
        retry.setOnClickListener(v -> budgetViewModel.retry());
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
            container.addView(rowView);
        }
    }
}

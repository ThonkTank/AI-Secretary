package com.autosecretary.features.budget.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.autosecretary.R;

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

        budgetViewModel.getTitle().observe(getViewLifecycleOwner(), title::setText);
        budgetViewModel.getSummary().observe(getViewLifecycleOwner(), summary::setText);
    }
}

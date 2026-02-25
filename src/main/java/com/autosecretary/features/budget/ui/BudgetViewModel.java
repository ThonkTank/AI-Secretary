package com.autosecretary.features.budget.ui;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

public class BudgetViewModel extends ViewModel {

    private final MutableLiveData<String> title = new MutableLiveData<>("Budgetübersicht");
    private final MutableLiveData<String> summary = new MutableLiveData<>("Monatliches Budget im Blick behalten.");

    public LiveData<String> getTitle() {
        return title;
    }

    public LiveData<String> getSummary() {
        return summary;
    }
}

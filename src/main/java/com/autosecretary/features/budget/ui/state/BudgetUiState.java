package com.autosecretary.features.budget.ui.state;

/**
 * Lifecycle state of the budget screen UI.
 *
 * <ul>
 *   <li>LOADING: initial data load in progress; show spinner or skeleton UI
 *   <li>EMPTY: data loaded successfully but no transactions in the selected time range
 *   <li>CONTENT: data loaded with one or more transactions; display balance chart, summary, and lists
 *   <li>ERROR: data load failed; show error message with retry option
 * </ul>
 *
 * State transitions occur via BudgetViewModel based on data load outcomes.
 */
public enum BudgetUiState {
    LOADING,
    EMPTY,
    CONTENT,
    ERROR
}

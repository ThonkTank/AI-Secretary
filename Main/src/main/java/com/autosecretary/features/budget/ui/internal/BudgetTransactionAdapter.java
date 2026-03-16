package com.autosecretary.features.budget.ui.internal;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.autosecretary.R;
import com.autosecretary.shared.DateFormatters;
import com.autosecretary.features.budget.ui.state.BudgetTransactionRow;

import java.util.ArrayList;
import java.util.List;

/**
 * RecyclerView adapter for the budget transaction list.
 * Replaces the previous approach of inflating views into a plain LinearLayout
 * to enable view recycling and avoid O(n) main-thread inflation on every month navigation.
 */
public class BudgetTransactionAdapter
        extends RecyclerView.Adapter<BudgetTransactionAdapter.ViewHolder> {

    public interface Listener {
        void onTransactionClick(BudgetTransactionRow row);
        void onTransactionLongClick(BudgetTransactionRow row);
    }

    private List<BudgetTransactionRow> items = new ArrayList<>();
    private final Listener listener;

    public BudgetTransactionAdapter(@NonNull Listener listener) {
        this.listener = listener;
    }

    public void setItems(List<BudgetTransactionRow> newItems) {
        List<BudgetTransactionRow> updatedItems = newItems != null ? newItems : new ArrayList<>();
        List<BudgetTransactionRow> previousItems = this.items;
        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return previousItems.size();
            }

            @Override
            public int getNewListSize() {
                return updatedItems.size();
            }

            @Override
            public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                BudgetTransactionRow oldRow = previousItems.get(oldItemPosition);
                BudgetTransactionRow newRow = updatedItems.get(newItemPosition);
                if (oldRow.transactionId() == null || newRow.transactionId() == null) {
                    return oldRow.equals(newRow);
                }
                return oldRow.transactionId().equals(newRow.transactionId());
            }

            @Override
            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                return previousItems.get(oldItemPosition).equals(updatedItems.get(newItemPosition));
            }
        });
        this.items = updatedItems;
        diff.dispatchUpdatesTo(this);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.budget_transaction_item, parent, false);
        ViewHolder holder = new ViewHolder(view);
        view.setOnClickListener(v -> {
            int pos = holder.getBindingAdapterPosition();
            if (pos >= 0 && pos < items.size()) {
                listener.onTransactionClick(items.get(pos));
            }
        });
        view.setOnLongClickListener(v -> {
            int pos = holder.getBindingAdapterPosition();
            if (pos >= 0 && pos < items.size()) {
                listener.onTransactionLongClick(items.get(pos));
                return true;
            }
            return false;
        });
        return holder;
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        BudgetTransactionRow row = items.get(position);
        String formattedAmount = CurrencyFormatter.eurosWithSign(row.amountCents(), row.direction());

        holder.label.setText(row.label());
        holder.amount.setText(formattedAmount);

        // Always set label color explicitly so recycled views don't retain a stale category tint.
        // Color is pre-resolved on the background thread in BudgetOverviewLoader to avoid
        // regex matching and Color.parseColor on every bind call.
        int categoryColor = row.categoryColor();
        holder.label.setTextColor(
                categoryColor != BudgetTransactionRow.NO_CATEGORY_COLOR
                        ? categoryColor
                        : holder.defaultLabelColor);

        holder.amount.setTextColor(row.isExpense() ? holder.negativeColor : holder.positiveColor);

        String formattedDate = row.bookingDate().format(DateFormatters.DATE_FULL_GERMAN);
        holder.itemView.setContentDescription(
                holder.itemView.getContext().getString(
                        R.string.budget_transaction_content_description,
                        row.label(), formattedAmount, formattedDate));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView label;
        final TextView amount;
        // Captured once so onBindViewHolder can reset a recycled view's color without
        // resolving the theme attribute on every bind.
        final int defaultLabelColor;
        final int positiveColor;
        final int negativeColor;

        ViewHolder(@NonNull View view) {
            super(view);
            label = view.findViewById(R.id.BudgetTransactionLabel);
            amount = view.findViewById(R.id.BudgetTransactionAmount);
            defaultLabelColor = label.getCurrentTextColor();
            positiveColor = ContextCompat.getColor(view.getContext(), R.color.budget_positive);
            negativeColor = ContextCompat.getColor(view.getContext(), R.color.budget_negative);
        }
    }

}

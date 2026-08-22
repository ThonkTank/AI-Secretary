package de.thonktank.autosecretary.presentation.alltasks;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import de.thonktank.autosecretary.DayPalette;
import de.thonktank.autosecretary.UiStyle;

import java.util.List;

/** Stable-ID adapter that delegates every row type to a fixed-hierarchy ViewHolder. */
final class AllTasksListAdapter extends ListAdapter<AllTasksRow, AllTasksRowViewHolder> {
    private final AllTasksRowUi ui;
    private AllTasksReorderController reorder;
    private AllTasksUiState state = AllTasksUiState.empty();
    private DayPalette palette;

    AllTasksListAdapter(Context context, UiStyle style, AllTasksView.Listener listener) {
        super(new DiffUtil.ItemCallback<AllTasksRow>() {
            @Override public boolean areItemsTheSame(@NonNull AllTasksRow oldItem,
                                                     @NonNull AllTasksRow newItem) {
                return oldItem.key.equals(newItem.key);
            }

            @Override public boolean areContentsTheSame(@NonNull AllTasksRow oldItem,
                                                        @NonNull AllTasksRow newItem) {
                return oldItem.content.equals(newItem.content);
            }
        });
        ui = new AllTasksRowUi(context, style, listener);
        setHasStableIds(true);
    }

    void attachReorderController(AllTasksReorderController value) { reorder = value; }

    void bind(AllTasksUiState state, DayPalette palette) {
        boolean paletteChanged = this.palette == null || this.palette.ink != palette.ink
                || this.palette.leaf1 != palette.leaf1 || this.palette.accent != palette.accent;
        this.state = state;
        this.palette = palette;
        submitList(AllTasksRow.project(state));
        if (paletteChanged && getItemCount() > 0)
            notifyItemRangeChanged(0, getItemCount(), "palette");
    }

    @Override public long getItemId(int position) { return getItem(position).stableId; }
    @Override public int getItemViewType(int position) { return getItem(position).kind.ordinal(); }

    @NonNull @Override public AllTasksRowViewHolder onCreateViewHolder(@NonNull ViewGroup parent,
                                                                       int viewType) {
        AllTasksRow.Kind kind = AllTasksRow.Kind.values()[viewType];
        if (kind == AllTasksRow.Kind.TASK_HEADER || kind == AllTasksRow.Kind.STEP_ADD)
            return new AllTasksTaskCardViewHolder(parent, kind, ui);
        if (kind == AllTasksRow.Kind.STEP || kind == AllTasksRow.Kind.STEP_TARGET)
            return new AllTasksStepViewHolder(parent, kind, ui);
        if (kind == AllTasksRow.Kind.SLOT_HEADER || kind == AllTasksRow.Kind.SCHEDULE
                || kind == AllTasksRow.Kind.SCHEDULE_TARGET)
            return new AllTasksScheduleViewHolder(parent, kind, ui);
        return new AllTasksEmptyViewHolder(parent, ui);
    }

    @Override public void onBindViewHolder(@NonNull AllTasksRowViewHolder holder, int position) {
        bindHolder(holder, position);
    }

    @Override public void onBindViewHolder(@NonNull AllTasksRowViewHolder holder, int position,
                                           @NonNull List<Object> payloads) {
        bindHolder(holder, position);
    }

    private void bindHolder(AllTasksRowViewHolder holder, int position) {
        AllTasksRow row = getItem(position);
        holder.bind(row, state, palette, reorder != null && reorder.isDragActive());
        if (reorder != null) reorder.installAccessibility(holder.itemView, row);
    }

    AllTasksRow rowAt(int position) { return getCurrentList().get(position); }

    void notifyStepTargets() {
        for (int index = 0; index < getItemCount(); index++)
            if (rowAt(index).kind == AllTasksRow.Kind.STEP_TARGET)
                notifyItemChanged(index, "drag");
    }

    View hierarchyAnchor(RecyclerView recycler, int position) {
        RecyclerView.ViewHolder holder = recycler.findViewHolderForAdapterPosition(position);
        return holder instanceof AllTasksRowViewHolder
                ? ((AllTasksRowViewHolder) holder).hierarchyAnchor() : null;
    }
}

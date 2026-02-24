package views.taskTab;

import androidx.recyclerview.widget.RecyclerView;
import android.view.View;
import android.view.ViewGroup;
import android.view.LayoutInflater;
import android.widget.TextView;
import android.widget.CheckBox;

import java.util.List;
import java.util.function.Consumer;
import java.time.format.DateTimeFormatter;

import views.models.ViewSlotList.ViewSlot;
import database.task.TaskSlot;
import com.autosecretary.R;

public class ListRowAdapter extends RecyclerView.Adapter<ListRowAdapter.TaskRowViewHolder> {
    List<ViewSlot> viewSlots;
    Consumer<TaskSlot> onCheck;
    Consumer<ViewSlot> onLongPress;

    public ListRowAdapter(List<ViewSlot> viewSlots, Consumer<TaskSlot> onCheck, Consumer<ViewSlot> onLongPress) {
        this.viewSlots = viewSlots;
        this.onCheck = onCheck;
        this.onLongPress = onLongPress;
    }

    static class TaskRowViewHolder extends RecyclerView.ViewHolder {
        TextView title;
        TextView start;
        TextView end;
        CheckBox checkBox;

        TaskRowViewHolder(View taskRow) {
            super(taskRow);
            this.title = taskRow.findViewById(R.id.TaskTitle);
            this.start = taskRow.findViewById(R.id.StartTime);
            this.end = taskRow.findViewById(R.id.EndTime);
            this.checkBox = taskRow.findViewById(R.id.TaskCheckBox);
        }
    }

    // Adapter Methoden
    @Override
    public int getItemCount() {
        return viewSlots.size();
    }

    @Override
    public TaskRowViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View taskRow = LayoutInflater.from(parent.getContext()).inflate(R.layout.task_row, parent, false);
        return new TaskRowViewHolder(taskRow);
    }

    @Override
    public void onBindViewHolder(TaskRowViewHolder holder, int position) {
        ViewSlot viewSlot = viewSlots.get(position);
        int step = holder.itemView.getContext().getResources().getDimensionPixelSize(R.dimen.indent_step);

        holder.title.setText(viewSlot.task.core.title);

        String startString = viewSlot.slot.start != null ? viewSlot.slot.start.format(DateTimeFormatter.ofPattern("HH:mm")) : "Nicht";
        String endString = viewSlot.slot.end != null ? viewSlot.slot.end.format(DateTimeFormatter.ofPattern("HH:mm")) : "Heute";
        holder.start.setText(startString);
        holder.end.setText(endString);
        holder.itemView.setPadding(step * viewSlot.depth, 0, 0, 0);
        holder.checkBox.setOnClickListener(v -> onCheck.accept(viewSlot.slot));
        holder.itemView.setOnLongClickListener(v -> {
            onLongPress.accept(viewSlot);
            return true;
        });
        holder.checkBox.setChecked(viewSlot.slot.completed);
    }

    public void setList(List<ViewSlot> viewSlots) {
        this.viewSlots = viewSlots;
        notifyDataSetChanged();
    }
}

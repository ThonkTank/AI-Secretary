package views.taskTab;

import androidx.recyclerview.widget.RecyclerView;
import android.view.View;
import android.view.ViewGroup;
import android.view.LayoutInflater;
import android.widget.TextView;
import android.widget.CheckBox;

import java.util.List;

import views.models.ViewTask;
import com.autosecretary.R;

public class TaskRowAdapter extends RecyclerView.Adapter<TaskRowAdapter.TaskRowViewHolder> {
    List<ViewTask> viewTasks;

    public TaskRowAdapter(List<ViewTask> viewTasks) {
        this.viewTasks = viewTasks;
    }

    static class TaskRowViewHolder extends RecyclerView.ViewHolder {
        TextView title;
        CheckBox checkBox;

        TaskRowViewHolder(View taskRow) {
            super(taskRow);
            this.title = taskRow.findViewById(R.id.TaskTitle);
            this.checkBox = taskRow.findViewById(R.id.TaskCheckBox);
        }
    }

    // Adapter Methoden
    @Override
    public int getItemCount() {
        return viewTasks.size();
    }
    @Override
    public TaskRowViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.task_row, parent, false);
        return new TaskRowViewHolder(view);
    }
    @Override
    public void onBindViewHolder(TaskRowViewHolder holder, int position) {
        ViewTask viewTask = viewTasks.get(position);
        int step = holder.itemView.getContext().getResources().getDimensionPixelSize(R.dimen.indent_step);

        holder.title.setText(viewTask.task.core.title);
        holder.itemView.setPadding(step * viewTask.indent, 0, 0, 0);
    }
    public void setList(List<ViewTask> viewTasks) {
        this.viewTasks = viewTasks;
        notifyDataSetChanged();
    }
}

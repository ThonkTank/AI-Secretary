package activities.inApp.tasksTab;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.autosecretary.R;

import activities.generic.ViewBuilder;
import activities.generic.TaskList;
import controller.EditorManager;
import controller.TodoManager;

public class TaskView implements ViewBuilder {

    private final Context context;
    private final TodoManager todoMgr;

    private TextView tabTagesplan;
    private TextView tabVerwalten;
    private FrameLayout content;

    private int currentTab = 0;

    private View tagesplanView;
    private View verwaltenView;

    private TaskList taskListInstance;
    private EditItem editItemInstance;

    public TaskView(Context context, TodoManager todoMgr) {
        this.context = context;
        this.todoMgr = todoMgr;
    }

    @Override
    public View buildView() {
        View root = LayoutInflater.from(context).inflate(R.layout.view_tasks, null);

        tabTagesplan = root.findViewById(R.id.tab_tagesplan);
        tabVerwalten = root.findViewById(R.id.tab_verwalten);
        content = root.findViewById(R.id.task_content);

        tabTagesplan.setOnClickListener(v -> selectSubTab(0));
        tabVerwalten.setOnClickListener(v -> selectSubTab(1));

        taskListInstance = new TaskList(context, todoMgr);
        tagesplanView = taskListInstance.buildView();

        editItemInstance = new EditItem(context, new EditorManager(context));
        verwaltenView = editItemInstance.buildView();

        selectSubTab(0);

        return root;
    }

    private void selectSubTab(int index) {
        currentTab = index;

        int accent = ContextCompat.getColor(context, R.color.accent);
        int secondary = ContextCompat.getColor(context, R.color.text_secondary);

        tabTagesplan.setTextColor(index == 0 ? accent : secondary);
        tabVerwalten.setTextColor(index == 1 ? accent : secondary);

        content.removeAllViews();
        switch (currentTab) {
            case 0 -> content.addView(tagesplanView);
            case 1 -> content.addView(verwaltenView);
        }
    }

    public void openCreateModal() {
        selectSubTab(1);
        if (editItemInstance != null) {
            editItemInstance.openCreateModal();
        }
    }
}

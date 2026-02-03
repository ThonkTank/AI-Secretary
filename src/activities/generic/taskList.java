package activities.generic;

import android.content.Context;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import static activities.generic.ViewHelper.dp;

import com.autosecretary.R;

import controller.todoManager;
import controller.todoManager.TodoListener;

/**
 * ══════════════════════════════════════════════════════════════════════════════
 * TASK LIST VIEW - Stub für Neuimplementierung
 * ══════════════════════════════════════════════════════════════════════════════
 *
 * Diese Klasse wurde im Rahmen der Aufräumung auf einen Stub reduziert.
 * Die vollständige Neuimplementierung erfolgt gemäß WIDGET_REPORT.md.
 *
 * TODO: Neuimplementierung mit TaskRowRenderer und einheitlichen Layouts
 */
public class taskList implements TodoListener, ViewBuilder {

    private Context context;
    private todoManager manager;
    private LinearLayout container;
    private Runnable onUpdate;

    public taskList(Context context, todoManager manager) {
        this.context = context;
        this.manager = manager;
        this.manager.setListener(this);
    }

    /** Callback bei Listenänderung (optional) */
    public void setOnUpdate(Runnable onUpdate) {
        this.onUpdate = onUpdate;
    }

    /** Baut die komplette Task-Listen-View und gibt sie zurück */
    public View buildView() {
        View root = LayoutInflater.from(context).inflate(R.layout.view_task_list, null);
        container = root.findViewById(R.id.task_container);

        Button replanBtn = root.findViewById(R.id.btn_replan);
        replanBtn.setOnClickListener(v -> manager.replanToday());

        render();
        return root;
    }

    /** Rendert die Task-Liste in den Container */
    public void render() {
        // Alle dynamischen Views entfernen (Button bleibt als erstes Kind)
        while (container.getChildCount() > 1) {
            container.removeViewAt(1);
        }

        // TODO: Neuimplementierung mit TaskRowRenderer
        // Vorläufig: Placeholder-Meldung anzeigen
        TextView placeholder = new TextView(context);
        placeholder.setText("TaskList wird neu implementiert...\n\nSiehe WIDGET_REPORT.md");
        placeholder.setTextSize(TypedValue.COMPLEX_UNIT_PX,
            context.getResources().getDimension(R.dimen.sp_title));
        placeholder.setGravity(Gravity.CENTER);
        placeholder.setPadding(0, dp(context, 64), 0, 0);
        container.addView(placeholder);
    }

    @Override
    public void onListUpdated() {
        container.post(this::render);
        if (onUpdate != null) onUpdate.run();
    }
}

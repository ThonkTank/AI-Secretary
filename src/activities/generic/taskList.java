package activities.generic;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;

import static activities.generic.ViewHelper.dp;

import androidx.core.content.ContextCompat;

import com.autosecretary.R;

import controller.todoManager;
import controller.todoManager.TodoListener;
import data.TaskListData;
import data.TaskListData.*;
import data.TaskRowConfig.*;
import render.TaskRowRenderer;

import java.util.List;

/**
 * ══════════════════════════════════════════════════════════════════════════════
 * TASK LIST VIEW - Einheitliche Darstellung für App (Widget nutzt dieselben Layouts)
 * ══════════════════════════════════════════════════════════════════════════════
 *
 * Verwendet TaskRowRenderer und die einheitlichen Layouts (item_task.xml,
 * item_goal_header.xml, item_calendar.xml) für 100% visuelle Parität mit dem Widget.
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
        render();
        return root;
    }

    /** Rendert die Task-Liste in den Container */
    public void render() {
        container.removeAllViews();

        // Replan-Button hinzufügen
        Button replanBtn = new Button(context);
        replanBtn.setText("Neu planen");
        replanBtn.setTextColor(Color.WHITE);
        replanBtn.setBackgroundColor(ContextCompat.getColor(context, R.color.accent));
        replanBtn.setOnClickListener(v -> manager.replanToday());
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        btnParams.bottomMargin = dp(context, 12);
        container.addView(replanBtn, btnParams);

        // Task-Liste rendern
        LayoutInflater inflater = LayoutInflater.from(context);
        List<DisplayRow> rows = TaskListData.fromEntries(manager.provideList());

        for (DisplayRow row : rows) {
            if (row instanceof GoalHeader header) {
                View view = inflater.inflate(R.layout.item_goal_header, container, false);
                GoalHeaderConfig cfg = GoalHeaderConfig.from(header);
                TaskRowRenderer.applyGoalHeader(view, cfg, context);
                container.addView(view);
            }
            else if (row instanceof TaskItem item) {
                View view = inflater.inflate(R.layout.item_task, container, false);
                TaskConfig cfg = TaskConfig.from(item.entry());
                TaskRowRenderer.applyTask(view, cfg, context);

                if (cfg.hasProgress()) {
                    // Progress-Modus: +/- Button Listener
                    View btnPlus = view.findViewById(R.id.btn_progress_plus);
                    View btnMinus = view.findViewById(R.id.btn_progress_minus);

                    btnPlus.setOnClickListener(v -> {
                        if (cfg.progressCurrent() < cfg.progressTarget()) {
                            animateProgressIncrement(view, btnPlus, cfg.progressDoneToday());
                            manager.incrementProgress(cfg.slotId());
                        }
                    });

                    btnMinus.setOnClickListener(v -> {
                        if (cfg.progressCurrent() > 0) {
                            manager.decrementProgress(cfg.slotId());
                        }
                    });
                } else {
                    // Normal-Modus: Checkbox Listener
                    View checkbox = view.findViewById(R.id.task_checkbox);
                    checkbox.setOnClickListener(v -> {
                        if (cfg.checked()) {
                            manager.uncompleteSlot(cfg.slotId());
                        } else {
                            animateCompletion(view, checkbox);
                            manager.completeSlot(cfg.slotId());
                        }
                    });
                }

                // Timer Listener (nur wenn sichtbar)
                View timer = view.findViewById(R.id.task_timer);
                if (cfg.showTimer()) {
                    timer.setOnClickListener(v -> {
                        if (cfg.timerRunning()) manager.stopTimer(cfg.slotId());
                        else manager.startTimer(cfg.slotId());
                    });
                }

                container.addView(view);
            }
            else if (row instanceof CalendarEvent event) {
                View view = inflater.inflate(R.layout.item_calendar, container, false);
                CalendarConfig cfg = CalendarConfig.from(event.entry());
                TaskRowRenderer.applyCalendar(view, cfg, context);
                container.addView(view);
            }
        }
    }

    @Override
    public void onListUpdated() {
        container.post(this::render);
        if (onUpdate != null) onUpdate.run();
    }

    /** Completion-Animation: Checkbox Bounce + Hintergrund Flash */
    private void animateCompletion(View row, View checkbox) {
        // 1. Checkbox bounce (scale 1.0 -> 1.3 -> 1.0)
        checkbox.animate()
            .scaleX(1.3f).scaleY(1.3f)
            .setDuration(100)
            .withEndAction(() ->
                checkbox.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
            ).start();

        // 2. Background flash (completion_flash -> surface_complete)
        int flashColor = ContextCompat.getColor(context, R.color.completion_flash);
        int finalColor = ContextCompat.getColor(context, R.color.surface_complete);
        ValueAnimator colorAnim = ValueAnimator.ofArgb(flashColor, finalColor);
        colorAnim.setDuration(300);
        colorAnim.addUpdateListener(a -> row.setBackgroundColor((int) a.getAnimatedValue()));
        colorAnim.start();
    }

    /** Progress-Increment-Animation: Button Bounce + optionaler Hintergrund Flash */
    private void animateProgressIncrement(View row, View button, boolean alreadyDoneToday) {
        // 1. Button bounce (scale 1.0 -> 1.2 -> 1.0)
        button.animate()
            .scaleX(1.2f).scaleY(1.2f)
            .setDuration(80)
            .withEndAction(() ->
                button.animate().scaleX(1f).scaleY(1f).setDuration(80).start()
            ).start();

        // 2. Background flash nur wenn noch nicht "heute erledigt"
        if (!alreadyDoneToday) {
            int flashColor = ContextCompat.getColor(context, R.color.completion_flash);
            int finalColor = ContextCompat.getColor(context, R.color.surface_complete);
            ValueAnimator colorAnim = ValueAnimator.ofArgb(flashColor, finalColor);
            colorAnim.setDuration(300);
            colorAnim.addUpdateListener(a -> row.setBackgroundColor((int) a.getAnimatedValue()));
            colorAnim.start();
        }
    }
}

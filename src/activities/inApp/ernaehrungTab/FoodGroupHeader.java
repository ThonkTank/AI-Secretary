package activities.inApp.ernaehrungTab;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.autosecretary.R;

import java.time.LocalDate;
import java.util.List;

import controller.MealManager;
import controller.MealManager.FoodGroupProgress;

/**
 * Rendert den Wochenfortschritt pro Lebensmittelgruppe als horizontale
 * Balkenleiste im Kopf der Ernaehrungs-View.
 */
public class FoodGroupHeader {

    private final Context context;
    private final MealManager manager;

    public FoodGroupHeader(Context context, MealManager manager) {
        this.context = context;
        this.manager = manager;
    }

    /**
     * Rendert die Food-Group-Fortschrittsbalken in den Container.
     */
    public void render(LinearLayout container) {
        container.removeAllViews();
        List<FoodGroupProgress> progress = manager.provideFoodGroupProgress(LocalDate.now());

        LayoutInflater inflater = LayoutInflater.from(context);

        for (FoodGroupProgress fg : progress) {
            View bar = inflater.inflate(R.layout.item_food_group_bar, container, false);

            TextView icon = bar.findViewById(R.id.group_icon);
            TextView label = bar.findViewById(R.id.group_label);
            ProgressBar progressBar = bar.findViewById(R.id.group_progress);
            TextView value = bar.findViewById(R.id.group_value);

            icon.setText(fg.icon());
            label.setText(fg.label());
            progressBar.setProgress((int) fg.percent());
            value.setText(fg.formatted());

            container.addView(bar);
        }
    }
}

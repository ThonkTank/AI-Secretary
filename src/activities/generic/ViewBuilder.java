package activities.generic;

import android.view.View;

/**
 * Interface fuer View-Builder Klassen (WeekPlanView, TaskManagerView, etc.).
 * Formalisiert den Kontrakt: Konstruktor(Context, Manager) + buildView().
 */
public interface ViewBuilder {
    View buildView();
}

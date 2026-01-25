package activities.generic;

import android.view.View;

/**
 * Interface für View-Builder Klassen (taskList, editItem, etc.).
 * Formalisiert den Kontrakt: Konstruktor(Context, Manager) + buildView().
 */
public interface ViewBuilder {
    View buildView();
}

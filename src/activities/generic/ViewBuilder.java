package activities.generic;

import android.view.View;

/**
 * Interface für View-Builder Klassen (TaskList, EditItem, etc.).
 * Formalisiert den Kontrakt: Konstruktor(Context, Manager) + buildView().
 */
public interface ViewBuilder {
    View buildView();
}

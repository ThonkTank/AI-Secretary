package activities.widget;

import android.app.Activity;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import com.autosecretary.R;

/**
 * Transparente Activity die ein Task-Beschreibung-Popup anzeigt.
 * Wird vom Widget verwendet, da Widgets keine PopupWindows zeigen können.
 * Schließt bei Touch außerhalb oder Back-Button.
 */
public class DescriptionPopupActivity extends Activity {

    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_DESCRIPTION = "description";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_description_popup);

        // Activity transparent machen, Touch außerhalb erkennen
        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        );
        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
        );

        // Daten aus Intent holen
        String title = getIntent().getStringExtra(EXTRA_TITLE);
        String description = getIntent().getStringExtra(EXTRA_DESCRIPTION);

        if (description == null || description.isEmpty()) {
            description = "Keine Beschreibung";
        }

        // Inhalt setzen
        TextView titleView = findViewById(R.id.popup_title);
        TextView descView = findViewById(R.id.popup_description);
        titleView.setText(title != null ? title : "");
        descView.setText(description);

        // Klick auf Overlay (außerhalb Popup-Card) schließt Activity
        View overlay = findViewById(R.id.popup_overlay);
        overlay.setOnClickListener(v -> finish());

        // Klick auf Popup-Card selbst tut nichts (verhindert Durchklicken)
        View popupCard = findViewById(R.id.popup_card);
        popupCard.setOnClickListener(v -> { /* Absichtlich leer */ });
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // Touch außerhalb schließt Activity
        if (event.getAction() == MotionEvent.ACTION_OUTSIDE) {
            finish();
            return true;
        }
        return super.onTouchEvent(event);
    }

    @Override
    public void onBackPressed() {
        finish();
    }
}

package activities.generic;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import android.graphics.Color;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.IntConsumer;

import android.widget.AdapterView;
import android.widget.Spinner;

import androidx.core.content.ContextCompat;

import com.autosecretary.R;

import static activities.generic.DateTimeHelper.getWeekNumber;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Statische Helfer-Methoden für programmatischen View-Aufbau.
 * Eliminiert dp()-Duplikate und wiederkehrende UI-Patterns.
 */
public final class ViewHelper {

    public static final int FAB_CORNER_RADIUS_DP = 28;
    public static final int FAB_SECONDARY_CORNER_RADIUS_DP = 24;

    private ViewHelper() {}

    /** Konvertiert dp zu Pixel */
    public static int dp(Context ctx, int value) {
        return (int) TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, value,
            ctx.getResources().getDisplayMetrics());
    }

    /** Erstellt ein GradientDrawable mit Farbe und abgerundeten Ecken */
    public static GradientDrawable roundedBg(Context ctx, int color, int cornerDp) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(dp(ctx, cornerDp));
        return bg;
    }

    /**
     * Baut eine "< KW X (dd.-dd.MM.) >" Wochen-Navigation.
     * Gibt das LinearLayout zurueck, damit der Aufrufer extra Buttons anhaengen kann.
     */
    public static LinearLayout buildWeekHeader(Context ctx, LocalDate weekStart,
                                               Runnable onPrev, Runnable onNext) {
        LinearLayout weekHeader = new LinearLayout(ctx);
        weekHeader.setOrientation(LinearLayout.HORIZONTAL);
        weekHeader.setGravity(android.view.Gravity.CENTER_VERTICAL);
        weekHeader.setPadding(0, dp(ctx, 16), 0, dp(ctx, 8));

        // "<" Button
        TextView btnPrev = new TextView(ctx);
        btnPrev.setText("<");
        btnPrev.setTextSize(20);
        btnPrev.setTextColor(ContextCompat.getColor(ctx, R.color.accent));
        btnPrev.setPadding(dp(ctx, 16), dp(ctx, 8), dp(ctx, 16), dp(ctx, 8));
        btnPrev.setOnClickListener(v -> onPrev.run());
        weekHeader.addView(btnPrev);

        // KW Label
        TextView weekLabel = new TextView(ctx);
        weekLabel.setTextSize(14);
        weekLabel.setTextColor(ContextCompat.getColor(ctx, R.color.text_primary));
        weekLabel.setGravity(android.view.Gravity.CENTER);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        weekLabel.setLayoutParams(labelParams);

        int weekNum = getWeekNumber(weekStart);
        LocalDate weekEnd = weekStart.plusDays(6);
        DateTimeFormatter dayFmt = DateTimeFormatter.ofPattern("dd.");
        DateTimeFormatter monthFmt = DateTimeFormatter.ofPattern("dd.MM.");
        weekLabel.setText("KW " + weekNum + " (" + dayFmt.format(weekStart) + "-" + monthFmt.format(weekEnd) + ")");
        weekHeader.addView(weekLabel);

        // ">" Button
        TextView btnNext = new TextView(ctx);
        btnNext.setText(">");
        btnNext.setTextSize(20);
        btnNext.setTextColor(ContextCompat.getColor(ctx, R.color.accent));
        btnNext.setPadding(dp(ctx, 16), dp(ctx, 8), dp(ctx, 16), dp(ctx, 8));
        btnNext.setOnClickListener(v -> onNext.run());
        weekHeader.addView(btnNext);

        return weekHeader;
    }

    /** Zeigt eine "Keine X vorhanden"-Meldung in einem Container */
    public static void showEmptyState(ViewGroup container, String message) {
        Context ctx = container.getContext();
        TextView empty = new TextView(ctx);
        empty.setText(message);
        empty.setTextColor(ContextCompat.getColor(ctx, R.color.text_secondary));
        empty.setPadding(dp(ctx, 16), dp(ctx, 32), dp(ctx, 16), dp(ctx, 32));
        container.addView(empty);
    }

    /** Erstellt einen Standard-SpinnerAdapter (simple_spinner_item + dropdown) */
    public static ArrayAdapter<String> spinnerAdapter(Context ctx, String[] items) {
        ArrayAdapter<String> a = new ArrayAdapter<>(ctx, android.R.layout.simple_spinner_item, items);
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        return a;
    }

    public static ArrayAdapter<String> spinnerAdapter(Context ctx, List<String> items) {
        ArrayAdapter<String> a = new ArrayAdapter<>(ctx, android.R.layout.simple_spinner_item, items);
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        return a;
    }

    /** Parst Integer aus EditText, gibt fallback bei leerem/ungueltigem Wert */
    public static int parseInt(EditText input, int fallback) {
        try {
            String s = input.getText().toString().trim();
            return s.isEmpty() ? fallback : Integer.parseInt(s);
        } catch (NumberFormatException e) { return fallback; }
    }

    /** Parst Double aus EditText, gibt fallback bei leerem/ungueltigem Wert */
    public static double parseDouble(EditText input, double fallback) {
        try {
            String s = input.getText().toString().trim();
            return s.isEmpty() ? fallback : Double.parseDouble(s);
        } catch (NumberFormatException e) { return fallback; }
    }

    /** Erstellt einen TextWatcher der nur afterTextChanged implementiert. */
    public static TextWatcher afterTextChanged(Runnable action) {
        return new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
            @Override public void afterTextChanged(Editable s) { action.run(); }
        };
    }

    /** Highlight fuer Toggle-Button-Gruppen (Type, Priority, RepType, etc.) */
    public static void updateButtonGroup(Context ctx, Button[] buttons, int selectedIdx) {
        int accent = ContextCompat.getColor(ctx, R.color.accent);
        int inactive = ContextCompat.getColor(ctx, R.color.button_inactive);
        int textPrimary = ContextCompat.getColor(ctx, R.color.text_primary);
        for (int i = 0; i < buttons.length; i++) {
            if (buttons[i] == null) continue;
            if (i == selectedIdx) {
                buttons[i].setBackgroundColor(accent);
                buttons[i].setTextColor(Color.WHITE);
            } else {
                buttons[i].setBackgroundColor(inactive);
                buttons[i].setTextColor(textPrimary);
            }
        }
    }

    /** Highlight fuer An/Aus-Toggle-Buttons */
    public static void updateToggleButton(Context ctx, Button button, boolean enabled) {
        int accent = ContextCompat.getColor(ctx, R.color.accent);
        int inactive = ContextCompat.getColor(ctx, R.color.button_inactive);
        int textPrimary = ContextCompat.getColor(ctx, R.color.text_primary);
        if (enabled) {
            button.setBackgroundColor(accent);
            button.setTextColor(Color.WHITE);
            button.setText("An");
        } else {
            button.setBackgroundColor(inactive);
            button.setTextColor(textPrimary);
            button.setText("Aus");
        }
    }

    /** Synct EditText mit Wert nur wenn unterschiedlich (verhindert Cursor-Reset). */
    public static void syncText(EditText field, String value) {
        String current = field.getText().toString();
        if (!current.equals(value)) {
            field.setText(value);
            field.setSelection(value.length());
        }
    }

    /** Setzt Spinner-Listener mit Skip-Guard (analog zu afterTextChanged). */
    public static void onSpinnerSelected(Spinner spinner, BooleanSupplier skip, IntConsumer action) {
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                if (skip.getAsBoolean()) return;
                action.accept(pos);
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    /** Findet 1-basierten Offset-Index in einer Liste (0 = "kein X"). */
    public static <T> int findOffsetIdx(List<T> list, Object id, Function<T, Object> getId) {
        if (id == null) return 0;
        for (int i = 0; i < list.size(); i++) {
            if (id.equals(getId.apply(list.get(i)))) return i + 1;
        }
        return 0;
    }

    /** Loest 1-basierten Offset-Index zu einem Wert auf (0 = null). */
    public static <T, R> R resolveAtOffset(List<T> list, int idx, Function<T, R> extract) {
        return (idx > 0 && idx <= list.size()) ? extract.apply(list.get(idx - 1)) : null;
    }

    /** Konfiguriert Modal-Overlay: Klick ausserhalb schliesst, Klick auf Card wird absorbiert. */
    public static void setupModalOverlay(View overlay, Runnable onDismiss) {
        overlay.setOnClickListener(v -> onDismiss.run());
        View card = overlay.findViewById(R.id.modal_card);
        if (card == null && overlay instanceof ViewGroup) {
            card = ((ViewGroup) overlay).getChildAt(0);
        }
        if (card != null) {
            card.setOnClickListener(v -> { /* Absorb click */ });
        }
    }
}

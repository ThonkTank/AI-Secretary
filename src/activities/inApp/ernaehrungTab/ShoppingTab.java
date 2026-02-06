package activities.inApp.ernaehrungTab;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Paint;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import static activities.generic.ViewHelper.buildWeekHeader;
import static activities.generic.ViewHelper.dp;
import static activities.generic.DateTimeHelper.getWeekKey;
import static activities.generic.ViewHelper.roundedBg;
import static activities.generic.ViewHelper.showEmptyState;

import androidx.core.content.ContextCompat;

import com.autosecretary.R;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

import controller.MealManager;
import controller.MealManager.*;

/**
 * "Einkauf"-Tab: Einkaufsliste fuer die aktuelle Woche.
 *
 * Zeigt Artikel gruppiert nach Lebensmittelgruppe, mit Toggle-Checkbox
 * und "Einkauf abschliessen"-Button.
 */
public class ShoppingTab {

    private final Context context;
    private final MealManager manager;

    // Callback um MealPlanView ueber Aenderungen zu informieren
    private MealTabListener listener;

    // ============================================================================
    // CONSTRUCTOR + INIT
    // ============================================================================

    public ShoppingTab(Context context, MealManager manager) {
        this.context = context;
        this.manager = manager;
    }

    public void setListener(MealTabListener listener) {
        this.listener = listener;
    }

    // ============================================================================
    // RENDER
    // ============================================================================

    public void render(FrameLayout container, LocalDate weekStart,
                       Runnable onPrevWeek, Runnable onNextWeek) {
        container.removeAllViews();

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);

        // Wochen-Navigation (shared mit WeekPlanTab)
        content.addView(buildWeekHeader(context, weekStart, onPrevWeek, onNextWeek));

        // Einkaufsliste
        renderShoppingList(content, weekStart);

        container.addView(content);
    }

    // ============================================================================
    // SHOPPING LIST
    // ============================================================================

    private void renderShoppingList(LinearLayout container, LocalDate weekStart) {
        String weekKey = getWeekKey(weekStart);
        List<ShoppingEntry> items = manager.provideShoppingList(weekKey);
        ShoppingSummary summary = manager.provideShoppingSummary(weekKey);

        // === Header mit Summary ===
        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(0, 0, 0, dp(context, 16));

        // Laden-Empfehlung
        TextView storeLabel = new TextView(context);
        storeLabel.setText("Empfohlener Laden: " + summary.suggestedStore());
        storeLabel.setTextSize(14);
        storeLabel.setTextColor(ContextCompat.getColor(context, R.color.text_primary));
        storeLabel.setTypeface(null, android.graphics.Typeface.BOLD);
        header.addView(storeLabel);

        // Fortschritt
        TextView progressLabel = new TextView(context);
        progressLabel.setText(summary.purchasedItems() + " von " + summary.totalItems() + " Artikeln");
        progressLabel.setTextSize(12);
        progressLabel.setTextColor(ContextCompat.getColor(context, R.color.text_secondary));
        header.addView(progressLabel);

        // Geschaetzter Preis
        if (summary.estimatedTotalCents() > 0) {
            TextView priceLabel = new TextView(context);
            priceLabel.setText("Geschätzt: " + summary.formattedTotal());
            priceLabel.setTextSize(12);
            priceLabel.setTextColor(ContextCompat.getColor(context, R.color.text_secondary));
            header.addView(priceLabel);
        }

        container.addView(header);

        if (items.isEmpty()) {
            showEmptyState(container, "Keine Einkaufsliste vorhanden.\nGeneriere erst einen Wochenplan.");
            return;
        }

        // === Items gruppiert nach FoodGroup ===
        String currentGroup = null;
        LayoutInflater inflater = LayoutInflater.from(context);

        for (ShoppingEntry item : items) {
            // Gruppen-Header
            if (!item.foodGroup().equals(currentGroup)) {
                currentGroup = item.foodGroup();

                TextView groupHeader = new TextView(context);
                groupHeader.setText(item.foodGroupIcon() + " " + item.foodGroup());
                groupHeader.setTextSize(12);
                groupHeader.setTextColor(ContextCompat.getColor(context, R.color.text_secondary));
                groupHeader.setTypeface(null, android.graphics.Typeface.BOLD);
                groupHeader.setPadding(0, dp(context, 12), 0, dp(context, 4));
                container.addView(groupHeader);
            }

            // Item-Zeile
            View row = inflater.inflate(R.layout.item_shopping_row, container, false);

            ImageView checkbox = row.findViewById(R.id.shopping_checkbox);
            TextView nameView = row.findViewById(R.id.shopping_name);
            TextView excessView = row.findViewById(R.id.shopping_excess);
            TextView amountView = row.findViewById(R.id.shopping_amount);

            nameView.setText(item.ingredientName());
            amountView.setText(item.formattedAmount());

            // Ueberschuss anzeigen
            if (item.excessAmount() > 0) {
                excessView.setText(item.formattedExcess());
                excessView.setVisibility(View.VISIBLE);
            }

            // Checkbox-Status
            if (item.isPurchased()) {
                checkbox.setImageResource(android.R.drawable.checkbox_on_background);
                nameView.setPaintFlags(nameView.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
                nameView.setTextColor(ContextCompat.getColor(context, R.color.text_tertiary));
            }

            // Click-Handler
            final Long itemId = item.id();
            row.setOnClickListener(v -> {
                manager.toggleShoppingItemPurchased(itemId);
                notifyChanged();
            });

            container.addView(row);
        }

        // === "Einkauf abschliessen" Button ===
        if (summary.totalItems() > 0) {
            TextView finishButton = new TextView(context);
            finishButton.setText("Einkauf abschließen");
            finishButton.setTextSize(16);
            finishButton.setTextColor(ContextCompat.getColor(context, R.color.surface));
            finishButton.setGravity(android.view.Gravity.CENTER);
            finishButton.setPadding(dp(context, 16), dp(context, 12), dp(context, 16), dp(context, 12));

            int btnColor = summary.isComplete()
                ? ContextCompat.getColor(context, R.color.accent)
                : ContextCompat.getColor(context, R.color.text_tertiary);
            finishButton.setBackground(roundedBg(context, btnColor, 8));

            LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            );
            btnParams.setMargins(0, dp(context, 24), 0, dp(context, 16));
            finishButton.setLayoutParams(btnParams);

            final String finalWeekKey = weekKey;
            finishButton.setOnClickListener(v -> {
                showFinishShoppingDialog(finalWeekKey, summary);
            });

            container.addView(finishButton);
        }
    }

    // ============================================================================
    // FINISH SHOPPING DIALOG
    // ============================================================================

    private void showFinishShoppingDialog(String weekKey, ShoppingSummary summary) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Einkauf abschließen");

        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(context, 16), dp(context, 8), dp(context, 16), 0);

        // Betrag-Eingabe
        TextView amountLabel = new TextView(context);
        amountLabel.setText("Gesamtbetrag (EUR):");
        layout.addView(amountLabel);

        EditText amountInput = new EditText(context);
        amountInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        amountInput.setHint("z.B. 45.50");
        if (summary.estimatedTotalCents() > 0) {
            amountInput.setText(String.format(Locale.GERMANY, "%.2f", summary.estimatedTotalCents() / 100.0));
        }
        layout.addView(amountInput);

        builder.setView(layout);
        builder.setPositiveButton("Abschließen", (dialog, which) -> {
            try {
                String amtStr = amountInput.getText().toString().replace(',', '.');
                double amount = Double.parseDouble(amtStr);
                int cents = (int) (amount * 100);

                Long accountId = manager.getFirstActiveAccountId();
                manager.finishShopping(weekKey, accountId, cents);
            } catch (NumberFormatException e) {
                manager.finishShopping(weekKey, null, 0);
            }
        });
        builder.setNegativeButton("Abbrechen", null);
        builder.show();
    }

    // ============================================================================
    // HELPERS
    // ============================================================================

    private void notifyChanged() {
        if (listener != null) listener.onDataChanged();
    }
}

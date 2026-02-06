package activities.inApp.ernaehrungTab;

import android.app.DatePickerDialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import static activities.generic.ViewHelper.dp;
import static activities.generic.ViewHelper.parseDouble;
import static activities.generic.ViewHelper.roundedBg;
import static activities.generic.ViewHelper.setupModalOverlay;
import static activities.generic.ViewHelper.showEmptyState;
import static activities.generic.ViewHelper.spinnerAdapter;

import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import com.autosecretary.R;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import controller.MealManager;
import controller.MealManager.*;
import entities.PantryItem;

/**
 * Vorrat-Tab: Zeigt Vorratsartikel mit Filter-Chips, Stepper-Controls
 * und Pantry-Modal fuer Create/Edit.
 *
 * Folgt dem WeekPlanTab-Pattern: eigenstaendige Klasse mit
 * bindModals(), render(), setOnDataChanged().
 */
public class PantryTab {

    private final Context context;
    private final MealManager manager;

    // State
    private PantryItem.StorageLocation pantryFilter = null;  // null = Alle
    private List<PantryEntry> pantryList = new ArrayList<>();
    private List<IngredientEntry> ingredientsList = new ArrayList<>();
    private PantryItem editingPantryItem = null;
    private LocalDate selectedExpiryDate = null;
    private FrameLayout currentContainer = null;

    // Pantry Modal References
    private FrameLayout modalPantryOverlay;
    private TextView modalPantryTitle;
    private Spinner spinnerPantryIngredient;
    private EditText inputPantryAmount;
    private TextView pantryUnitLabel;
    private Spinner spinnerPantryLocation;
    private TextView inputPantryExpiry;
    private TextView btnPantryDelete;
    private TextView btnPantryCancel;
    private TextView btnPantrySave;

    // Callback
    private MealTabListener listener;

    // ============================================================================
    // CONSTRUCTOR + INIT
    // ============================================================================

    public PantryTab(Context context, MealManager manager) {
        this.context = context;
        this.manager = manager;
    }

    public void setListener(MealTabListener listener) {
        this.listener = listener;
    }

    /**
     * Inflated und bindet das Pantry-Modal.
     * Jeder Sub-Tab besitzt sein eigenes Modal (kein Zugriff auf Parent-Layout).
     */
    public void initModals(FrameLayout rootContainer) {
        modalPantryOverlay = (FrameLayout) LayoutInflater.from(context)
            .inflate(R.layout.modal_pantry, rootContainer, false);
        modalPantryOverlay.setVisibility(View.GONE);
        rootContainer.addView(modalPantryOverlay);

        modalPantryTitle = modalPantryOverlay.findViewById(R.id.modal_pantry_title);
        spinnerPantryIngredient = modalPantryOverlay.findViewById(R.id.spinner_pantry_ingredient);
        inputPantryAmount = modalPantryOverlay.findViewById(R.id.input_pantry_amount);
        pantryUnitLabel = modalPantryOverlay.findViewById(R.id.pantry_unit_label);
        spinnerPantryLocation = modalPantryOverlay.findViewById(R.id.spinner_pantry_location);
        inputPantryExpiry = modalPantryOverlay.findViewById(R.id.input_pantry_expiry);
        btnPantryDelete = modalPantryOverlay.findViewById(R.id.btn_pantry_delete);
        btnPantryCancel = modalPantryOverlay.findViewById(R.id.btn_pantry_cancel);
        btnPantrySave = modalPantryOverlay.findViewById(R.id.btn_pantry_save);

        setupModal();
    }

    // ============================================================================
    // RENDER
    // ============================================================================

    /**
     * Rendert die Vorrat-Liste ins gegebene Container-Layout.
     */
    public void render(FrameLayout container) {
        currentContainer = container;
        container.removeAllViews();

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);

        // === Filter-Chips ===
        HorizontalScrollView filterScroll = new HorizontalScrollView(context);
        filterScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout filterRow = new LinearLayout(context);
        filterRow.setOrientation(LinearLayout.HORIZONTAL);
        filterRow.setPadding(0, 0, 0, dp(context, 12));

        String[] filters = {"Alle", "\uD83E\uDDCA K\u00fchlschrank", "❄️ Gefrierfach", "\uD83C\uDFE0 Vorratskammer"};
        PantryItem.StorageLocation[] filterValues = {null,
            PantryItem.StorageLocation.FRIDGE,
            PantryItem.StorageLocation.FREEZER,
            PantryItem.StorageLocation.PANTRY};

        for (int i = 0; i < filters.length; i++) {
            TextView chip = createFilterChip(filters[i], filterValues[i]);
            filterRow.addView(chip);
        }
        filterScroll.addView(filterRow);
        content.addView(filterScroll);

        // === Daten laden ===
        pantryList = manager.providePantry(pantryFilter);

        if (pantryList.isEmpty()) {
            showEmptyState(content, "Noch keine Artikel im Vorrat.\nTippe auf + um Artikel hinzuzufügen.");
            container.addView(content);
            return;
        }

        // === Ablauf-Warnungen (rote Sektion) ===
        List<PantryEntry> expiring = new ArrayList<>();
        List<PantryEntry> normal = new ArrayList<>();

        for (PantryEntry entry : pantryList) {
            if (entry.isExpired() || entry.isExpiringSoon()) {
                expiring.add(entry);
            } else {
                normal.add(entry);
            }
        }

        if (!expiring.isEmpty()) {
            addSectionHeader(content, "⚠️ Bald ablaufend");
            for (PantryEntry entry : expiring) {
                View row = createRow(entry, true);
                content.addView(row);
            }
        }

        // === Normale Items nach Location gruppiert ===
        if (pantryFilter == null) {
            for (PantryItem.StorageLocation loc : PantryItem.StorageLocation.values()) {
                List<PantryEntry> byLoc = new ArrayList<>();
                for (PantryEntry entry : normal) {
                    if (entry.locationType() == loc) {
                        byLoc.add(entry);
                    }
                }
                if (byLoc.isEmpty()) continue;

                String icon = switch (loc) {
                    case FRIDGE -> "\uD83E\uDDCA";
                    case FREEZER -> "❄️";
                    case PANTRY -> "\uD83C\uDFE0";
                };
                addSectionHeader(content, icon + " " + loc.label);

                for (PantryEntry entry : byLoc) {
                    View row = createRow(entry, false);
                    content.addView(row);
                }
            }
        } else {
            for (PantryEntry entry : normal) {
                View row = createRow(entry, false);
                content.addView(row);
            }
        }

        container.addView(content);
    }

    // ============================================================================
    // FILTER CHIPS
    // ============================================================================

    private TextView createFilterChip(String label, PantryItem.StorageLocation value) {
        TextView chip = new TextView(context);
        chip.setText(label);
        chip.setTextSize(12);
        chip.setPadding(dp(context, 12), dp(context, 6), dp(context, 12), dp(context, 6));

        boolean selected = (pantryFilter == value);
        int bgColor = selected
            ? ContextCompat.getColor(context, R.color.accent)
            : ContextCompat.getColor(context, R.color.surface_card);
        int textColor = selected
            ? ContextCompat.getColor(context, R.color.white)
            : ContextCompat.getColor(context, R.color.text_primary);

        chip.setBackground(roundedBg(context, bgColor, 16));
        chip.setTextColor(textColor);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, dp(context, 8), 0);
        chip.setLayoutParams(params);

        chip.setOnClickListener(v -> {
            pantryFilter = value;
            if (currentContainer != null) {
                render(currentContainer);
            }
        });

        return chip;
    }

    // ============================================================================
    // PANTRY ROW
    // ============================================================================

    private View createRow(PantryEntry entry, boolean isWarning) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding(dp(context, 8), dp(context, 8), dp(context, 8), dp(context, 8));

        if (isWarning) {
            int warningBg = ContextCompat.getColor(context, R.color.budget_bar_warning);
            row.setBackground(roundedBg(context, ColorUtils.setAlphaComponent(warningBg, 0x30), 8));
        } else {
            int cardBg = ContextCompat.getColor(context, R.color.surface_card);
            row.setBackground(roundedBg(context, cardBg, 8));
        }

        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowParams.setMargins(0, 0, 0, dp(context, 4));
        row.setLayoutParams(rowParams);

        // Location Icon
        TextView locIcon = new TextView(context);
        locIcon.setText(entry.locationIcon());
        locIcon.setTextSize(16);
        locIcon.setPadding(0, 0, dp(context, 8), 0);
        row.addView(locIcon);

        // Name + Expiry Info
        LinearLayout textCol = new LinearLayout(context);
        textCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        textCol.setLayoutParams(textParams);

        TextView nameView = new TextView(context);
        nameView.setText(entry.name());
        nameView.setTextSize(14);
        nameView.setTextColor(ContextCompat.getColor(context, R.color.text_primary));
        textCol.addView(nameView);

        if (entry.expiryInfo() != null && !entry.expiryInfo().isEmpty()) {
            TextView expiryView = new TextView(context);
            expiryView.setText(entry.expiryInfo());
            expiryView.setTextSize(11);
            int expiryColor = entry.isExpired()
                ? ContextCompat.getColor(context, R.color.error)
                : (entry.isExpiringSoon()
                    ? ContextCompat.getColor(context, R.color.budget_bar_warning)
                    : ContextCompat.getColor(context, R.color.text_secondary));
            expiryView.setTextColor(expiryColor);
            textCol.addView(expiryView);
        }
        row.addView(textCol);

        // [-] Button
        TextView btnMinus = new TextView(context);
        btnMinus.setText("\u2212");
        btnMinus.setTextSize(20);
        btnMinus.setTextColor(ContextCompat.getColor(context, R.color.accent));
        btnMinus.setPadding(dp(context, 12), dp(context, 4), dp(context, 12), dp(context, 4));
        final Long itemId = entry.id();
        final String unit = entry.unit();
        btnMinus.setOnClickListener(v -> {
            double step = getStepSize(unit);
            manager.adjustPantryAmount(itemId, -step);
        });
        row.addView(btnMinus);

        // Amount
        TextView amountView = new TextView(context);
        amountView.setText(entry.amount());
        amountView.setTextSize(14);
        amountView.setTextColor(ContextCompat.getColor(context, R.color.text_primary));
        amountView.setGravity(android.view.Gravity.CENTER);
        amountView.setMinWidth(dp(context, 60));
        row.addView(amountView);

        // [+] Button
        TextView btnPlus = new TextView(context);
        btnPlus.setText("+");
        btnPlus.setTextSize(20);
        btnPlus.setTextColor(ContextCompat.getColor(context, R.color.accent));
        btnPlus.setPadding(dp(context, 12), dp(context, 4), dp(context, 12), dp(context, 4));
        btnPlus.setOnClickListener(v -> {
            double step = getStepSize(unit);
            manager.adjustPantryAmount(itemId, step);
        });
        row.addView(btnPlus);

        // Long-Click: Edit Modal
        row.setOnLongClickListener(v -> {
            showPantryModal(itemId);
            return true;
        });

        return row;
    }

    private static final double STEP_GRAMS = 50;
    private static final double STEP_KG = 0.1;

    private double getStepSize(String unit) {
        if (unit == null) return 1;
        return switch (unit.toLowerCase()) {
            case "g", "ml" -> STEP_GRAMS;
            case "kg", "l" -> STEP_KG;
            case "st\u00fcck", "stk" -> 1;
            default -> 1;
        };
    }

    // ============================================================================
    // PANTRY MODAL
    // ============================================================================

    private void setupModal() {
        // Location Spinner befuellen
        String[] locations = {"K\u00fchlschrank", "Gefrierfach", "Vorratskammer"};
        spinnerPantryLocation.setAdapter(spinnerAdapter(context, locations));

        // Buttons
        btnPantryCancel.setOnClickListener(v -> hideModal());
        btnPantrySave.setOnClickListener(v -> saveItem());
        btnPantryDelete.setOnClickListener(v -> deleteItem());

        setupModalOverlay(modalPantryOverlay, this::hideModal);

        // Zutat-Spinner -> Unit-Label
        spinnerPantryIngredient.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                if (position > 0 && position <= ingredientsList.size()) {
                    IngredientEntry ing = ingredientsList.get(position - 1);
                    pantryUnitLabel.setText(ing.unit());
                } else {
                    pantryUnitLabel.setText("");
                }
            }
            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });

        // Ablaufdatum -> DatePicker
        inputPantryExpiry.setOnClickListener(v -> showExpiryDatePicker());
    }

    public void showPantryModal(Long itemId) {
        ingredientsList = manager.provideIngredients();

        // Zutat-Spinner befuellen
        List<String> names = new ArrayList<>();
        names.add("-- Zutat w\u00e4hlen --");
        for (IngredientEntry ing : ingredientsList) {
            names.add(ing.name());
        }
        spinnerPantryIngredient.setAdapter(spinnerAdapter(context, names));

        if (itemId != null) {
            // Edit mode
            editingPantryItem = manager.getPantryItem(itemId);
            modalPantryTitle.setText("Vorrat bearbeiten");
            btnPantryDelete.setVisibility(View.VISIBLE);

            // Felder befuellen
            for (int i = 0; i < ingredientsList.size(); i++) {
                if (ingredientsList.get(i).id().equals(editingPantryItem.ingredientId)) {
                    spinnerPantryIngredient.setSelection(i + 1);
                    break;
                }
            }
            inputPantryAmount.setText(String.valueOf((int) editingPantryItem.amount));
            spinnerPantryLocation.setSelection(editingPantryItem.location.ordinal());
            selectedExpiryDate = editingPantryItem.expiryDate;
            updateExpiryDisplay();
        } else {
            // Create mode
            editingPantryItem = null;
            modalPantryTitle.setText("Vorrat hinzuf\u00fcgen");
            btnPantryDelete.setVisibility(View.GONE);

            spinnerPantryIngredient.setSelection(0);
            inputPantryAmount.setText("");
            spinnerPantryLocation.setSelection(0);
            selectedExpiryDate = LocalDate.now().plusDays(7);
            updateExpiryDisplay();
        }

        modalPantryOverlay.setVisibility(View.VISIBLE);
    }

    private void hideModal() {
        modalPantryOverlay.setVisibility(View.GONE);
        editingPantryItem = null;
        selectedExpiryDate = null;
    }

    private void updateExpiryDisplay() {
        if (selectedExpiryDate != null) {
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd.MM.yyyy");
            inputPantryExpiry.setText(fmt.format(selectedExpiryDate));
        } else {
            inputPantryExpiry.setText("\u2014");
        }
    }

    private void showExpiryDatePicker() {
        LocalDate initial = selectedExpiryDate != null ? selectedExpiryDate : LocalDate.now();
        new DatePickerDialog(context, (view, year, month, day) -> {
            selectedExpiryDate = LocalDate.of(year, month + 1, day);
            updateExpiryDisplay();
        }, initial.getYear(), initial.getMonthValue() - 1, initial.getDayOfMonth()).show();
    }

    private void saveItem() {
        int ingPos = spinnerPantryIngredient.getSelectedItemPosition();
        if (ingPos == 0) {
            return;
        }

        IngredientEntry ing = ingredientsList.get(ingPos - 1);

        double amount = parseDouble(inputPantryAmount, 100);

        PantryItem.StorageLocation location = PantryItem.StorageLocation.values()
            [spinnerPantryLocation.getSelectedItemPosition()];

        if (editingPantryItem != null) {
            editingPantryItem.ingredientId = ing.id();
            editingPantryItem.ingredientName = ing.name();
            editingPantryItem.amount = amount;
            editingPantryItem.unit = ing.unit();
            editingPantryItem.location = location;
            editingPantryItem.expiryDate = selectedExpiryDate;
            manager.updatePantryItem(editingPantryItem);
        } else {
            manager.addToPantry(ing.id(), amount, ing.unit(), location, selectedExpiryDate);
        }

        hideModal();
        notifyChanged();
    }

    private void deleteItem() {
        if (editingPantryItem != null && editingPantryItem.id != null) {
            manager.deletePantryItem(editingPantryItem.id);
        }
        hideModal();
        notifyChanged();
    }

    // ============================================================================
    // HELPERS
    // ============================================================================

    private void addSectionHeader(LinearLayout container, String title) {
        TextView header = new TextView(context);
        header.setText(title);
        header.setTextSize(14);
        header.setTextColor(ContextCompat.getColor(context, R.color.text_secondary));
        header.setTypeface(null, android.graphics.Typeface.BOLD);
        header.setPadding(0, dp(context, 16), 0, dp(context, 8));
        container.addView(header);
    }

    private void notifyChanged() {
        if (listener != null) listener.onDataChanged();
    }
}

package activities.inApp;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;

import static activities.generic.ViewHelper.dp;
import static activities.generic.ViewHelper.roundedBg;

import androidx.core.content.ContextCompat;

import com.autosecretary.R;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import android.graphics.Paint;
import android.text.InputType;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;

import activities.generic.ViewBuilder;
import entities.HouseholdMember;
import entities.PantryItem;
import controller.mealManager;
import controller.mealManager.*;
import entities.Ingredient;
import entities.MealPlan;
import entities.MealSchedule;
import entities.MealType;
import entities.Recipe;
import scheduling.CalendarReader;
import scheduling.generateMealPlan;

/**
 * ══════════════════════════════════════════════════════════════════════════════
 * MEAL PLAN VIEW - UI für Ernährungs-Tab
 * ══════════════════════════════════════════════════════════════════════════════
 *
 * Zeigt:
 * - Wochenfortschritt pro Lebensmittelgruppe
 * - Sub-Tabs: Wochenplan | Rezepte | Einkauf | Vorrat
 * - Phase 2: Nur Rezepte-Tab implementiert
 *
 * Implementiert MealListener für automatische UI-Updates.
 */
public class mealPlanView implements MealListener, ViewBuilder {

    private Context context;
    private mealManager manager;

    // UI References
    private View root;
    private LinearLayout foodGroupContainer;
    private FrameLayout mealContent;
    private TextView tabWeekPlan;
    private TextView tabRecipes;
    private TextView tabShopping;
    private TextView tabPantry;
    private TextView tabPlanning;
    private FrameLayout fabAdd;
    private FrameLayout modalOverlay;
    private FrameLayout modalMealPlanOverlay;
    private FrameLayout modalMemberOverlay;

    // MealPlan Modal References
    private TextView modalMealPlanTitle;
    private TextView mealPlanDateInfo;
    private Spinner spinnerMealPlanRecipe;
    private LinearLayout mealPlanRecipeInfo;
    private TextView mealPlanRecipeTime;
    private TextView mealPlanRecipeCalories;
    private EditText inputMealPlanServings;
    private TextView btnMealPlanDelete;
    private TextView btnMealPlanCancel;
    private TextView btnMealPlanSave;

    // Recipe Modal References
    private TextView modalTitle;
    private EditText inputTitle;
    private Spinner spinnerMealType;
    private EditText inputPrepTime;
    private EditText inputCookTime;
    private EditText inputServings;
    private LinearLayout ingredientsContainer;
    private TextView btnAddIngredient;
    private EditText inputInstructions;
    private EditText inputTags;
    private TextView btnCancel;
    private TextView btnSave;

    // Member Modal References
    private TextView modalMemberTitle;
    private EditText inputMemberName;
    private EditText inputMemberBirthYear;
    private Spinner spinnerMemberGender;
    private EditText inputMemberHeight;
    private EditText inputMemberWeight;
    private Spinner spinnerMemberActivity;
    private TextView memberTdeePreview;
    private TextView btnMemberDelete;
    private TextView btnMemberCancel;
    private TextView btnMemberSave;

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

    // State
    private int currentTab = 0;  // Default: Wochenplan (changed from 1)
    private Recipe editingRecipe = null;
    private List<RecipeEntry> recipesList = new ArrayList<>();
    private List<IngredientEntry> ingredientsList = new ArrayList<>();

    // WeekPlan State
    private LocalDate currentWeekStart;
    private List<MealPlanEntry> weekPlanEntries = new ArrayList<>();
    private List<ScheduleEntry> scheduleEntries = new ArrayList<>();
    private MealPlan editingMealPlan = null;
    private LocalDate modalMealDate;
    private MealType modalMealType;

    // Member Modal State
    private HouseholdMember editingMember = null;
    private List<MemberEntry> membersList = new ArrayList<>();

    // Pantry State
    private PantryItem.StorageLocation pantryFilter = null;  // null = Alle
    private List<PantryEntry> pantryList = new ArrayList<>();
    private PantryItem editingPantryItem = null;
    private LocalDate selectedExpiryDate = null;

    // Zutat-Zeilen im Modal
    private List<IngredientRowState> ingredientRows = new ArrayList<>();

    private record IngredientRowState(
        View view,
        Spinner spinner,
        EditText amountInput,
        TextView unitLabel,
        Long ingredientId,
        String ingredientName
    ) {}

    public mealPlanView(Context context, mealManager manager) {
        this.context = context;
        this.manager = manager;
        this.manager.setListener(this);
    }

    @Override
    public View buildView() {
        root = LayoutInflater.from(context).inflate(R.layout.view_meal_plan, null);

        // Bind Views
        foodGroupContainer = root.findViewById(R.id.food_group_container);
        mealContent = root.findViewById(R.id.meal_content);
        tabWeekPlan = root.findViewById(R.id.tab_week_plan);
        tabRecipes = root.findViewById(R.id.tab_recipes);
        tabShopping = root.findViewById(R.id.tab_shopping);
        tabPantry = root.findViewById(R.id.tab_pantry);
        tabPlanning = root.findViewById(R.id.tab_planning);
        fabAdd = root.findViewById(R.id.fab_add);
        modalOverlay = root.findViewById(R.id.modal_overlay);
        modalMealPlanOverlay = root.findViewById(R.id.modal_meal_plan_overlay);
        modalMemberOverlay = root.findViewById(R.id.modal_member_overlay);

        // Bind MealPlan Modal Views
        modalMealPlanTitle = root.findViewById(R.id.modal_meal_plan_title);
        mealPlanDateInfo = root.findViewById(R.id.meal_plan_date_info);
        spinnerMealPlanRecipe = root.findViewById(R.id.spinner_meal_plan_recipe);
        mealPlanRecipeInfo = root.findViewById(R.id.meal_plan_recipe_info);
        mealPlanRecipeTime = root.findViewById(R.id.meal_plan_recipe_time);
        mealPlanRecipeCalories = root.findViewById(R.id.meal_plan_recipe_calories);
        inputMealPlanServings = root.findViewById(R.id.input_meal_plan_servings);
        btnMealPlanDelete = root.findViewById(R.id.btn_meal_plan_delete);
        btnMealPlanCancel = root.findViewById(R.id.btn_meal_plan_cancel);
        btnMealPlanSave = root.findViewById(R.id.btn_meal_plan_save);

        // Bind Recipe Modal Views
        modalTitle = root.findViewById(R.id.modal_title);
        inputTitle = root.findViewById(R.id.input_title);
        spinnerMealType = root.findViewById(R.id.spinner_meal_type);
        inputPrepTime = root.findViewById(R.id.input_prep_time);
        inputCookTime = root.findViewById(R.id.input_cook_time);
        inputServings = root.findViewById(R.id.input_servings);
        ingredientsContainer = root.findViewById(R.id.ingredients_container);
        btnAddIngredient = root.findViewById(R.id.btn_add_ingredient);
        inputInstructions = root.findViewById(R.id.input_instructions);
        inputTags = root.findViewById(R.id.input_tags);
        btnCancel = root.findViewById(R.id.btn_cancel);
        btnSave = root.findViewById(R.id.btn_save);

        // Bind Member Modal Views
        modalMemberTitle = root.findViewById(R.id.modal_member_title);
        inputMemberName = root.findViewById(R.id.input_member_name);
        inputMemberBirthYear = root.findViewById(R.id.input_member_birth_year);
        spinnerMemberGender = root.findViewById(R.id.spinner_member_gender);
        inputMemberHeight = root.findViewById(R.id.input_member_height);
        inputMemberWeight = root.findViewById(R.id.input_member_weight);
        spinnerMemberActivity = root.findViewById(R.id.spinner_member_activity);
        memberTdeePreview = root.findViewById(R.id.member_tdee_preview);
        btnMemberDelete = root.findViewById(R.id.btn_member_delete);
        btnMemberCancel = root.findViewById(R.id.btn_member_cancel);
        btnMemberSave = root.findViewById(R.id.btn_member_save);

        // Bind Pantry Modal Views
        modalPantryOverlay = root.findViewById(R.id.modal_pantry_overlay);
        modalPantryTitle = root.findViewById(R.id.modal_pantry_title);
        spinnerPantryIngredient = root.findViewById(R.id.spinner_pantry_ingredient);
        inputPantryAmount = root.findViewById(R.id.input_pantry_amount);
        pantryUnitLabel = root.findViewById(R.id.pantry_unit_label);
        spinnerPantryLocation = root.findViewById(R.id.spinner_pantry_location);
        inputPantryExpiry = root.findViewById(R.id.input_pantry_expiry);
        btnPantryDelete = root.findViewById(R.id.btn_pantry_delete);
        btnPantryCancel = root.findViewById(R.id.btn_pantry_cancel);
        btnPantrySave = root.findViewById(R.id.btn_pantry_save);

        // Initialize week to current Monday
        currentWeekStart = getMonday(LocalDate.now());

        setupTabs();
        setupFAB();
        setupModal();
        setupMealPlanModal();
        setupMemberModal();
        setupPantryModal();
        render();

        return root;
    }

    @Override
    public void onDataUpdated() {
        if (root != null) {
            root.post(this::render);
        }
    }

    // ============================================================================
    // SUB-TAB NAVIGATION
    // ============================================================================

    private void setupTabs() {
        tabWeekPlan.setOnClickListener(v -> selectSubTab(0));
        tabRecipes.setOnClickListener(v -> selectSubTab(1));
        tabShopping.setOnClickListener(v -> selectSubTab(2));
        tabPantry.setOnClickListener(v -> selectSubTab(3));
        tabPlanning.setOnClickListener(v -> selectSubTab(4));
    }

    private void selectSubTab(int index) {
        currentTab = index;
        int accent = ContextCompat.getColor(context, R.color.accent);
        int secondary = ContextCompat.getColor(context, R.color.text_secondary);

        tabWeekPlan.setTextColor(index == 0 ? accent : secondary);
        tabRecipes.setTextColor(index == 1 ? accent : secondary);
        tabShopping.setTextColor(index == 2 ? accent : secondary);
        tabPantry.setTextColor(index == 3 ? accent : secondary);
        tabPlanning.setTextColor(index == 4 ? accent : secondary);

        updateFabVisibility();
        renderContent();
    }

    // ============================================================================
    // FAB
    // ============================================================================

    private void setupFAB() {
        // Runder Hintergrund
        int accent = ContextCompat.getColor(context, R.color.accent);
        fabAdd.setBackground(roundedBg(context, accent, 28));

        fabAdd.setOnClickListener(v -> {
            switch (currentTab) {
                case 0 -> showAutoGenerateDialog();  // Wochenplan: Auto-Generierung
                case 1 -> showRecipeModal(null);     // Neues Rezept
                case 3 -> showPantryModal(null);     // Neuer Vorrat
            }
        });

        // FAB nur bei bestimmten Tabs anzeigen
        updateFabVisibility();
    }

    private void updateFabVisibility() {
        // FAB anzeigen für Wochenplan (0), Rezepte (1) und Vorrat (3)
        fabAdd.setVisibility((currentTab == 0 || currentTab == 1 || currentTab == 3) ? View.VISIBLE : View.GONE);
    }

    /**
     * Zeigt Dialog zur automatischen Wochenplanung.
     */
    private void showAutoGenerateDialog() {
        new AlertDialog.Builder(context)
            .setTitle("Woche automatisch planen")
            .setMessage("Es werden automatisch Rezepte für die angezeigte Woche ausgewählt.\n\n" +
                       "Bestehende Einträge für diese Woche werden überschrieben.")
            .setPositiveButton("Planen", (dialog, which) -> {
                generateWeekPlanAsync();
            })
            .setNegativeButton("Abbrechen", null)
            .show();
    }

    /**
     * Generiert den Wochenplan im Hintergrund.
     */
    private void generateWeekPlanAsync() {
        LocalDate weekStart = currentWeekStart;

        // Bestehende MealPlans für diese Woche löschen
        List<MealPlanEntry> existing = manager.provideMealPlan(weekStart);
        for (MealPlanEntry entry : existing) {
            manager.deleteMealPlan(entry.id());
        }

        // Neuen Wochenplan generieren
        generateMealPlan generator = new generateMealPlan(
            new repository.SQLrepo(context),
            manager,
            (day) -> CalendarReader.getEventsForDay(context, day,
                java.time.LocalTime.of(6, 0), java.time.LocalTime.of(22, 0))
        );

        generator.generateWeekPlan(weekStart);

        // UI aktualisieren
        render();
    }

    // ============================================================================
    // RENDER
    // ============================================================================

    private void render() {
        renderFoodGroupProgress();
        renderContent();
    }

    private void renderFoodGroupProgress() {
        foodGroupContainer.removeAllViews();
        List<FoodGroupProgress> progress = manager.provideFoodGroupProgress(LocalDate.now());

        LayoutInflater inflater = LayoutInflater.from(context);

        for (FoodGroupProgress fg : progress) {
            View bar = inflater.inflate(R.layout.item_food_group_bar, foodGroupContainer, false);

            TextView icon = bar.findViewById(R.id.group_icon);
            TextView label = bar.findViewById(R.id.group_label);
            ProgressBar progressBar = bar.findViewById(R.id.group_progress);
            TextView value = bar.findViewById(R.id.group_value);

            icon.setText(fg.icon());
            label.setText(fg.label());
            progressBar.setProgress((int) fg.percent());
            value.setText(fg.formatted());

            foodGroupContainer.addView(bar);
        }
    }

    private void renderContent() {
        mealContent.removeAllViews();

        switch (currentTab) {
            case 0 -> renderWeekPlan();
            case 1 -> renderRecipes();
            case 2 -> renderShopping();
            case 3 -> renderPantry();
            case 4 -> renderPlanning();
        }
    }

    private void renderWeekPlan() {
        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);

        // === Week Navigation Header ===
        LinearLayout weekHeader = new LinearLayout(context);
        weekHeader.setOrientation(LinearLayout.HORIZONTAL);
        weekHeader.setGravity(android.view.Gravity.CENTER_VERTICAL);
        weekHeader.setPadding(0, 0, 0, dp(context, 12));

        TextView btnPrev = new TextView(context);
        btnPrev.setText("<");
        btnPrev.setTextSize(20);
        btnPrev.setTextColor(ContextCompat.getColor(context, R.color.accent));
        btnPrev.setPadding(dp(context, 16), dp(context, 8), dp(context, 16), dp(context, 8));
        btnPrev.setOnClickListener(v -> {
            currentWeekStart = currentWeekStart.minusWeeks(1);
            renderContent();
        });

        TextView weekLabel = new TextView(context);
        weekLabel.setTextSize(14);
        weekLabel.setTextColor(ContextCompat.getColor(context, R.color.text_primary));
        weekLabel.setGravity(android.view.Gravity.CENTER);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        weekLabel.setLayoutParams(labelParams);

        // Format: "KW 06 (03.-09.02.)"
        WeekFields weekFields = WeekFields.of(Locale.GERMANY);
        int weekNum = currentWeekStart.get(weekFields.weekOfWeekBasedYear());
        LocalDate weekEnd = currentWeekStart.plusDays(6);
        DateTimeFormatter dayFmt = DateTimeFormatter.ofPattern("dd.");
        DateTimeFormatter monthFmt = DateTimeFormatter.ofPattern("dd.MM.");
        weekLabel.setText("KW " + weekNum + " (" + dayFmt.format(currentWeekStart) + "-" + monthFmt.format(weekEnd) + ")");

        TextView btnNext = new TextView(context);
        btnNext.setText(">");
        btnNext.setTextSize(20);
        btnNext.setTextColor(ContextCompat.getColor(context, R.color.accent));
        btnNext.setPadding(dp(context, 16), dp(context, 8), dp(context, 16), dp(context, 8));
        btnNext.setOnClickListener(v -> {
            currentWeekStart = currentWeekStart.plusWeeks(1);
            renderContent();
        });

        weekHeader.addView(btnPrev);
        weekHeader.addView(weekLabel);
        weekHeader.addView(btnNext);
        container.addView(weekHeader);

        // Load data
        weekPlanEntries = manager.provideMealPlan(currentWeekStart);
        scheduleEntries = manager.provideSchedule();

        LayoutInflater inflater = LayoutInflater.from(context);
        DateTimeFormatter dateFmt = DateTimeFormatter.ofPattern("EEEE, dd.MM.", Locale.GERMAN);

        // === 7 Day Sections ===
        for (int dayOffset = 0; dayOffset < 7; dayOffset++) {
            LocalDate date = currentWeekStart.plusDays(dayOffset);
            DayOfWeek dayOfWeek = date.getDayOfWeek();

            // Day Header
            TextView dayHeader = new TextView(context);
            dayHeader.setText(dateFmt.format(date).toUpperCase());
            dayHeader.setTextSize(12);
            dayHeader.setTextColor(ContextCompat.getColor(context, R.color.text_secondary));
            dayHeader.setTypeface(null, android.graphics.Typeface.BOLD);
            dayHeader.setPadding(0, dp(context, 12), 0, dp(context, 8));
            container.addView(dayHeader);

            // Meal Slots (horizontal row)
            LinearLayout mealRow = new LinearLayout(context);
            mealRow.setOrientation(LinearLayout.HORIZONTAL);

            for (MealType mealType : MealType.values()) {
                View slotView = inflater.inflate(R.layout.item_meal_slot, mealRow, false);

                TextView iconView = slotView.findViewById(R.id.meal_icon);
                TextView timeView = slotView.findViewById(R.id.meal_time);
                TextView titleView = slotView.findViewById(R.id.meal_recipe_title);
                TextView caloriesView = slotView.findViewById(R.id.meal_calories);
                LinearLayout cardView = slotView.findViewById(R.id.meal_slot_card);

                // Find schedule for this day+meal
                ScheduleEntry schedule = findSchedule(dayOfWeek, mealType);
                boolean isEnabled = schedule != null && schedule.isEnabled();

                iconView.setText(mealType.icon);

                if (!isEnabled) {
                    // Disabled slot
                    timeView.setText("—");
                    titleView.setText("—");
                    titleView.setTextColor(ContextCompat.getColor(context, R.color.text_tertiary));
                    cardView.setAlpha(0.5f);
                } else {
                    timeView.setText(schedule != null ? schedule.formattedTime() : "—");

                    // Find meal plan for this day+meal
                    MealPlanEntry planEntry = findMealPlan(date, mealType);

                    if (planEntry != null) {
                        // Has recipe assigned
                        titleView.setText(planEntry.recipeTitle());
                        titleView.setTextColor(ContextCompat.getColor(context, R.color.text_primary));
                        caloriesView.setText(planEntry.calories() + " kcal");
                        caloriesView.setVisibility(View.VISIBLE);

                        if (planEntry.isCompleted()) {
                            cardView.setBackgroundColor(ContextCompat.getColor(context, R.color.surface_complete));
                        }
                    } else {
                        // Empty slot
                        titleView.setText("+ Hinzu-\nfügen");
                        titleView.setTextColor(ContextCompat.getColor(context, R.color.accent));
                    }

                    // Click handler
                    final LocalDate finalDate = date;
                    final MealType finalMealType = mealType;
                    slotView.setOnClickListener(v -> {
                        MealPlan existing = manager.findMealPlan(finalDate, finalMealType);
                        showMealPlanModal(finalDate, finalMealType, existing);
                    });
                }

                mealRow.addView(slotView);
            }

            container.addView(mealRow);
        }

        mealContent.addView(container);
    }

    private ScheduleEntry findSchedule(DayOfWeek day, MealType type) {
        for (ScheduleEntry entry : scheduleEntries) {
            if (entry.day() == day && entry.mealType() == type) {
                return entry;
            }
        }
        return null;
    }

    private MealPlanEntry findMealPlan(LocalDate date, MealType type) {
        for (MealPlanEntry entry : weekPlanEntries) {
            if (entry.date().equals(date) && entry.mealType().equals(type.toString().replace("_", " "))) {
                return entry;
            }
        }
        // Also check by label
        String typeLabel = type.label;
        for (MealPlanEntry entry : weekPlanEntries) {
            if (entry.date().equals(date) && entry.mealType().equals(typeLabel)) {
                return entry;
            }
        }
        return null;
    }

    private LocalDate getMonday(LocalDate date) {
        return date.with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    private void renderRecipes() {
        recipesList = manager.provideAllRecipes();

        if (recipesList.isEmpty()) {
            TextView empty = new TextView(context);
            empty.setText("Noch keine Rezepte vorhanden.\nTippe auf + um ein Rezept zu erstellen.");
            empty.setTextColor(ContextCompat.getColor(context, R.color.text_secondary));
            empty.setPadding(dp(context, 16), dp(context, 32), dp(context, 16), dp(context, 32));
            mealContent.addView(empty);
            return;
        }

        // Grid mit 2 Spalten
        LinearLayout gridContainer = new LinearLayout(context);
        gridContainer.setOrientation(LinearLayout.VERTICAL);

        LayoutInflater inflater = LayoutInflater.from(context);
        LinearLayout currentRow = null;

        for (int i = 0; i < recipesList.size(); i++) {
            if (i % 2 == 0) {
                currentRow = new LinearLayout(context);
                currentRow.setOrientation(LinearLayout.HORIZONTAL);
                gridContainer.addView(currentRow);
            }

            RecipeEntry recipe = recipesList.get(i);
            View card = inflater.inflate(R.layout.item_recipe_card, currentRow, false);

            TextView title = card.findViewById(R.id.recipe_title);
            TextView meta = card.findViewById(R.id.recipe_meta);
            TextView mealType = card.findViewById(R.id.recipe_meal_type);
            ImageView favorite = card.findViewById(R.id.recipe_favorite);
            TextView tags = card.findViewById(R.id.recipe_tags);

            title.setText(recipe.title());
            meta.setText(recipe.totalTime() + " min • " + recipe.calories() + " kcal");
            mealType.setText(recipe.mealType());

            // Favorit-Icon
            favorite.setImageResource(recipe.isFavorite()
                ? android.R.drawable.btn_star_big_on
                : android.R.drawable.btn_star_big_off);

            // Favorit-Toggle
            int index = i;
            favorite.setOnClickListener(v -> {
                manager.toggleFavorite(recipesList.get(index).id());
            });

            // Tags
            if (recipe.tags() != null && !recipe.tags().isEmpty()) {
                tags.setText(recipe.tags());
                tags.setVisibility(View.VISIBLE);
            }

            // Klick: Rezept bearbeiten
            card.setOnClickListener(v -> {
                Recipe full = manager.getRecipe(recipe.id());
                showRecipeModal(full);
            });

            // Layout-Params für halbe Breite
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            params.setMargins(0, 0, dp(context, 8), dp(context, 8));
            card.setLayoutParams(params);

            currentRow.addView(card);
        }

        // Falls ungerade Anzahl, Platzhalter hinzufügen
        if (recipesList.size() % 2 == 1 && currentRow != null) {
            View spacer = new View(context);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, 0, 1f);
            spacer.setLayoutParams(params);
            currentRow.addView(spacer);
        }

        mealContent.addView(gridContainer);
    }

    private void renderShopping() {
        String weekKey = getWeekKey(currentWeekStart);
        List<mealManager.ShoppingEntry> items = manager.provideShoppingList(weekKey);
        mealManager.ShoppingSummary summary = manager.provideShoppingSummary(weekKey);

        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);

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

        // Geschätzter Preis
        if (summary.estimatedTotalCents() > 0) {
            TextView priceLabel = new TextView(context);
            priceLabel.setText("Geschätzt: " + summary.formattedTotal());
            priceLabel.setTextSize(12);
            priceLabel.setTextColor(ContextCompat.getColor(context, R.color.text_secondary));
            header.addView(priceLabel);
        }

        container.addView(header);

        if (items.isEmpty()) {
            TextView empty = new TextView(context);
            empty.setText("Keine Einkaufsliste vorhanden.\nGeneriere erst einen Wochenplan.");
            empty.setTextColor(ContextCompat.getColor(context, R.color.text_secondary));
            empty.setPadding(dp(context, 16), dp(context, 32), dp(context, 16), dp(context, 32));
            container.addView(empty);
            mealContent.addView(container);
            return;
        }

        // === Items gruppiert nach FoodGroup ===
        String currentGroup = null;
        LayoutInflater inflater = LayoutInflater.from(context);

        for (mealManager.ShoppingEntry item : items) {
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

            // Überschuss anzeigen
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
            });

            container.addView(row);
        }

        // === "Einkauf abschließen" Button ===
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

        mealContent.addView(container);
    }

    private void showFinishShoppingDialog(String weekKey, mealManager.ShoppingSummary summary) {
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

                // Erstes aktives Konto finden
                Long accountId = getFirstActiveAccountId();

                manager.finishShopping(weekKey, accountId, cents);
            } catch (NumberFormatException e) {
                // Ungültige Eingabe - ohne Transaktion abschließen
                manager.finishShopping(weekKey, null, 0);
            }
        });
        builder.setNegativeButton("Abbrechen", null);
        builder.show();
    }

    private Long getFirstActiveAccountId() {
        // Erstes aktives Konto aus DB finden
        repository.SQLrepo repo = new repository.SQLrepo(context);
        List<Long> accountIds = repo.lookups("accounts", java.util.Map.of("is_active", "1"), "id");
        return accountIds.isEmpty() ? null : accountIds.get(0);
    }

    private String getWeekKey(LocalDate weekStart) {
        WeekFields weekFields = WeekFields.of(Locale.GERMANY);
        int week = weekStart.get(weekFields.weekOfWeekBasedYear());
        int year = weekStart.get(weekFields.weekBasedYear());
        return String.format("%d-W%02d", year, week);
    }

    private void renderPantry() {
        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);

        // === Filter-Chips ===
        HorizontalScrollView filterScroll = new HorizontalScrollView(context);
        filterScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout filterRow = new LinearLayout(context);
        filterRow.setOrientation(LinearLayout.HORIZONTAL);
        filterRow.setPadding(0, 0, 0, dp(context, 12));

        String[] filters = {"Alle", "🧊 Kühlschrank", "❄️ Gefrierfach", "🏠 Vorratskammer"};
        PantryItem.StorageLocation[] filterValues = {null,
            PantryItem.StorageLocation.FRIDGE,
            PantryItem.StorageLocation.FREEZER,
            PantryItem.StorageLocation.PANTRY};

        for (int i = 0; i < filters.length; i++) {
            TextView chip = createPantryFilterChip(filters[i], filterValues[i]);
            filterRow.addView(chip);
        }
        filterScroll.addView(filterRow);
        container.addView(filterScroll);

        // === Daten laden ===
        pantryList = manager.providePantry(pantryFilter);

        if (pantryList.isEmpty()) {
            TextView empty = new TextView(context);
            empty.setText("Noch keine Artikel im Vorrat.\nTippe auf + um Artikel hinzuzufügen.");
            empty.setTextColor(ContextCompat.getColor(context, R.color.text_secondary));
            empty.setPadding(dp(context, 16), dp(context, 32), dp(context, 16), dp(context, 32));
            container.addView(empty);
            mealContent.addView(container);
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
            addSectionHeader(container, "⚠️ Bald ablaufend");
            for (PantryEntry entry : expiring) {
                View row = createPantryRow(entry, true);
                container.addView(row);
            }
        }

        // === Normale Items nach Location gruppiert ===
        if (pantryFilter == null) {
            // Nur gruppieren wenn "Alle" ausgewählt
            for (PantryItem.StorageLocation loc : PantryItem.StorageLocation.values()) {
                List<PantryEntry> byLoc = new ArrayList<>();
                for (PantryEntry entry : normal) {
                    if (entry.locationType() == loc) {
                        byLoc.add(entry);
                    }
                }
                if (byLoc.isEmpty()) continue;

                String icon = switch (loc) {
                    case FRIDGE -> "🧊";
                    case FREEZER -> "❄️";
                    case PANTRY -> "🏠";
                };
                addSectionHeader(container, icon + " " + loc.label);

                for (PantryEntry entry : byLoc) {
                    View row = createPantryRow(entry, false);
                    container.addView(row);
                }
            }
        } else {
            for (PantryEntry entry : normal) {
                View row = createPantryRow(entry, false);
                container.addView(row);
            }
        }

        mealContent.addView(container);
    }

    private TextView createPantryFilterChip(String label, PantryItem.StorageLocation value) {
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
            renderContent();
        });

        return chip;
    }

    private View createPantryRow(PantryEntry entry, boolean isWarning) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding(dp(context, 8), dp(context, 8), dp(context, 8), dp(context, 8));

        if (isWarning) {
            int warningBg = ContextCompat.getColor(context, R.color.budget_bar_warning);
            row.setBackground(roundedBg(context, (warningBg & 0x00FFFFFF) | 0x30000000, 8));
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
        btnMinus.setText("−");
        btnMinus.setTextSize(20);
        btnMinus.setTextColor(ContextCompat.getColor(context, R.color.accent));
        btnMinus.setPadding(dp(context, 12), dp(context, 4), dp(context, 12), dp(context, 4));
        final Long itemId = entry.id();
        final String unit = entry.unit();
        btnMinus.setOnClickListener(v -> {
            double step = getPantryStepSize(unit);
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
            double step = getPantryStepSize(unit);
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

    private double getPantryStepSize(String unit) {
        if (unit == null) return 1;
        return switch (unit.toLowerCase()) {
            case "g", "ml" -> 50;
            case "kg", "l" -> 0.1;
            case "stück", "stk" -> 1;
            default -> 1;
        };
    }

    // ============================================================================
    // PLANNING TAB
    // ============================================================================

    private void renderPlanning() {
        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);

        // === 1. Haushalt-Sektion ===
        addSectionHeader(container, "Haushalt");
        HorizontalScrollView memberScroll = buildMemberSection();
        container.addView(memberScroll);

        // === 2. Mahlzeiten-Zeiten ===
        addSectionHeader(container, "Mahlzeiten-Zeiten");
        HorizontalScrollView scheduleScroll = buildScheduleSection();
        container.addView(scheduleScroll);

        // === 3. Wochenbedarf ===
        addSectionHeader(container, "Wochenbedarf (berechnet)");
        LinearLayout targetSection = buildWeeklyTargetSection();
        container.addView(targetSection);

        mealContent.addView(container);
    }

    private void addSectionHeader(LinearLayout container, String title) {
        TextView header = new TextView(context);
        header.setText(title);
        header.setTextSize(14);
        header.setTextColor(ContextCompat.getColor(context, R.color.text_secondary));
        header.setTypeface(null, android.graphics.Typeface.BOLD);
        header.setPadding(0, dp(context, 16), 0, dp(context, 8));
        container.addView(header);
    }

    private HorizontalScrollView buildMemberSection() {
        HorizontalScrollView scroll = new HorizontalScrollView(context);
        scroll.setHorizontalScrollBarEnabled(false);

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, 0, 0, dp(context, 8));

        membersList = manager.provideMembers();
        LayoutInflater inflater = LayoutInflater.from(context);

        for (MemberEntry member : membersList) {
            View card = inflater.inflate(R.layout.item_member_card, row, false);

            TextView nameView = card.findViewById(R.id.member_name);
            TextView infoView = card.findViewById(R.id.member_info);
            TextView caloriesView = card.findViewById(R.id.member_calories);

            nameView.setText(member.name());
            infoView.setText(member.age() + " Jahre • " + member.activityLabel());
            caloriesView.setText(String.format("%,d kcal/Tag", member.dailyCalories()).replace(",", "."));

            // Rounded background
            int cardBg = ContextCompat.getColor(context, R.color.surface_card);
            card.setBackground(roundedBg(context, cardBg, 8));

            // Click to edit
            final Long memberId = member.id();
            card.setOnClickListener(v -> showMemberModal(memberId));

            row.addView(card);
        }

        // "+ Mitglied" Button
        TextView addButton = new TextView(context);
        addButton.setText("+ Mitglied");
        addButton.setTextSize(14);
        addButton.setTextColor(ContextCompat.getColor(context, R.color.accent));
        addButton.setGravity(android.view.Gravity.CENTER);
        addButton.setPadding(dp(context, 16), dp(context, 24), dp(context, 16), dp(context, 24));

        int bgColor = ContextCompat.getColor(context, R.color.surface_card);
        addButton.setBackground(roundedBg(context, bgColor, 8));

        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
            dp(context, 100), LinearLayout.LayoutParams.WRAP_CONTENT);
        btnParams.setMargins(0, 0, dp(context, 8), 0);
        addButton.setLayoutParams(btnParams);

        addButton.setOnClickListener(v -> showMemberModal(null));
        row.addView(addButton);

        scroll.addView(row);
        return scroll;
    }

    private HorizontalScrollView buildScheduleSection() {
        HorizontalScrollView scroll = new HorizontalScrollView(context);
        scroll.setHorizontalScrollBarEnabled(false);

        // 7x4 Grid (+ header row & column)
        GridLayout grid = new GridLayout(context);
        grid.setColumnCount(8);  // 1 für Labels + 7 Tage
        grid.setRowCount(5);     // 1 für Header + 4 Mahlzeiten

        scheduleEntries = manager.provideSchedule();

        int cellWidth = dp(context, 52);
        int cellHeight = dp(context, 40);
        int headerBg = ContextCompat.getColor(context, R.color.surface);
        int cellBg = ContextCompat.getColor(context, R.color.surface_card);

        // Header Row (Wochentage)
        String[] dayLabels = {"", "Mo", "Di", "Mi", "Do", "Fr", "Sa", "So"};
        for (int col = 0; col < 8; col++) {
            TextView cell = new TextView(context);
            cell.setText(dayLabels[col]);
            cell.setTextSize(11);
            cell.setTextColor(ContextCompat.getColor(context, R.color.text_secondary));
            cell.setGravity(android.view.Gravity.CENTER);
            cell.setTypeface(null, android.graphics.Typeface.BOLD);

            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = col == 0 ? dp(context, 48) : cellWidth;
            params.height = cellHeight;
            params.setMargins(1, 1, 1, 1);
            cell.setLayoutParams(params);
            cell.setBackgroundColor(headerBg);

            grid.addView(cell);
        }

        // Mahlzeiten-Zeilen
        MealType[] mealTypes = MealType.values();
        DayOfWeek[] days = {DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                           DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY};

        for (int row = 0; row < 4; row++) {
            MealType mealType = mealTypes[row];

            // Label-Spalte
            TextView labelCell = new TextView(context);
            labelCell.setText(mealType.icon + " " + mealType.label.substring(0, 4));
            labelCell.setTextSize(10);
            labelCell.setTextColor(ContextCompat.getColor(context, R.color.text_primary));
            labelCell.setGravity(android.view.Gravity.CENTER);

            GridLayout.LayoutParams labelParams = new GridLayout.LayoutParams();
            labelParams.width = dp(context, 48);
            labelParams.height = cellHeight;
            labelParams.setMargins(1, 1, 1, 1);
            labelCell.setLayoutParams(labelParams);
            labelCell.setBackgroundColor(headerBg);

            grid.addView(labelCell);

            // Zeit-Zellen
            for (int col = 0; col < 7; col++) {
                DayOfWeek day = days[col];
                ScheduleEntry schedule = findScheduleEntry(day, mealType);

                TextView timeCell = new TextView(context);
                if (schedule != null && schedule.isEnabled()) {
                    timeCell.setText(schedule.formattedTime());
                    timeCell.setTextColor(ContextCompat.getColor(context, R.color.text_primary));
                } else {
                    timeCell.setText("—");
                    timeCell.setTextColor(ContextCompat.getColor(context, R.color.text_tertiary));
                }
                timeCell.setTextSize(11);
                timeCell.setGravity(android.view.Gravity.CENTER);
                timeCell.setBackgroundColor(cellBg);

                GridLayout.LayoutParams cellParams = new GridLayout.LayoutParams();
                cellParams.width = cellWidth;
                cellParams.height = cellHeight;
                cellParams.setMargins(1, 1, 1, 1);
                timeCell.setLayoutParams(cellParams);

                // Click handler für TimePicker
                final ScheduleEntry finalSchedule = schedule;
                final DayOfWeek finalDay = day;
                final MealType finalMealType = mealType;
                timeCell.setOnClickListener(v -> showScheduleTimePicker(finalSchedule, finalDay, finalMealType));

                grid.addView(timeCell);
            }
        }

        scroll.addView(grid);
        return scroll;
    }

    private ScheduleEntry findScheduleEntry(DayOfWeek day, MealType type) {
        for (ScheduleEntry entry : scheduleEntries) {
            if (entry.day() == day && entry.mealType() == type) {
                return entry;
            }
        }
        return null;
    }

    private void showScheduleTimePicker(ScheduleEntry schedule, DayOfWeek day, MealType mealType) {
        if (schedule == null) return;

        // Current time or default
        int hour = schedule.time() != null ? schedule.time().getHour() : 12;
        int minute = schedule.time() != null ? schedule.time().getMinute() : 0;

        // AlertDialog mit Optionen
        new AlertDialog.Builder(context)
            .setTitle(mealType.label + " am " + getDayLabel(day))
            .setItems(new String[]{"Zeit ändern", schedule.isEnabled() ? "Deaktivieren" : "Aktivieren"}, (dialog, which) -> {
                if (which == 0) {
                    // Zeit ändern
                    new TimePickerDialog(context, (view, selectedHour, selectedMinute) -> {
                        LocalTime newTime = LocalTime.of(selectedHour, selectedMinute);
                        manager.updateSchedule(schedule.id(), newTime, true);
                    }, hour, minute, true).show();
                } else {
                    // Toggle enable/disable
                    manager.updateSchedule(schedule.id(), schedule.time(), !schedule.isEnabled());
                }
            })
            .setNegativeButton("Abbrechen", null)
            .show();
    }

    private String getDayLabel(DayOfWeek day) {
        return switch (day) {
            case MONDAY -> "Montag";
            case TUESDAY -> "Dienstag";
            case WEDNESDAY -> "Mittwoch";
            case THURSDAY -> "Donnerstag";
            case FRIDAY -> "Freitag";
            case SATURDAY -> "Samstag";
            case SUNDAY -> "Sonntag";
        };
    }

    private LinearLayout buildWeeklyTargetSection() {
        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);

        List<FoodGroupProgress> progress = manager.provideFoodGroupProgress(LocalDate.now());

        LayoutInflater inflater = LayoutInflater.from(context);

        // 2-Spalten-Grid für kompakte Darstellung
        LinearLayout currentRow = null;
        int itemsInRow = 0;

        for (FoodGroupProgress fg : progress) {
            if (itemsInRow == 0) {
                currentRow = new LinearLayout(context);
                currentRow.setOrientation(LinearLayout.HORIZONTAL);
                container.addView(currentRow);
            }

            LinearLayout item = new LinearLayout(context);
            item.setOrientation(LinearLayout.HORIZONTAL);
            item.setGravity(android.view.Gravity.CENTER_VERTICAL);
            item.setPadding(dp(context, 4), dp(context, 4), dp(context, 4), dp(context, 4));

            LinearLayout.LayoutParams itemParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            item.setLayoutParams(itemParams);

            // Icon
            TextView icon = new TextView(context);
            icon.setText(fg.icon());
            icon.setTextSize(16);
            icon.setPadding(0, 0, dp(context, 4), 0);
            item.addView(icon);

            // Label + Wert
            TextView label = new TextView(context);
            label.setText(fg.label() + ": " + formatGrams(fg.targetGrams()));
            label.setTextSize(11);
            label.setTextColor(ContextCompat.getColor(context, R.color.text_secondary));
            item.addView(label);

            currentRow.addView(item);
            itemsInRow++;

            if (itemsInRow == 2) {
                itemsInRow = 0;
            }
        }

        // Falls ungerade Anzahl, Platzhalter hinzufügen
        if (itemsInRow == 1 && currentRow != null) {
            View spacer = new View(context);
            LinearLayout.LayoutParams spacerParams = new LinearLayout.LayoutParams(0, 0, 1f);
            spacer.setLayoutParams(spacerParams);
            currentRow.addView(spacer);
        }

        return container;
    }

    private String formatGrams(int grams) {
        if (grams >= 1000) {
            return String.format("%.1f kg", grams / 1000.0);
        }
        return grams + "g";
    }

    // ============================================================================
    // RECIPE MODAL
    // ============================================================================

    private void setupModal() {
        // MealType Spinner befüllen
        String[] mealTypes = {"Frühstück", "Mittagessen", "Abendessen", "Snack"};
        ArrayAdapter<String> mealTypeAdapter = new ArrayAdapter<>(
            context, android.R.layout.simple_spinner_item, mealTypes);
        mealTypeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerMealType.setAdapter(mealTypeAdapter);

        // Buttons
        btnAddIngredient.setOnClickListener(v -> addIngredientRow());
        btnCancel.setOnClickListener(v -> hideModal());
        btnSave.setOnClickListener(v -> saveRecipe());

        // Modal-Overlay schließen bei Klick außerhalb
        modalOverlay.setOnClickListener(v -> hideModal());
        // Inneres Modal soll Klicks nicht durchlassen
        View modalCard = modalOverlay.getChildAt(0);
        if (modalCard != null) {
            modalCard.setOnClickListener(v -> { /* Absorb click */ });
        }
    }

    private void showRecipeModal(Recipe recipe) {
        editingRecipe = recipe;
        ingredientsList = manager.provideIngredients();
        ingredientRows.clear();
        ingredientsContainer.removeAllViews();

        if (recipe == null) {
            // Create mode
            modalTitle.setText("Rezept erstellen");
            inputTitle.setText("");
            spinnerMealType.setSelection(1);  // Default: Mittagessen
            inputPrepTime.setText("");
            inputCookTime.setText("");
            inputServings.setText("2");
            inputInstructions.setText("");
            inputTags.setText("");

            // Eine leere Zutat-Zeile
            addIngredientRow();
        } else {
            // Edit mode
            modalTitle.setText("Rezept bearbeiten");
            inputTitle.setText(recipe.title);
            spinnerMealType.setSelection(getMealTypeIndex(recipe.mealType));
            inputPrepTime.setText(recipe.prepTimeMinutes > 0 ? String.valueOf(recipe.prepTimeMinutes) : "");
            inputCookTime.setText(recipe.cookTimeMinutes > 0 ? String.valueOf(recipe.cookTimeMinutes) : "");
            inputServings.setText(String.valueOf(recipe.servings));
            inputInstructions.setText(recipe.instructions != null ? recipe.instructions : "");
            inputTags.setText(recipe.tags != null ? recipe.tags : "");

            // Bestehende Zutaten laden
            if (recipe.ingredients != null) {
                for (Recipe.RecipeIngredient ri : recipe.ingredients) {
                    addIngredientRow(ri);
                }
            }
            if (ingredientRows.isEmpty()) {
                addIngredientRow();
            }
        }

        modalOverlay.setVisibility(View.VISIBLE);
    }

    private void hideModal() {
        modalOverlay.setVisibility(View.GONE);
        editingRecipe = null;
    }

    private int getMealTypeIndex(MealType type) {
        return switch (type) {
            case BREAKFAST -> 0;
            case LUNCH -> 1;
            case DINNER -> 2;
            case SNACK -> 3;
        };
    }

    private MealType getMealTypeFromIndex(int index) {
        return switch (index) {
            case 0 -> MealType.BREAKFAST;
            case 2 -> MealType.DINNER;
            case 3 -> MealType.SNACK;
            default -> MealType.LUNCH;
        };
    }

    // ============================================================================
    // INGREDIENT ROWS
    // ============================================================================

    private void addIngredientRow() {
        addIngredientRow(null);
    }

    private void addIngredientRow(Recipe.RecipeIngredient existing) {
        LayoutInflater inflater = LayoutInflater.from(context);
        View row = inflater.inflate(R.layout.item_ingredient_row, ingredientsContainer, false);

        Spinner spinner = row.findViewById(R.id.spinner_ingredient);
        EditText amountInput = row.findViewById(R.id.input_amount);
        TextView unitLabel = row.findViewById(R.id.unit_label);
        ImageView btnRemove = row.findViewById(R.id.btn_remove);

        // Spinner mit Zutaten befüllen
        List<String> names = new ArrayList<>();
        names.add("-- Zutat wählen --");
        for (IngredientEntry ing : ingredientsList) {
            names.add(ing.name());
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
            context, android.R.layout.simple_spinner_item, names);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);

        // Spinner-Auswahl → Unit-Label aktualisieren
        spinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                if (position > 0) {
                    IngredientEntry selected = ingredientsList.get(position - 1);
                    unitLabel.setText(selected.unit());
                } else {
                    unitLabel.setText("");
                }
            }
            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });

        // Wenn bestehendes Ingredient, vorausfüllen
        if (existing != null) {
            for (int i = 0; i < ingredientsList.size(); i++) {
                if (ingredientsList.get(i).id().equals(existing.ingredientId())) {
                    spinner.setSelection(i + 1);
                    break;
                }
            }
            amountInput.setText(String.valueOf((int) existing.amount()));
        }

        // Remove-Button
        btnRemove.setOnClickListener(v -> {
            ingredientsContainer.removeView(row);
            ingredientRows.removeIf(r -> r.view == row);
        });

        ingredientsContainer.addView(row);

        // State speichern
        Long ingId = existing != null ? existing.ingredientId() : null;
        String ingName = existing != null ? existing.ingredientName() : null;
        ingredientRows.add(new IngredientRowState(row, spinner, amountInput, unitLabel, ingId, ingName));
    }

    // ============================================================================
    // SAVE RECIPE
    // ============================================================================

    private void saveRecipe() {
        String title = inputTitle.getText().toString().trim();
        if (title.isEmpty()) {
            inputTitle.setError("Titel erforderlich");
            return;
        }

        MealType mealType = getMealTypeFromIndex(spinnerMealType.getSelectedItemPosition());

        int prepTime = 0;
        try {
            String prepStr = inputPrepTime.getText().toString().trim();
            if (!prepStr.isEmpty()) prepTime = Integer.parseInt(prepStr);
        } catch (NumberFormatException ignored) {}

        int cookTime = 0;
        try {
            String cookStr = inputCookTime.getText().toString().trim();
            if (!cookStr.isEmpty()) cookTime = Integer.parseInt(cookStr);
        } catch (NumberFormatException ignored) {}

        int servings = 2;
        try {
            String servStr = inputServings.getText().toString().trim();
            if (!servStr.isEmpty()) servings = Integer.parseInt(servStr);
        } catch (NumberFormatException ignored) {}

        String instructions = inputInstructions.getText().toString().trim();
        String tags = inputTags.getText().toString().trim();

        Recipe.Builder builder = new Recipe.Builder(title, mealType)
            .servings(servings)
            .prepTime(prepTime)
            .cookTime(cookTime);

        if (!instructions.isEmpty()) builder.instructions(instructions);
        if (!tags.isEmpty()) builder.tags(tags);

        // Zutaten hinzufügen
        for (IngredientRowState row : ingredientRows) {
            int position = row.spinner.getSelectedItemPosition();
            if (position > 0) {
                IngredientEntry ing = ingredientsList.get(position - 1);
                double amount = 100;  // Default
                try {
                    String amtStr = row.amountInput.getText().toString().trim();
                    if (!amtStr.isEmpty()) amount = Double.parseDouble(amtStr);
                } catch (NumberFormatException ignored) {}

                builder.addIngredient(ing.id(), ing.name(), amount, ing.unit());
            }
        }

        Recipe recipe = builder.build();

        if (editingRecipe == null) {
            manager.createRecipe(recipe);
        } else {
            recipe.id = editingRecipe.id;
            recipe.isFavorite = editingRecipe.isFavorite;
            recipe.lastUsed = editingRecipe.lastUsed;
            recipe.usageCount = editingRecipe.usageCount;
            manager.updateRecipe(recipe);
        }

        hideModal();
    }

    // ============================================================================
    // MEAL PLAN MODAL
    // ============================================================================

    private void setupMealPlanModal() {
        // Buttons
        btnMealPlanCancel.setOnClickListener(v -> hideMealPlanModal());
        btnMealPlanSave.setOnClickListener(v -> saveMealPlan());
        btnMealPlanDelete.setOnClickListener(v -> deleteMealPlan());

        // Modal-Overlay schließen bei Klick außerhalb
        modalMealPlanOverlay.setOnClickListener(v -> hideMealPlanModal());
        // Inneres Modal soll Klicks nicht durchlassen
        View modalCard = modalMealPlanOverlay.getChildAt(0);
        if (modalCard != null) {
            modalCard.setOnClickListener(v -> { /* Absorb click */ });
        }

        // Spinner Listener für Rezept-Info
        spinnerMealPlanRecipe.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                if (position > 0 && position <= recipesList.size()) {
                    RecipeEntry recipe = recipesList.get(position - 1);
                    mealPlanRecipeTime.setText(recipe.totalTime() + " min");
                    mealPlanRecipeCalories.setText(recipe.calories() + " kcal");
                    mealPlanRecipeInfo.setVisibility(View.VISIBLE);
                } else {
                    mealPlanRecipeInfo.setVisibility(View.GONE);
                }
            }
            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
    }

    private void showMealPlanModal(LocalDate date, MealType mealType, MealPlan existing) {
        editingMealPlan = existing;
        modalMealDate = date;
        modalMealType = mealType;

        // Load recipes for this meal type
        MealType recipeType = switch (mealType) {
            case BREAKFAST -> MealType.BREAKFAST;
            case LUNCH -> MealType.LUNCH;
            case DINNER -> MealType.DINNER;
            case SNACK -> MealType.SNACK;
        };
        recipesList = manager.provideRecipes(recipeType);

        // Header info
        String mealLabel = switch (mealType) {
            case BREAKFAST -> "Frühstück";
            case LUNCH -> "Mittagessen";
            case DINNER -> "Abendessen";
            case SNACK -> "Snack";
        };
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("EEEE, dd.MM.", Locale.GERMAN);
        mealPlanDateInfo.setText(fmt.format(date) + " • " + mealLabel);

        // Recipe Spinner
        List<String> recipeNames = new ArrayList<>();
        recipeNames.add("— Kein Rezept —");
        for (RecipeEntry r : recipesList) {
            recipeNames.add(r.title());
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
            context, android.R.layout.simple_spinner_item, recipeNames);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerMealPlanRecipe.setAdapter(adapter);

        if (existing != null) {
            // Edit mode
            modalMealPlanTitle.setText("Mahlzeit bearbeiten");
            btnMealPlanDelete.setVisibility(View.VISIBLE);
            inputMealPlanServings.setText(String.valueOf(existing.plannedServings));

            // Select recipe in spinner
            for (int i = 0; i < recipesList.size(); i++) {
                if (recipesList.get(i).id().equals(existing.recipeId)) {
                    spinnerMealPlanRecipe.setSelection(i + 1);
                    break;
                }
            }
        } else {
            // Create mode
            modalMealPlanTitle.setText("Mahlzeit planen");
            btnMealPlanDelete.setVisibility(View.GONE);
            inputMealPlanServings.setText("2");
            spinnerMealPlanRecipe.setSelection(0);
            mealPlanRecipeInfo.setVisibility(View.GONE);
        }

        modalMealPlanOverlay.setVisibility(View.VISIBLE);
    }

    private void hideMealPlanModal() {
        modalMealPlanOverlay.setVisibility(View.GONE);
        editingMealPlan = null;
        modalMealDate = null;
        modalMealType = null;
    }

    private void saveMealPlan() {
        int recipePosition = spinnerMealPlanRecipe.getSelectedItemPosition();

        if (recipePosition == 0) {
            // No recipe selected - delete if editing
            if (editingMealPlan != null) {
                deleteMealPlan();
            } else {
                hideMealPlanModal();
            }
            return;
        }

        RecipeEntry selectedRecipe = recipesList.get(recipePosition - 1);

        int servings = 2;
        try {
            String servStr = inputMealPlanServings.getText().toString().trim();
            if (!servStr.isEmpty()) servings = Integer.parseInt(servStr);
        } catch (NumberFormatException ignored) {}

        if (editingMealPlan != null) {
            // Update existing
            editingMealPlan.recipeId = selectedRecipe.id();
            editingMealPlan.plannedServings = servings;
            manager.updateMealPlan(editingMealPlan);
        } else {
            // Create new
            MealPlan newPlan = new MealPlan.Builder(modalMealDate, modalMealType, selectedRecipe.id())
                .servings(servings)
                .build();
            manager.createMealPlan(newPlan);
        }

        hideMealPlanModal();
    }

    private void deleteMealPlan() {
        if (editingMealPlan != null && editingMealPlan.id != null) {
            manager.deleteMealPlan(editingMealPlan.id);
        }
        hideMealPlanModal();
    }

    // ============================================================================
    // MEMBER MODAL
    // ============================================================================

    private void setupMemberModal() {
        // Gender Spinner befüllen
        String[] genders = {"Männlich", "Weiblich", "Divers"};
        ArrayAdapter<String> genderAdapter = new ArrayAdapter<>(
            context, android.R.layout.simple_spinner_item, genders);
        genderAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerMemberGender.setAdapter(genderAdapter);

        // Activity Spinner befüllen
        String[] activities = {"Sitzend", "Leicht aktiv", "Moderat aktiv", "Aktiv", "Sehr aktiv"};
        ArrayAdapter<String> activityAdapter = new ArrayAdapter<>(
            context, android.R.layout.simple_spinner_item, activities);
        activityAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerMemberActivity.setAdapter(activityAdapter);

        // Buttons
        btnMemberCancel.setOnClickListener(v -> hideMemberModal());
        btnMemberSave.setOnClickListener(v -> saveMember());
        btnMemberDelete.setOnClickListener(v -> deleteMember());

        // Modal-Overlay schließen bei Klick außerhalb
        modalMemberOverlay.setOnClickListener(v -> hideMemberModal());
        // Inneres Modal soll Klicks nicht durchlassen
        View modalCard = modalMemberOverlay.getChildAt(0);
        if (modalCard != null) {
            modalCard.setOnClickListener(v -> { /* Absorb click */ });
        }

        // TDEE live berechnen bei Eingabe-Änderungen
        android.text.TextWatcher tdeeWatcher = new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(android.text.Editable s) { updateTdeePreview(); }
        };
        inputMemberBirthYear.addTextChangedListener(tdeeWatcher);
        inputMemberHeight.addTextChangedListener(tdeeWatcher);
        inputMemberWeight.addTextChangedListener(tdeeWatcher);
        spinnerMemberGender.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> p, View v, int pos, long id) { updateTdeePreview(); }
            @Override public void onNothingSelected(android.widget.AdapterView<?> p) {}
        });
        spinnerMemberActivity.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> p, View v, int pos, long id) { updateTdeePreview(); }
            @Override public void onNothingSelected(android.widget.AdapterView<?> p) {}
        });
    }

    private void showMemberModal(Long memberId) {
        if (memberId != null) {
            editingMember = manager.getMember(memberId);
        } else {
            editingMember = null;
        }

        if (editingMember == null) {
            // Create mode
            modalMemberTitle.setText("Mitglied hinzufügen");
            inputMemberName.setText("");
            inputMemberBirthYear.setText("1990");
            spinnerMemberGender.setSelection(0);
            inputMemberHeight.setText("175");
            inputMemberWeight.setText("70");
            spinnerMemberActivity.setSelection(1);  // Leicht aktiv
            btnMemberDelete.setVisibility(View.GONE);
        } else {
            // Edit mode
            modalMemberTitle.setText("Mitglied bearbeiten");
            inputMemberName.setText(editingMember.name);
            inputMemberBirthYear.setText(String.valueOf(editingMember.birthYear));
            spinnerMemberGender.setSelection(getGenderIndex(editingMember.gender));
            inputMemberHeight.setText(String.valueOf(editingMember.heightCm));
            inputMemberWeight.setText(String.valueOf(editingMember.weightKg));
            spinnerMemberActivity.setSelection(getActivityIndex(editingMember.activityLevel));
            btnMemberDelete.setVisibility(View.VISIBLE);
        }

        updateTdeePreview();
        modalMemberOverlay.setVisibility(View.VISIBLE);
    }

    private void hideMemberModal() {
        modalMemberOverlay.setVisibility(View.GONE);
        editingMember = null;
    }

    private void updateTdeePreview() {
        try {
            int birthYear = Integer.parseInt(inputMemberBirthYear.getText().toString().trim());
            int height = Integer.parseInt(inputMemberHeight.getText().toString().trim());
            int weight = Integer.parseInt(inputMemberWeight.getText().toString().trim());

            HouseholdMember.Gender gender = getGenderFromIndex(spinnerMemberGender.getSelectedItemPosition());
            HouseholdMember.ActivityLevel activity = getActivityFromIndex(spinnerMemberActivity.getSelectedItemPosition());

            // Temporäres Member-Objekt für Berechnung
            HouseholdMember temp = new HouseholdMember.Builder("temp")
                .birthYear(birthYear)
                .gender(gender)
                .heightCm(height)
                .weightKg(weight)
                .activityLevel(activity)
                .build();

            int tdee = temp.calculateTDEE();
            memberTdeePreview.setText("Berechneter Tagesbedarf: " + String.format("%,d", tdee).replace(",", ".") + " kcal");
        } catch (NumberFormatException e) {
            memberTdeePreview.setText("Berechneter Tagesbedarf: — kcal");
        }
    }

    private void saveMember() {
        String name = inputMemberName.getText().toString().trim();
        if (name.isEmpty()) {
            inputMemberName.setError("Name erforderlich");
            return;
        }

        int birthYear = 1990;
        try {
            birthYear = Integer.parseInt(inputMemberBirthYear.getText().toString().trim());
        } catch (NumberFormatException ignored) {}

        int height = 175;
        try {
            height = Integer.parseInt(inputMemberHeight.getText().toString().trim());
        } catch (NumberFormatException ignored) {}

        int weight = 70;
        try {
            weight = Integer.parseInt(inputMemberWeight.getText().toString().trim());
        } catch (NumberFormatException ignored) {}

        HouseholdMember.Gender gender = getGenderFromIndex(spinnerMemberGender.getSelectedItemPosition());
        HouseholdMember.ActivityLevel activity = getActivityFromIndex(spinnerMemberActivity.getSelectedItemPosition());

        HouseholdMember member = new HouseholdMember.Builder(name)
            .birthYear(birthYear)
            .gender(gender)
            .heightCm(height)
            .weightKg(weight)
            .activityLevel(activity)
            .build();

        if (editingMember != null) {
            member.id = editingMember.id;
            member.isActive = editingMember.isActive;
            manager.updateMember(member);
        } else {
            manager.createMember(member);
        }

        hideMemberModal();
    }

    private void deleteMember() {
        if (editingMember != null && editingMember.id != null) {
            new AlertDialog.Builder(context)
                .setTitle("Mitglied löschen")
                .setMessage("Soll \"" + editingMember.name + "\" wirklich gelöscht werden?")
                .setPositiveButton("Löschen", (dialog, which) -> {
                    manager.deleteMember(editingMember.id);
                    hideMemberModal();
                })
                .setNegativeButton("Abbrechen", null)
                .show();
        }
    }

    private int getGenderIndex(HouseholdMember.Gender gender) {
        return switch (gender) {
            case MALE -> 0;
            case FEMALE -> 1;
            case OTHER -> 2;
        };
    }

    private HouseholdMember.Gender getGenderFromIndex(int index) {
        return switch (index) {
            case 1 -> HouseholdMember.Gender.FEMALE;
            case 2 -> HouseholdMember.Gender.OTHER;
            default -> HouseholdMember.Gender.MALE;
        };
    }

    private int getActivityIndex(HouseholdMember.ActivityLevel level) {
        return switch (level) {
            case SEDENTARY -> 0;
            case LIGHT -> 1;
            case MODERATE -> 2;
            case ACTIVE -> 3;
            case VERY_ACTIVE -> 4;
        };
    }

    private HouseholdMember.ActivityLevel getActivityFromIndex(int index) {
        return switch (index) {
            case 0 -> HouseholdMember.ActivityLevel.SEDENTARY;
            case 2 -> HouseholdMember.ActivityLevel.MODERATE;
            case 3 -> HouseholdMember.ActivityLevel.ACTIVE;
            case 4 -> HouseholdMember.ActivityLevel.VERY_ACTIVE;
            default -> HouseholdMember.ActivityLevel.LIGHT;
        };
    }

    // ============================================================================
    // PANTRY MODAL
    // ============================================================================

    private void setupPantryModal() {
        // Location Spinner befüllen
        String[] locations = {"Kühlschrank", "Gefrierfach", "Vorratskammer"};
        ArrayAdapter<String> locAdapter = new ArrayAdapter<>(
            context, android.R.layout.simple_spinner_item, locations);
        locAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerPantryLocation.setAdapter(locAdapter);

        // Buttons
        btnPantryCancel.setOnClickListener(v -> hidePantryModal());
        btnPantrySave.setOnClickListener(v -> savePantryItem());
        btnPantryDelete.setOnClickListener(v -> deletePantryItem());

        // Modal-Overlay schließen bei Klick außerhalb
        modalPantryOverlay.setOnClickListener(v -> hidePantryModal());
        View modalCard = modalPantryOverlay.getChildAt(0);
        if (modalCard != null) {
            modalCard.setOnClickListener(v -> { /* Absorb click */ });
        }

        // Zutat-Spinner → Unit-Label
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

        // Ablaufdatum → DatePicker
        inputPantryExpiry.setOnClickListener(v -> showPantryExpiryDatePicker());
    }

    private void showPantryModal(Long itemId) {
        ingredientsList = manager.provideIngredients();

        // Zutat-Spinner befüllen
        List<String> names = new ArrayList<>();
        names.add("-- Zutat wählen --");
        for (IngredientEntry ing : ingredientsList) {
            names.add(ing.name());
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
            context, android.R.layout.simple_spinner_item, names);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerPantryIngredient.setAdapter(adapter);

        if (itemId != null) {
            // Edit mode
            editingPantryItem = manager.getPantryItem(itemId);
            modalPantryTitle.setText("Vorrat bearbeiten");
            btnPantryDelete.setVisibility(View.VISIBLE);

            // Felder befüllen
            for (int i = 0; i < ingredientsList.size(); i++) {
                if (ingredientsList.get(i).id().equals(editingPantryItem.ingredientId)) {
                    spinnerPantryIngredient.setSelection(i + 1);
                    break;
                }
            }
            inputPantryAmount.setText(String.valueOf((int) editingPantryItem.amount));
            spinnerPantryLocation.setSelection(editingPantryItem.location.ordinal());
            selectedExpiryDate = editingPantryItem.expiryDate;
            updatePantryExpiryDisplay();
        } else {
            // Create mode
            editingPantryItem = null;
            modalPantryTitle.setText("Vorrat hinzufügen");
            btnPantryDelete.setVisibility(View.GONE);

            spinnerPantryIngredient.setSelection(0);
            inputPantryAmount.setText("");
            spinnerPantryLocation.setSelection(0);
            selectedExpiryDate = LocalDate.now().plusDays(7);  // Default: 1 Woche
            updatePantryExpiryDisplay();
        }

        modalPantryOverlay.setVisibility(View.VISIBLE);
    }

    private void hidePantryModal() {
        modalPantryOverlay.setVisibility(View.GONE);
        editingPantryItem = null;
        selectedExpiryDate = null;
    }

    private void updatePantryExpiryDisplay() {
        if (selectedExpiryDate != null) {
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd.MM.yyyy");
            inputPantryExpiry.setText(fmt.format(selectedExpiryDate));
        } else {
            inputPantryExpiry.setText("—");
        }
    }

    private void showPantryExpiryDatePicker() {
        LocalDate initial = selectedExpiryDate != null ? selectedExpiryDate : LocalDate.now();
        new DatePickerDialog(context, (view, year, month, day) -> {
            selectedExpiryDate = LocalDate.of(year, month + 1, day);
            updatePantryExpiryDisplay();
        }, initial.getYear(), initial.getMonthValue() - 1, initial.getDayOfMonth()).show();
    }

    private void savePantryItem() {
        int ingPos = spinnerPantryIngredient.getSelectedItemPosition();
        if (ingPos == 0) {
            // Keine Zutat gewählt
            return;
        }

        IngredientEntry ing = ingredientsList.get(ingPos - 1);

        double amount = 100;
        try {
            String amtStr = inputPantryAmount.getText().toString().trim();
            if (!amtStr.isEmpty()) {
                amount = Double.parseDouble(amtStr);
            }
        } catch (NumberFormatException ignored) {}

        PantryItem.StorageLocation location = PantryItem.StorageLocation.values()
            [spinnerPantryLocation.getSelectedItemPosition()];

        if (editingPantryItem != null) {
            // Update
            editingPantryItem.ingredientId = ing.id();
            editingPantryItem.ingredientName = ing.name();
            editingPantryItem.amount = amount;
            editingPantryItem.unit = ing.unit();
            editingPantryItem.location = location;
            editingPantryItem.expiryDate = selectedExpiryDate;
            manager.updatePantryItem(editingPantryItem);
        } else {
            // Create
            manager.addToPantry(ing.id(), amount, ing.unit(), location, selectedExpiryDate);
        }

        hidePantryModal();
    }

    private void deletePantryItem() {
        if (editingPantryItem != null && editingPantryItem.id != null) {
            manager.deletePantryItem(editingPantryItem.id);
        }
        hidePantryModal();
    }
}

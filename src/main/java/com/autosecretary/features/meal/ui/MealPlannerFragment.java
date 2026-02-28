package com.autosecretary.features.meal.ui;

import android.app.AlertDialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.autosecretary.R;
import com.autosecretary.app.AutoSecretaryApplication;
import com.autosecretary.features.meal.application.MealPlannerPresenter;
import com.autosecretary.features.meal.domain.MealPlan;
import com.autosecretary.features.meal.domain.MealType;
import com.autosecretary.features.meal.domain.PantryItem;
import com.autosecretary.features.meal.domain.Recipe;
import com.autosecretary.features.meal.domain.ShoppingListItem;

import android.widget.Toast;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Meal planner UI fragment — primary entry point for the meal feature.
 *
 * <p>Manages a three-tab interface:
 * <ul>
 *   <li><strong>Week Plan:</strong> view and manage meal plans for the coming week (add, toggle completion)
 *   <li><strong>Recipes:</strong> browse available recipes and view details
 *   <li><strong>Stock & Shopping:</strong> manage pantry inventory and shopping list
 * </ul>
 *
 * <p><strong>Architecture:</strong> Uses a presenter pattern ({@link MealPlannerPresenter}) to delegate
 * all business logic to the application layer. The fragment is purely presentational: it inflates layouts,
 * builds dialogs, and calls presenter methods on user actions (dialog commits, button clicks).
 *
 * <p><strong>Data flow:</strong> Presenter is injected via constructor (or fetched from AppCompositionRoot
 * if the no-arg constructor is used by Android during recreation). User actions trigger presenter calls,
 * which return updated data. The fragment then re-renders affected views (meals, recipes, or stock).
 *
 * <p><strong>Dialog pattern:</strong> All three creation dialogs (plan, shopping need, pantry item) follow
 * the same pattern: inflate layout → build AlertDialog → on positive button, extract input fields → call
 * presenter → re-render. See {@link #showPlanDialog()}, {@link #showNeedDialog()}, {@link #showPantryDialog()}.
 */
public class MealPlannerFragment extends Fragment {

    // See features/meal/application/MealPlannerPresenter for business logic (fetch data, plan/delete meals, etc.)
    private MealPlannerPresenter presenter;

    private View weekScreen;
    private View recipesScreen;
    private View stockScreen;
    private LinearLayout weekList;
    private LinearLayout recipeList;
    private TextView recipeDetail;
    private LinearLayout pantryList;
    private LinearLayout shoppingList;

    /**
     * Constructor for manual dependency injection (used by AppCompositionRoot).
     * The no-arg constructor below is required by Android for fragment recreation during config changes.
     */
    public MealPlannerFragment(MealPlannerPresenter presenter) {
        this.presenter = presenter;
    }

    /**
     * No-arg constructor required by Android framework.
     * When used, presenter will be fetched from AppCompositionRoot in onViewCreated().
     */
    public MealPlannerFragment() {
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.meal_overview_fragment, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (presenter == null) {
            presenter = AutoSecretaryApplication.from(requireContext())
                    .getAppCompositionRoot()
                    .getMealPlannerPresenter();
        }

        weekScreen = view.findViewById(R.id.MealWeekScreen);
        recipesScreen = view.findViewById(R.id.MealRecipeScreen);
        stockScreen = view.findViewById(R.id.MealStockScreen);
        weekList = view.findViewById(R.id.MealWeekList);
        recipeList = view.findViewById(R.id.MealRecipeList);
        recipeDetail = view.findViewById(R.id.MealRecipeDetail);
        pantryList = view.findViewById(R.id.MealPantryList);
        shoppingList = view.findViewById(R.id.MealShoppingList);

        Button showWeek = view.findViewById(R.id.MealTabWeek);
        Button showRecipes = view.findViewById(R.id.MealTabRecipes);
        Button showStock = view.findViewById(R.id.MealTabStock);
        Button addMealPlan = view.findViewById(R.id.MealAddPlan);
        Button addNeed = view.findViewById(R.id.MealAddNeed);
        Button addPantry = view.findViewById(R.id.MealAddPantry);

        showWeek.setOnClickListener(v -> switchScreen(weekScreen));
        showRecipes.setOnClickListener(v -> switchScreen(recipesScreen));
        showStock.setOnClickListener(v -> switchScreen(stockScreen));
        addMealPlan.setOnClickListener(v -> showPlanDialog());
        addNeed.setOnClickListener(v -> showNeedDialog());
        addPantry.setOnClickListener(v -> showPantryDialog());

        switchScreen(weekScreen);
        renderAll();
    }

    /**
     * Switch active tab by toggling View visibility. Uses visibility toggling (not fragment replacement)
     * to preserve all state (scroll position, expanded items, etc.) when switching tabs.
     */
    private void switchScreen(View visible) {
        weekScreen.setVisibility(visible == weekScreen ? View.VISIBLE : View.GONE);
        recipesScreen.setVisibility(visible == recipesScreen ? View.VISIBLE : View.GONE);
        stockScreen.setVisibility(visible == stockScreen ? View.VISIBLE : View.GONE);
    }

    /**
     * Render all tabs from presenter data. Called on initial view creation and after user actions
     * (dialog completion, button clicks) to keep the UI in sync with presenter state.
     * For simplicity, this re-renders the entire view; consider caching if performance becomes an issue.
     */
    private void renderAll() {
        renderMealPlans();
        renderRecipes();
        renderStock();
    }

    /**
     * Rebuild the meal plan list from presenter data.
     * Called on init and after user actions (add plan, toggle completion).
     */
    private void renderMealPlans() {
        weekList.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (MealPlan plan : presenter.getWeekMealPlans()) {
            View planRow = inflater.inflate(R.layout.meal_plan_row_item, weekList, false);
            TextView title = planRow.findViewById(R.id.MealPlanRowTitle);
            TextView subtitle = planRow.findViewById(R.id.MealPlanRowSubtitle);
            Button done = planRow.findViewById(R.id.MealPlanRowDone);

            title.setText(plan.recipeTitle + " · " + plan.mealType.label);
            subtitle.setText(plan.date + " · " + plan.plannedServings + " Portionen");
            done.setText(plan.isCompleted
                    ? getString(R.string.meal_mark_open)
                    : getString(R.string.meal_mark_done));
            if (plan.id != null) {
                done.setOnClickListener(v -> {
                    presenter.toggleMealCompleted(plan.id);
                    renderMealPlans();
                });
            }
            weekList.addView(planRow);
        }
    }

    /**
     * Rebuild the recipe list and show details for the first recipe.
     * Each recipe appears as a button; clicking it updates the detail pane on the right.
     */
    private void renderRecipes() {
        recipeList.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        List<Recipe> recipes = presenter.getRecipes();
        for (Recipe recipe : recipes) {
            Button recipeButton = (Button) inflater.inflate(R.layout.meal_recipe_row_item, recipeList, false);
            recipeButton.setText(recipe.title);
            recipeButton.setOnClickListener(v -> recipeDetail.setText(buildRecipeDetails(recipe)));
            recipeList.addView(recipeButton);
        }
        if (!recipes.isEmpty()) {
            recipeDetail.setText(buildRecipeDetails(recipes.get(0)));
        }
    }

    /**
     * Format recipe title, description, and instructions for display in the detail pane.
     */
    private String buildRecipeDetails(Recipe recipe) {
        return recipe.title + "\n\n" +
                (TextUtils.isEmpty(recipe.description) ? "" : recipe.description + "\n\n") +
                (TextUtils.isEmpty(recipe.instructions) ? "" : recipe.instructions);
    }

    /**
     * Rebuild the pantry (inventory) and shopping list views from presenter data.
     * Called on init and after user actions (add pantry item, add shopping need).
     */
    private void renderStock() {
        pantryList.removeAllViews();
        shoppingList.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(requireContext());

        for (PantryItem item : presenter.getPantryItems()) {
            TextView pantryRow = (TextView) inflater.inflate(R.layout.meal_text_row_item, pantryList, false);
            pantryRow.setText(item.ingredientName + " · " + item.getFormattedAmount() + " · " + item.getExpiryInfo());
            pantryList.addView(pantryRow);
        }

        for (ShoppingListItem item : presenter.getShoppingListItemsForToday()) {
            TextView shoppingRow = (TextView) inflater.inflate(R.layout.meal_text_row_item, shoppingList, false);
            shoppingRow.setText(item.ingredientName + " · " + item.getFormattedAmount());
            shoppingList.addView(shoppingRow);
        }
    }

    /**
     * Returns trimmed text from an EditText, or shows an error toast and returns null if empty.
     * Consolidates the repeated "check empty → toast → abort" pattern across all dialogs.
     */
    @Nullable
    private String requireNonEmpty(EditText field, String fieldLabel) {
        String value = field.getText().toString().trim();
        if (value.isEmpty()) {
            Toast.makeText(requireContext(), "Bitte geben Sie " + fieldLabel + " ein.", Toast.LENGTH_SHORT).show();
            return null;
        }
        return value;
    }

    /**
     * Safely parse a date string, showing error toast on failure.
     * Returns null if parsing fails; caller should check before using.
     */
    @Nullable
    private LocalDate safeParse(String dateString) {
        try {
            return LocalDate.parse(dateString);
        } catch (DateTimeParseException e) {
            Toast.makeText(requireContext(), "Ungültiges Datum. Format: YYYY-MM-DD", Toast.LENGTH_SHORT).show();
            return null;
        }
    }

    /**
     * Safely parse an integer from a string, showing error toast on failure.
     * Returns null if parsing fails; caller should check before using.
     */
    @Nullable
    private Integer safeParseInt(String value, String fieldName) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            Toast.makeText(requireContext(), fieldName + " muss eine Zahl sein.", Toast.LENGTH_SHORT).show();
            return null;
        }
    }

    /**
     * Safely parse a double from a string, showing error toast on failure.
     * Returns null if parsing fails; caller should check before using.
     */
    @Nullable
    private Double safeParseDouble(String value, String fieldName) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            Toast.makeText(requireContext(), fieldName + " muss eine Zahl sein.", Toast.LENGTH_SHORT).show();
            return null;
        }
    }

    /**
     * Show dialog to create a new meal plan.
     *
     * <p>All three creation dialogs ({@link #showPlanDialog()}, {@link #showNeedDialog()},
     * {@link #showPantryDialog()}) follow the same pattern:
     * <ol>
     *   <li>Inflate dialog layout and bind fields to UI elements
     *   <li>Populate spinners and set default values
     *   <li>Build AlertDialog with positive (commit) and negative (cancel) buttons
     *   <li>On positive button, extract input values and call presenter
     *   <li>After presenter call, re-render affected view
     * </ol>
     *
     * <p>When adding new dialogs, follow this pattern for consistency.
     */
    private void showPlanDialog() {
        View planDialogContent = LayoutInflater.from(requireContext()).inflate(R.layout.meal_plan_create_dialog, null);
        Spinner recipeSpinner = planDialogContent.findViewById(R.id.MealDialogRecipe);
        EditText dateField = planDialogContent.findViewById(R.id.MealDialogDate);
        Spinner typeSpinner = planDialogContent.findViewById(R.id.MealDialogType);
        EditText servingsField = planDialogContent.findViewById(R.id.MealDialogServings);

        List<Recipe> recipes = presenter.getRecipes();
        if (recipes == null || recipes.isEmpty()) {
            Toast.makeText(requireContext(), "Keine Rezepte verfügbar. Bitte erstellen Sie zuerst ein Rezept.", Toast.LENGTH_SHORT).show();
            return;
        }

        List<String> recipeTitles = new ArrayList<>();
        for (Recipe recipe : recipes) {
            recipeTitles.add(recipe.title);
        }
        recipeSpinner.setAdapter(new ArrayAdapter<>(
                requireContext(), android.R.layout.simple_spinner_dropdown_item, recipeTitles));

        dateField.setText(LocalDate.now().toString());
        typeSpinner.setAdapter(new ArrayAdapter<>(
                requireContext(), android.R.layout.simple_spinner_dropdown_item, MealType.values()));
        servingsField.setText("2");

        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.meal_plan_dialog_title)
                .setView(planDialogContent)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.meal_plan_dialog_save, (d, w) -> {
                    int recipeIndex = recipeSpinner.getSelectedItemPosition();
                    if (recipeIndex < 0 || recipeIndex >= recipes.size()) return;
                    String dateStr = requireNonEmpty(dateField, "ein Datum");
                    if (dateStr == null) return;
                    LocalDate parsedDate = safeParse(dateStr);
                    if (parsedDate == null) return;
                    String servingsStr = requireNonEmpty(servingsField, "die Anzahl der Portionen");
                    if (servingsStr == null) return;
                    Integer servings = safeParseInt(servingsStr, "Portionen");
                    if (servings == null || servings <= 0) return;
                    MealType mealType = (MealType) typeSpinner.getSelectedItem();
                    Long recipeId = recipes.get(recipeIndex).id;
                    if (recipeId == null) return;
                    presenter.planRecipe(recipeId, parsedDate, mealType, servings);
                    renderMealPlans();
                })
                .show();
    }

    /**
     * Show dialog to create a shopping list item. Follows the shared dialog pattern documented in {@link #showPlanDialog()}.
     */
    private void showNeedDialog() {
        View needDialogContent = LayoutInflater.from(requireContext()).inflate(R.layout.meal_need_create_dialog, null);
        EditText nameField = needDialogContent.findViewById(R.id.MealNeedName);
        EditText amountField = needDialogContent.findViewById(R.id.MealNeedAmount);
        EditText unitField = needDialogContent.findViewById(R.id.MealNeedUnit);

        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.meal_need_dialog_title)
                .setView(needDialogContent)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.meal_need_dialog_save, (d, w) -> {
                    String ingredientName = requireNonEmpty(nameField, "einen Zutatennamen");
                    if (ingredientName == null) return;
                    String amountStr = requireNonEmpty(amountField, "eine Menge");
                    if (amountStr == null) return;
                    Double amount = safeParseDouble(amountStr, "Menge");
                    if (amount == null || amount < 0) return;
                    String unitStr = requireNonEmpty(unitField, "eine Einheit");
                    if (unitStr == null) return;
                    presenter.createShoppingItemFromNeed(ingredientName, amount, unitStr);
                    renderStock();
                })
                .show();
    }

    /**
     * Show dialog to create a pantry (inventory) item. Follows the shared dialog pattern documented in {@link #showPlanDialog()}.
     */
    private void showPantryDialog() {
        View pantryDialogContent = LayoutInflater.from(requireContext()).inflate(R.layout.meal_pantry_create_dialog, null);
        EditText nameField = pantryDialogContent.findViewById(R.id.MealPantryName);
        EditText amountField = pantryDialogContent.findViewById(R.id.MealPantryAmount);
        EditText unitField = pantryDialogContent.findViewById(R.id.MealPantryUnit);
        EditText shelfLifeDaysField = pantryDialogContent.findViewById(R.id.MealPantryShelfLifeDays);
        Spinner locationSpinner = pantryDialogContent.findViewById(R.id.MealPantryLocation);
        locationSpinner.setAdapter(new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_dropdown_item,
                PantryItem.StorageLocation.values()));

        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.meal_pantry_dialog_title)
                .setView(pantryDialogContent)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.meal_pantry_dialog_save, (d, w) -> {
                    String ingredientName = requireNonEmpty(nameField, "einen Zutatennamen");
                    if (ingredientName == null) return;
                    String amountStr = requireNonEmpty(amountField, "eine Menge");
                    if (amountStr == null) return;
                    Double amount = safeParseDouble(amountStr, "Menge");
                    if (amount == null || amount < 0) return;
                    String unitStr = requireNonEmpty(unitField, "eine Einheit");
                    if (unitStr == null) return;
                    String shelfLifeStr = requireNonEmpty(shelfLifeDaysField, "die Haltbarkeitsdauer");
                    if (shelfLifeStr == null) return;
                    Integer shelfLifeDays = safeParseInt(shelfLifeStr, "Haltbarkeitsdauer");
                    if (shelfLifeDays == null || shelfLifeDays < 0) return;
                    presenter.createPantryItem(
                            ingredientName,
                            amount,
                            unitStr,
                            (PantryItem.StorageLocation) locationSpinner.getSelectedItem(),
                            shelfLifeDays
                    );
                    renderStock();
                })
                .show();
    }
}

package com.autosecretary.features.meal.ui;

import android.app.AlertDialog;
import android.content.DialogInterface;
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

import com.google.android.material.button.MaterialButtonToggleGroup;

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
import java.util.List;
import java.util.stream.Collectors;

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

    // Default values for form fields
    private static final int DEFAULT_SPINNER_SELECTION = 0;
    private static final String DEFAULT_SERVINGS = "2";

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
    private LayoutInflater layoutInflater;

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

        layoutInflater = LayoutInflater.from(requireContext());
        weekScreen = view.findViewById(R.id.MealWeekScreen);
        recipesScreen = view.findViewById(R.id.MealRecipeScreen);
        stockScreen = view.findViewById(R.id.MealStockScreen);
        weekList = view.findViewById(R.id.MealWeekList);
        recipeList = view.findViewById(R.id.MealRecipeList);
        recipeDetail = view.findViewById(R.id.MealRecipeDetail);
        pantryList = view.findViewById(R.id.MealPantryList);
        shoppingList = view.findViewById(R.id.MealShoppingList);

        MaterialButtonToggleGroup tabToggle = view.findViewById(R.id.MealTabToggle);
        tabToggle.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            weekScreen.setVisibility(checkedId == R.id.MealTabWeek ? View.VISIBLE : View.GONE);
            recipesScreen.setVisibility(checkedId == R.id.MealTabRecipes ? View.VISIBLE : View.GONE);
            stockScreen.setVisibility(checkedId == R.id.MealTabStock ? View.VISIBLE : View.GONE);
        });
        // Required: checked listener does not fire for the pre-checked button at registration time
        weekScreen.setVisibility(View.VISIBLE);
        recipesScreen.setVisibility(View.GONE);
        stockScreen.setVisibility(View.GONE);

        Button addMealPlan = view.findViewById(R.id.MealAddPlan);
        Button addNeed = view.findViewById(R.id.MealAddNeed);
        Button addPantry = view.findViewById(R.id.MealAddPantry);
        addMealPlan.setContentDescription(getString(R.string.meal_add_plan_desc));
        addNeed.setContentDescription(getString(R.string.meal_add_need_desc));
        addPantry.setContentDescription(getString(R.string.meal_add_pantry_desc));

        addMealPlan.setOnClickListener(v -> showPlanDialog());
        addNeed.setOnClickListener(v -> showNeedDialog());
        addPantry.setOnClickListener(v -> showPantryDialog());

        renderAll();
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
        List<MealPlan> plans = presenter.getWeekMealPlans();
        if (plans.isEmpty()) {
            weekList.addView(inflateTextRow(getString(R.string.meal_empty_week), weekList));
            return;
        }
        for (MealPlan plan : plans) {
            View planRow = layoutInflater.inflate(R.layout.meal_plan_row_item, weekList, false);
            TextView title = planRow.findViewById(R.id.MealPlanRowTitle);
            TextView subtitle = planRow.findViewById(R.id.MealPlanRowSubtitle);
            Button done = planRow.findViewById(R.id.MealPlanRowDone);

            title.setText(plan.recipeTitle + " · " + plan.mealType.label);
            subtitle.setText(plan.date + " · " + plan.plannedServings + " Portionen");
            setMealPlanButtonState(done, plan);
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
        List<Recipe> recipes = presenter.getRecipes();
        for (Recipe recipe : recipes) {
            TextView recipeButton = inflateRecipeButton(recipe, recipeList);
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
        StringBuilder sb = new StringBuilder(recipe.title);
        if (!TextUtils.isEmpty(recipe.description)) sb.append("\n\n").append(recipe.description);
        if (!TextUtils.isEmpty(recipe.instructions)) sb.append("\n\n").append(recipe.instructions);
        return sb.toString();
    }

    /**
     * Rebuild the pantry (inventory) and shopping list views from presenter data.
     * Called on init and after user actions (add pantry item, add shopping need).
     */
    private void renderStock() {
        pantryList.removeAllViews();
        shoppingList.removeAllViews();

        List<PantryItem> pantryItems = presenter.getPantryItems();
        if (pantryItems.isEmpty()) {
            pantryList.addView(inflateTextRow(getString(R.string.meal_empty_pantry), pantryList));
        } else {
            for (PantryItem item : pantryItems) {
                TextView pantryRow = inflateTextRow(item.ingredientName + " · " + item.getFormattedAmount() + " · " + item.getExpiryInfo(LocalDate.now()), pantryList);
                pantryList.addView(pantryRow);
            }
        }

        List<ShoppingListItem> shoppingItems = presenter.getShoppingListItemsForCurrentPeriod();
        if (shoppingItems.isEmpty()) {
            shoppingList.addView(inflateTextRow(getString(R.string.meal_empty_shopping), shoppingList));
        } else {
            for (ShoppingListItem item : shoppingItems) {
                TextView shoppingRow = inflateTextRow(item.ingredientName + " · " + item.getFormattedAmount(), shoppingList);
                shoppingList.addView(shoppingRow);
            }
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
            String errorMsg = getString(R.string.meal_error_field_required, fieldLabel);
            Toast.makeText(requireContext(), errorMsg, Toast.LENGTH_SHORT).show();
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
            String errorMsg = getString(R.string.meal_error_invalid_date);
            Toast.makeText(requireContext(), errorMsg, Toast.LENGTH_SHORT).show();
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
            String errorMsg = getString(R.string.meal_error_invalid_number, fieldName);
            Toast.makeText(requireContext(), errorMsg, Toast.LENGTH_SHORT).show();
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
            String errorMsg = getString(R.string.meal_error_invalid_number, fieldName);
            Toast.makeText(requireContext(), errorMsg, Toast.LENGTH_SHORT).show();
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
        View planDialogContent = layoutInflater.inflate(R.layout.meal_plan_create_dialog, null);
        Spinner recipeSpinner = planDialogContent.findViewById(R.id.MealDialogRecipe);
        EditText dateField = planDialogContent.findViewById(R.id.MealDialogDate);
        Spinner typeSpinner = planDialogContent.findViewById(R.id.MealDialogType);
        EditText servingsField = planDialogContent.findViewById(R.id.MealDialogServings);

        List<Recipe> recipes = presenter.getRecipes();
        if (recipes == null || recipes.isEmpty()) {
            Toast.makeText(requireContext(), R.string.meal_error_no_recipes, Toast.LENGTH_SHORT).show();
            return;
        }

        List<String> recipeTitles = recipes.stream()
                .map(recipe -> recipe.title)
                .collect(Collectors.toList());
        ArrayAdapter<String> recipeAdapter = new ArrayAdapter<>(
                requireContext(), android.R.layout.simple_spinner_dropdown_item, recipeTitles);
        recipeSpinner.setAdapter(recipeAdapter);
        recipeSpinner.setSelection(DEFAULT_SPINNER_SELECTION);

        dateField.setText(LocalDate.now().toString());
        ArrayAdapter<MealType> typeAdapter = new ArrayAdapter<>(
                requireContext(), android.R.layout.simple_spinner_dropdown_item, MealType.values());
        typeSpinner.setAdapter(typeAdapter);
        typeSpinner.setSelection(DEFAULT_SPINNER_SELECTION);
        servingsField.setText(DEFAULT_SERVINGS);

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle(R.string.meal_plan_dialog_title)
                .setView(planDialogContent)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.meal_plan_dialog_save, null)
                .create();
        dialog.show();
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(v -> {
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
            MealType mealType = getSpinnerSelection(typeSpinner, MealType.class);
            if (mealType == null) return;
            Long recipeId = recipes.get(recipeIndex).id;
            if (recipeId == null) return;
            presenter.planRecipe(recipeId, parsedDate, mealType, servings);
            Toast.makeText(requireContext(), R.string.meal_success_plan_created, Toast.LENGTH_SHORT).show();
            dialog.dismiss();
            renderMealPlans();
        });
    }

    /**
     * Show dialog to create a shopping list item. Follows the shared dialog pattern documented in {@link #showPlanDialog()}.
     */
    private void showNeedDialog() {
        View needDialogContent = layoutInflater.inflate(R.layout.meal_need_create_dialog, null);
        EditText nameField = needDialogContent.findViewById(R.id.MealNeedName);
        EditText amountField = needDialogContent.findViewById(R.id.MealNeedAmount);
        EditText unitField = needDialogContent.findViewById(R.id.MealNeedUnit);

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle(R.string.meal_need_dialog_title)
                .setView(needDialogContent)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.meal_need_dialog_save, null)
                .create();
        dialog.show();
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(v -> {
            String ingredientName = requireNonEmpty(nameField, "einen Zutatennamen");
            if (ingredientName == null) return;
            String amountStr = requireNonEmpty(amountField, "eine Menge");
            if (amountStr == null) return;
            Double amount = safeParseDouble(amountStr, "Menge");
            if (amount == null || amount < 0) return;
            String unitStr = requireNonEmpty(unitField, "eine Einheit");
            if (unitStr == null) return;
            presenter.createShoppingItemFromNeed(ingredientName, amount, unitStr);
            Toast.makeText(requireContext(), R.string.meal_success_need_created, Toast.LENGTH_SHORT).show();
            dialog.dismiss();
            renderStock();
        });
    }

    /**
     * Show dialog to create a pantry (inventory) item. Follows the shared dialog pattern documented in {@link #showPlanDialog()}.
     */
    private void showPantryDialog() {
        View pantryDialogContent = layoutInflater.inflate(R.layout.meal_pantry_create_dialog, null);
        EditText nameField = pantryDialogContent.findViewById(R.id.MealPantryName);
        EditText amountField = pantryDialogContent.findViewById(R.id.MealPantryAmount);
        EditText unitField = pantryDialogContent.findViewById(R.id.MealPantryUnit);
        EditText shelfLifeDaysField = pantryDialogContent.findViewById(R.id.MealPantryShelfLifeDays);
        Spinner locationSpinner = pantryDialogContent.findViewById(R.id.MealPantryLocation);
        ArrayAdapter<PantryItem.StorageLocation> locationAdapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_dropdown_item,
                PantryItem.StorageLocation.values());
        locationSpinner.setAdapter(locationAdapter);
        locationSpinner.setSelection(DEFAULT_SPINNER_SELECTION);

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle(R.string.meal_pantry_dialog_title)
                .setView(pantryDialogContent)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.meal_pantry_dialog_save, null)
                .create();
        dialog.show();
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(v -> {
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
            PantryItem.StorageLocation location = getSpinnerSelection(locationSpinner, PantryItem.StorageLocation.class);
            if (location == null) return;
            presenter.createPantryItem(
                    ingredientName,
                    amount,
                    unitStr,
                    location,
                    shelfLifeDays
            );
            Toast.makeText(requireContext(), R.string.meal_success_pantry_created, Toast.LENGTH_SHORT).show();
            dialog.dismiss();
            renderStock();
        });
    }

    /**
     * Set button state (text and content description) based on meal completion status.
     */
    private void setMealPlanButtonState(Button button, MealPlan plan) {
        String buttonText = plan.isCompleted
                ? getString(R.string.meal_mark_open)
                : getString(R.string.meal_mark_done);
        String description = plan.isCompleted
                ? getString(R.string.meal_mark_open_desc, plan.recipeTitle)
                : getString(R.string.meal_mark_done_desc, plan.recipeTitle);
        button.setText(buttonText);
        button.setContentDescription(description);
    }

    /**
     * Inflate and configure a recipe button for the recipe list.
     */
    private TextView inflateRecipeButton(Recipe recipe, ViewGroup parent) {
        TextView button = (TextView) layoutInflater.inflate(R.layout.meal_recipe_row_item, parent, false);
        button.setText(recipe.title);
        button.setContentDescription(getString(R.string.meal_recipe_select_desc, recipe.title));
        button.setOnClickListener(v -> recipeDetail.setText(buildRecipeDetails(recipe)));
        return button;
    }

    /**
     * Inflate a text row item for pantry or shopping list display.
     */
    private TextView inflateTextRow(String text, ViewGroup parent) {
        TextView row = (TextView) layoutInflater.inflate(R.layout.meal_text_row_item, parent, false);
        row.setText(text);
        return row;
    }

    /**
     * Safely get and cast a spinner selection to the expected type.
     * Returns null if spinner selection is null or invalid.
     */
    @Nullable
    private <T> T getSpinnerSelection(Spinner spinner, Class<T> expectedType) {
        Object selected = spinner.getSelectedItem();
        if (selected == null) {
            return null;
        }
        return expectedType.cast(selected);
    }
}

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

import java.time.LocalDate;
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
            View row = inflater.inflate(R.layout.meal_plan_row_item, weekList, false);
            TextView title = row.findViewById(R.id.MealPlanRowTitle);
            TextView subtitle = row.findViewById(R.id.MealPlanRowSubtitle);
            Button done = row.findViewById(R.id.MealPlanRowDone);

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
            weekList.addView(row);
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
            Button row = (Button) inflater.inflate(R.layout.meal_recipe_row_item, recipeList, false);
            row.setText(recipe.title);
            row.setOnClickListener(v -> recipeDetail.setText(buildRecipeDetails(recipe)));
            recipeList.addView(row);
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
            TextView row = (TextView) inflater.inflate(R.layout.meal_text_row_item, pantryList, false);
            row.setText(item.ingredientName + " · " + item.getFormattedAmount() + " · " + item.getExpiryInfo());
            pantryList.addView(row);
        }

        for (ShoppingListItem item : presenter.getShoppingListItemsForToday()) {
            TextView row = (TextView) inflater.inflate(R.layout.meal_text_row_item, shoppingList, false);
            row.setText(item.ingredientName + " · " + item.getFormattedAmount());
            shoppingList.addView(row);
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
        View content = LayoutInflater.from(requireContext()).inflate(R.layout.meal_plan_create_dialog, null);
        Spinner recipeSpinner = content.findViewById(R.id.MealDialogRecipe);
        EditText dateField = content.findViewById(R.id.MealDialogDate);
        Spinner typeSpinner = content.findViewById(R.id.MealDialogType);
        EditText servingsField = content.findViewById(R.id.MealDialogServings);

        List<Recipe> recipes = presenter.getRecipes();
        List<String> recipeTitles = new java.util.ArrayList<>();
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
                .setView(content)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.meal_plan_dialog_save, (d, w) -> {
                    int recipeIndex = recipeSpinner.getSelectedItemPosition();
                    if (recipeIndex < 0 || recipeIndex >= recipes.size()) {
                        return;
                    }
                    MealType mealType = (MealType) typeSpinner.getSelectedItem();
                    presenter.planRecipe(
                            recipes.get(recipeIndex).id,
                            LocalDate.parse(dateField.getText().toString()),
                            mealType,
                            Integer.parseInt(servingsField.getText().toString())
                    );
                    renderMealPlans();
                })
                .show();
    }

    /**
     * Show dialog to create a shopping list item. Follows the shared dialog pattern documented in {@link #showPlanDialog()}.
     */
    private void showNeedDialog() {
        View content = LayoutInflater.from(requireContext()).inflate(R.layout.meal_need_create_dialog, null);
        EditText name = content.findViewById(R.id.MealNeedName);
        EditText amount = content.findViewById(R.id.MealNeedAmount);
        EditText unit = content.findViewById(R.id.MealNeedUnit);

        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.meal_need_dialog_title)
                .setView(content)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.meal_need_dialog_save, (d, w) -> {
                    presenter.createShoppingItemFromNeed(
                            name.getText().toString(),
                            Double.parseDouble(amount.getText().toString()),
                            unit.getText().toString()
                    );
                    renderStock();
                })
                .show();
    }

    /**
     * Show dialog to create a pantry (inventory) item. Follows the shared dialog pattern documented in {@link #showPlanDialog()}.
     */
    private void showPantryDialog() {
        View content = LayoutInflater.from(requireContext()).inflate(R.layout.meal_pantry_create_dialog, null);
        EditText name = content.findViewById(R.id.MealPantryName);
        EditText amount = content.findViewById(R.id.MealPantryAmount);
        EditText unit = content.findViewById(R.id.MealPantryUnit);
        EditText shelfLifeDays = content.findViewById(R.id.MealPantryShelfLifeDays);
        Spinner location = content.findViewById(R.id.MealPantryLocation);
        location.setAdapter(new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_dropdown_item,
                PantryItem.StorageLocation.values()));

        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.meal_pantry_dialog_title)
                .setView(content)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.meal_pantry_dialog_save, (d, w) -> {
                    presenter.createPantryItem(
                            name.getText().toString(),
                            Double.parseDouble(amount.getText().toString()),
                            unit.getText().toString(),
                            (PantryItem.StorageLocation) location.getSelectedItem(),
                            Integer.parseInt(shelfLifeDays.getText().toString())
                    );
                    renderStock();
                })
                .show();
    }
}

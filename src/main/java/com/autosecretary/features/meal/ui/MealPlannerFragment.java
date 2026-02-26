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

public class MealPlannerFragment extends Fragment {

    private MealPlannerPresenter presenter;

    private View weekScreen;
    private View recipesScreen;
    private View stockScreen;
    private LinearLayout weekList;
    private LinearLayout recipeList;
    private TextView recipeDetail;
    private LinearLayout pantryList;
    private LinearLayout shoppingList;

    public MealPlannerFragment(MealPlannerPresenter presenter) {
        this.presenter = presenter;
    }

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

    private void switchScreen(View visible) {
        weekScreen.setVisibility(visible == weekScreen ? View.VISIBLE : View.GONE);
        recipesScreen.setVisibility(visible == recipesScreen ? View.VISIBLE : View.GONE);
        stockScreen.setVisibility(visible == stockScreen ? View.VISIBLE : View.GONE);
    }

    private void renderAll() {
        renderMealPlans();
        renderRecipes();
        renderStock();
    }

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

    private String buildRecipeDetails(Recipe recipe) {
        return recipe.title + "\n\n" +
                (TextUtils.isEmpty(recipe.description) ? "" : recipe.description + "\n\n") +
                (TextUtils.isEmpty(recipe.instructions) ? "" : recipe.instructions);
    }

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

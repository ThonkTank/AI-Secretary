package com.autosecretary.features.meal.ui;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.autosecretary.R;
import com.autosecretary.app.AutoSecretaryApplication;
import com.autosecretary.features.meal.application.MealHomeModel;
import com.autosecretary.features.meal.application.MealPlannerPresenter;
import com.autosecretary.features.meal.domain.MealPlan;
import com.autosecretary.features.meal.domain.PantryItem;
import com.autosecretary.features.meal.domain.Recipe;
import com.autosecretary.features.meal.domain.ShoppingListItem;
import com.autosecretary.features.meal.ui.internal.MealNeedDialogController;
import com.autosecretary.features.meal.ui.internal.MealPantryDialogController;
import com.autosecretary.features.meal.ui.internal.MealPlanDialogController;
import com.autosecretary.shared.DateFormatters;
import com.autosecretary.shared.ui.SimpleButtonCheckedListener;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;

import java.time.LocalDate;

public class MealPlannerFragment extends Fragment {

    private MealPlannerPresenter presenter;
    private MealHomeModel currentHome;

    private View weekScreen;
    private View recipesScreen;
    private View stockScreen;
    private LinearLayout weekList;
    private View weekEmptyState;
    private LinearLayout recipeList;
    private View recipesEmptyState;
    private TextView recipeDetail;
    private LinearLayout pantryList;
    private LinearLayout shoppingList;

    private MealPlanDialogController planDialogController;
    private MealNeedDialogController needDialogController;
    private MealPantryDialogController pantryDialogController;

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

        planDialogController = new MealPlanDialogController(this, (recipeId, date, mealType, servings) ->
                presenter.planRecipe(recipeId, date, mealType, servings, () -> {
                    if (!isAdded()) return;
                    Toast.makeText(requireContext(), R.string.meal_success_plan_created, Toast.LENGTH_SHORT).show();
                    reloadHome();
                }));

        needDialogController = new MealNeedDialogController(this, (name, amount, unit) ->
                presenter.createShoppingItemFromNeed(name, amount, unit, () -> {
                    if (!isAdded()) return;
                    Toast.makeText(requireContext(), R.string.meal_success_need_created, Toast.LENGTH_SHORT).show();
                    reloadHome();
                }));

        pantryDialogController = new MealPantryDialogController(this, (name, amount, unit, location, shelfLifeDays) ->
                presenter.createPantryItem(name, amount, unit, location, shelfLifeDays, () -> {
                    if (!isAdded()) return;
                    Toast.makeText(requireContext(), R.string.meal_success_pantry_created, Toast.LENGTH_SHORT).show();
                    reloadHome();
                }));

        weekScreen = view.findViewById(R.id.MealWeekScreen);
        recipesScreen = view.findViewById(R.id.MealRecipeScreen);
        stockScreen = view.findViewById(R.id.MealStockScreen);
        weekList = view.findViewById(R.id.MealWeekList);
        weekEmptyState = weekScreen.findViewById(R.id.EmptyStateContainer);
        ((TextView) weekScreen.findViewById(R.id.EmptyStateTitle)).setText(R.string.meal_empty_week_title);
        ((TextView) weekScreen.findViewById(R.id.EmptyStateSubtitle)).setText(R.string.meal_empty_week_subtitle);
        recipeList = view.findViewById(R.id.MealRecipeList);
        recipesEmptyState = recipesScreen.findViewById(R.id.EmptyStateContainer);
        ((TextView) recipesScreen.findViewById(R.id.EmptyStateTitle)).setText(R.string.meal_empty_recipes_title);
        ((TextView) recipesScreen.findViewById(R.id.EmptyStateSubtitle)).setText(R.string.meal_empty_recipes_subtitle);
        recipeDetail = view.findViewById(R.id.MealRecipeDetail);
        pantryList = view.findViewById(R.id.MealPantryList);
        shoppingList = view.findViewById(R.id.MealShoppingList);

        MaterialButtonToggleGroup tabToggle = view.findViewById(R.id.MealTabToggle);
        tabToggle.addOnButtonCheckedListener(new SimpleButtonCheckedListener() {
            @Override
            public void onChecked(MaterialButtonToggleGroup group, int checkedId) {
                weekScreen.setVisibility(checkedId == R.id.MealTabWeek ? View.VISIBLE : View.GONE);
                recipesScreen.setVisibility(checkedId == R.id.MealTabRecipes ? View.VISIBLE : View.GONE);
                stockScreen.setVisibility(checkedId == R.id.MealTabStock ? View.VISIBLE : View.GONE);
            }
        });
        weekScreen.setVisibility(View.VISIBLE);
        recipesScreen.setVisibility(View.GONE);
        stockScreen.setVisibility(View.GONE);

        MaterialButton addMealPlan = view.findViewById(R.id.MealAddPlan);
        MaterialButton addNeed = view.findViewById(R.id.MealAddNeed);
        MaterialButton addPantry = view.findViewById(R.id.MealAddPantry);
        addMealPlan.setContentDescription(getString(R.string.meal_add_plan_desc));
        addNeed.setContentDescription(getString(R.string.meal_add_need_desc));
        addPantry.setContentDescription(getString(R.string.meal_add_pantry_desc));

        addMealPlan.setOnClickListener(v -> presenter.openManagePlan(recipes -> {
            if (!isAdded()) return;
            planDialogController.show(recipes);
        }));
        addNeed.setOnClickListener(v -> presenter.openManageNeed(() -> {
            if (!isAdded()) return;
            needDialogController.show();
        }));
        addPantry.setOnClickListener(v -> presenter.openManagePantry(() -> {
            if (!isAdded()) return;
            pantryDialogController.show();
        }));

        reloadHome();
    }

    private void reloadHome() {
        presenter.loadHome(home -> {
            if (!isAdded()) return;
            currentHome = home;
            render(home);
        });
    }

    private void render(MealHomeModel home) {
        renderMealPlans(home);
        renderRecipes(home.recipes);
        renderStock(home);
    }

    private void renderMealPlans(MealHomeModel home) {
        weekList.removeAllViews();
        boolean empty = home.weekPlans.isEmpty();
        weekEmptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
        weekList.setVisibility(empty ? View.GONE : View.VISIBLE);
        for (MealPlan plan : home.weekPlans) {
            View planRow = LayoutInflater.from(requireContext()).inflate(R.layout.meal_plan_row_item, weekList, false);
            TextView title = planRow.findViewById(R.id.MealPlanRowTitle);
            TextView subtitle = planRow.findViewById(R.id.MealPlanRowSubtitle);
            MaterialButton done = planRow.findViewById(R.id.MealPlanRowDone);

            title.setText(getString(R.string.meal_plan_row_title_format, plan.recipeTitle, plan.mealType.label));
            subtitle.setText(getString(R.string.meal_plan_row_subtitle_format, plan.date.format(DateFormatters.DATE_FULL_GERMAN), plan.plannedServings));
            planRow.setContentDescription(getString(R.string.meal_plan_row_content_description,
                    plan.recipeTitle, plan.mealType.label,
                    plan.date.format(DateFormatters.DATE_FULL_GERMAN), plan.plannedServings));
            setMealPlanButtonState(done, plan);
            if (plan.id != null) {
                done.setOnClickListener(v -> presenter.toggleMealCompleted(plan.id, this::reloadHome));
            }
            weekList.addView(planRow);
        }
    }

    private void renderRecipes(java.util.List<Recipe> recipes) {
        recipeList.removeAllViews();
        boolean empty = recipes.isEmpty();
        recipesEmptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
        recipeList.setVisibility(empty ? View.GONE : View.VISIBLE);
        if (empty) {
            recipeDetail.setText(null);
            return;
        }
        for (Recipe recipe : recipes) {
            TextView recipeButton = inflateRecipeButton(recipe, recipeList);
            recipeList.addView(recipeButton);
        }
        recipeDetail.setText(buildRecipeDetails(recipes.get(0)));
    }

    private String buildRecipeDetails(Recipe recipe) {
        StringBuilder sb = new StringBuilder(recipe.title);
        if (!TextUtils.isEmpty(recipe.description)) sb.append("\n\n").append(recipe.description);
        if (!TextUtils.isEmpty(recipe.instructions)) sb.append("\n\n").append(recipe.instructions);
        return sb.toString();
    }

    private void renderStock(MealHomeModel home) {
        pantryList.removeAllViews();
        if (home.pantryItems.isEmpty()) {
            pantryList.addView(inflateTextRow(getString(R.string.meal_empty_pantry), pantryList));
        } else {
            for (PantryItem item : home.pantryItems) {
                pantryList.addView(inflateTextRow(getString(R.string.meal_pantry_row_format,
                        item.ingredientName, item.getFormattedAmount(), item.getExpiryInfo(LocalDate.now())), pantryList));
            }
        }

        shoppingList.removeAllViews();
        if (home.shoppingItems.isEmpty()) {
            shoppingList.addView(inflateTextRow(getString(R.string.meal_empty_shopping), shoppingList));
            return;
        }
        for (ShoppingListItem item : home.shoppingItems) {
            String label = getString(R.string.meal_shopping_row_format, item.ingredientName, item.getFormattedAmount());
            if (item.isPurchased) {
                label = "✓ " + label;
            }
            if (item.id != null) {
                final String shoppingItemId = item.id;
                TextView row = inflateTextRow(label, shoppingList);
                row.setOnClickListener(v -> presenter.toggleShoppingDone(shoppingItemId, this::reloadHome));
                shoppingList.addView(row);
            } else {
                shoppingList.addView(inflateTextRow(label, shoppingList));
            }
        }
    }

    private void setMealPlanButtonState(MaterialButton button, MealPlan plan) {
        button.setText(plan.isCompleted ? getString(R.string.meal_mark_open) : getString(R.string.meal_mark_done));
        button.setContentDescription(plan.isCompleted
                ? getString(R.string.meal_mark_open_desc, plan.recipeTitle)
                : getString(R.string.meal_mark_done_desc, plan.recipeTitle));
        button.setIconResource(plan.isCompleted ? R.drawable.ic_check_24 : 0);
    }

    private TextView inflateRecipeButton(Recipe recipe, ViewGroup parent) {
        TextView button = (TextView) LayoutInflater.from(requireContext()).inflate(R.layout.meal_text_row_item, parent, false);
        button.setText(recipe.title);
        button.setContentDescription(getString(R.string.meal_recipe_select_desc, recipe.title));
        button.setOnClickListener(v -> recipeDetail.setText(buildRecipeDetails(recipe)));
        return button;
    }

    private TextView inflateTextRow(String text, ViewGroup parent) {
        TextView row = (TextView) LayoutInflater.from(requireContext()).inflate(R.layout.meal_text_row_item, parent, false);
        row.setText(text);
        row.setContentDescription(text);
        return row;
    }
}

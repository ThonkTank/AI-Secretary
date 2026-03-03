package com.autosecretary.features.meal.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.autosecretary.R;
import com.autosecretary.app.AutoSecretaryApplication;
import com.autosecretary.features.meal.application.MealPlannerPresenter;
import com.autosecretary.features.meal.domain.HouseholdMember;
import com.autosecretary.features.meal.domain.Ingredient;
import com.autosecretary.features.meal.domain.MealPlan;
import com.autosecretary.features.meal.domain.PantryItem;
import com.autosecretary.features.meal.domain.Recipe;
import com.autosecretary.features.meal.domain.ShoppingListItem;
import com.autosecretary.features.meal.ui.internal.MealHouseholdDialogController;
import com.autosecretary.features.meal.ui.internal.MealIngredientDialogController;
import com.autosecretary.features.meal.ui.internal.MealNeedDialogController;
import com.autosecretary.features.meal.ui.internal.MealPantryDialogController;
import com.autosecretary.features.meal.ui.internal.MealPlanDialogController;
import com.autosecretary.features.meal.ui.internal.MealRecipeDialogController;
import com.autosecretary.shared.DateFormatters;
import com.google.android.material.button.MaterialButton;

import java.time.LocalDate;
import java.util.List;

public class MealPlannerFragment extends Fragment {

    private MealPlannerPresenter presenter;

    private LinearLayout weekList;
    private View weekEmptyState;
    private LinearLayout pantryList;
    private LinearLayout shoppingList;

    private MealPlanDialogController planDialogController;
    private MealNeedDialogController needDialogController;
    private MealPantryDialogController pantryDialogController;
    private MealRecipeDialogController recipeDialogController;
    private MealIngredientDialogController ingredientDialogController;
    private MealHouseholdDialogController householdDialogController;

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
                    renderMealPlans();
                }));

        needDialogController = new MealNeedDialogController(this, (name, amount, unit) ->
                presenter.createShoppingItemFromNeed(name, amount, unit, () -> {
                    if (!isAdded()) return;
                    Toast.makeText(requireContext(), R.string.meal_success_need_created, Toast.LENGTH_SHORT).show();
                    renderStock();
                }));

        pantryDialogController = new MealPantryDialogController(this, (name, amount, unit, location, shelfLifeDays) ->
                presenter.createPantryItem(name, amount, unit, location, shelfLifeDays, () -> {
                    if (!isAdded()) return;
                    Toast.makeText(requireContext(), R.string.meal_success_pantry_created, Toast.LENGTH_SHORT).show();
                    renderStock();
                }));

        recipeDialogController = new MealRecipeDialogController(this, (title, description, instructions, servings) ->
                presenter.createRecipe(title, description, instructions, servings, () -> {
                    if (!isAdded()) return;
                    Toast.makeText(requireContext(), R.string.meal_success_recipe_created, Toast.LENGTH_SHORT).show();
                    renderMealPlans();
                }));

        ingredientDialogController = new MealIngredientDialogController(this, (name, foodGroup, unit) ->
                presenter.createIngredient(name, foodGroup, unit, () -> {
                    if (!isAdded()) return;
                    Toast.makeText(requireContext(), R.string.meal_success_ingredient_created, Toast.LENGTH_SHORT).show();
                }));

        householdDialogController = new MealHouseholdDialogController(this, (name, gender, birthYear) ->
                presenter.createHouseholdMember(name, gender, birthYear, () -> {
                    if (!isAdded()) return;
                    Toast.makeText(requireContext(), R.string.meal_success_household_created, Toast.LENGTH_SHORT).show();
                }));

        weekList = view.findViewById(R.id.MealWeekList);
        weekEmptyState = view.findViewById(R.id.EmptyStateContainer);
        ((TextView) view.findViewById(R.id.EmptyStateTitle)).setText(R.string.meal_empty_week_title);
        ((TextView) view.findViewById(R.id.EmptyStateSubtitle)).setText(R.string.meal_empty_week_subtitle);
        pantryList = view.findViewById(R.id.MealPantryList);
        shoppingList = view.findViewById(R.id.MealShoppingList);

        MaterialButton addMealPlan = view.findViewById(R.id.MealAddPlan);
        MaterialButton addNeed = view.findViewById(R.id.MealAddNeed);
        MaterialButton addPantry = view.findViewById(R.id.MealAddPantry);
        MaterialButton manageRecipes = view.findViewById(R.id.MealManageRecipes);
        MaterialButton manageHousehold = view.findViewById(R.id.MealManageHousehold);
        MaterialButton managePantry = view.findViewById(R.id.MealManagePantry);
        MaterialButton manageIngredients = view.findViewById(R.id.MealManageIngredients);

        addMealPlan.setOnClickListener(v -> presenter.getRecipes(recipes -> {
            if (!isAdded()) return;
            planDialogController.show(recipes);
        }));
        addNeed.setOnClickListener(v -> needDialogController.show());
        addPantry.setOnClickListener(v -> pantryDialogController.show());

        manageRecipes.setOnClickListener(v -> showRecipeManagementDialog());
        manageHousehold.setOnClickListener(v -> showHouseholdManagementDialog());
        managePantry.setOnClickListener(v -> showPantryManagementDialog());
        manageIngredients.setOnClickListener(v -> showIngredientManagementDialog());

        renderAll();
    }

    private void showRecipeManagementDialog() {
        presenter.getRecipes(recipes -> {
            if (!isAdded()) return;
            showManagementDialog(
                    getString(R.string.meal_manage_recipes),
                    recipes,
                    recipe -> recipe.title,
                    getString(R.string.meal_manage_add_recipe),
                    () -> recipeDialogController.show()
            );
        });
    }

    private void showHouseholdManagementDialog() {
        presenter.getHouseholdMembers(members -> {
            if (!isAdded()) return;
            showManagementDialog(
                    getString(R.string.meal_manage_household),
                    members,
                    member -> getString(R.string.meal_household_row_format, member.name, member.birthYear),
                    getString(R.string.meal_manage_add_household),
                    () -> householdDialogController.show()
            );
        });
    }

    private void showPantryManagementDialog() {
        presenter.getPantryItems(items -> {
            if (!isAdded()) return;
            showManagementDialog(
                    getString(R.string.meal_manage_pantry),
                    items,
                    item -> getString(R.string.meal_pantry_row_format,
                            item.ingredientName,
                            item.getFormattedAmount(),
                            item.getExpiryInfo(LocalDate.now())),
                    getString(R.string.meal_manage_add_pantry),
                    () -> pantryDialogController.show()
            );
        });
    }

    private void showIngredientManagementDialog() {
        presenter.getIngredients(items -> {
            if (!isAdded()) return;
            showManagementDialog(
                    getString(R.string.meal_manage_ingredients),
                    items,
                    ingredient -> getString(R.string.meal_ingredient_row_format, ingredient.name, ingredient.foodGroup.label),
                    getString(R.string.meal_manage_add_ingredient),
                    () -> ingredientDialogController.show()
            );
        });
    }

    private <T> void showManagementDialog(String title,
                                          List<T> items,
                                          RowFormatter<T> formatter,
                                          String addAction,
                                          Runnable onAddPressed) {
        View content = LayoutInflater.from(requireContext()).inflate(R.layout.meal_management_dialog, null);
        TextView emptyState = content.findViewById(R.id.MealManagementEmpty);
        LinearLayout rows = content.findViewById(R.id.MealManagementList);
        MaterialButton addButton = content.findViewById(R.id.MealManagementAdd);
        addButton.setText(addAction);

        if (items.isEmpty()) {
            emptyState.setVisibility(View.VISIBLE);
        } else {
            emptyState.setVisibility(View.GONE);
            for (T item : items) {
                rows.addView(inflateTextRow(formatter.format(item), rows));
            }
        }

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle(title)
                .setView(content)
                .setNegativeButton(R.string.action_cancel, null)
                .create();
        addButton.setOnClickListener(v -> {
            dialog.dismiss();
            onAddPressed.run();
        });
        dialog.show();
    }

    private void renderAll() {
        renderMealPlans();
        renderStock();
    }

    private void renderMealPlans() {
        presenter.getWeekMealPlans(plans -> {
            if (!isAdded()) return;
            weekList.removeAllViews();
            boolean empty = plans.isEmpty();
            weekEmptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
            weekList.setVisibility(empty ? View.GONE : View.VISIBLE);
            for (MealPlan plan : plans) {
                View planRow = LayoutInflater.from(requireContext()).inflate(R.layout.meal_plan_row_item, weekList, false);
                TextView title = planRow.findViewById(R.id.MealPlanRowTitle);
                TextView subtitle = planRow.findViewById(R.id.MealPlanRowSubtitle);
                MaterialButton done = planRow.findViewById(R.id.MealPlanRowDone);

                title.setText(getString(R.string.meal_plan_row_title_format, plan.recipeTitle, plan.mealType.label));
                subtitle.setText(getString(R.string.meal_plan_row_subtitle_format, plan.date.format(DateFormatters.DATE_FULL_GERMAN), plan.plannedServings));
                setMealPlanButtonState(done, plan);
                if (plan.id != null) {
                    done.setOnClickListener(v -> presenter.toggleMealCompleted(plan.id, this::renderMealPlans));
                }
                weekList.addView(planRow);
            }
        });
    }

    private void renderStock() {
        presenter.getPantryItems(pantryItems -> {
            if (!isAdded()) return;
            pantryList.removeAllViews();
            if (pantryItems.isEmpty()) {
                pantryList.addView(inflateTextRow(getString(R.string.meal_empty_pantry), pantryList));
            } else {
                for (PantryItem item : pantryItems) {
                    pantryList.addView(inflateTextRow(getString(R.string.meal_pantry_row_format,
                            item.ingredientName, item.getFormattedAmount(), item.getExpiryInfo(LocalDate.now())), pantryList));
                }
            }
        });

        presenter.getShoppingListItems(shoppingItems -> {
            if (!isAdded()) return;
            shoppingList.removeAllViews();
            if (shoppingItems.isEmpty()) {
                shoppingList.addView(inflateTextRow(getString(R.string.meal_empty_shopping), shoppingList));
            } else {
                for (ShoppingListItem item : shoppingItems) {
                    shoppingList.addView(inflateTextRow(getString(R.string.meal_shopping_row_format,
                            item.ingredientName, item.getFormattedAmount()), shoppingList));
                }
            }
        });
    }

    private void setMealPlanButtonState(MaterialButton button, MealPlan plan) {
        button.setText(plan.isCompleted ? getString(R.string.meal_mark_open) : getString(R.string.meal_mark_done));
        button.setIconResource(plan.isCompleted ? R.drawable.ic_check_24 : 0);
    }

    private TextView inflateTextRow(String text, ViewGroup parent) {
        TextView row = (TextView) LayoutInflater.from(requireContext()).inflate(R.layout.meal_text_row_item, parent, false);
        row.setText(text);
        row.setContentDescription(text);
        return row;
    }

    private interface RowFormatter<T> {
        String format(T item);
    }
}

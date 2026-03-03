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
import androidx.fragment.app.Fragment;

import com.autosecretary.R;
import com.autosecretary.app.AutoSecretaryApplication;
import com.autosecretary.features.meal.application.MealPlannerPresenter;
import com.autosecretary.features.meal.domain.MealPlan;
import com.autosecretary.features.meal.domain.ShoppingListItem;
import com.autosecretary.features.meal.ui.internal.MealNeedDialogController;
import com.autosecretary.features.meal.ui.internal.MealPantryDialogController;
import com.autosecretary.features.meal.ui.internal.MealPlanDialogController;
import com.autosecretary.shared.DateFormatters;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** One-page meal dashboard with sectional rendering. */
public class MealPlannerFragment extends Fragment {

    private MealPlannerPresenter presenter;

    private LinearLayout weekList;
    private View weekEmptyState;
    private TextView calorieProgress;
    private LinearLayout needProgressList;
    private LinearLayout shoppingOpenList;
    private LinearLayout shoppingDoneList;
    private TextView shoppingOpenTitle;
    private TextView shoppingDoneTitle;

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
                    renderDashboardSections();
                }));

        needDialogController = new MealNeedDialogController(this, (name, amount, unit) ->
                presenter.createShoppingItemFromNeed(name, amount, unit, () -> {
                    if (!isAdded()) return;
                    Toast.makeText(requireContext(), R.string.meal_success_need_created, Toast.LENGTH_SHORT).show();
                    renderNeedAndShoppingSections();
                }));

        pantryDialogController = new MealPantryDialogController(this, (name, amount, unit, location, shelfLifeDays) ->
                presenter.createPantryItem(name, amount, unit, location, shelfLifeDays, () -> {
                    if (!isAdded()) return;
                    Toast.makeText(requireContext(), R.string.meal_success_pantry_created, Toast.LENGTH_SHORT).show();
                }));

        weekList = view.findViewById(R.id.MealWeekList);
        weekEmptyState = view.findViewById(R.id.EmptyStateContainer);
        ((TextView) view.findViewById(R.id.EmptyStateTitle)).setText(R.string.meal_empty_week_title);
        ((TextView) view.findViewById(R.id.EmptyStateSubtitle)).setText(R.string.meal_empty_week_subtitle);
        calorieProgress = view.findViewById(R.id.MealCalorieProgress);
        needProgressList = view.findViewById(R.id.MealNeedProgressList);
        shoppingOpenList = view.findViewById(R.id.MealShoppingOpenList);
        shoppingDoneList = view.findViewById(R.id.MealShoppingDoneList);
        shoppingOpenTitle = view.findViewById(R.id.MealShoppingOpenTitle);
        shoppingDoneTitle = view.findViewById(R.id.MealShoppingDoneTitle);

        MaterialButton addMealPlan = view.findViewById(R.id.MealAddPlan);
        MaterialButton addNeed = view.findViewById(R.id.MealAddNeed);
        MaterialButton addPantry = view.findViewById(R.id.MealAddPantry);
        MaterialButton recipesAction = view.findViewById(R.id.MealActionRecipes);
        MaterialButton membersAction = view.findViewById(R.id.MealActionMembers);
        MaterialButton ingredientsAction = view.findViewById(R.id.MealActionIngredients);

        addMealPlan.setContentDescription(getString(R.string.meal_add_plan_desc));
        addNeed.setContentDescription(getString(R.string.meal_add_need_desc));
        addPantry.setContentDescription(getString(R.string.meal_add_pantry_desc));

        addMealPlan.setOnClickListener(v -> presenter.getRecipes(recipes -> {
            if (!isAdded()) return;
            planDialogController.show(recipes);
        }));
        addNeed.setOnClickListener(v -> needDialogController.show());
        addPantry.setOnClickListener(v -> pantryDialogController.show());

        recipesAction.setOnClickListener(v -> presenter.getRecipes(recipes -> {
            if (!isAdded()) return;
            String[] titles = new String[recipes.size()];
            for (int i = 0; i < recipes.size(); i++) {
                titles[i] = recipes.get(i).title;
            }
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.meal_action_recipes)
                    .setItems(titles, null)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
        }));
        membersAction.setOnClickListener(v -> showInfoDialog(R.string.meal_action_members));
        ingredientsAction.setOnClickListener(v -> showInfoDialog(R.string.meal_action_ingredients));

        renderDashboardSections();
    }

    private void showInfoDialog(int titleRes) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(titleRes)
                .setMessage(R.string.meal_secondary_action_info)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private void renderDashboardSections() {
        renderWeekSection();
        renderNeedAndShoppingSections();
    }

    private void renderNeedAndShoppingSections() {
        presenter.getShoppingListItems(this::renderNeedProgressAndShoppingFromItems);
    }

    private void renderWeekSection() {
        presenter.getWeekMealPlans(plans -> {
            if (!isAdded()) return;
            weekList.removeAllViews();
            LocalDate today = LocalDate.now();
            List<MealPlan> upcomingPlans = new ArrayList<>();
            int plannedCalories = 0;
            int completedCalories = 0;
            for (MealPlan plan : plans) {
                if (!plan.date.isBefore(today)) {
                    upcomingPlans.add(plan);
                    plannedCalories += Math.max(plan.calories, 0);
                    if (plan.isCompleted) completedCalories += Math.max(plan.calories, 0);
                }
            }

            boolean empty = upcomingPlans.isEmpty();
            weekEmptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
            weekList.setVisibility(empty ? View.GONE : View.VISIBLE);
            for (MealPlan plan : upcomingPlans) {
                View planRow = LayoutInflater.from(requireContext()).inflate(R.layout.meal_plan_row_item, weekList, false);
                TextView title = planRow.findViewById(R.id.MealPlanRowTitle);
                TextView subtitle = planRow.findViewById(R.id.MealPlanRowSubtitle);
                MaterialButton done = planRow.findViewById(R.id.MealPlanRowDone);

                title.setText(getString(R.string.meal_plan_row_title_format, plan.recipeTitle, plan.mealType.label));
                subtitle.setText(getString(R.string.meal_plan_row_subtitle_format,
                        plan.date.format(DateFormatters.DATE_FULL_GERMAN), plan.plannedServings));
                setMealPlanButtonState(done, plan);
                if (plan.id != null) {
                    done.setOnClickListener(v -> presenter.toggleMealCompleted(plan.id, this::renderDashboardSections));
                }
                weekList.addView(planRow);
            }
            int percent = plannedCalories == 0 ? 0 : Math.round((completedCalories * 100f) / plannedCalories);
            calorieProgress.setText(getString(R.string.meal_calorie_progress_format, completedCalories, plannedCalories, percent));
        });
    }

    private void renderNeedProgressAndShoppingFromItems(List<ShoppingListItem> items) {
        if (!isAdded()) return;
        needProgressList.removeAllViews();
        shoppingOpenList.removeAllViews();
        shoppingDoneList.removeAllViews();

        Map<String, int[]> groupProgress = new LinkedHashMap<>();
        int openCount = 0;
        int doneCount = 0;

        for (ShoppingListItem item : items) {
            String groupLabel = (item.foodGroupLabel == null || item.foodGroupLabel.isBlank())
                    ? getString(R.string.meal_food_group_other)
                    : item.foodGroupLabel;
            int[] totals = groupProgress.computeIfAbsent(groupLabel, key -> new int[2]);
            totals[1] += 1;
            if (item.isPurchased) totals[0] += 1;

            MaterialCheckBox row = new MaterialCheckBox(requireContext());
            row.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            row.setChecked(item.isPurchased);
            row.setText(getString(R.string.meal_shopping_row_format, item.ingredientName, item.getFormattedAmount()));
            row.setOnClickListener(v -> {
                if (item.id != null) {
                    presenter.toggleShoppingItemPurchased(item.id, this::renderNeedAndShoppingSections);
                }
            });

            if (item.isPurchased) {
                doneCount++;
                shoppingDoneList.addView(row);
            } else {
                openCount++;
                shoppingOpenList.addView(row);
            }
        }

        if (items.isEmpty()) {
            shoppingOpenList.addView(inflateTextRow(getString(R.string.meal_empty_shopping), shoppingOpenList));
        }

        if (groupProgress.isEmpty()) {
            needProgressList.addView(inflateTextRow(getString(R.string.meal_need_progress_empty), needProgressList));
        } else {
            for (Map.Entry<String, int[]> entry : groupProgress.entrySet()) {
                int done = entry.getValue()[0];
                int total = entry.getValue()[1];
                needProgressList.addView(inflateTextRow(
                        getString(R.string.meal_need_progress_row_format, entry.getKey(), done, total), needProgressList));
            }
        }

        shoppingOpenTitle.setText(getString(R.string.meal_shopping_open_count, openCount));
        shoppingDoneTitle.setText(getString(R.string.meal_shopping_done_count, doneCount));
    }

    private void setMealPlanButtonState(MaterialButton button, MealPlan plan) {
        button.setText(plan.isCompleted ? getString(R.string.meal_mark_open) : getString(R.string.meal_mark_done));
        button.setContentDescription(plan.isCompleted
                ? getString(R.string.meal_mark_open_desc, plan.recipeTitle)
                : getString(R.string.meal_mark_done_desc, plan.recipeTitle));
        button.setIconResource(plan.isCompleted ? R.drawable.ic_check_24 : 0);
    }

    private TextView inflateTextRow(String text, ViewGroup parent) {
        TextView row = (TextView) LayoutInflater.from(requireContext()).inflate(R.layout.meal_text_row_item, parent, false);
        row.setText(text);
        row.setContentDescription(text);
        return row;
    }
}

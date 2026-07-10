package com.autosecretary.features.meal.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.autosecretary.R;
import com.autosecretary.features.meal.domain.HouseholdMember;
import com.autosecretary.features.meal.domain.Ingredient;
import com.autosecretary.features.meal.domain.MealPlan;
import com.autosecretary.features.meal.domain.PantryItem;
import com.autosecretary.features.meal.domain.Recipe;
import com.autosecretary.features.meal.domain.ShoppingItemStatus;
import com.autosecretary.features.meal.domain.ShoppingListItem;
import com.autosecretary.features.meal.ui.state.HouseholdMemberRowState;
import com.autosecretary.features.meal.ui.internal.MealHouseholdDialogController;
import com.autosecretary.features.meal.ui.internal.MealIngredientDialogController;
import com.autosecretary.features.meal.ui.internal.MealNeedDialogController;
import com.autosecretary.features.meal.ui.internal.MealPantryDialogController;
import com.autosecretary.features.meal.ui.internal.MealPlanDialogController;
import com.autosecretary.features.meal.ui.internal.MealRecipeDialogController;
import com.autosecretary.features.meal.ui.state.HomeState;
import com.autosecretary.features.meal.ui.state.WeeklyProgressFoodGroupState;
import com.autosecretary.features.meal.ui.state.WeeklyProgressState;
import com.autosecretary.shared.DateFormatters;
import com.autosecretary.shared.ui.SimpleButtonCheckedListener;
import com.autosecretary.shared.ui.SimpleItemSelectedListener;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class MealPlannerFragment extends Fragment {

    private enum ManageFilter {
        RECIPES, INGREDIENTS, PANTRY, HOUSEHOLD
    }

    private MealPlannerViewModel viewModel;
    private HomeState currentHome;

    // Progress views (inside scroll, visible in both modes)
    private TextView progressPeriod;
    private TextView caloriesSummary;
    private ProgressBar caloriesProgress;
    private LinearLayout foodGroupProgressList;

    // Dashboard views
    private View dashboard;
    private LinearLayout weekList;
    private View weekEmptyState;
    private LinearLayout shoppingOpenList;
    private LinearLayout shoppingDoneList;
    private TextView shoppingDoneTitle;
    private MaterialButton hideDoneButton;
    private boolean hideCompletedShoppingItems;

    // Management views
    private View manageScreen;
    private LinearLayout manageList;
    private View manageEmptyState;
    private MaterialButton manageAddButton;
    private Spinner manageFilterSpinner;
    private ManageFilter currentFilter = ManageFilter.RECIPES;

    // Dialog controllers
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
        if (viewModel == null) {
            MealPlannerViewModelFactory factory = ((MealPlannerDependencies) requireContext()
                    .getApplicationContext())
                    .getMealPlannerViewModelFactory();
            viewModel = new ViewModelProvider(this, factory).get(MealPlannerViewModel.class);
        }

        initDialogControllers();
        bindViews(view);
        setupModeToggle(view);
        setupDashboardActions(view);
        setupManageActions();
        observeViewModel();
        reloadHome();
        reloadProgress();
    }

    // ── Initialization ────────────────────────────────────────────────

    private void initDialogControllers() {
        planDialogController = new MealPlanDialogController(
                this,
                (recipeId, date, mealType, servings) ->
                        viewModel.planRecipe(recipeId, date, mealType, servings, () -> {
                            if (!isAdded()) return;
                            Toast.makeText(requireContext(), R.string.meal_success_plan_created, Toast.LENGTH_SHORT).show();
                            reloadManageList();
                        }),
                (recipe, servings) -> viewModel.scaleRecipePreview(recipe, servings));

        needDialogController = new MealNeedDialogController(this, (name, amount, unit) ->
                viewModel.createShoppingItemFromNeed(name, amount, unit, () -> {
                    if (!isAdded()) return;
                    Toast.makeText(requireContext(), R.string.meal_success_need_created, Toast.LENGTH_SHORT).show();
                }));

        pantryDialogController = new MealPantryDialogController(this, new MealPantryDialogController.Listener() {
            @Override
            public void onPantryItemSubmitted(String name, double amount, String unit,
                                              PantryItem.StorageLocation location, int shelfLifeDays) {
                viewModel.createPantryItem(name, amount, unit, location, shelfLifeDays, () -> {
                    if (!isAdded()) return;
                    Toast.makeText(requireContext(), R.string.meal_success_pantry_created, Toast.LENGTH_SHORT).show();
                    reloadManageList();
                });
            }

            @Override
            public void onPantryItemUpdated(PantryItem updated) {
                viewModel.savePantryItem(updated, () -> {
                    if (!isAdded()) return;
                    Toast.makeText(requireContext(), R.string.meal_success_pantry_saved, Toast.LENGTH_SHORT).show();
                    reloadManageList();
                });
            }

            @Override
            public void onPantryItemDeleted(String pantryItemId) {
                viewModel.deletePantryItem(pantryItemId, () -> {
                    if (!isAdded()) return;
                    Toast.makeText(requireContext(), R.string.meal_success_pantry_deleted, Toast.LENGTH_SHORT).show();
                    reloadManageList();
                });
            }
        });

        recipeDialogController = new MealRecipeDialogController(this, new MealRecipeDialogController.Listener() {
            @Override
            public void onRecipeSaved(Recipe recipe) {
                viewModel.saveRecipe(recipe, () -> {
                    if (!isAdded()) return;
                    Toast.makeText(requireContext(), R.string.meal_success_recipe_saved, Toast.LENGTH_SHORT).show();
                    reloadManageList();
                });
            }

            @Override
            public void onRecipeDeleted(String recipeId) {
                viewModel.deleteRecipe(recipeId, () -> {
                    if (!isAdded()) return;
                    Toast.makeText(requireContext(), R.string.meal_success_recipe_deleted, Toast.LENGTH_SHORT).show();
                    reloadManageList();
                });
            }
        });

        ingredientDialogController = new MealIngredientDialogController(this, new MealIngredientDialogController.Listener() {
            @Override
            public void onIngredientSaved(Ingredient ingredient) {
                viewModel.saveIngredient(ingredient, () -> {
                    if (!isAdded()) return;
                    Toast.makeText(requireContext(), R.string.meal_success_ingredient_saved, Toast.LENGTH_SHORT).show();
                    reloadManageList();
                });
            }

            @Override
            public void onIngredientDeleted(String ingredientId) {
                viewModel.deleteIngredient(ingredientId, () -> {
                    if (!isAdded()) return;
                    Toast.makeText(requireContext(), R.string.meal_success_ingredient_deleted, Toast.LENGTH_SHORT).show();
                    reloadManageList();
                });
            }
        });

        householdDialogController = new MealHouseholdDialogController(this, new MealHouseholdDialogController.Listener() {
            @Override
            public void onMemberSaved(HouseholdMember member) {
                viewModel.saveHouseholdMember(member, () -> {
                    if (!isAdded()) return;
                    Toast.makeText(requireContext(), R.string.meal_success_household_saved, Toast.LENGTH_SHORT).show();
                    reloadManageList();
                });
            }

            @Override
            public void onMemberDeleted(String memberId) {
                viewModel.deleteHouseholdMember(memberId, () -> {
                    if (!isAdded()) return;
                    Toast.makeText(requireContext(), R.string.meal_success_household_deleted, Toast.LENGTH_SHORT).show();
                    reloadManageList();
                });
            }
        });
    }

    private void bindViews(View view) {
        // Progress (inside scroll, visible in both modes)
        progressPeriod = view.findViewById(R.id.MealProgressPeriod);
        caloriesSummary = view.findViewById(R.id.MealCaloriesSummary);
        caloriesProgress = view.findViewById(R.id.MealCaloriesProgress);
        foodGroupProgressList = view.findViewById(R.id.MealFoodGroupProgressList);

        // Dashboard
        dashboard = view.findViewById(R.id.MealDashboard);
        weekList = view.findViewById(R.id.MealWeekList);
        weekEmptyState = dashboard.findViewById(R.id.EmptyStateContainer);
        ((TextView) dashboard.findViewById(R.id.EmptyStateTitle)).setText(R.string.meal_empty_week_title);
        ((TextView) dashboard.findViewById(R.id.EmptyStateSubtitle)).setText(R.string.meal_empty_week_subtitle);
        shoppingOpenList = view.findViewById(R.id.MealShoppingOpenList);
        shoppingDoneList = view.findViewById(R.id.MealShoppingDoneList);
        shoppingDoneTitle = view.findViewById(R.id.MealShoppingDoneTitle);
        hideDoneButton = view.findViewById(R.id.MealHideDone);

        // Management
        manageScreen = view.findViewById(R.id.MealManageScreen);
        manageList = view.findViewById(R.id.MealManageList);
        manageEmptyState = manageScreen.findViewById(R.id.EmptyStateContainer);
        manageAddButton = view.findViewById(R.id.MealManageAddButton);
        manageFilterSpinner = view.findViewById(R.id.MealManageFilterSpinner);
    }

    private void setupModeToggle(View view) {
        MaterialButtonToggleGroup modeToggle = view.findViewById(R.id.MealModeToggle);
        modeToggle.addOnButtonCheckedListener(new SimpleButtonCheckedListener() {
            @Override
            public void onChecked(MaterialButtonToggleGroup group, int checkedId) {
                boolean isManage = checkedId == R.id.MealModeManage;
                dashboard.setVisibility(isManage ? View.GONE : View.VISIBLE);
                manageScreen.setVisibility(isManage ? View.VISIBLE : View.GONE);
                manageFilterSpinner.setVisibility(isManage ? View.VISIBLE : View.GONE);
                manageAddButton.setVisibility(isManage ? View.VISIBLE : View.GONE);
                if (isManage) {
                    loadManageList();
                }
            }
        });

        ArrayAdapter<CharSequence> filterAdapter = ArrayAdapter.createFromResource(
                requireContext(), R.array.meal_manage_filter_labels,
                android.R.layout.simple_spinner_item);
        filterAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        manageFilterSpinner.setAdapter(filterAdapter);
        manageFilterSpinner.setOnItemSelectedListener(new SimpleItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                currentFilter = ManageFilter.values()[position];
                updateManageAddButton();
                loadManageList();
            }
        });
    }

    private void setupDashboardActions(View view) {
        MaterialButton addMealPlan = view.findViewById(R.id.MealAddPlan);
        MaterialButton addNeed = view.findViewById(R.id.MealAddNeed);
        addMealPlan.setContentDescription(getString(R.string.meal_add_plan_desc));
        addNeed.setContentDescription(getString(R.string.meal_add_need_desc));

        addMealPlan.setOnClickListener(v -> viewModel.openManagePlan(recipes -> {
            if (!isAdded()) return;
            planDialogController.show(recipes);
        }));
        addNeed.setOnClickListener(v -> viewModel.openManageNeed(() -> {
            if (!isAdded()) return;
            needDialogController.show();
        }));
        hideDoneButton.setOnClickListener(v -> {
            hideCompletedShoppingItems = !hideCompletedShoppingItems;
            updateHideDoneButton();
            if (currentHome != null) renderShopping(currentHome);
        });
        updateHideDoneButton();
    }

    private void setupManageActions() {
        manageAddButton.setOnClickListener(v -> openManageAddDialog());
        updateManageAddButton();
    }

    private void observeViewModel() {
        viewModel.getWeeklyProgress().observe(getViewLifecycleOwner(), progress -> {
            if (progress == null || !isAdded()) {
                return;
            }
            renderProgressSection(progress);
        });
        viewModel.getHomeState().observe(getViewLifecycleOwner(), home -> {
            if (home == null || !isAdded()) {
                return;
            }
            currentHome = home;
            renderDashboard(home);
        });
    }

    // ── Progress rendering (always visible) ────────────────────────────

    private void reloadProgress() {
        viewModel.reloadWeeklyProgress();
    }

    private void renderProgressSection(WeeklyProgressState progress) {
        progressPeriod.setText(getString(R.string.meal_progress_period_format,
                progress.fromDate().format(DateFormatters.DATE_SHORT),
                progress.toDate().format(DateFormatters.DATE_SHORT)));

        caloriesSummary.setText(getString(R.string.meal_progress_calorie_summary,
                progress.calorieActual(),
                progress.calorieTarget(),
                progress.calorieCompletionPercent()));
        caloriesProgress.setProgress(Math.max(0, Math.min(100, progress.calorieCompletionPercent())));

        foodGroupProgressList.removeAllViews();
        for (WeeklyProgressFoodGroupState group : progress.foodGroups()) {
            View row = LayoutInflater.from(requireContext()).inflate(
                    R.layout.meal_progress_row_item, foodGroupProgressList, false);
            TextView groupTitle = row.findViewById(R.id.MealProgressRowTitle);
            ProgressBar bar = row.findViewById(R.id.MealProgressRowBar);
            TextView percent = row.findViewById(R.id.MealProgressRowPercent);

            groupTitle.setText(getString(R.string.meal_progress_food_group_title,
                    group.foodGroup().icon,
                    group.foodGroup().label));
            bar.setProgress(Math.max(0, Math.min(100, group.completionPercent())));
            percent.setText(getString(R.string.meal_progress_food_group_percent,
                    group.completionPercent()));
            foodGroupProgressList.addView(row);
        }
    }

    // ── Dashboard rendering ───────────────────────────────────────────

    private void reloadHome() {
        viewModel.reloadHome();
    }

    private void renderDashboard(HomeState home) {
        renderMealPlans(home);
        renderShopping(home);
    }

    private void renderMealPlans(HomeState home) {
        weekList.removeAllViews();
        boolean empty = home.weekPlans().isEmpty();
        weekEmptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
        weekList.setVisibility(empty ? View.GONE : View.VISIBLE);
        for (MealPlan plan : home.weekPlans()) {
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
                done.setOnClickListener(v -> viewModel.toggleMealCompleted(plan.id));
            }
            weekList.addView(planRow);
        }
    }

    private void renderShopping(HomeState home) {
        shoppingOpenList.removeAllViews();
        shoppingDoneList.removeAllViews();
        if (home.shoppingItems().isEmpty()) {
            shoppingOpenList.addView(inflateTextRow(getString(R.string.meal_empty_shopping), shoppingOpenList));
            shoppingDoneTitle.setVisibility(View.GONE);
            shoppingDoneList.setVisibility(View.GONE);
            return;
        }

        List<ShoppingListItem> openItems = new ArrayList<>();
        List<ShoppingListItem> doneItems = new ArrayList<>();
        for (ShoppingListItem item : home.shoppingItems()) {
            if (item.isDone()) {
                doneItems.add(item);
            } else {
                openItems.add(item);
            }
        }

        if (openItems.isEmpty()) {
            shoppingOpenList.addView(inflateTextRow(getString(R.string.meal_empty_shopping_open), shoppingOpenList));
        } else {
            for (ShoppingListItem item : openItems) {
                shoppingOpenList.addView(inflateShoppingRow(item, shoppingOpenList));
            }
        }

        boolean showDoneSection = !hideCompletedShoppingItems;
        shoppingDoneTitle.setVisibility(showDoneSection ? View.VISIBLE : View.GONE);
        shoppingDoneList.setVisibility(showDoneSection ? View.VISIBLE : View.GONE);
        if (showDoneSection) {
            if (doneItems.isEmpty()) {
                shoppingDoneList.addView(inflateTextRow(getString(R.string.meal_empty_shopping_done), shoppingDoneList));
            } else {
                for (ShoppingListItem item : doneItems) {
                    shoppingDoneList.addView(inflateShoppingRow(item, shoppingDoneList));
                }
            }
        }
    }

    // ── Management rendering ──────────────────────────────────────────

    private void reloadManageList() {
        if (manageScreen.getVisibility() == View.VISIBLE) {
            loadManageList();
        }
    }

    private void loadManageList() {
        switch (currentFilter) {
            case RECIPES:
                viewModel.loadRecipesForManagement(recipes -> {
                    if (!isAdded()) return;
                    renderManageRecipes(recipes);
                });
                break;
            case INGREDIENTS:
                viewModel.loadIngredientsForManagement(ingredients -> {
                    if (!isAdded()) return;
                    renderManageIngredients(ingredients);
                });
                break;
            case PANTRY:
                viewModel.loadPantryItemsForManagement(items -> {
                    if (!isAdded()) return;
                    renderManagePantry(items);
                });
                break;
            case HOUSEHOLD:
                viewModel.loadHouseholdMemberRowsForManagement(members -> {
                    if (!isAdded()) return;
                    renderManageHousehold(members);
                });
                break;
        }
    }

    private void renderManageRecipes(List<Recipe> recipes) {
        manageList.removeAllViews();
        boolean empty = recipes.isEmpty();
        manageEmptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
        ((TextView) manageScreen.findViewById(R.id.EmptyStateTitle)).setText(R.string.meal_manage_empty_recipes);
        ((TextView) manageScreen.findViewById(R.id.EmptyStateSubtitle)).setText("");
        manageList.setVisibility(empty ? View.GONE : View.VISIBLE);
        for (Recipe recipe : recipes) {
            View row = inflateTextRow(recipe.title, manageList);
            row.setOnClickListener(v ->
                    viewModel.loadIngredientsForManagement(ingredients -> {
                        if (!isAdded()) return;
                        recipeDialogController.showEdit(recipe, ingredients);
                    }));
            manageList.addView(row);
        }
    }

    private void renderManageIngredients(List<Ingredient> ingredients) {
        manageList.removeAllViews();
        boolean empty = ingredients.isEmpty();
        manageEmptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
        ((TextView) manageScreen.findViewById(R.id.EmptyStateTitle)).setText(R.string.meal_manage_empty_ingredients);
        ((TextView) manageScreen.findViewById(R.id.EmptyStateSubtitle)).setText("");
        manageList.setVisibility(empty ? View.GONE : View.VISIBLE);
        for (Ingredient ingredient : ingredients) {
            View row = LayoutInflater.from(requireContext()).inflate(
                    R.layout.meal_ingredient_row_item, manageList, false);
            TextView icon = row.findViewById(R.id.MealIngredientRowIcon);
            TextView title = row.findViewById(R.id.MealIngredientRowTitle);
            TextView subtitle = row.findViewById(R.id.MealIngredientRowSubtitle);
            icon.setText(ingredient.foodGroup.icon);
            title.setText(ingredient.name);
            subtitle.setText(getString(R.string.meal_ingredient_row_subtitle_format,
                    ingredient.defaultUnit, ingredient.caloriesPer100));
            row.setOnClickListener(v -> ingredientDialogController.showEdit(ingredient));
            manageList.addView(row);
        }
    }

    private void renderManagePantry(List<PantryItem> items) {
        manageList.removeAllViews();
        boolean empty = items.isEmpty();
        manageEmptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
        ((TextView) manageScreen.findViewById(R.id.EmptyStateTitle)).setText(R.string.meal_manage_empty_pantry);
        ((TextView) manageScreen.findViewById(R.id.EmptyStateSubtitle)).setText("");
        manageList.setVisibility(empty ? View.GONE : View.VISIBLE);
        LocalDate today = LocalDate.now();
        for (PantryItem item : items) {
            View row = LayoutInflater.from(requireContext()).inflate(
                    R.layout.meal_pantry_row_item, manageList, false);
            TextView icon = row.findViewById(R.id.MealPantryRowIcon);
            TextView title = row.findViewById(R.id.MealPantryRowTitle);
            TextView subtitle = row.findViewById(R.id.MealPantryRowSubtitle);
            icon.setText(item.location != null ? item.location.icon : "");
            title.setText(item.ingredientName);
            subtitle.setText(getString(R.string.meal_pantry_row_format_amount,
                    item.getFormattedAmount(), item.getExpiryInfo(today)));
            row.setOnClickListener(v -> pantryDialogController.showEdit(item));
            manageList.addView(row);
        }
    }

    private void renderManageHousehold(List<HouseholdMemberRowState> members) {
        manageList.removeAllViews();
        boolean empty = members.isEmpty();
        manageEmptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
        ((TextView) manageScreen.findViewById(R.id.EmptyStateTitle)).setText(R.string.meal_manage_empty_household);
        ((TextView) manageScreen.findViewById(R.id.EmptyStateSubtitle)).setText("");
        manageList.setVisibility(empty ? View.GONE : View.VISIBLE);
        for (HouseholdMemberRowState rowState : members) {
            HouseholdMember member = rowState.member();
            View row = LayoutInflater.from(requireContext()).inflate(
                    R.layout.meal_household_row_item, manageList, false);
            TextView title = row.findViewById(R.id.MealHouseholdRowTitle);
            TextView subtitle = row.findViewById(R.id.MealHouseholdRowSubtitle);
            String name = member.name;
            if (!member.isActive) name += " " + getString(R.string.meal_household_row_inactive);
            title.setText(name);
            subtitle.setText(getString(R.string.meal_household_row_subtitle_format,
                    rowState.age(), member.gender.toString(), rowState.tdee()));
            row.setOnClickListener(v -> householdDialogController.showEdit(member));
            manageList.addView(row);
        }
    }

    // ── Management actions ────────────────────────────────────────────

    private void openManageAddDialog() {
        switch (currentFilter) {
            case RECIPES:
                viewModel.loadIngredientsForManagement(ingredients -> {
                    if (!isAdded()) return;
                    recipeDialogController.show(ingredients);
                });
                break;
            case INGREDIENTS:
                ingredientDialogController.show();
                break;
            case PANTRY:
                pantryDialogController.show();
                break;
            case HOUSEHOLD:
                householdDialogController.show();
                break;
        }
    }

    private void updateManageAddButton() {
        switch (currentFilter) {
            case RECIPES:
                manageAddButton.setText(R.string.meal_manage_add_recipe);
                break;
            case INGREDIENTS:
                manageAddButton.setText(R.string.meal_manage_add_ingredient);
                break;
            case PANTRY:
                manageAddButton.setText(R.string.meal_manage_add_pantry);
                break;
            case HOUSEHOLD:
                manageAddButton.setText(R.string.meal_manage_add_household);
                break;
        }
    }

    // ── Shared helpers ────────────────────────────────────────────────

    private void setMealPlanButtonState(MaterialButton button, MealPlan plan) {
        button.setText(plan.isCompleted ? getString(R.string.meal_mark_open) : getString(R.string.meal_mark_done));
        button.setContentDescription(plan.isCompleted
                ? getString(R.string.meal_mark_open_desc, plan.recipeTitle)
                : getString(R.string.meal_mark_done_desc, plan.recipeTitle));
        button.setIconResource(plan.isCompleted ? R.drawable.ic_check_24 : 0);
    }

    private void updateHideDoneButton() {
        hideDoneButton.setText(hideCompletedShoppingItems
                ? R.string.meal_show_done
                : R.string.meal_hide_done);
    }

    private TextView inflateTextRow(String text, ViewGroup parent) {
        TextView row = (TextView) LayoutInflater.from(requireContext()).inflate(R.layout.meal_text_row_item, parent, false);
        row.setText(text);
        row.setContentDescription(text);
        return row;
    }

    private View inflateShoppingRow(ShoppingListItem item, ViewGroup parent) {
        View row = LayoutInflater.from(requireContext()).inflate(R.layout.meal_shopping_row_item, parent, false);
        TextView text = row.findViewById(R.id.MealShoppingRowText);
        android.widget.CheckBox checkbox = row.findViewById(R.id.MealShoppingRowCheck);
        String rowText = getString(R.string.meal_shopping_row_format, item.ingredientName, item.getFormattedAmount());
        text.setText(rowText);
        String statusLabel = getString(item.isDone() ? R.string.meal_shopping_status_done : R.string.meal_shopping_status_open);
        row.setContentDescription(getString(R.string.meal_shopping_row_desc, item.ingredientName, item.getFormattedAmount(), statusLabel));
        checkbox.setChecked(item.isDone());
        checkbox.setOnClickListener(v -> {
            if (item.id == null) return;
            ShoppingItemStatus newStatus = checkbox.isChecked() ? ShoppingItemStatus.DONE : ShoppingItemStatus.OPEN;
            viewModel.updateShoppingItemStatus(item.id, newStatus);
        });
        return row;
    }
}

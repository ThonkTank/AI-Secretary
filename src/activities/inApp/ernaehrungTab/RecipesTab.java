package activities.inApp.ernaehrungTab;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import static activities.generic.ViewHelper.dp;
import static activities.generic.ViewHelper.parseDouble;
import static activities.generic.ViewHelper.parseInt;
import static activities.generic.ViewHelper.roundedBg;
import static activities.generic.ViewHelper.setupModalOverlay;
import static activities.generic.ViewHelper.showEmptyState;
import static activities.generic.ViewHelper.spinnerAdapter;

import androidx.core.content.ContextCompat;

import com.autosecretary.R;

import java.util.ArrayList;
import java.util.List;

import controller.MealManager;
import controller.MealManager.*;
import entities.MealType;
import entities.Recipe;

/**
 * Rezepte-Tab: Grid-Ansicht aller Rezepte + Create/Edit-Modal.
 *
 * Extrahiert aus MealPlanView nach Vorbild von WeekPlanTab.
 */
public class RecipesTab {

    private final Context context;
    private final MealManager manager;

    // Recipe Modal References
    private FrameLayout modalOverlay;
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

    // State
    private Recipe editingRecipe = null;
    private List<RecipeEntry> recipesList = new ArrayList<>();
    private List<IngredientEntry> ingredientsList = new ArrayList<>();
    private List<IngredientRowState> ingredientRows = new ArrayList<>();

    private record IngredientRowState(
        View view,
        Spinner spinner,
        EditText amountInput,
        TextView unitLabel,
        Long ingredientId,
        String ingredientName
    ) {}

    // Callback um MealPlanView ueber Aenderungen zu informieren
    private MealTabListener listener;

    // ============================================================================
    // CONSTRUCTOR + INIT
    // ============================================================================

    public RecipesTab(Context context, MealManager manager) {
        this.context = context;
        this.manager = manager;
    }

    public void setListener(MealTabListener listener) {
        this.listener = listener;
    }

    /**
     * Inflated und bindet das Rezept-Modal.
     * Jeder Sub-Tab besitzt sein eigenes Modal (kein Zugriff auf Parent-Layout).
     */
    public void initModals(FrameLayout rootContainer) {
        modalOverlay = (FrameLayout) LayoutInflater.from(context)
            .inflate(R.layout.modal_recipe, rootContainer, false);
        modalOverlay.setVisibility(View.GONE);
        rootContainer.addView(modalOverlay);

        modalTitle = modalOverlay.findViewById(R.id.modal_title);
        inputTitle = modalOverlay.findViewById(R.id.input_title);
        spinnerMealType = modalOverlay.findViewById(R.id.spinner_meal_type);
        inputPrepTime = modalOverlay.findViewById(R.id.input_prep_time);
        inputCookTime = modalOverlay.findViewById(R.id.input_cook_time);
        inputServings = modalOverlay.findViewById(R.id.input_servings);
        ingredientsContainer = modalOverlay.findViewById(R.id.ingredients_container);
        btnAddIngredient = modalOverlay.findViewById(R.id.btn_add_ingredient);
        inputInstructions = modalOverlay.findViewById(R.id.input_instructions);
        inputTags = modalOverlay.findViewById(R.id.input_tags);
        btnCancel = modalOverlay.findViewById(R.id.btn_cancel);
        btnSave = modalOverlay.findViewById(R.id.btn_save);

        setupModal();
    }

    // ============================================================================
    // RENDER - Rezept-Grid (2 Spalten)
    // ============================================================================

    public void render(FrameLayout container) {
        container.removeAllViews();
        recipesList = manager.provideAllRecipes();

        if (recipesList.isEmpty()) {
            showEmptyState(container, "Noch keine Rezepte vorhanden.\nTippe auf + um ein Rezept zu erstellen.");
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

            // Layout-Params fuer halbe Breite
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            params.setMargins(0, 0, dp(context, 8), dp(context, 8));
            card.setLayoutParams(params);

            currentRow.addView(card);
        }

        // Falls ungerade Anzahl, Platzhalter hinzufuegen
        if (recipesList.size() % 2 == 1 && currentRow != null) {
            View spacer = new View(context);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, 0, 1f);
            spacer.setLayoutParams(params);
            currentRow.addView(spacer);
        }

        container.addView(gridContainer);
    }

    // ============================================================================
    // FAB
    // ============================================================================

    public void openCreateModal() {
        showRecipeModal(null);
    }

    // ============================================================================
    // RECIPE MODAL
    // ============================================================================

    private void setupModal() {
        // MealType Spinner befuellen
        String[] mealTypes = {"Frühstück", "Mittagessen", "Abendessen", "Snack"};
        spinnerMealType.setAdapter(spinnerAdapter(context, mealTypes));

        // Buttons
        btnAddIngredient.setOnClickListener(v -> addIngredientRow());
        btnCancel.setOnClickListener(v -> hideModal());
        btnSave.setOnClickListener(v -> saveRecipe());

        setupModalOverlay(modalOverlay, this::hideModal);
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
            spinnerMealType.setSelection(recipe.mealType.ordinal());
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

        // Spinner mit Zutaten befuellen
        List<String> names = new ArrayList<>();
        names.add("-- Zutat wählen --");
        for (IngredientEntry ing : ingredientsList) {
            names.add(ing.name());
        }
        spinner.setAdapter(spinnerAdapter(context, names));

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

        // Wenn bestehendes Ingredient, vorausfuellen
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

        MealType mealType = MealType.values()[spinnerMealType.getSelectedItemPosition()];

        int prepTime = parseInt(inputPrepTime, 0);
        int cookTime = parseInt(inputCookTime, 0);
        int servings = parseInt(inputServings, 2);

        String instructions = inputInstructions.getText().toString().trim();
        String tags = inputTags.getText().toString().trim();

        Recipe.Builder builder = new Recipe.Builder(title, mealType)
            .servings(servings)
            .prepTime(prepTime)
            .cookTime(cookTime);

        if (!instructions.isEmpty()) builder.instructions(instructions);
        if (!tags.isEmpty()) builder.tags(tags);

        // Zutaten hinzufuegen
        for (IngredientRowState row : ingredientRows) {
            int position = row.spinner.getSelectedItemPosition();
            if (position > 0) {
                IngredientEntry ing = ingredientsList.get(position - 1);
                double amount = parseDouble(row.amountInput, 100);

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
        notifyChanged();
    }

    // ============================================================================
    // HELPERS
    // ============================================================================

    private void notifyChanged() {
        if (listener != null) listener.onDataChanged();
    }
}

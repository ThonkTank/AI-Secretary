package com.autosecretary.features.assistant.application.internal;

import static com.autosecretary.features.assistant.application.internal.AssistantJson.asString;
import static com.autosecretary.features.assistant.application.internal.AssistantJson.optString;
import static com.autosecretary.features.assistant.application.internal.AssistantJson.requireArray;
import static com.autosecretary.features.assistant.application.internal.AssistantJson.requireDate;

import com.autosecretary.features.meal.domain.Ingredient;
import com.autosecretary.features.meal.domain.MealPlan;
import com.autosecretary.features.meal.domain.Recipe;
import com.autosecretary.features.assistant.application.AssistantProposals.IngredientsProposal;
import com.autosecretary.features.assistant.application.AssistantProposals.MealPlanDraft;
import com.autosecretary.features.assistant.application.AssistantProposals.MealPlansProposal;
import com.autosecretary.features.assistant.application.AssistantProposals.PendingProposal;
import com.autosecretary.features.assistant.application.AssistantProposals.RecipesProposal;
import com.autosecretary.features.assistant.application.internal.AssistantMealGateway;
import com.autosecretary.shared.ClaudeApiException;
import com.autosecretary.shared.MealType;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The assistant's meal tools: read recipes/ingredients/meal-plans and propose new
 * recipes/ingredients/meal-plan entries as parked write-intents. All meal access goes through
 * {@link AssistantMealGateway} (the foreign meal-domain seam); each tool's wire schema sits directly
 * above the parser it must match.
 */
public final class MealTools {

    private static final String GET_RECIPES_DESCRIPTION =
            "Liefert alle Rezepte inklusive Zutaten (ingredients), Nährwerten, mealTypes und tags.";
    private static final String GET_RECIPES_SCHEMA = "{\"type\":\"object\",\"properties\":{}}";

    private static final String GET_INGREDIENTS_DESCRIPTION =
            "Liefert alle bekannten Zutaten inklusive foodGroup und Nährwerten. Nährwerte der Makros "
            + "sind ×10-Festkomma (125 = 12,5 g pro 100 g); caloriesPer100 ist reine kcal.";
    private static final String GET_INGREDIENTS_SCHEMA = "{\"type\":\"object\",\"properties\":{}}";

    private static final String GET_MEAL_PLANS_DESCRIPTION =
            "Liefert die Wochenplan-Einträge im Datumsbereich [from, to] (ISO-Datum, inklusiv).";
    private static final String GET_MEAL_PLANS_SCHEMA =
            "{\"type\":\"object\",\"properties\":{"
            + "\"from\":{\"type\":\"string\",\"description\":\"Startdatum YYYY-MM-DD\"},"
            + "\"to\":{\"type\":\"string\",\"description\":\"Enddatum YYYY-MM-DD\"}},"
            + "\"required\":[\"from\",\"to\"]}";

    private static final String PROPOSE_RECIPES_DESCRIPTION =
            "Schlägt neue Rezepte vor (erst nach Bestätigung gespeichert). ingredients referenzieren "
            + "bestehende Zutaten per name; unbekannte Namen werden als freie Textzutat übernommen.";
    private static final String PROPOSE_RECIPES_SCHEMA =
            "{\"type\":\"object\",\"properties\":{"
            + "\"recipes\":{\"type\":\"array\",\"items\":{\"type\":\"object\",\"properties\":{"
            + "\"title\":{\"type\":\"string\"},\"description\":{\"type\":\"string\"},"
            + "\"instructions\":{\"type\":\"string\"},"
            + "\"mealTypes\":{\"type\":\"array\",\"items\":{\"type\":\"string\"}},"
            + "\"servings\":{\"type\":\"integer\"},\"prepTimeMinutes\":{\"type\":\"integer\"},"
            + "\"cookTimeMinutes\":{\"type\":\"integer\"},\"tags\":{\"type\":\"string\"},"
            + "\"ingredients\":{\"type\":\"array\",\"items\":{\"type\":\"object\",\"properties\":{"
            + "\"name\":{\"type\":\"string\"},\"amount\":{\"type\":\"number\"},\"unit\":{\"type\":\"string\"}},"
            + "\"required\":[\"name\"]}}},\"required\":[\"title\"]}}},\"required\":[\"recipes\"]}";

    private static final String PROPOSE_INGREDIENTS_DESCRIPTION =
            "Schlägt neue Zutaten vor (erst nach Bestätigung gespeichert). foodGroup ist einer der DGE-"
            + "Werte (GRAIN, POTATO, VEGETABLE, FRUIT, DAIRY, MEAT, FISH, EGG, FAT, LEGUME, NUT, OTHER). "
            + "Makros sind ×10-Festkomma (125 = 12,5 g), caloriesPer100 reine kcal.";
    private static final String PROPOSE_INGREDIENTS_SCHEMA =
            "{\"type\":\"object\",\"properties\":{"
            + "\"ingredients\":{\"type\":\"array\",\"items\":{\"type\":\"object\",\"properties\":{"
            + "\"name\":{\"type\":\"string\"},\"foodGroup\":{\"type\":\"string\"},"
            + "\"unit\":{\"type\":\"string\"},\"gramsPerUnit\":{\"type\":\"integer\"},"
            + "\"caloriesPer100\":{\"type\":\"integer\"},\"proteinPer100\":{\"type\":\"integer\"},"
            + "\"carbsPer100\":{\"type\":\"integer\"},\"fatPer100\":{\"type\":\"integer\"},"
            + "\"fiberPer100\":{\"type\":\"integer\"},\"shelfLifeDays\":{\"type\":\"integer\"}},"
            + "\"required\":[\"name\",\"foodGroup\"]}}},\"required\":[\"ingredients\"]}";

    private static final String PROPOSE_MEAL_PLANS_DESCRIPTION =
            "Schlägt Wochenplan-Einträge vor (erst nach Bestätigung gespeichert). Jeder Eintrag "
            + "identifiziert sein Rezept per recipeId ODER recipeTitle (Titel erlaubt Rezepte, die im "
            + "selben Chat gerade vorgeschlagen wurden). mealType: BREAKFAST/LUNCH/DINNER/SNACK.";
    private static final String PROPOSE_MEAL_PLANS_SCHEMA =
            "{\"type\":\"object\",\"properties\":{"
            + "\"entries\":{\"type\":\"array\",\"items\":{\"type\":\"object\",\"properties\":{"
            + "\"date\":{\"type\":\"string\",\"description\":\"YYYY-MM-DD\"},"
            + "\"mealType\":{\"type\":\"string\"},\"recipeId\":{\"type\":\"string\"},"
            + "\"recipeTitle\":{\"type\":\"string\"},\"servings\":{\"type\":\"integer\"}},"
            + "\"required\":[\"date\",\"mealType\"]}}},\"required\":[\"entries\"]}";

    private final AssistantMealGateway gateway;
    private final DbCalls db;

    public MealTools(AssistantMealGateway gateway, DbCalls db) {
        this.gateway = gateway;
        this.db = db;
    }

    public List<AssistantTool> tools() {
        return List.of(
                AssistantTool.read("get_recipes", GET_RECIPES_DESCRIPTION, GET_RECIPES_SCHEMA,
                        "Prüfe Rezepte…", input -> getRecipes()),
                AssistantTool.read("get_ingredients", GET_INGREDIENTS_DESCRIPTION, GET_INGREDIENTS_SCHEMA,
                        "Prüfe Zutaten…", input -> getIngredients()),
                AssistantTool.read("get_meal_plans", GET_MEAL_PLANS_DESCRIPTION, GET_MEAL_PLANS_SCHEMA,
                        "Prüfe Wochenpläne…", this::getMealPlans),
                AssistantTool.proposal("propose_recipes", PROPOSE_RECIPES_DESCRIPTION, PROPOSE_RECIPES_SCHEMA,
                        AssistantTool.PROGRESS_PROPOSAL, this::proposeRecipes),
                AssistantTool.proposal("propose_ingredients", PROPOSE_INGREDIENTS_DESCRIPTION,
                        PROPOSE_INGREDIENTS_SCHEMA, AssistantTool.PROGRESS_PROPOSAL, this::proposeIngredients),
                AssistantTool.proposal("propose_meal_plans", PROPOSE_MEAL_PLANS_DESCRIPTION,
                        PROPOSE_MEAL_PLANS_SCHEMA, AssistantTool.PROGRESS_PROPOSAL, this::proposeMealPlans));
    }

    // ---- read tools -----------------------------------------------------------

    private String getRecipes() {
        List<Recipe> recipes = db.call(gateway::recipes);
        try {
            JSONArray array = new JSONArray();
            for (Recipe recipe : recipes) {
                JSONArray ingredients = new JSONArray();
                if (recipe.ingredients != null) {
                    for (Recipe.RecipeIngredient ingredient : recipe.ingredients) {
                        ingredients.put(new JSONObject()
                                .put("name", ingredient.ingredientName())
                                .put("amount", ingredient.amount())
                                .put("unit", ingredient.unit()));
                    }
                }
                array.put(new JSONObject()
                        .put("id", recipe.id)
                        .put("title", recipe.title)
                        .put("description", recipe.description)
                        .put("mealTypes", mealTypesJson(recipe.mealTypes))
                        .put("servings", recipe.servings)
                        .put("totalCalories", recipe.totalCalories)
                        .put("tags", recipe.tags)
                        .put("ingredients", ingredients));
            }
            return new JSONObject().put("recipes", array).toString();
        } catch (JSONException e) {
            throw new ClaudeApiException("Rezepte konnten nicht serialisiert werden: " + e.getMessage(), e);
        }
    }

    private static JSONArray mealTypesJson(Set<MealType> mealTypes) {
        JSONArray array = new JSONArray();
        if (mealTypes != null) {
            for (MealType mealType : mealTypes) {
                array.put(mealType.name());
            }
        }
        return array;
    }

    private String getIngredients() {
        List<Ingredient> ingredients = db.call(gateway::ingredients);
        try {
            JSONArray array = new JSONArray();
            for (Ingredient ingredient : ingredients) {
                array.put(new JSONObject()
                        .put("id", ingredient.id)
                        .put("name", ingredient.name)
                        .put("foodGroup", ingredient.foodGroup != null ? ingredient.foodGroup.name() : null)
                        .put("unit", ingredient.defaultUnit)
                        .put("caloriesPer100", ingredient.caloriesPer100)
                        .put("proteinPer100", ingredient.proteinPer100)
                        .put("carbsPer100", ingredient.carbsPer100)
                        .put("fatPer100", ingredient.fatPer100)
                        .put("fiberPer100", ingredient.fiberPer100));
            }
            return new JSONObject().put("ingredients", array).toString();
        } catch (JSONException e) {
            throw new ClaudeApiException("Zutaten konnten nicht serialisiert werden: " + e.getMessage(), e);
        }
    }

    private String getMealPlans(JSONObject input) {
        LocalDate from = requireDate(input, "from");
        LocalDate to = requireDate(input, "to");
        List<MealPlan> plans = db.call(() -> gateway.mealPlans(from, to));
        try {
            JSONArray array = new JSONArray();
            for (MealPlan plan : plans) {
                array.put(new JSONObject()
                        .put("date", asString(plan.date))
                        .put("mealType", plan.mealType != null ? plan.mealType.name() : null)
                        .put("recipeTitle", plan.recipeTitle)
                        .put("servings", plan.plannedServings)
                        .put("completed", plan.isCompleted)
                        .put("kcal", plan.estimatedCalories));
            }
            return new JSONObject().put("mealPlans", array).toString();
        } catch (JSONException e) {
            throw new ClaudeApiException("Wochenplan konnte nicht serialisiert werden: " + e.getMessage(), e);
        }
    }

    // ---- proposal tools -------------------------------------------------------

    private PendingProposal proposeRecipes(JSONObject input) {
        JSONArray recipesJson = requireArray(input, "recipes");
        Map<String, String> ingredientIdsByName = db.call(() -> {
            Map<String, String> map = new HashMap<>();
            for (Ingredient ingredient : gateway.ingredients()) {
                if (ingredient.name != null) {
                    map.put(ingredient.name.trim().toLowerCase(Locale.ROOT), ingredient.id);
                }
            }
            return map;
        });

        List<Recipe> recipes = new ArrayList<>();
        for (int i = 0; i < recipesJson.length(); i++) {
            JSONObject entry = recipesJson.optJSONObject(i);
            if (entry == null) {
                continue;
            }
            String title = optString(entry, "title");
            if (title == null) {
                throw new IllegalArgumentException("Rezept ohne Titel im Vorschlag.");
            }
            Recipe.Builder builder = new Recipe.Builder(title)
                    .description(optString(entry, "description"))
                    .instructions(optString(entry, "instructions"))
                    .prepTime(entry.optInt("prepTimeMinutes", 0))
                    .cookTime(entry.optInt("cookTimeMinutes", 0))
                    .tags(optString(entry, "tags"));
            if (entry.has("servings")) {
                builder.servings(entry.optInt("servings"));
            }
            for (MealType mealType : parseMealTypes(entry.optJSONArray("mealTypes"))) {
                builder.mealType(mealType);
            }
            JSONArray ingredientsJson = entry.optJSONArray("ingredients");
            if (ingredientsJson != null) {
                for (int j = 0; j < ingredientsJson.length(); j++) {
                    JSONObject ingredient = ingredientsJson.optJSONObject(j);
                    if (ingredient == null) {
                        continue;
                    }
                    String name = optString(ingredient, "name");
                    if (name == null) {
                        continue;
                    }
                    String ingredientId = ingredientIdsByName.get(name.trim().toLowerCase(Locale.ROOT));
                    builder.ingredient(ingredientId, name, ingredient.optDouble("amount", 0),
                            optString(ingredient, "unit"));
                }
            }
            recipes.add(builder.build());
        }
        if (recipes.isEmpty()) {
            throw new IllegalArgumentException("Der Vorschlag enthält keine Rezepte.");
        }
        return new RecipesProposal(recipes);
    }

    private PendingProposal proposeIngredients(JSONObject input) {
        JSONArray ingredientsJson = requireArray(input, "ingredients");
        List<Ingredient> ingredients = new ArrayList<>();
        for (int i = 0; i < ingredientsJson.length(); i++) {
            JSONObject entry = ingredientsJson.optJSONObject(i);
            if (entry == null) {
                continue;
            }
            String name = optString(entry, "name");
            if (name == null) {
                throw new IllegalArgumentException("Zutat ohne Namen im Vorschlag.");
            }
            Ingredient.Builder builder = new Ingredient.Builder(name, parseFoodGroup(optString(entry, "foodGroup")))
                    .calories(entry.optInt("caloriesPer100", 0))
                    .protein(entry.optInt("proteinPer100", 0))
                    .carbs(entry.optInt("carbsPer100", 0))
                    .fat(entry.optInt("fatPer100", 0))
                    .fiber(entry.optInt("fiberPer100", 0))
                    .shelfLife(entry.optInt("shelfLifeDays", 0));
            String unit = optString(entry, "unit");
            if (unit != null) {
                builder.unit(unit, entry.optInt("gramsPerUnit", 1));
            }
            ingredients.add(builder.build());
        }
        if (ingredients.isEmpty()) {
            throw new IllegalArgumentException("Der Vorschlag enthält keine Zutaten.");
        }
        return new IngredientsProposal(ingredients);
    }

    private static Ingredient.FoodGroup parseFoodGroup(String raw) {
        if (raw == null) {
            return Ingredient.FoodGroup.OTHER;
        }
        try {
            return Ingredient.FoodGroup.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unbekannte foodGroup: " + raw);
        }
    }

    private PendingProposal proposeMealPlans(JSONObject input) {
        JSONArray entriesJson = requireArray(input, "entries");
        List<MealPlanDraft> entries = new ArrayList<>();
        for (int i = 0; i < entriesJson.length(); i++) {
            JSONObject entry = entriesJson.optJSONObject(i);
            if (entry == null) {
                continue;
            }
            LocalDate date = requireDate(entry, "date");
            MealType mealType = parseMealType(optString(entry, "mealType"));
            String recipeId = optString(entry, "recipeId");
            String recipeTitle = optString(entry, "recipeTitle");
            if (recipeId == null && recipeTitle == null) {
                throw new IllegalArgumentException("Wochenplan-Eintrag ohne recipeId/recipeTitle.");
            }
            entries.add(new MealPlanDraft(date, mealType, recipeId, recipeTitle, entry.optInt("servings", 0)));
        }
        if (entries.isEmpty()) {
            throw new IllegalArgumentException("Der Vorschlag enthält keine Wochenplan-Einträge.");
        }
        return new MealPlansProposal(entries);
    }

    private Set<MealType> parseMealTypes(JSONArray array) {
        Set<MealType> mealTypes = new HashSet<>();
        if (array != null) {
            for (int i = 0; i < array.length(); i++) {
                String raw = array.optString(i, null);
                if (raw != null && !raw.isBlank()) {
                    mealTypes.add(parseMealType(raw));
                }
            }
        }
        return mealTypes;
    }

    private static MealType parseMealType(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("mealType fehlt.");
        }
        try {
            return MealType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unbekannter mealType: " + raw);
        }
    }
}

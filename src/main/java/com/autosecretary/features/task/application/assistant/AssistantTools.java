package com.autosecretary.features.task.application.assistant;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

/**
 * The tool catalogue the assistant may call: read tools ({@code get_*}) that answer questions from
 * real local data, and proposal tools ({@code propose_*}) that park a write-intent for the user to
 * confirm. Descriptions are German and document when to call each tool and the shape it returns or
 * expects; {@link AssistantChatUseCase} dispatches by tool name and executes them.
 */
public final class AssistantTools {

    /** Read tools run immediately during the loop; proposal tools park a {@link AssistantProposals.PendingProposal}. */
    public enum Kind { READ, PROPOSAL }

    /** One tool: its wire {@code name}/{@code description}/{@code input_schema} plus its {@link Kind}. */
    public record ToolSpec(String name, String description, JSONObject inputSchema, Kind kind) {
    }

    public static final String GET_TASKS = "get_tasks";
    public static final String GET_RECIPES = "get_recipes";
    public static final String GET_INGREDIENTS = "get_ingredients";
    public static final String GET_MEAL_PLANS = "get_meal_plans";
    public static final String GET_BUDGET_CONTEXT = "get_budget_context";
    public static final String GET_TRANSACTIONS = "get_transactions";
    public static final String PROPOSE_TASK_CHANGES = "propose_task_changes";
    public static final String PROPOSE_RECIPES = "propose_recipes";
    public static final String PROPOSE_INGREDIENTS = "propose_ingredients";
    public static final String PROPOSE_MEAL_PLANS = "propose_meal_plans";
    public static final String PROPOSE_TRANSACTION_IMPORT = "propose_transaction_import";

    private static final List<ToolSpec> SPECS = List.of(
            read(GET_TASKS,
                    "Liefert alle Kategorien und Tasks aus der lokalen Datenbank – vollständig inklusive "
                    + "Wiederholung (repetition: reps, perPeriod, periodUnit, periodCompletions, carryoverDebt), "
                    + "Fortschritt, Streak/History, prefSlots und den nächsten geplanten Slots. Nutze dieses Tool "
                    + "für JEDE Frage zu bestehenden Tasks (z. B. \"Welche Morgenroutine-Tasks habe ich und wie "
                    + "oft werden sie wiederholt?\"). Optional per titleContains/categoryName vorfiltern; die "
                    + "semantische Zuordnung machst du selbst.",
                    "{\"type\":\"object\",\"properties\":{"
                    + "\"titleContains\":{\"type\":\"string\",\"description\":\"Nur Tasks, deren Titel diesen Text enthält\"},"
                    + "\"categoryName\":{\"type\":\"string\",\"description\":\"Nur Tasks dieser Kategorie\"}}}"),
            read(GET_RECIPES,
                    "Liefert alle Rezepte inklusive Zutaten (ingredients), Nährwerten, mealTypes und tags.",
                    "{\"type\":\"object\",\"properties\":{}}"),
            read(GET_INGREDIENTS,
                    "Liefert alle bekannten Zutaten inklusive foodGroup und Nährwerten. Nährwerte der Makros "
                    + "sind ×10-Festkomma (125 = 12,5 g pro 100 g); caloriesPer100 ist reine kcal.",
                    "{\"type\":\"object\",\"properties\":{}}"),
            read(GET_MEAL_PLANS,
                    "Liefert die Wochenplan-Einträge im Datumsbereich [from, to] (ISO-Datum, inklusiv).",
                    "{\"type\":\"object\",\"properties\":{"
                    + "\"from\":{\"type\":\"string\",\"description\":\"Startdatum YYYY-MM-DD\"},"
                    + "\"to\":{\"type\":\"string\",\"description\":\"Enddatum YYYY-MM-DD\"}},"
                    + "\"required\":[\"from\",\"to\"]}"),
            read(GET_BUDGET_CONTEXT,
                    "Liefert die Budget-Konten (id, name, balanceCents) und Kategorien (id, name, direction "
                    + "INCOME/EXPENSE). Nutze es, um accountId/categoryId für andere Tools aufzulösen.",
                    "{\"type\":\"object\",\"properties\":{}}"),
            read(GET_TRANSACTIONS,
                    "Liefert Transaktionen im Datumsbereich [from, to] (ISO-Datum, inklusiv), optional auf ein "
                    + "Konto beschränkt. amountCents ist der Absolutbetrag, direction (INCOME/EXPENSE) trägt das "
                    + "Vorzeichen; categoryName ist eingesetzt.",
                    "{\"type\":\"object\",\"properties\":{"
                    + "\"from\":{\"type\":\"string\",\"description\":\"Startdatum YYYY-MM-DD\"},"
                    + "\"to\":{\"type\":\"string\",\"description\":\"Enddatum YYYY-MM-DD\"},"
                    + "\"accountId\":{\"type\":\"string\",\"description\":\"Optionale Konto-id\"}},"
                    + "\"required\":[\"from\",\"to\"]}"),
            proposal(PROPOSE_TASK_CHANGES,
                    "Schlägt Änderungen an Kategorien und Tasks vor (wird dem Nutzer als Diff angezeigt, erst "
                    + "nach Bestätigung geschrieben). op ist CREATE/UPDATE/DELETE. Bei UPDATE/DELETE eine echte id "
                    + "aus get_tasks verwenden; bei CREATE keine id. Bei UPDATE nur die zu ändernden Felder "
                    + "angeben – weggelassene Felder bleiben unverändert. "
                    + "priority: LOW/MEDIUM/HIGH/CRITICAL. leisure (bool). "
                    + "Wiederholung über repetition:{reps, perPeriod, periodUnit DAY/WEEK/MONTH, completeFirst}. "
                    + "Beispiel \"3× pro Woche\" = reps:3, perPeriod:1, periodUnit:WEEK; \"5× pro 2 Wochen\" = "
                    + "reps:5, perPeriod:2, periodUnit:WEEK. Für eine einmalige Task ohne Wiederholung reps:0. "
                    + "schedulingType: TASK (frei geplant) oder TERMIN (fester Termin – benötigt fixedDate und "
                    + "fixedStart, optional fixedEnd oder fixedDuration in Minuten). "
                    + "Daten als YYYY-MM-DD: startDate (frühester Planungstag), deadline (Fälligkeit). "
                    + "closeOnMiss (bool): bei true wird die Task nach Ablauf/Deadline automatisch geschlossen. "
                    + "prefSlots: bevorzugte Zeiten als Liste {days:[1-7, Mo=1, leer=jeder Tag], start:\"HH:MM\"}. "
                    + "adaptive (bool) lernt Zeiten aus echten Abschlüssen. minDuration/maxDuration in Minuten, "
                    + "cooldown in Tagen. progress:{unit, target, current, resetPerRep, minPerRep, maxPerRep} für "
                    + "Fortschritts-Tracking. budgetRequiredCents + budgetAccountId/budgetCategoryId verknüpfen eine "
                    + "Ausgabe. Bei langen Listen alle Tasks in EINEM Aufruf.",
                    "{\"type\":\"object\",\"properties\":{"
                    + "\"categories\":{\"type\":\"array\",\"items\":{\"type\":\"object\",\"properties\":{"
                    + "\"op\":{\"type\":\"string\"},\"id\":{\"type\":\"string\"},\"name\":{\"type\":\"string\"},"
                    + "\"icon\":{\"type\":\"string\"},\"colorHex\":{\"type\":\"string\"}}}},"
                    + "\"tasks\":{\"type\":\"array\",\"items\":{\"type\":\"object\",\"properties\":{"
                    + "\"op\":{\"type\":\"string\"},\"id\":{\"type\":\"string\"},\"title\":{\"type\":\"string\"},"
                    + "\"description\":{\"type\":\"string\"},\"categoryId\":{\"type\":\"string\"},"
                    + "\"priority\":{\"type\":\"string\"},\"leisure\":{\"type\":\"boolean\"},"
                    + "\"adaptive\":{\"type\":\"boolean\"},\"minDuration\":{\"type\":\"integer\"},"
                    + "\"maxDuration\":{\"type\":\"integer\"},\"cooldown\":{\"type\":\"integer\"},"
                    + "\"schedulingType\":{\"type\":\"string\"},\"startDate\":{\"type\":\"string\"},"
                    + "\"deadline\":{\"type\":\"string\"},\"closeOnMiss\":{\"type\":\"boolean\"},"
                    + "\"fixedDate\":{\"type\":\"string\"},\"fixedStart\":{\"type\":\"string\"},"
                    + "\"fixedEnd\":{\"type\":\"string\"},\"fixedDuration\":{\"type\":\"integer\"},"
                    + "\"budgetRequiredCents\":{\"type\":\"integer\"},\"budgetAccountId\":{\"type\":\"string\"},"
                    + "\"budgetCategoryId\":{\"type\":\"string\"},"
                    + "\"repetition\":{\"type\":\"object\",\"properties\":{\"reps\":{\"type\":\"integer\"},"
                    + "\"perPeriod\":{\"type\":\"integer\"},\"periodUnit\":{\"type\":\"string\"},"
                    + "\"completeFirst\":{\"type\":\"boolean\"}}},"
                    + "\"progress\":{\"type\":\"object\",\"properties\":{\"unit\":{\"type\":\"string\"},"
                    + "\"target\":{\"type\":\"integer\"},\"current\":{\"type\":\"integer\"},"
                    + "\"resetPerRep\":{\"type\":\"boolean\"},\"minPerRep\":{\"type\":\"integer\"},"
                    + "\"maxPerRep\":{\"type\":\"integer\"}}},"
                    + "\"prefSlots\":{\"type\":\"array\",\"items\":{\"type\":\"object\",\"properties\":{"
                    + "\"days\":{\"type\":\"array\",\"items\":{\"type\":\"integer\"}},"
                    + "\"start\":{\"type\":\"string\"}}}}}}}}}"),
            proposal(PROPOSE_RECIPES,
                    "Schlägt neue Rezepte vor (erst nach Bestätigung gespeichert). ingredients referenzieren "
                    + "bestehende Zutaten per name; unbekannte Namen werden als freie Textzutat übernommen.",
                    "{\"type\":\"object\",\"properties\":{"
                    + "\"recipes\":{\"type\":\"array\",\"items\":{\"type\":\"object\",\"properties\":{"
                    + "\"title\":{\"type\":\"string\"},\"description\":{\"type\":\"string\"},"
                    + "\"instructions\":{\"type\":\"string\"},"
                    + "\"mealTypes\":{\"type\":\"array\",\"items\":{\"type\":\"string\"}},"
                    + "\"servings\":{\"type\":\"integer\"},\"prepTimeMinutes\":{\"type\":\"integer\"},"
                    + "\"cookTimeMinutes\":{\"type\":\"integer\"},\"tags\":{\"type\":\"string\"},"
                    + "\"ingredients\":{\"type\":\"array\",\"items\":{\"type\":\"object\",\"properties\":{"
                    + "\"name\":{\"type\":\"string\"},\"amount\":{\"type\":\"number\"},\"unit\":{\"type\":\"string\"}},"
                    + "\"required\":[\"name\"]}}},\"required\":[\"title\"]}}},\"required\":[\"recipes\"]}"),
            proposal(PROPOSE_INGREDIENTS,
                    "Schlägt neue Zutaten vor (erst nach Bestätigung gespeichert). foodGroup ist einer der DGE-"
                    + "Werte (GRAIN, POTATO, VEGETABLE, FRUIT, DAIRY, MEAT, FISH, EGG, FAT, LEGUME, NUT, OTHER). "
                    + "Makros sind ×10-Festkomma (125 = 12,5 g), caloriesPer100 reine kcal.",
                    "{\"type\":\"object\",\"properties\":{"
                    + "\"ingredients\":{\"type\":\"array\",\"items\":{\"type\":\"object\",\"properties\":{"
                    + "\"name\":{\"type\":\"string\"},\"foodGroup\":{\"type\":\"string\"},"
                    + "\"unit\":{\"type\":\"string\"},\"gramsPerUnit\":{\"type\":\"integer\"},"
                    + "\"caloriesPer100\":{\"type\":\"integer\"},\"proteinPer100\":{\"type\":\"integer\"},"
                    + "\"carbsPer100\":{\"type\":\"integer\"},\"fatPer100\":{\"type\":\"integer\"},"
                    + "\"fiberPer100\":{\"type\":\"integer\"},\"shelfLifeDays\":{\"type\":\"integer\"}},"
                    + "\"required\":[\"name\",\"foodGroup\"]}}},\"required\":[\"ingredients\"]}"),
            proposal(PROPOSE_MEAL_PLANS,
                    "Schlägt Wochenplan-Einträge vor (erst nach Bestätigung gespeichert). Jeder Eintrag "
                    + "identifiziert sein Rezept per recipeId ODER recipeTitle (Titel erlaubt Rezepte, die im "
                    + "selben Chat gerade vorgeschlagen wurden). mealType: BREAKFAST/LUNCH/DINNER/SNACK.",
                    "{\"type\":\"object\",\"properties\":{"
                    + "\"entries\":{\"type\":\"array\",\"items\":{\"type\":\"object\",\"properties\":{"
                    + "\"date\":{\"type\":\"string\",\"description\":\"YYYY-MM-DD\"},"
                    + "\"mealType\":{\"type\":\"string\"},\"recipeId\":{\"type\":\"string\"},"
                    + "\"recipeTitle\":{\"type\":\"string\"},\"servings\":{\"type\":\"integer\"}},"
                    + "\"required\":[\"date\",\"mealType\"]}}},\"required\":[\"entries\"]}"),
            proposal(PROPOSE_TRANSACTION_IMPORT,
                    "Schlägt den Import der Transaktionen aus dem zuletzt angehängten Kontoauszug vor (erst nach "
                    + "Bestätigung importiert; Duplikate werden übersprungen). Benötigt einen zuvor angehängten "
                    + "PDF-Kontoauszug. amountCents ist SIGNIERT (negativ = Ausgabe, positiv = Einnahme).",
                    "{\"type\":\"object\",\"properties\":{"
                    + "\"accountId\":{\"type\":\"string\",\"description\":\"Optional; sonst Standardkonto\"},"
                    + "\"periodStart\":{\"type\":\"string\",\"description\":\"YYYY-MM-DD\"},"
                    + "\"periodEnd\":{\"type\":\"string\",\"description\":\"YYYY-MM-DD\"},"
                    + "\"transactions\":{\"type\":\"array\",\"items\":{\"type\":\"object\",\"properties\":{"
                    + "\"date\":{\"type\":\"string\",\"description\":\"YYYY-MM-DD\"},"
                    + "\"amountCents\":{\"type\":\"integer\",\"description\":\"signiert\"},"
                    + "\"payee\":{\"type\":\"string\"},\"description\":{\"type\":\"string\"},"
                    + "\"categoryId\":{\"type\":\"string\"}},\"required\":[\"date\",\"amountCents\"]}}},"
                    + "\"required\":[\"transactions\"]}"));

    private AssistantTools() {
    }

    /** The tools array for the Messages API request body. */
    public static JSONArray toolsJson() {
        try {
            JSONArray tools = new JSONArray();
            for (ToolSpec spec : SPECS) {
                tools.put(new JSONObject()
                        .put("name", spec.name())
                        .put("description", spec.description())
                        .put("input_schema", spec.inputSchema()));
            }
            return tools;
        } catch (JSONException e) {
            throw new IllegalStateException("Tool-Definitionen konnten nicht erstellt werden", e);
        }
    }

    private static ToolSpec read(String name, String description, String schemaJson) {
        return new ToolSpec(name, description, schema(schemaJson), Kind.READ);
    }

    private static ToolSpec proposal(String name, String description, String schemaJson) {
        return new ToolSpec(name, description, schema(schemaJson), Kind.PROPOSAL);
    }

    private static JSONObject schema(String json) {
        try {
            return new JSONObject(json);
        } catch (JSONException e) {
            throw new IllegalStateException("Ungültiges Tool-Schema: " + json, e);
        }
    }
}

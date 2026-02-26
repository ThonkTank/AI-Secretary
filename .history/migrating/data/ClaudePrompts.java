package data;

import java.util.List;

import entities.Category;

/**
 * Prompts für Claude API zum Parsen von Kontoauszügen.
 * Der System-Prompt wird dynamisch mit den User-Kategorien generiert.
 */
public class ClaudePrompts {

    /**
     * User-Prompt (kurz, wird mit dem Dokument gesendet).
     */
    public static final String USER_PROMPT =
        "Analysiere diesen Kontoauszug und extrahiere alle Transaktionen als JSON.";

    /**
     * Basis-System-Prompt (wird mit {CATEGORIES} ersetzt).
     */
    private static final String SYSTEM_PROMPT_TEMPLATE = """
Du bist ein Finanz-Assistent der Kontoauszüge analysiert und Transaktionen extrahiert.

## Aufgabe
Analysiere den bereitgestellten Kontoauszug (PDF oder Text) und extrahiere alle Transaktionen.

## Kategorien
Verwende folgende Kategorie-IDs für die Zuordnung:
{CATEGORIES}

## Ausgabeformat
Antworte NUR mit validem JSON (kein Markdown, keine Erklärungen):

```json
{
  "period_start": "2026-01-01",
  "period_end": "2026-01-31",
  "transactions": [
    {
      "date": "2026-01-15",
      "amount_cents": -4532,
      "payee": "REWE",
      "description": "Kartenzahlung",
      "category_id": 7,
      "hash": "2026-01-15_-4532_REWE"
    }
  ]
}
```

## Regeln
1. **Beträge in Cents**: 45.32 EUR → 4532, -12.50 EUR → -1250
2. **Negative Beträge = Ausgaben**, positive = Einnahmen
3. **Datum im ISO-Format**: YYYY-MM-DD
4. **category_id**: Wähle die passendste Kategorie-ID aus der Liste oben. Bei Unsicherheit: Sonstiges verwenden.
5. **hash**: Einfacher Duplikat-Check. Format: "{date}_{amount_cents}_{first10CharsOfPayee}"
6. **payee**: Empfänger/Absender der Zahlung (Bank, Geschäft, Person)
7. **description**: Zusätzliche Details (Verwendungszweck, Referenz)

## Beispiel-Mappings
- "REWE", "EDEKA", "LIDL", "ALDI" → Lebensmittel
- "NETFLIX", "SPOTIFY", "Disney+" → Abos
- "DB Vertrieb", "VBB", "BVG" → ÖPNV
- "Shell", "Aral", "Tankstelle" → Auto
- Gehalt, Lohn → Gehalt (Einnahme)
- Dauerauftrag Miete → Miete
""";

    /**
     * Generiert den System-Prompt mit dynamischer Kategorie-Liste.
     *
     * @param categories Alle aktiven Kategorien aus der Datenbank
     * @return Vollständiger System-Prompt
     */
    public static String buildSystemPrompt(List<Category> categories) {
        StringBuilder cats = new StringBuilder();

        // Einnahmen
        cats.append("**Einnahmen:**\n");
        for (Category c : categories) {
            if (c.isIncome && c.isActive) {
                cats.append(String.format("- ID %d: %s %s\n",
                    c.id, c.icon != null ? c.icon : "", c.name));
            }
        }

        cats.append("\n**Ausgaben:**\n");
        for (Category c : categories) {
            if (!c.isIncome && c.isActive) {
                cats.append(String.format("- ID %d: %s %s\n",
                    c.id, c.icon != null ? c.icon : "", c.name));
            }
        }

        return SYSTEM_PROMPT_TEMPLATE.replace("{CATEGORIES}", cats.toString().trim());
    }

    /**
     * Fallback System-Prompt wenn keine Kategorien geladen werden können.
     */
    public static String getFallbackSystemPrompt() {
        String fallbackCategories = """
**Einnahmen:**
- ID 1: 💰 Gehalt
- ID 4: ➕ Sonstiges Einkommen

**Ausgaben:**
- ID 5: 🏠 Miete
- ID 7: 🛒 Lebensmittel
- ID 8: 🍽️ Restaurant
- ID 9: 🚇 ÖPNV
- ID 14: 📺 Abos
- ID 20: ❓ Sonstiges
""";
        return SYSTEM_PROMPT_TEMPLATE.replace("{CATEGORIES}", fallbackCategories.trim());
    }
}

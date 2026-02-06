package controller;

import android.content.Context;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import entities.Account;
import entities.Category;
import entities.TrackedItem;
import entities.TrackedItem.ItemType;
import repository.SQLrepo;
import repository.Table;

public class EditorManager {

    /**
     * ══════════════════════════════════════════════════════════════════════════════
     * ITEM-VERWALTUNG (CRUD) - Dokumentation
     * ══════════════════════════════════════════════════════════════════════════════
     *
     * ZIEL:
     *   Controller-Schicht zwischen EditItem (UI) und SQLrepo (Persistenz).
     *   Stellt alle Operationen bereit, die für das Erstellen, Editieren und
     *   Anzeigen von TrackedItems benötigt werden.
     *
     * ──────────────────────────────────────────────────────────────────────────────
     * DATENFLUSS
     * ──────────────────────────────────────────────────────────────────────────────
     *
     *   EditItem (View-Builder)
     *       │
     *       ├─ getAllItems()        → Baum-Ansicht rendern
     *       ├─ getAvailableParents()→ Parent-Spinner befüllen
     *       ├─ createItem()         → Neues Item speichern
     *       └─ updateItem()         → Bestehendes Item ändern
     *       │
     *       ▼
     *   EditorManager (dieser Controller)
     *       │
     *       ├─ repo.lookups("items", filters, "id")  → IDs laden
     *       ├─ repo.fetch(Table.ITEMS, id)           → Entity laden
     *       └─ repo.write(TrackedItem)               → Entity speichern
     *       │
     *       ▼
     *   SQLrepo → SQLite (items-Tabelle)
     *
     * ──────────────────────────────────────────────────────────────────────────────
     * PUBLIC API
     * ──────────────────────────────────────────────────────────────────────────────
     *
     *   Konstruktor:
     *     EditorManager(Context context)
     *       → Erstellt SQLrepo-Instanz
     *
     *   Record:
     *     TreeEntry(TrackedItem item, int depth)
     *       → Für Baum-Darstellung: Item + Einrückungstiefe (0=Root, 1=Kind, ...)
     *
     *   Methoden:
     *     List<TreeEntry> getAllItems()
     *     List<TrackedItem> getAvailableParents(ItemType childType)
     *     void createItem(TrackedItem item)
     *     void updateItem(TrackedItem item)
     *
     * ──────────────────────────────────────────────────────────────────────────────
     * ALGORITHMEN
     * ──────────────────────────────────────────────────────────────────────────────
     *
     *  getAllItems() - Lädt alle Items hierarchisch sortiert
     *      1. Alle nicht-abgeschlossenen Item-IDs laden:
     *         → repo.lookups("items", Map.of("is_completed", "0"), "id")
     *      2. Jedes Item fetchen:
     *         → repo.fetch(Table.ITEMS, id)
     *      3. Roots finden (parent == null)
     *         → Sortiert nach Typ-Ordnung: PROJECT → GOAL → TASK
     *      4. DFS-Traversal ab jedem Root:
     *         → addToResult(item, depth=0)
     *         → Für jedes child in item.children:
     *            → childItem = repo.fetch(Table.ITEMS, childId)
     *            → addToResult(childItem, depth+1)  (rekursiv)
     *      5. Return: Flache Liste von TreeEntry(item, depth)
     *
     *  getAvailableParents(ItemType childType) - Mögliche Parents für Typ
     *      Typ-Mapping:
     *        TASK    → Parents vom Typ "Goal"
     *        GOAL    → Parents vom Typ "Project"
     *        PROJECT → keine Parents (return leere Liste)
     *      1. parentType bestimmen via switch
     *      2. IDs laden: repo.lookups("items", Map.of("type", parentType), "id")
     *      3. Jede ID fetchen: repo.fetch(Table.ITEMS, id)
     *      4. Return: Liste der Parent-Items
     *
     *  createItem(trackedItem item) - Neues Item persistieren
     *      1. item.created = LocalDate.now()
     *      2. repo.write(item)  → INSERT (id wird generiert)
     *      3. Falls item.parent != null:
     *         → parent = repo.fetch(Table.ITEMS, item.parent)
     *         → parent.children.add(item.id)
     *         → repo.write(parent)  → UPDATE
     *
     *  updateItem(TrackedItem item) - Bestehendes Item überschreiben
     *      1. repo.write(item)  → UPDATE (id vorhanden)
     *
     * ──────────────────────────────────────────────────────────────────────────────
     * SQL ZUGRIFF (SQLrepo)
     * ──────────────────────────────────────────────────────────────────────────────
     *
     *   lookups("items", Map.of("is_completed", "0"), "id")
     *     → Alle aktiven Item-IDs
     *
     *   lookups("items", Map.of("type", "Goal"), "id")
     *     → Alle Goal-IDs (für Parent-Auswahl)
     *
     *   fetch(Table.ITEMS, id)
     *     → Einzelnes TrackedItem laden
     *
     *   write(TrackedItem)
     *     → INSERT (id==null) oder UPDATE (id!=null)
     *
     */

    // ============================================================================
    // DATENSTRUKTUREN
    // ============================================================================

    /** Baum-Eintrag: Item + Einrückungstiefe für die Baum-Ansicht */
    public record TreeEntry(TrackedItem item, int depth) {}

    // ============================================================================
    // FELDER + KONSTRUKTOR
    // ============================================================================

    private SQLrepo repo;

    public EditorManager(Context context) {
        this.repo = SQLrepo.getInstance(context);
    }

    // ============================================================================
    // GETALLITEMS - Lädt alle Items hierarchisch sortiert (DFS-Traversal)
    // ============================================================================
    /**
     * Liefert alle nicht-abgeschlossenen Items als flache Liste in Baum-Reihenfolge.
     * Roots (parent==null) sortiert nach Typ: PROJECT → GOAL → TASK.
     * Kinder rekursiv eingehängt mit steigender depth.
     *
     * @return Liste von TreeEntry(item, depth)
     */
    public List<TreeEntry> getAllItems() {
        List<TreeEntry> result = new ArrayList<>();

        // Alle nicht-abgeschlossenen Items per Batch laden
        List<TrackedItem> allItems = repo.fetchAll(Table.ITEMS, Map.of("is_completed", "0"));

        // Roots finden (parent == null), sortiert nach Typ-Ordnung
        List<TrackedItem> roots = allItems.stream()
            .filter(i -> i.parent == null)
            .sorted(Comparator.comparingInt(i -> typeOrder(i.type)))
            .collect(Collectors.toList());

        // DFS-Traversal ab jedem Root
        for (TrackedItem root : roots) {
            traverse(root, 0, result);
        }

        return result;
    }

    /** Typ-Ordnung: PROJECT=0, GOAL=1, TASK=2 */
    private int typeOrder(ItemType type) {
        return switch (type) {
            case PROJECT -> 0;
            case GOAL -> 1;
            case TASK -> 2;
        };
    }

    // ============================================================================
    // GETAVAILABLEPARENTS - Mögliche Parent-Items für einen gegebenen Typ
    // ============================================================================
    /**
     * Gibt alle Items zurück, die als Parent für den übergebenen childType
     * in Frage kommen (TASK→Goal, GOAL→Project, PROJECT→leer).
     *
     * @param childType Der Typ des Kindes, für das ein Parent gesucht wird
     * @return Liste möglicher Parent-Items (leer bei PROJECT)
     */
    public List<TrackedItem> getAvailableParents(ItemType childType) {
        String parentType = switch (childType) {
            case TASK -> "GOAL";
            case GOAL -> "PROJECT";
            case PROJECT -> null;
        };

        if (parentType == null) return new ArrayList<>();

        // Batch fetch: Alle Parents des entsprechenden Typs
        return repo.fetchAll(Table.ITEMS, Map.of("type", parentType));
    }

    // ============================================================================
    // GETACTIVEACCOUNTS - Alle aktiven Konten für Budget-Auswahl
    // ============================================================================
    /**
     * Liefert alle aktiven Konten für die Budget-Konto-Auswahl im Edit-Modal.
     *
     * @return Liste aller aktiven Account-Entities
     */
    public List<Account> getActiveAccounts() {
        // Batch fetch: Alle aktiven Konten
        return repo.fetchAll(Table.ACCOUNTS, Map.of("is_active", "1"));
    }

    /**
     * Liefert alle aktiven Ausgabe-Kategorien für die Budget-Kategorie-Auswahl.
     *
     * @return Liste aller aktiven Category-Entities mit isIncome=false
     */
    public List<Category> getExpenseCategories() {
        // Batch fetch: Alle aktiven Ausgabe-Kategorien
        List<Category> categories = repo.fetchAll(Table.CATEGORIES,
            Map.of("is_active", "1", "is_income", "0"));
        // Sortieren nach sortOrder, dann Name
        categories.sort((a, b) -> {
            if (a.sortOrder != b.sortOrder) return Integer.compare(a.sortOrder, b.sortOrder);
            return a.name.compareTo(b.name);
        });
        return categories;
    }

    // ============================================================================
    // CREATEITEM - Neues Item persistieren + Parent-children aktualisieren
    // ============================================================================
    /**
     * Erstellt ein neues Item in der Datenbank.
     * Setzt created-Datum, schreibt das Item, und fügt die neue ID
     * zur children-Liste des Parents hinzu (falls parent != null).
     *
     * @param item Das neue Item (id wird von DB vergeben)
     */
    public void createItem(TrackedItem item) {
        item.created = LocalDate.now();
        repo.write(item);

        // Parent-children aktualisieren
        if (item.parent != null) {
            TrackedItem parent = repo.fetch(Table.ITEMS, item.parent);
            if (parent != null) {
                if (parent.children == null) {
                    parent.children = new ArrayList<>();
                }
                if (item.id != null && !parent.children.contains(item.id)) {
                    parent.children.add(item.id);
                    repo.write(parent);
                }
            }
        }
    }

    // ============================================================================
    // UPDATEITEM - Bestehendes Item überschreiben
    // ============================================================================
    /**
     * Aktualisiert ein bestehendes Item in der Datenbank.
     * Item muss eine gültige id haben.
     *
     * @param item Das zu aktualisierende Item
     */
    public void updateItem(TrackedItem item) {
        repo.write(item);
    }

    // ============================================================================
    // TRAVERSE - Rekursiver DFS-Helfer für getAllItems
    // ============================================================================
    /**
     * Fügt item mit gegebener depth zur result-Liste hinzu,
     * dann rekursiv alle children mit depth+1.
     */
    private void traverse(TrackedItem item, int depth, List<TreeEntry> result) {
        result.add(new TreeEntry(item, depth));

        if (item.children != null) {
            for (Long childId : item.children) {
                TrackedItem child = repo.fetch(Table.ITEMS, childId);
                if (child != null) {
                    traverse(child, depth + 1, result);
                }
            }
        }
    }
}

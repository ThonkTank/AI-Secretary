package controller;

import android.content.Context;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import entities.trackedItem;
import entities.trackedItem.ItemType;
import repository.SQLrepo;
import repository.Table;

public class editorManager {

    /**
     * ══════════════════════════════════════════════════════════════════════════════
     * ITEM-VERWALTUNG (CRUD) - Dokumentation
     * ══════════════════════════════════════════════════════════════════════════════
     *
     * ZIEL:
     *   Controller-Schicht zwischen editItem (UI) und SQLrepo (Persistenz).
     *   Stellt alle Operationen bereit, die für das Erstellen, Editieren und
     *   Anzeigen von trackedItems benötigt werden.
     *
     * ──────────────────────────────────────────────────────────────────────────────
     * DATENFLUSS
     * ──────────────────────────────────────────────────────────────────────────────
     *
     *   editItem (View-Builder)
     *       │
     *       ├─ getAllItems()        → Baum-Ansicht rendern
     *       ├─ getAvailableParents()→ Parent-Spinner befüllen
     *       ├─ createItem()         → Neues Item speichern
     *       └─ updateItem()         → Bestehendes Item ändern
     *       │
     *       ▼
     *   editorManager (dieser Controller)
     *       │
     *       ├─ repo.lookups("items", filters, "id")  → IDs laden
     *       ├─ repo.fetch(Table.ITEMS, id)           → Entity laden
     *       └─ repo.write(trackedItem)               → Entity speichern
     *       │
     *       ▼
     *   SQLrepo → SQLite (items-Tabelle)
     *
     * ──────────────────────────────────────────────────────────────────────────────
     * PUBLIC API
     * ──────────────────────────────────────────────────────────────────────────────
     *
     *   Konstruktor:
     *     editorManager(Context context)
     *       → Erstellt SQLrepo-Instanz
     *
     *   Record:
     *     TreeEntry(trackedItem item, int depth)
     *       → Für Baum-Darstellung: Item + Einrückungstiefe (0=Root, 1=Kind, ...)
     *
     *   Methoden:
     *     List<TreeEntry> getAllItems()
     *     List<trackedItem> getAvailableParents(ItemType childType)
     *     void createItem(trackedItem item)
     *     void updateItem(trackedItem item)
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
     *         → Sortiert nach Typ-Ordnung: PROJECT → BLOCK → GOAL → TASK
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
     *        GOAL    → Parents vom Typ "Block"
     *        BLOCK   → Parents vom Typ "Project"
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
     *  updateItem(trackedItem item) - Bestehendes Item überschreiben
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
     *     → Einzelnes trackedItem laden
     *
     *   write(trackedItem)
     *     → INSERT (id==null) oder UPDATE (id!=null)
     *
     */

    // ============================================================================
    // DATENSTRUKTUREN
    // ============================================================================

    /** Baum-Eintrag: Item + Einrückungstiefe für die Baum-Ansicht */
    public record TreeEntry(trackedItem item, int depth) {}

    // ============================================================================
    // FELDER + KONSTRUKTOR
    // ============================================================================

    private SQLrepo repo;

    public editorManager(Context context) {
        this.repo = new SQLrepo(context);
    }

    // ============================================================================
    // GETALLITEMS - Lädt alle Items hierarchisch sortiert (DFS-Traversal)
    // ============================================================================
    /**
     * Liefert alle nicht-abgeschlossenen Items als flache Liste in Baum-Reihenfolge.
     * Roots (parent==null) sortiert nach Typ: PROJECT → BLOCK → GOAL → TASK.
     * Kinder rekursiv eingehängt mit steigender depth.
     *
     * @return Liste von TreeEntry(item, depth)
     */
    public List<TreeEntry> getAllItems() {
        List<TreeEntry> result = new ArrayList<>();

        // Alle nicht-abgeschlossenen Item-IDs laden
        List<Long> allIds = repo.lookups("items", Map.of("is_completed", "0"), "id");

        // Items fetchen
        List<trackedItem> allItems = new ArrayList<>();
        for (Long id : allIds) {
            trackedItem item = repo.fetch(Table.ITEMS, id);
            if (item != null) allItems.add(item);
        }

        // Roots finden (parent == null), sortiert nach Typ-Ordnung
        List<trackedItem> roots = allItems.stream()
            .filter(i -> i.parent == null)
            .sorted(Comparator.comparingInt(i -> typeOrder(i.type)))
            .collect(Collectors.toList());

        // DFS-Traversal ab jedem Root
        for (trackedItem root : roots) {
            traverse(root, 0, result);
        }

        return result;
    }

    /** Typ-Ordnung: PROJECT=0, BLOCK=1, GOAL=2, TASK=3 */
    private int typeOrder(ItemType type) {
        return switch (type) {
            case PROJECT -> 0;
            case BLOCK -> 1;
            case GOAL -> 2;
            case TASK -> 3;
        };
    }

    // ============================================================================
    // GETAVAILABLEPARENTS - Mögliche Parent-Items für einen gegebenen Typ
    // ============================================================================
    /**
     * Gibt alle Items zurück, die als Parent für den übergebenen childType
     * in Frage kommen (TASK→Goal, GOAL→Block, BLOCK→Project, PROJECT→leer).
     *
     * @param childType Der Typ des Kindes, für das ein Parent gesucht wird
     * @return Liste möglicher Parent-Items (leer bei PROJECT)
     */
    public List<trackedItem> getAvailableParents(ItemType childType) {
        String parentType = switch (childType) {
            case TASK -> "GOAL";
            case GOAL -> "BLOCK";
            case BLOCK -> "PROJECT";
            case PROJECT -> null;
        };

        if (parentType == null) return new ArrayList<>();

        List<Long> ids = repo.lookups("items", Map.of("type", parentType), "id");
        List<trackedItem> parents = new ArrayList<>();
        for (Long id : ids) {
            trackedItem item = repo.fetch(Table.ITEMS, id);
            if (item != null) parents.add(item);
        }
        return parents;
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
    public void createItem(trackedItem item) {
        item.created = LocalDate.now();
        repo.write(item);

        // Parent-children aktualisieren
        if (item.parent != null) {
            trackedItem parent = repo.fetch(Table.ITEMS, item.parent);
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
    public void updateItem(trackedItem item) {
        repo.write(item);
    }

    // ============================================================================
    // TRAVERSE - Rekursiver DFS-Helfer für getAllItems
    // ============================================================================
    /**
     * Fügt item mit gegebener depth zur result-Liste hinzu,
     * dann rekursiv alle children mit depth+1.
     */
    private void traverse(trackedItem item, int depth, List<TreeEntry> result) {
        result.add(new TreeEntry(item, depth));

        if (item.children != null) {
            for (Long childId : item.children) {
                trackedItem child = repo.fetch(Table.ITEMS, childId);
                if (child != null) {
                    traverse(child, depth + 1, result);
                }
            }
        }
    }
}

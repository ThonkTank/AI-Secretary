package activities.inApp;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import static activities.generic.UIConstants.*;
import static activities.generic.ViewHelper.*;

import activities.generic.ViewBuilder;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import controller.editorManager;
import controller.editorManager.TreeEntry;
import entities.trackedItem;
import entities.trackedItem.ItemType;
import entities.trackedItem.Priority;
import entities.trackedItem.RepetitionType;
import entities.trackedItem.RepUnits;

public class editItem implements ViewBuilder {

    /**
     * ══════════════════════════════════════════════════════════════════════════════
     * ITEM-VERWALTUNG VIEW-BUILDER - Dokumentation
     * ══════════════════════════════════════════════════════════════════════════════
     *
     * ZIEL:
     *   View-Builder für den "Verwalten"-Tab in mainActivity.
     *   Zeigt alle Items hierarchisch (Project→Block→Goal→Task) als aufklappbaren
     *   Baum und bietet ein Modal zum Erstellen/Editieren von Items.
     *   Analoges Pattern zu taskList.java (Konstruktor + buildView()).
     *
     * ──────────────────────────────────────────────────────────────────────────────
     * DATENFLUSS
     * ──────────────────────────────────────────────────────────────────────────────
     *
     *   mainActivity
     *       │ buildVerwaltenView()
     *       ▼
     *   editItem(context, editorManager)
     *       │ buildView()
     *       ▼
     *   ┌─────────────────────────────────────────┐
     *   │  Baum-Ansicht                           │
     *   │  manager.getAllItems() → TreeEntry-Liste │
     *   │  → buildTreeRow() pro Entry             │
     *   └─────────────────────────────────────────┘
     *       │ Klick / Create-Button
     *       ▼
     *   ┌─────────────────────────────────────────┐
     *   │  Modal (Create/Edit)                    │
     *   │  Felder befüllen → saveItem()           │
     *   │  → manager.createItem(item)             │
     *   │  → manager.updateItem(item)             │
     *   │  → refreshTree()                        │
     *   └─────────────────────────────────────────┘
     *
     * ──────────────────────────────────────────────────────────────────────────────
     * PUBLIC API
     * ──────────────────────────────────────────────────────────────────────────────
     *
     *   Konstruktor:
     *     editItem(Context context, editorManager manager)
     *
     *   Methoden:
     *     View buildView()  → Gibt die komplette View-Hierarchie zurück
     *
     * ──────────────────────────────────────────────────────────────────────────────
     * LAYOUT-HIERARCHIE (buildView)
     * ──────────────────────────────────────────────────────────────────────────────
     *
     *   ┌─────────────────────────────────────────────────────────────────────────┐
     *   │  FrameLayout (root, MATCH_PARENT)                                      │
     *   │  Ermöglicht Modal-Overlay über dem Hauptinhalt                          │
     *   ├─────────────────────────────────────────────────────────────────────────┤
     *   │                                                                         │
     *   │  ┌───────────────────────────────────────────────────────────────────┐  │
     *   │  │  ScrollView (Hauptinhalt)                                         │  │
     *   │  │  └── LinearLayout (VERTICAL)                                      │  │
     *   │  │      ├── Toolbar (buildToolbar)                                   │  │
     *   │  │      │   ├── Suchleiste (EditText, weight=1)                      │  │
     *   │  │      │   ├── Filter-Button (togglet Typ-Filter)                   │  │
     *   │  │      │   └── "+" Create-Button (ACCENT)                     │  │
     *   │  │      └── treeContainer (buildTree)                                │  │
     *   │  │          ├── Zeile depth=0: [P] "Fitness"                         │  │
     *   │  │          │   ├── Zeile depth=1: [B] "Schulter"                    │  │
     *   │  │          │   │   ├── Zeile depth=2: [G] "Stretches"               │  │
     *   │  │          │   │   │   └── Zeile depth=3: [T] "10 Dehnübungen"      │  │
     *   │  │          │   │   └── Zeile depth=2: [G] "Training"                │  │
     *   │  │          │   └── ...                                              │  │
     *   │  │          └── ...                                                  │  │
     *   │  └───────────────────────────────────────────────────────────────────┘  │
     *   │                                                                         │
     *   │  ┌───────────────────────────────────────────────────────────────────┐  │
     *   │  │  Modal-Overlay (initial GONE)                                     │  │
     *   │  │  FrameLayout, halbtransparent 0x80000000                          │  │
     *   │  │  └── Modal-Card (weiß, 90% Breite, zentriert)                    │  │
     *   │  │      └── ScrollView → Formular (siehe MODAL-LAYOUT)               │  │
     *   │  └───────────────────────────────────────────────────────────────────┘  │
     *   └─────────────────────────────────────────────────────────────────────────┘
     *
     * ──────────────────────────────────────────────────────────────────────────────
     * BAUM-ANSICHT (buildTree / buildTreeRow)
     * ──────────────────────────────────────────────────────────────────────────────
     *
     *   Datenquelle:
     *     manager.getAllItems() → List<TreeEntry(item, depth)>
     *
     *   Zeilen-Aufbau (buildTreeRow):
     *     ┌──────────────────────────────────────────────────────┐
     *     │  [depth×16dp Padding] [Typ-Badge] [Titel]           │
     *     └──────────────────────────────────────────────────────┘
     *
     *   Typ-Badge Farben:
     *     PROJECT  → 0xFF7B1FA2 (Lila)   Label: "P"
     *     BLOCK    → 0xFFE65100 (Orange)  Label: "B"
     *     GOAL     → 0xFF1976D2 (Blau)    Label: "G"
     *     TASK     → 0xFF388E3C (Grün)    Label: "T"
     *
     *   Expand/Collapse:
     *     Items mit children → Klick togglet Sichtbarkeit der Kind-Zeilen
     *     Zustand: Map<Long, Boolean> expandedState (itemId → expanded)
     *     Kinder-Zeilen: visibility = parent expanded ? VISIBLE : GONE
     *
     *   Edit-Trigger:
     *     Long-Press auf beliebiges Item ODER Klick auf Blatt-Item (keine children)
     *     → showModal(item) mit vorgefüllten Feldern
     *
     * ──────────────────────────────────────────────────────────────────────────────
     * CREATE/EDIT-MODAL (buildModal / showModal / hideModal)
     * ──────────────────────────────────────────────────────────────────────────────
     *
     *   Modal-Layout:
     *     ┌──────────────────────────────────────────────────────────────────────┐
     *     │  LinearLayout (VERTICAL, 16dp Padding)                              │
     *     │  ├── Typ-Auswahl: 4 Buttons [TASK] [GOAL] [BLOCK] [PROJECT]         │
     *     │  ├── Titel (EditText, singleLine)                                   │
     *     │  ├── Beschreibung (EditText, maxLines=3)                            │
     *     │  ├── Dauer/timeToComplete (EditText, number)                        │
     *     │  ├── Priorität: 4 Buttons [L] [M] [H] [C]                          │
     *     │  ├── Parent (Spinner, befüllt via getAvailableParents)              │
     *     │  ├── Cooldown (EditText, number)                                    │
     *     │  ├── minIntervalDays (EditText, number)                             │
     *     │  ├── Wiederholung-Sektion (ausklappbar)                             │
     *     │  │   ├── Typ: 3 Buttons [INTERVAL] [REPS] [DAY_OF]                 │
     *     │  │   ├── Wert (EditText, number)                                    │
     *     │  │   ├── Einheit: 3 Buttons [TAG] [WOCHE] [MONAT]                  │
     *     │  │   └── Wochentag (Spinner) — nur bei DAY_OF_TIME + WEEK           │
     *     │  ├── Fehler-Text (rot, initial GONE)                                │
     *     │  ├── Speichern-Button (ACCENT)                                │
     *     │  └── Abbrechen-Button (grau)                                        │
     *     └──────────────────────────────────────────────────────────────────────┘
     *
     *   Feld-Sichtbarkeit nach ItemType:
     *     ┌──────────────────┬──────┬──────┬───────┬─────────┐
     *     │ Feld             │ TASK │ GOAL │ BLOCK │ PROJECT │
     *     ├──────────────────┼──────┼──────┼───────┼─────────┤
     *     │ Dauer            │  ✓   │  ✓   │  —    │    —    │
     *     │ Parent           │  ✓   │  ✓   │  ✓   │    —    │
     *     │ Cooldown         │  ✓   │  —   │  —    │    —    │
     *     │ minIntervalDays  │  —   │  —   │  —    │    ✓    │
     *     │ Wiederholung     │  ✓   │  —   │  —    │    —    │
     *     └──────────────────┴──────┴──────┴───────┴─────────┘
     *
     *   Typ-Wechsel:
     *     → Irrelevante Felder: setVisibility(GONE)
     *     → Parent-Spinner neu befüllen via getAvailableParents(newType)
     *
     *   Edit-Modus:
     *     → Typ-Buttons disabled (Typ kann nicht geändert werden)
     *     → Felder vorgefüllt mit item.title, item.description, etc.
     *
     *   saveItem():
     *     1. Validierung: Titel darf nicht leer sein
     *        → Fehler-Text VISIBLE bei Verstoß
     *     2. trackedItem aus Feldern zusammenbauen (via Builder)
     *     3. Neues Item: manager.createItem(item)
     *        Bestehendes: manager.updateItem(item)
     *     4. hideModal()
     *     5. refreshTree() → treeContainer neu aufbauen
     *
     * ──────────────────────────────────────────────────────────────────────────────
     * INTERAKTIONEN (Toolbar)
     * ──────────────────────────────────────────────────────────────────────────────
     *
     *   Suchleiste:
     *     TextWatcher auf EditText
     *     Bei Eingabe: Zeilen filtern nach title.toLowerCase().contains(query)
     *     Leere Suche → alle Zeilen sichtbar
     *
     *   Filter-Button:
     *     Togglet Set<ItemType> activeFilters (initial: alle aktiv)
     *     Klick → Popup oder Cycle durch Typen
     *     Zeilen mit type ∉ activeFilters → GONE
     *
     *   Create-Button ("+"):
     *     → showModal(null) → leeres Formular, Typ-Buttons aktiv
     *
     * ──────────────────────────────────────────────────────────────────────────────
     * INTERNE METHODEN
     * ──────────────────────────────────────────────────────────────────────────────
     *
     *   buildView()              → Root-FrameLayout mit Tree + Modal aufbauen
     *   buildToolbar()           → Suchleiste + Filter + Create-Button
     *   buildTree()              → manager.getAllItems() → Zeilen rendern
     *   buildTreeRow(TreeEntry)  → Einzelne Zeile: Padding + Badge + Titel
     *   buildModal()             → Overlay + Card + Formular (initial GONE)
     *   showModal(trackedItem)   → Modal VISIBLE, Felder befüllen (null=Create)
     *   hideModal()              → Modal GONE
     *   saveItem()               → Validierung + create/update + refresh
     *   refreshTree()            → treeContainer.removeAllViews() + buildTree()
     *   applyFilter(query, types)→ Zeilen-Sichtbarkeit aktualisieren
     *   dp(int)                  → dp-zu-Pixel Konvertierung
     *
     */

    // ============================================================================
    // FELDER
    // ============================================================================

    private Context context;
    private editorManager manager;

    private FrameLayout root;              // Root-Layout (ermöglicht Modal-Overlay)
    private LinearLayout treeContainer;    // Container für Baum-Zeilen
    private View modalOverlay;             // Modal-Overlay (GONE/VISIBLE)

    // Modal-Felder
    private EditText titleField;
    private EditText descriptionField;
    private EditText durationField;
    private EditText cooldownField;
    private EditText minIntervalField;
    private EditText repValueField;
    private Spinner parentSpinner;
    private Spinner weekdaySpinner;
    private TextView errorText;

    // Modal-Layout-Container (für Visibility-Toggling)
    private View durationRow;
    private View parentRow;
    private View cooldownRow;
    private View minIntervalRow;
    private LinearLayout repetitionSection;
    private View weekdayRow;

    // Typ-Buttons (für Disable im Edit-Modus)
    private Button[] typeButtons = new Button[4];
    private Button[] priorityButtons = new Button[4];
    private Button[] repTypeButtons = new Button[3];
    private Button[] repUnitButtons = new Button[3];

    // Parent-Auswahl Daten
    private List<trackedItem> availableParents = new ArrayList<>();

    // Modal-Zustand
    private ItemType selectedType = ItemType.TASK;
    private Priority selectedPriority = Priority.MODERATE;
    private RepetitionType selectedRepType = RepetitionType.INTERVAL;
    private RepUnits selectedRepUnit = RepUnits.DAY;
    private trackedItem editingItem = null;  // null = Create-Modus

    // Baum-Zustand
    private Map<Long, Boolean> expandedState = new HashMap<>();
    private Set<ItemType> activeFilters = EnumSet.allOf(ItemType.class);
    private String searchQuery = "";

    // ============================================================================
    // KONSTRUKTOR
    // ============================================================================

    public editItem(Context context, editorManager manager) {
        this.context = context;
        this.manager = manager;
    }

    // ============================================================================
    // BUILDVIEW - Haupteinstieg: Baut Root-FrameLayout mit Tree + Modal
    // ============================================================================
    /**
     * Erstellt die komplette View-Hierarchie für den "Verwalten"-Tab.
     * FrameLayout als Root ermöglicht Modal-Overlay über dem ScrollView.
     *
     * @return Die fertige View zum Einbetten in mainActivity
     */
    public View buildView() {
        root = new FrameLayout(context);
        root.setLayoutParams(new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT));

        // Hauptinhalt: ScrollView mit Toolbar + Baum
        ScrollView scrollView = new ScrollView(context);
        scrollView.setFillViewport(true);
        scrollView.setLayoutParams(new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT));

        LinearLayout mainContent = new LinearLayout(context);
        mainContent.setOrientation(LinearLayout.VERTICAL);
        mainContent.setPadding(dp(context, 8), dp(context, 8), dp(context, 8), dp(context, 8));

        mainContent.addView(buildToolbar());

        treeContainer = new LinearLayout(context);
        treeContainer.setOrientation(LinearLayout.VERTICAL);
        mainContent.addView(treeContainer);

        scrollView.addView(mainContent);
        root.addView(scrollView);

        // Modal-Overlay
        modalOverlay = buildModal();
        root.addView(modalOverlay);

        // Baum initial aufbauen
        buildTree();

        return root;
    }

    // ============================================================================
    // BUILDTOOLBAR - Suchleiste + Filter-Button + Create-Button
    // ============================================================================
    /**
     * Horizontale Toolbar oben:
     * - EditText (weight=1) mit TextWatcher für Suche
     * - Filter-Button (togglet activeFilters)
     * - "+" Button (ACCENT) → showModal(null)
     */
    private View buildToolbar() {
        LinearLayout toolbar = new LinearLayout(context);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(dp(context, 4), dp(context, 4), dp(context, 4), dp(context, 4));

        // Suchleiste
        EditText searchField = new EditText(context);
        searchField.setHint("Suchen...");
        searchField.setSingleLine(true);
        searchField.setTextSize(TypedValue.COMPLEX_UNIT_SP, SP_BODY);
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        searchParams.setMarginEnd(dp(context, 8));
        searchField.setLayoutParams(searchParams);
        searchField.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                searchQuery = s.toString().toLowerCase();
                applyFilter();
            }
        });
        toolbar.addView(searchField);

        // Filter-Button
        Button filterBtn = new Button(context);
        filterBtn.setText("F");
        filterBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, SP_SMALL);
        filterBtn.setMinimumWidth(dp(context, 40));
        filterBtn.setMinimumHeight(dp(context, 40));
        filterBtn.setPadding(dp(context, 4), 0, dp(context, 4), 0);
        LinearLayout.LayoutParams filterParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        filterParams.setMarginEnd(dp(context, 8));
        filterBtn.setLayoutParams(filterParams);
        filterBtn.setOnClickListener(v -> {
            // Cycle durch Filter-Modi: Alle → nur PROJECT → nur BLOCK → nur GOAL → nur TASK → Alle
            if (activeFilters.size() == 4) {
                activeFilters = EnumSet.of(ItemType.PROJECT);
            } else if (activeFilters.contains(ItemType.PROJECT) && activeFilters.size() == 1) {
                activeFilters = EnumSet.of(ItemType.BLOCK);
            } else if (activeFilters.contains(ItemType.BLOCK) && activeFilters.size() == 1) {
                activeFilters = EnumSet.of(ItemType.GOAL);
            } else if (activeFilters.contains(ItemType.GOAL) && activeFilters.size() == 1) {
                activeFilters = EnumSet.of(ItemType.TASK);
            } else {
                activeFilters = EnumSet.allOf(ItemType.class);
            }
            applyFilter();
        });
        toolbar.addView(filterBtn);

        // Create-Button
        Button createBtn = new Button(context);
        createBtn.setText("+");
        createBtn.setTextColor(Color.WHITE);
        createBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, SP_HEADING);
        createBtn.setBackground(roundedBg(context, ACCENT, CORNER_RADIUS));
        createBtn.setMinimumWidth(dp(context, 40));
        createBtn.setMinimumHeight(dp(context, 40));
        createBtn.setPadding(dp(context, 8), 0, dp(context, 8), 0);
        createBtn.setOnClickListener(v -> showModal(null));
        toolbar.addView(createBtn);

        return toolbar;
    }

    // ============================================================================
    // BUILDTREE - Rendert Baum-Zeilen aus manager.getAllItems()
    // ============================================================================
    /**
     * Lädt TreeEntry-Liste via manager.getAllItems() und erstellt
     * pro Eintrag eine Zeile via buildTreeRow(). Befüllt treeContainer.
     */
    private void buildTree() {
        List<TreeEntry> entries = manager.getAllItems();
        for (TreeEntry entry : entries) {
            View row = buildTreeRow(entry);
            row.setTag(entry);
            treeContainer.addView(row);
        }
        applyFilter();
    }

    // ============================================================================
    // BUILDTREEROW - Einzelne Baum-Zeile: Einrückung + Badge + Titel
    // ============================================================================
    /**
     * Erstellt eine Zeile für den Baum:
     * - paddingStart = depth × 16dp
     * - Farbiges Typ-Badge (P/B/G/T)
     * - Titel-Text
     * - Klick: expand/collapse (wenn children) oder showModal (Blatt)
     * - Long-Press: showModal (immer)
     *
     * @param entry TreeEntry mit item und depth
     * @return Die fertige Zeilen-View
     */
    private View buildTreeRow(TreeEntry entry) {
        trackedItem item = entry.item();
        int depth = entry.depth();

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(context, 16 * depth + 8), dp(context, 10), dp(context, 8), dp(context, 10));
        row.setBackgroundColor(SURFACE);

        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        rowParams.setMargins(0, dp(context, 1), 0, 0);
        row.setLayoutParams(rowParams);

        // Typ-Badge
        TextView badge = new TextView(context);
        String badgeLabel = switch (item.type) {
            case PROJECT -> "P";
            case BLOCK -> "B";
            case GOAL -> "G";
            case TASK -> "T";
        };
        int badgeColor = switch (item.type) {
            case PROJECT -> BADGE_PROJECT;
            case BLOCK -> BADGE_BLOCK;
            case GOAL -> BADGE_GOAL;
            case TASK -> BADGE_TASK;
        };
        badge.setText(badgeLabel);
        badge.setTextColor(Color.WHITE);
        badge.setTextSize(TypedValue.COMPLEX_UNIT_SP, SP_BADGE);
        badge.setTypeface(null, Typeface.BOLD);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(roundedBg(context, badgeColor, 3));
        badge.setMinWidth(dp(context, 22));
        badge.setPadding(dp(context, 4), dp(context, 2), dp(context, 4), dp(context, 2));
        LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        badgeParams.setMarginEnd(dp(context, 8));
        badge.setLayoutParams(badgeParams);
        row.addView(badge);

        // Titel
        TextView title = new TextView(context);
        title.setText(item.title);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, SP_BODY);
        title.setTextColor(TEXT_PRIMARY);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        title.setLayoutParams(titleParams);
        row.addView(title);

        // Expand-Indikator für Items mit Kindern
        boolean hasChildren = item.children != null && !item.children.isEmpty();
        if (hasChildren) {
            TextView arrow = new TextView(context);
            Boolean expanded = expandedState.getOrDefault(item.id, true);
            arrow.setText(expanded ? "▼" : "▶");
            arrow.setTextSize(TypedValue.COMPLEX_UNIT_SP, SP_SMALL);
            arrow.setTextColor(TEXT_SECONDARY);
            row.addView(arrow);
        }

        // Klick-Handler
        row.setOnClickListener(v -> {
            if (hasChildren) {
                // Toggle expand/collapse
                boolean currentlyExpanded = expandedState.getOrDefault(item.id, true);
                expandedState.put(item.id, !currentlyExpanded);
                refreshTree();
            } else {
                // Blatt-Item: Edit
                showModal(item);
            }
        });

        // Long-Press: immer Edit
        row.setOnLongClickListener(v -> {
            showModal(item);
            return true;
        });

        // Sichtbarkeit basierend auf Parent-Expand-State
        if (depth > 0 && item.parent != null) {
            Boolean parentExpanded = expandedState.getOrDefault(item.parent, true);
            if (!parentExpanded) {
                row.setVisibility(View.GONE);
            }
        }

        return row;
    }

    // ============================================================================
    // BUILDMODAL - Erstellt das Overlay + Formular (initial GONE)
    // ============================================================================
    /**
     * Baut das modale Overlay:
     * - FrameLayout mit halbtransparentem Background (0x80000000)
     * - Zentrierte weiße Card (90% Breite)
     * - ScrollView mit allen Formular-Feldern
     * - Initial visibility = GONE
     */
    private View buildModal() {
        // Overlay (halbtransparent)
        FrameLayout overlay = new FrameLayout(context);
        overlay.setBackgroundColor(OVERLAY_DIM);
        overlay.setLayoutParams(new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT));
        overlay.setVisibility(View.GONE);
        overlay.setOnClickListener(v -> hideModal());

        // Card (weiß, 90% Breite, zentriert)
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundColor(Color.WHITE);
        card.setElevation(dp(context, 8));
        FrameLayout.LayoutParams cardParams = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT);
        cardParams.gravity = Gravity.CENTER;
        cardParams.setMargins(dp(context, 20), dp(context, 32), dp(context, 20), dp(context, 32));
        card.setLayoutParams(cardParams);
        card.setOnClickListener(v -> {}); // Klick auf Card schließt nicht

        // ScrollView für Formular
        ScrollView formScroll = new ScrollView(context);
        formScroll.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout form = new LinearLayout(context);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(context, 16), dp(context, 16), dp(context, 16), dp(context, 16));

        // === Typ-Auswahl ===
        form.addView(buildLabel(context, "Typ"));
        LinearLayout typeRow = new LinearLayout(context);
        typeRow.setOrientation(LinearLayout.HORIZONTAL);
        typeRow.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT));
        String[] typeLabels = {"TASK", "GOAL", "BLOCK", "PROJECT"};
        ItemType[] types = {ItemType.TASK, ItemType.GOAL, ItemType.BLOCK, ItemType.PROJECT};
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            Button btn = new Button(context);
            btn.setText(typeLabels[i]);
            btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, SP_BADGE);
            btn.setPadding(dp(context, 4), dp(context, 4), dp(context, 4), dp(context, 4));
            LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            btnParams.setMarginEnd(dp(context, 4));
            btn.setLayoutParams(btnParams);
            btn.setOnClickListener(v -> {
                selectedType = types[idx];
                updateButtonGroup(typeButtons, idx);
                updateFieldVisibility(selectedType);
                refreshParentSpinner();
            });
            typeButtons[i] = btn;
            typeRow.addView(btn);
        }
        form.addView(typeRow);
        form.addView(buildSpacer(context,8));

        // === Titel ===
        form.addView(buildLabel(context, "Titel"));
        titleField = new EditText(context);
        titleField.setSingleLine(true);
        titleField.setHint("Item-Titel");
        titleField.setTextSize(TypedValue.COMPLEX_UNIT_SP, SP_BODY);
        form.addView(titleField);

        // === Beschreibung ===
        form.addView(buildLabel(context, "Beschreibung"));
        descriptionField = new EditText(context);
        descriptionField.setHint("Beschreibung (optional)");
        descriptionField.setMaxLines(3);
        descriptionField.setTextSize(TypedValue.COMPLEX_UNIT_SP, SP_BODY);
        form.addView(descriptionField);

        // === Dauer ===
        durationRow = buildNumberRow("Dauer (Minuten)", durationField = new EditText(context));
        form.addView(durationRow);

        // === Priorität ===
        form.addView(buildLabel(context, "Priorität"));
        LinearLayout prioRow = new LinearLayout(context);
        prioRow.setOrientation(LinearLayout.HORIZONTAL);
        prioRow.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT));
        String[] prioLabels = {"L", "M", "H", "C"};
        Priority[] priorities = {Priority.LOW, Priority.MODERATE, Priority.HIGH, Priority.CRITICAL};
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            Button btn = new Button(context);
            btn.setText(prioLabels[i]);
            btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, SP_SMALL);
            LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            btnParams.setMarginEnd(dp(context, 4));
            btn.setLayoutParams(btnParams);
            btn.setOnClickListener(v -> {
                selectedPriority = priorities[idx];
                updateButtonGroup(priorityButtons, idx);
            });
            priorityButtons[i] = btn;
            prioRow.addView(btn);
        }
        form.addView(prioRow);
        form.addView(buildSpacer(context,8));

        // === Parent ===
        parentRow = buildSpinnerRow("Parent", parentSpinner = new Spinner(context));
        form.addView(parentRow);

        // === Cooldown ===
        cooldownRow = buildNumberRow("Cooldown (Tage)", cooldownField = new EditText(context));
        form.addView(cooldownRow);

        // === minIntervalDays ===
        minIntervalRow = buildNumberRow("Min. Intervall (Tage)", minIntervalField = new EditText(context));
        form.addView(minIntervalRow);

        // === Wiederholung ===
        repetitionSection = new LinearLayout(context);
        repetitionSection.setOrientation(LinearLayout.VERTICAL);
        repetitionSection.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT));

        repetitionSection.addView(buildLabel(context, "Wiederholung"));

        // RepType-Buttons
        LinearLayout repTypeRow = new LinearLayout(context);
        repTypeRow.setOrientation(LinearLayout.HORIZONTAL);
        String[] repLabels = {"INTERVAL", "REPS", "DAY_OF"};
        RepetitionType[] repTypes = {RepetitionType.INTERVAL, RepetitionType.REPS_PER_TIME, RepetitionType.DAY_OF_TIME};
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            Button btn = new Button(context);
            btn.setText(repLabels[i]);
            btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
            LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            btnParams.setMarginEnd(dp(context, 4));
            btn.setLayoutParams(btnParams);
            btn.setOnClickListener(v -> {
                selectedRepType = repTypes[idx];
                updateButtonGroup(repTypeButtons, idx);
                updateWeekdayVisibility();
            });
            repTypeButtons[i] = btn;
            repTypeRow.addView(btn);
        }
        repetitionSection.addView(repTypeRow);

        // Wert
        repetitionSection.addView(buildNumberRow("Wert", repValueField = new EditText(context)));

        // Einheit-Buttons
        LinearLayout unitRow = new LinearLayout(context);
        unitRow.setOrientation(LinearLayout.HORIZONTAL);
        String[] unitLabels = {"TAG", "WOCHE", "MONAT"};
        RepUnits[] units = {RepUnits.DAY, RepUnits.WEEK, RepUnits.MONTH};
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            Button btn = new Button(context);
            btn.setText(unitLabels[i]);
            btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
            LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            btnParams.setMarginEnd(dp(context, 4));
            btn.setLayoutParams(btnParams);
            btn.setOnClickListener(v -> {
                selectedRepUnit = units[idx];
                updateButtonGroup(repUnitButtons, idx);
                updateWeekdayVisibility();
            });
            repUnitButtons[i] = btn;
            unitRow.addView(btn);
        }
        repetitionSection.addView(unitRow);

        // Wochentag-Spinner (nur bei DAY_OF_TIME + WEEK)
        weekdayRow = buildSpinnerRow("Wochentag", weekdaySpinner = new Spinner(context));
        String[] days = {"Montag", "Dienstag", "Mittwoch", "Donnerstag", "Freitag", "Samstag", "Sonntag"};
        weekdaySpinner.setAdapter(new ArrayAdapter<>(context,
            android.R.layout.simple_spinner_dropdown_item, days));
        repetitionSection.addView(weekdayRow);

        form.addView(repetitionSection);
        form.addView(buildSpacer(context,12));

        // === Fehler-Text ===
        errorText = new TextView(context);
        errorText.setTextColor(TEXT_ERROR);
        errorText.setTextSize(TypedValue.COMPLEX_UNIT_SP, SP_SMALL);
        errorText.setVisibility(View.GONE);
        form.addView(errorText);
        form.addView(buildSpacer(context,8));

        // === Speichern-Button ===
        Button saveBtn = new Button(context);
        saveBtn.setText("Speichern");
        saveBtn.setTextColor(Color.WHITE);
        saveBtn.setBackground(roundedBg(context, ACCENT, CORNER_RADIUS));
        saveBtn.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT));
        saveBtn.setOnClickListener(v -> saveItem());
        form.addView(saveBtn);
        form.addView(buildSpacer(context,8));

        // === Abbrechen-Button ===
        Button cancelBtn = new Button(context);
        cancelBtn.setText("Abbrechen");
        cancelBtn.setTextColor(TEXT_SECONDARY);
        cancelBtn.setBackground(roundedBg(context, BUTTON_INACTIVE, CORNER_RADIUS));
        cancelBtn.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT));
        cancelBtn.setOnClickListener(v -> hideModal());
        form.addView(cancelBtn);

        formScroll.addView(form);
        card.addView(formScroll);
        overlay.addView(card);

        return overlay;
    }

    // === Helfer für Modal-Aufbau ===

    private View buildNumberRow(String label, EditText field) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT));
        row.addView(buildLabel(context, label));
        field.setInputType(InputType.TYPE_CLASS_NUMBER);
        field.setSingleLine(true);
        field.setTextSize(TypedValue.COMPLEX_UNIT_SP, SP_BODY);
        row.addView(field);
        return row;
    }

    private View buildSpinnerRow(String label, Spinner spinner) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT));
        row.addView(buildLabel(context, label));
        row.addView(spinner);
        return row;
    }

    private void updateButtonGroup(Button[] buttons, int selectedIdx) {
        for (int i = 0; i < buttons.length; i++) {
            if (buttons[i] == null) continue;
            if (i == selectedIdx) {
                buttons[i].setBackgroundColor(ACCENT);
                buttons[i].setTextColor(Color.WHITE);
            } else {
                buttons[i].setBackgroundColor(BUTTON_INACTIVE);
                buttons[i].setTextColor(TEXT_PRIMARY);
            }
        }
    }

    private void refreshParentSpinner() {
        availableParents = manager.getAvailableParents(selectedType);
        List<String> parentNames = new ArrayList<>();
        parentNames.add("(kein Parent)");
        for (trackedItem p : availableParents) {
            parentNames.add(p.title);
        }
        parentSpinner.setAdapter(new ArrayAdapter<>(context,
            android.R.layout.simple_spinner_dropdown_item, parentNames));
    }

    private void updateWeekdayVisibility() {
        boolean showWeekday = selectedRepType == RepetitionType.DAY_OF_TIME
            && selectedRepUnit == RepUnits.WEEK;
        weekdayRow.setVisibility(showWeekday ? View.VISIBLE : View.GONE);
    }

    // ============================================================================
    // SHOWMODAL - Modal sichtbar machen und Felder befüllen
    // ============================================================================
    /**
     * Zeigt das Modal an.
     * - item == null → Create-Modus (leere Felder, Typ-Buttons aktiv)
     * - item != null → Edit-Modus (Felder vorgefüllt, Typ-Buttons disabled)
     *
     * @param item Das zu editierende Item (null für neues Item)
     */
    private void showModal(trackedItem item) {
        editingItem = item;
        errorText.setVisibility(View.GONE);

        if (item == null) {
            // Create-Modus: leere Felder, Typ-Buttons aktiv
            selectedType = ItemType.TASK;
            selectedPriority = Priority.MODERATE;
            selectedRepType = RepetitionType.INTERVAL;
            selectedRepUnit = RepUnits.DAY;

            titleField.setText("");
            descriptionField.setText("");
            durationField.setText("");
            cooldownField.setText("");
            minIntervalField.setText("");
            repValueField.setText("");

            for (Button btn : typeButtons) btn.setEnabled(true);
            updateButtonGroup(typeButtons, 0);
            updateButtonGroup(priorityButtons, 1);
            updateButtonGroup(repTypeButtons, 0);
            updateButtonGroup(repUnitButtons, 0);
        } else {
            // Edit-Modus: Felder vorgefüllt, Typ-Buttons disabled
            selectedType = item.type;
            selectedPriority = item.priority != null ? item.priority : Priority.MODERATE;

            titleField.setText(item.title);
            descriptionField.setText(item.description != null ? item.description : "");
            durationField.setText(item.timeToComplete > 0 ? String.valueOf(item.timeToComplete) : "");
            cooldownField.setText(item.cooldown > 0 ? String.valueOf(item.cooldown) : "");
            minIntervalField.setText(item.minIntervalDays > 0 ? String.valueOf(item.minIntervalDays) : "");

            // Typ-Buttons: disabled + korrekt selektiert
            int typeIdx = switch (item.type) {
                case TASK -> 0;
                case GOAL -> 1;
                case BLOCK -> 2;
                case PROJECT -> 3;
            };
            for (Button btn : typeButtons) btn.setEnabled(false);
            updateButtonGroup(typeButtons, typeIdx);

            // Priorität selektieren
            int prioIdx = switch (selectedPriority) {
                case LOW -> 0;
                case MODERATE -> 1;
                case HIGH -> 2;
                case CRITICAL -> 3;
            };
            updateButtonGroup(priorityButtons, prioIdx);

            // Wiederholung befüllen
            if (item.repetition != null) {
                selectedRepType = item.repetition.type;
                selectedRepUnit = item.repetition.unit;
                repValueField.setText(String.valueOf(item.repetition.value));
                int repIdx = switch (selectedRepType) {
                    case INTERVAL -> 0;
                    case REPS_PER_TIME -> 1;
                    case DAY_OF_TIME -> 2;
                };
                updateButtonGroup(repTypeButtons, repIdx);
                int unitIdx = switch (selectedRepUnit) {
                    case DAY -> 0;
                    case WEEK -> 1;
                    case MONTH -> 2;
                };
                updateButtonGroup(repUnitButtons, unitIdx);
                if (item.repetition.dayOfWeek != null) {
                    weekdaySpinner.setSelection(item.repetition.dayOfWeek.getValue() - 1);
                }
            } else {
                repValueField.setText("");
                updateButtonGroup(repTypeButtons, 0);
                updateButtonGroup(repUnitButtons, 0);
            }
        }

        updateFieldVisibility(selectedType);
        refreshParentSpinner();

        // Parent-Spinner auf aktuellen Parent setzen
        if (item != null && item.parent != null) {
            for (int i = 0; i < availableParents.size(); i++) {
                if (availableParents.get(i).id.equals(item.parent)) {
                    parentSpinner.setSelection(i + 1); // +1 wegen "(kein Parent)"
                    break;
                }
            }
        }

        updateWeekdayVisibility();
        modalOverlay.setVisibility(View.VISIBLE);
    }

    // ============================================================================
    // HIDEMODAL - Modal ausblenden
    // ============================================================================
    /** Setzt Modal-Overlay visibility auf GONE, resettet editingItem */
    private void hideModal() {
        modalOverlay.setVisibility(View.GONE);
        editingItem = null;
    }

    // ============================================================================
    // SAVEITEM - Validierung + create/update + Tree refresh
    // ============================================================================
    /**
     * Liest Formular-Felder aus, validiert (Titel required),
     * baut trackedItem via Builder, ruft manager.createItem() oder
     * manager.updateItem() auf, und refresht den Baum.
     */
    private void saveItem() {
        // Validierung: Titel required
        String title = titleField.getText().toString().trim();
        if (title.isEmpty()) {
            errorText.setText("Titel darf nicht leer sein.");
            errorText.setVisibility(View.VISIBLE);
            return;
        }
        errorText.setVisibility(View.GONE);

        // trackedItem via Builder erstellen
        trackedItem.Builder builder = new trackedItem.Builder(selectedType, title, selectedPriority);

        // Beschreibung
        String desc = descriptionField.getText().toString().trim();
        if (!desc.isEmpty()) builder.description(desc);

        // Dauer
        String durStr = durationField.getText().toString().trim();
        if (!durStr.isEmpty()) {
            try { builder.timeToComplete(Integer.parseInt(durStr)); }
            catch (NumberFormatException e) { /* ignorieren */ }
        }

        // Cooldown
        String cdStr = cooldownField.getText().toString().trim();
        if (!cdStr.isEmpty()) {
            try { builder.cooldown(Integer.parseInt(cdStr)); }
            catch (NumberFormatException e) { /* ignorieren */ }
        }

        // minIntervalDays
        String miStr = minIntervalField.getText().toString().trim();
        if (!miStr.isEmpty()) {
            try { builder.minIntervalDays(Integer.parseInt(miStr)); }
            catch (NumberFormatException e) { /* ignorieren */ }
        }

        // Parent
        int parentIdx = parentSpinner.getSelectedItemPosition();
        if (parentIdx > 0 && parentIdx <= availableParents.size()) {
            builder.parent(availableParents.get(parentIdx - 1).id);
        }

        // Wiederholung (nur bei TASK)
        if (selectedType == ItemType.TASK) {
            String repStr = repValueField.getText().toString().trim();
            if (!repStr.isEmpty()) {
                try {
                    int repValue = Integer.parseInt(repStr);
                    if (selectedRepType == RepetitionType.DAY_OF_TIME
                        && selectedRepUnit == RepUnits.WEEK) {
                        DayOfWeek dow = DayOfWeek.of(weekdaySpinner.getSelectedItemPosition() + 1);
                        builder.repetition(selectedRepType, repValue, selectedRepUnit, dow);
                    } else {
                        builder.repetition(selectedRepType, repValue, selectedRepUnit);
                    }
                } catch (NumberFormatException e) { /* ignorieren */ }
            }
        }

        trackedItem newItem = builder.build();

        if (editingItem != null) {
            // Edit-Modus: ID übernehmen + update
            newItem.id = editingItem.id;
            newItem.created = editingItem.created;
            newItem.children = editingItem.children;
            newItem.lastCompletion = editingItem.lastCompletion;
            newItem.completions = editingItem.completions;
            newItem.isCompleted = editingItem.isCompleted;
            newItem.scheduled = editingItem.scheduled;
            newItem.currentStreak = editingItem.currentStreak;
            newItem.averageStreak = editingItem.averageStreak;
            newItem.nrOfStreaks = editingItem.nrOfStreaks;
            newItem.totalCompletions = editingItem.totalCompletions;
            newItem.followUps = editingItem.followUps;
            newItem.prefTime = editingItem.prefTime;
            newItem.nextRepetition = editingItem.nextRepetition;
            manager.updateItem(newItem);
        } else {
            // Create-Modus
            manager.createItem(newItem);
        }

        hideModal();
        refreshTree();
    }

    // ============================================================================
    // REFRESHTREE - Baum-Ansicht neu aufbauen
    // ============================================================================
    /** Entfernt alle Zeilen aus treeContainer und baut sie neu auf */
    private void refreshTree() {
        treeContainer.removeAllViews();
        buildTree();
    }

    // ============================================================================
    // APPLYFILTER - Zeilen-Sichtbarkeit nach Suche und Typ-Filter
    // ============================================================================
    /**
     * Iteriert über treeContainer-Kinder und setzt visibility basierend auf:
     * - searchQuery (title.toLowerCase().contains)
     * - activeFilters (Set<ItemType>)
     */
    private void applyFilter() {
        for (int i = 0; i < treeContainer.getChildCount(); i++) {
            View child = treeContainer.getChildAt(i);
            Object tag = child.getTag();
            if (!(tag instanceof TreeEntry entry)) continue;

            trackedItem item = entry.item();
            boolean matchesSearch = searchQuery.isEmpty()
                || (item.title != null && item.title.toLowerCase().contains(searchQuery));
            boolean matchesFilter = activeFilters.contains(item.type);

            // Expand-State prüfen
            boolean parentCollapsed = false;
            if (entry.depth() > 0 && item.parent != null) {
                Boolean expanded = expandedState.getOrDefault(item.parent, true);
                if (!expanded) parentCollapsed = true;
            }

            child.setVisibility(matchesSearch && matchesFilter && !parentCollapsed
                ? View.VISIBLE : View.GONE);
        }
    }

    // ============================================================================
    // UPDATEFIELDVISIBILITY - Felder ein-/ausblenden nach gewähltem Typ
    // ============================================================================
    /**
     * Setzt visibility der typ-abhängigen Felder:
     * TASK: Dauer ✓, Parent ✓, Cooldown ✓, Wiederholung ✓
     * GOAL: Dauer ✓, Parent ✓
     * BLOCK: Parent ✓
     * PROJECT: minIntervalDays ✓
     */
    private void updateFieldVisibility(ItemType type) {
        // TASK: Dauer ✓, Parent ✓, Cooldown ✓, Wiederholung ✓
        // GOAL: Dauer ✓, Parent ✓
        // BLOCK: Parent ✓
        // PROJECT: minIntervalDays ✓
        durationRow.setVisibility(
            (type == ItemType.TASK || type == ItemType.GOAL) ? View.VISIBLE : View.GONE);
        parentRow.setVisibility(
            (type != ItemType.PROJECT) ? View.VISIBLE : View.GONE);
        cooldownRow.setVisibility(
            (type == ItemType.TASK) ? View.VISIBLE : View.GONE);
        minIntervalRow.setVisibility(
            (type == ItemType.PROJECT) ? View.VISIBLE : View.GONE);
        repetitionSection.setVisibility(
            (type == ItemType.TASK) ? View.VISIBLE : View.GONE);
    }

}

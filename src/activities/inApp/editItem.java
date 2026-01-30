package activities.inApp;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;


import static activities.generic.UIConstants.*;
import static activities.generic.ViewHelper.*;

import com.autosecretary.R;

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
     * Inflated die View-Hierarchie fuer den "Verwalten"-Tab aus XML.
     * FrameLayout als Root ermoeglicht Modal-Overlay ueber dem ScrollView.
     *
     * @return Die fertige View zum Einbetten in mainActivity
     */
    public View buildView() {
        root = (FrameLayout) LayoutInflater.from(context).inflate(R.layout.view_edit_item, null);

        // Toolbar-Elemente binden
        EditText searchField = root.findViewById(R.id.search_field);
        searchField.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                searchQuery = s.toString().toLowerCase();
                applyFilter();
            }
        });

        Button filterBtn = root.findViewById(R.id.btn_filter);
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

        Button createBtn = root.findViewById(R.id.btn_create);
        createBtn.setBackground(roundedBg(context, ACCENT, CORNER_RADIUS));
        createBtn.setOnClickListener(v -> showModal(null));

        treeContainer = root.findViewById(R.id.tree_container);

        // Modal binden
        bindModal(root);

        // Baum initial aufbauen
        buildTree();

        return root;
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
    // BINDMODAL - Bindet Modal-Elemente aus XML-Layout
    // ============================================================================
    /**
     * Bindet alle Modal-Formularelemente aus dem inflated XML-Layout
     * und registriert Click-Listener fuer Buttons, Save/Cancel.
     */
    private void bindModal(View root) {
        modalOverlay = root.findViewById(R.id.modal_overlay);
        modalOverlay.setOnClickListener(v -> hideModal());

        // Formular-Felder binden
        titleField = root.findViewById(R.id.field_title);
        descriptionField = root.findViewById(R.id.field_description);
        durationField = root.findViewById(R.id.field_duration);
        cooldownField = root.findViewById(R.id.field_cooldown);
        minIntervalField = root.findViewById(R.id.field_min_interval);
        repValueField = root.findViewById(R.id.field_rep_value);
        parentSpinner = root.findViewById(R.id.spinner_parent);
        weekdaySpinner = root.findViewById(R.id.spinner_weekday);
        errorText = root.findViewById(R.id.text_error);

        // Container fuer Visibility-Toggling
        durationRow = root.findViewById(R.id.row_duration);
        parentRow = root.findViewById(R.id.row_parent);
        cooldownRow = root.findViewById(R.id.row_cooldown);
        minIntervalRow = root.findViewById(R.id.row_min_interval);
        repetitionSection = root.findViewById(R.id.section_repetition);
        weekdayRow = root.findViewById(R.id.row_weekday);

        // Typ-Buttons
        typeButtons[0] = root.findViewById(R.id.btn_type_task);
        typeButtons[1] = root.findViewById(R.id.btn_type_goal);
        typeButtons[2] = root.findViewById(R.id.btn_type_block);
        typeButtons[3] = root.findViewById(R.id.btn_type_project);
        ItemType[] types = {ItemType.TASK, ItemType.GOAL, ItemType.BLOCK, ItemType.PROJECT};
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            typeButtons[i].setOnClickListener(v -> {
                selectedType = types[idx];
                updateButtonGroup(typeButtons, idx);
                updateFieldVisibility(selectedType);
                refreshParentSpinner();
            });
        }

        // Prioritaet-Buttons
        priorityButtons[0] = root.findViewById(R.id.btn_prio_low);
        priorityButtons[1] = root.findViewById(R.id.btn_prio_moderate);
        priorityButtons[2] = root.findViewById(R.id.btn_prio_high);
        priorityButtons[3] = root.findViewById(R.id.btn_prio_critical);
        Priority[] priorities = {Priority.LOW, Priority.MODERATE, Priority.HIGH, Priority.CRITICAL};
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            priorityButtons[i].setOnClickListener(v -> {
                selectedPriority = priorities[idx];
                updateButtonGroup(priorityButtons, idx);
            });
        }

        // RepType-Buttons
        repTypeButtons[0] = root.findViewById(R.id.btn_rep_interval);
        repTypeButtons[1] = root.findViewById(R.id.btn_rep_reps);
        repTypeButtons[2] = root.findViewById(R.id.btn_rep_day_of);
        RepetitionType[] repTypes = {RepetitionType.INTERVAL, RepetitionType.REPS_PER_TIME, RepetitionType.DAY_OF_TIME};
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            repTypeButtons[i].setOnClickListener(v -> {
                selectedRepType = repTypes[idx];
                updateButtonGroup(repTypeButtons, idx);
                updateWeekdayVisibility();
            });
        }

        // RepUnit-Buttons
        repUnitButtons[0] = root.findViewById(R.id.btn_unit_day);
        repUnitButtons[1] = root.findViewById(R.id.btn_unit_week);
        repUnitButtons[2] = root.findViewById(R.id.btn_unit_month);
        RepUnits[] units = {RepUnits.DAY, RepUnits.WEEK, RepUnits.MONTH};
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            repUnitButtons[i].setOnClickListener(v -> {
                selectedRepUnit = units[idx];
                updateButtonGroup(repUnitButtons, idx);
                updateWeekdayVisibility();
            });
        }

        // Wochentag-Spinner vorbelegen
        String[] days = {"Montag", "Dienstag", "Mittwoch", "Donnerstag", "Freitag", "Samstag", "Sonntag"};
        weekdaySpinner.setAdapter(new ArrayAdapter<>(context,
            android.R.layout.simple_spinner_dropdown_item, days));

        // Save/Cancel
        Button saveBtn = root.findViewById(R.id.btn_save);
        saveBtn.setBackground(roundedBg(context, ACCENT, CORNER_RADIUS));
        saveBtn.setOnClickListener(v -> saveItem());

        Button cancelBtn = root.findViewById(R.id.btn_cancel);
        cancelBtn.setBackground(roundedBg(context, BUTTON_INACTIVE, CORNER_RADIUS));
        cancelBtn.setOnClickListener(v -> hideModal());
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

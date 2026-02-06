package activities.inApp;

import android.content.Context;
import android.graphics.Color;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import static activities.generic.ViewHelper.*;

import androidx.core.content.ContextCompat;

import com.autosecretary.R;

import activities.generic.ViewBuilder;

import android.app.DatePickerDialog;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import controller.editorManager;
import controller.editorManager.TreeEntry;
import data.BudgetDisplayData;
import entities.Account;
import entities.Category;
import entities.trackedItem;
import entities.trackedItem.DurationUnit;
import entities.trackedItem.ItemType;
import entities.trackedItem.Priority;
import entities.trackedItem.RepetitionType;
import entities.trackedItem.RepUnits;

import java.time.LocalTime;

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
     *     GOAL     → 0xFF1976D2 (Blau)   Label: "G"
     *     TASK     → 0xFF388E3C (Grün)   Label: "T"
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
     *     │  ├── Typ-Auswahl: 3 Buttons [TASK] [GOAL] [PROJECT]                  │
     *     │  ├── Titel (EditText, singleLine)                                   │
     *     │  ├── Beschreibung (EditText, maxLines=3)                            │
     *     │  ├── Dauer/timeToSchedule (EditText, number)                        │
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
     *     ┌──────────────────┬──────┬──────┬─────────┐
     *     │ Feld             │ TASK │ GOAL │ PROJECT │
     *     ├──────────────────┼──────┼──────┼─────────┤
     *     │ Dauer            │  ✓   │  ✓   │    —    │
     *     │ Parent           │  ✓   │  ✓   │    —    │
     *     │ Cooldown         │  ✓   │  —   │    —    │
     *     │ minIntervalDays  │  —   │  —   │    ✓    │
     *     │ Wiederholung     │  ✓   │  —   │    —    │
     *     └──────────────────┴──────┴──────┴─────────┘
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
    private EditText deadlineField;
    private EditText repValueField;
    private EditText progressCurrentField;
    private EditText progressTargetField;
    private EditText progressUnitField;
    private EditText goalIconField;
    private Spinner parentSpinner;
    private Spinner weekdaySpinner;
    private TextView errorText;

    // Modal-Layout-Container (für Visibility-Toggling)
    private View durationRow;
    private View parentRow;
    private View cooldownRow;
    private View deadlineRow;
    private View fixedDateRow;
    private EditText fixedDateField;
    private View fixedTimeRow;
    private EditText fixedTimeField;
    private View progressRow;
    private View progressPerRepRow;
    private Button progressPerRepToggle;
    private boolean progressPerRepEnabled = false;
    private View goalIconRow;
    private View goalColorRow;
    private LinearLayout colorGrid;
    private LinearLayout repetitionSection;
    private View repDetailsSection;
    private View weekdayRow;

    // Typ-Buttons (für Disable im Edit-Modus)
    private Button[] typeButtons = new Button[3];
    private Button[] priorityButtons = new Button[4];
    private Button[] repTypeButtons = new Button[4];
    private Button[] repUnitButtons = new Button[3];

    // Parent-Auswahl Daten
    private List<trackedItem> availableParents = new ArrayList<>();

    // Vorgänger-Auswahl (nur für TASK)
    private View predecessorRow;
    private Spinner predecessorSpinner;
    private List<trackedItem> availablePredecessors = new ArrayList<>();

    // Budget-Felder (nur für TASK)
    private View budgetRow;
    private EditText budgetAmountField;
    private View budgetAccountRow;
    private Spinner budgetAccountSpinner;
    private List<Account> availableAccounts = new ArrayList<>();
    private View budgetCategoryRow;
    private Spinner budgetCategorySpinner;
    private List<Category> expenseCategories = new ArrayList<>();

    // Neue Felder: Min/Max Dauer mit Unit
    private View minDurationRow;
    private EditText minDurationField;
    private Button[] minDurationUnitButtons = new Button[2];
    private DurationUnit selectedMinDurationUnit = DurationUnit.MINUTES;
    private Button[] maxDurationUnitButtons = new Button[2];
    private DurationUnit selectedMaxDurationUnit = DurationUnit.MINUTES;

    // Bevorzugte Uhrzeit
    private View prefTimeRow;
    private EditText prefTimeField;

    // Vorgänger-Wartezeit
    private View predecessorDelayRow;
    private EditText predecessorDelayField;

    // Erst erledigen vor Reset
    private View completeFirstRow;
    private Button completeFirstToggle;
    private boolean completeFirstEnabled = false;

    // Farb-Palette fuer Goal-Farben
    private static final String[] GOAL_COLORS = {
        "#FFE53935", "#FFD81B60", "#FF8E24AA", "#FF5E35B1", "#FF1E88E5",
        "#FF00ACC1", "#FF00897B", "#FF43A047", "#FFFB8C00", "#FF6D4C41"
    };

    // Modal-Zustand
    private ItemType selectedType = ItemType.TASK;
    private Priority selectedPriority = Priority.MODERATE;
    private RepetitionType selectedRepType = RepetitionType.NONE;
    private RepUnits selectedRepUnit = RepUnits.DAY;
    private String selectedGoalColor = null;
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
            // Cycle durch Filter-Modi: Alle → nur PROJECT → nur GOAL → nur TASK → Alle
            if (activeFilters.size() == 3) {
                activeFilters = EnumSet.of(ItemType.PROJECT);
            } else if (activeFilters.contains(ItemType.PROJECT) && activeFilters.size() == 1) {
                activeFilters = EnumSet.of(ItemType.GOAL);
            } else if (activeFilters.contains(ItemType.GOAL) && activeFilters.size() == 1) {
                activeFilters = EnumSet.of(ItemType.TASK);
            } else {
                activeFilters = EnumSet.allOf(ItemType.class);
            }
            applyFilter();
        });

        Button createBtn = root.findViewById(R.id.btn_create);
        createBtn.setBackground(roundedBg(context, ContextCompat.getColor(context, R.color.accent), 4));
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

        LinearLayout row = (LinearLayout) LayoutInflater.from(context)
            .inflate(R.layout.row_tree_item, treeContainer, false);

        // Dynamisches Padding links basierend auf Tiefe
        row.setPaddingRelative(dp(context, 16 * depth + 8),
            row.getPaddingTop(), row.getPaddingEnd(), row.getPaddingBottom());

        // Trenn-Margin
        LinearLayout.LayoutParams rowParams = (LinearLayout.LayoutParams) row.getLayoutParams();
        rowParams.setMargins(0, dp(context, 1), 0, 0);

        // Badge: Label + Farbe nach Typ
        TextView badge = row.findViewById(R.id.tree_badge);
        String badgeLabel = switch (item.type) {
            case PROJECT -> "P";
            case GOAL -> "G";
            case TASK -> "T";
        };
        int badgeColor = switch (item.type) {
            case PROJECT -> ContextCompat.getColor(context, R.color.badge_project);
            case GOAL -> ContextCompat.getColor(context, R.color.badge_goal);
            case TASK -> ContextCompat.getColor(context, R.color.badge_task);
        };
        badge.setText(badgeLabel);
        badge.setBackground(roundedBg(context, badgeColor, 3));

        // Titel
        TextView title = row.findViewById(R.id.tree_title);
        title.setText(item.title);

        // Expand-Pfeil (nur bei Items mit Kindern)
        boolean hasChildren = item.children != null && !item.children.isEmpty();
        if (hasChildren) {
            TextView arrow = row.findViewById(R.id.tree_arrow);
            arrow.setVisibility(View.VISIBLE);
            Boolean expanded = expandedState.getOrDefault(item.id, true);
            arrow.setText(expanded ? "\u25BC" : "\u25B6");
        }

        // Klick-Handler
        row.setOnClickListener(v -> {
            if (hasChildren) {
                boolean currentlyExpanded = expandedState.getOrDefault(item.id, true);
                expandedState.put(item.id, !currentlyExpanded);
                refreshTree();
            } else {
                showModal(item);
            }
        });

        // Long-Press: immer Edit
        row.setOnLongClickListener(v -> {
            showModal(item);
            return true;
        });

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

        // Basis-Felder binden
        bindBasicFields(root);

        // Spezialisierte Feld-Gruppen binden
        bindDeadlineFields(root);
        bindFixedAppointmentFields(root);
        bindGoalCustomizationFields(root);
        bindProgressFields(root);
        bindTypeButtons(root);
        bindPriorityButtons(root);
        bindRepetitionButtons(root);
        bindBudgetFields(root);
        bindDurationFields(root);
        bindPrefTimeFields(root);
        bindPredecessorDelayFields(root);
        bindCompleteFirstFields(root);

        // Save/Cancel
        Button saveBtn = root.findViewById(R.id.btn_save);
        saveBtn.setBackground(roundedBg(context, ContextCompat.getColor(context, R.color.accent), 4));
        saveBtn.setOnClickListener(v -> saveItem());

        Button cancelBtn = root.findViewById(R.id.btn_cancel);
        cancelBtn.setBackground(roundedBg(context, ContextCompat.getColor(context, R.color.button_inactive), 4));
        cancelBtn.setOnClickListener(v -> hideModal());
    }

    // ============================================================================
    // FIELD BINDING HELPERS
    // ============================================================================

    private void bindBasicFields(View root) {
        titleField = root.findViewById(R.id.field_title);
        descriptionField = root.findViewById(R.id.field_description);
        durationField = root.findViewById(R.id.field_duration);
        cooldownField = root.findViewById(R.id.field_cooldown);
        repValueField = root.findViewById(R.id.field_rep_value);
        parentSpinner = root.findViewById(R.id.spinner_parent);
        predecessorSpinner = root.findViewById(R.id.spinner_predecessor);
        weekdaySpinner = root.findViewById(R.id.spinner_weekday);
        errorText = root.findViewById(R.id.text_error);

        // Container fuer Visibility-Toggling
        durationRow = root.findViewById(R.id.row_duration);
        parentRow = root.findViewById(R.id.row_parent);
        predecessorRow = root.findViewById(R.id.row_predecessor);
        cooldownRow = root.findViewById(R.id.row_cooldown);
        repetitionSection = root.findViewById(R.id.section_repetition);
        weekdayRow = root.findViewById(R.id.row_weekday);

        // Wochentag-Spinner vorbelegen
        String[] days = {"Montag", "Dienstag", "Mittwoch", "Donnerstag", "Freitag", "Samstag", "Sonntag"};
        weekdaySpinner.setAdapter(new ArrayAdapter<>(context,
            android.R.layout.simple_spinner_dropdown_item, days));
    }

    private void bindDeadlineFields(View root) {
        deadlineRow = root.findViewById(R.id.row_deadline);
        deadlineField = root.findViewById(R.id.field_deadline);
        deadlineField.setOnClickListener(v -> {
            LocalDate init = LocalDate.now().plusDays(7);
            new DatePickerDialog(context, (dp, y, m, d) -> {
                LocalDate picked = LocalDate.of(y, m + 1, d);
                deadlineField.setText(picked.toString());
            }, init.getYear(), init.getMonthValue() - 1, init.getDayOfMonth()).show();
        });
    }

    private void bindFixedAppointmentFields(View root) {
        fixedDateRow = root.findViewById(R.id.row_fixed_date);
        fixedDateField = root.findViewById(R.id.field_fixed_date);
        fixedDateField.setOnClickListener(v -> {
            LocalDate init = LocalDate.now().plusDays(1);
            new DatePickerDialog(context, (dp, y, m, d) -> {
                LocalDate picked = LocalDate.of(y, m + 1, d);
                fixedDateField.setText(picked.toString());
            }, init.getYear(), init.getMonthValue() - 1, init.getDayOfMonth()).show();
        });

        fixedTimeRow = root.findViewById(R.id.row_fixed_time);
        fixedTimeField = root.findViewById(R.id.field_fixed_time);
        fixedTimeField.setOnClickListener(v -> {
            new android.app.TimePickerDialog(context, (tp, h, m) -> {
                fixedTimeField.setText(String.format("%02d:%02d", h, m));
            }, 12, 0, true).show();  // 24h Format
        });
    }

    private void bindGoalCustomizationFields(View root) {
        goalIconRow = root.findViewById(R.id.row_goal_icon);
        goalIconField = root.findViewById(R.id.field_goal_icon);
        goalColorRow = root.findViewById(R.id.row_goal_color);
        colorGrid = root.findViewById(R.id.color_grid);
        buildColorGrid();
    }

    private void bindProgressFields(View root) {
        progressCurrentField = root.findViewById(R.id.field_progress_current);
        progressTargetField = root.findViewById(R.id.field_progress_target);
        progressUnitField = root.findViewById(R.id.field_progress_unit);
        progressRow = root.findViewById(R.id.row_progress);
        progressPerRepRow = root.findViewById(R.id.row_progress_per_rep);
        progressPerRepToggle = root.findViewById(R.id.btn_progress_per_rep);
        progressPerRepToggle.setOnClickListener(v -> {
            progressPerRepEnabled = !progressPerRepEnabled;
            updateProgressPerRepButton();
        });
    }

    private void bindTypeButtons(View root) {
        typeButtons[0] = root.findViewById(R.id.btn_type_task);
        typeButtons[1] = root.findViewById(R.id.btn_type_goal);
        typeButtons[2] = root.findViewById(R.id.btn_type_project);
        ItemType[] types = {ItemType.TASK, ItemType.GOAL, ItemType.PROJECT};
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            typeButtons[i].setOnClickListener(v -> {
                selectedType = types[idx];
                updateButtonGroup(typeButtons, idx);
                updateFieldVisibility(selectedType);
                refreshParentSpinner();
                refreshPredecessorSpinner();
            });
        }
    }

    private void bindPriorityButtons(View root) {
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
    }

    private void bindRepetitionButtons(View root) {
        repTypeButtons[0] = root.findViewById(R.id.btn_rep_none);
        repTypeButtons[1] = root.findViewById(R.id.btn_rep_interval);
        repTypeButtons[2] = root.findViewById(R.id.btn_rep_reps);
        repTypeButtons[3] = root.findViewById(R.id.btn_rep_day_of);
        repDetailsSection = root.findViewById(R.id.section_rep_details);
        RepetitionType[] repTypes = {RepetitionType.NONE, RepetitionType.INTERVAL, RepetitionType.REPS_PER_TIME, RepetitionType.DAY_OF_TIME};
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            repTypeButtons[i].setOnClickListener(v -> {
                selectedRepType = repTypes[idx];
                updateButtonGroup(repTypeButtons, idx);
                updateRepDetailsVisibility();
                updateWeekdayVisibility();
            });
        }

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
    }

    private void bindBudgetFields(View root) {
        budgetRow = root.findViewById(R.id.row_budget);
        budgetAmountField = root.findViewById(R.id.field_budget_amount);
        budgetAccountRow = root.findViewById(R.id.row_budget_account);
        budgetAccountSpinner = root.findViewById(R.id.spinner_budget_account);
        budgetCategoryRow = root.findViewById(R.id.row_budget_category);
        budgetCategorySpinner = root.findViewById(R.id.spinner_budget_category);
        refreshBudgetAccountSpinner();
        refreshBudgetCategorySpinner();
    }

    private void bindDurationFields(View root) {
        minDurationRow = root.findViewById(R.id.row_min_duration);
        minDurationField = root.findViewById(R.id.field_min_duration);
        minDurationUnitButtons[0] = root.findViewById(R.id.btn_min_unit_minutes);
        minDurationUnitButtons[1] = root.findViewById(R.id.btn_min_unit_progress);
        DurationUnit[] minUnits = {DurationUnit.MINUTES, DurationUnit.PROGRESS_UNITS};
        for (int i = 0; i < 2; i++) {
            final int idx = i;
            minDurationUnitButtons[i].setOnClickListener(v -> {
                selectedMinDurationUnit = minUnits[idx];
                updateButtonGroup(minDurationUnitButtons, idx);
            });
        }

        maxDurationUnitButtons[0] = root.findViewById(R.id.btn_max_unit_minutes);
        maxDurationUnitButtons[1] = root.findViewById(R.id.btn_max_unit_progress);
        DurationUnit[] maxUnits = {DurationUnit.MINUTES, DurationUnit.PROGRESS_UNITS};
        for (int i = 0; i < 2; i++) {
            final int idx = i;
            maxDurationUnitButtons[i].setOnClickListener(v -> {
                selectedMaxDurationUnit = maxUnits[idx];
                updateButtonGroup(maxDurationUnitButtons, idx);
            });
        }
    }

    private void bindPrefTimeFields(View root) {
        prefTimeRow = root.findViewById(R.id.row_pref_time);
        prefTimeField = root.findViewById(R.id.field_pref_time);
        prefTimeField.setOnClickListener(v -> {
            int initHour = 9, initMin = 0;
            String current = prefTimeField.getText().toString().trim();
            if (!current.isEmpty()) {
                try {
                    LocalTime parsed = LocalTime.parse(current);
                    initHour = parsed.getHour();
                    initMin = parsed.getMinute();
                } catch (Exception e) { /* Default verwenden */ }
            }
            new android.app.TimePickerDialog(context, (tp, h, m) -> {
                prefTimeField.setText(String.format("%02d:%02d", h, m));
            }, initHour, initMin, true).show();
        });
    }

    private void bindPredecessorDelayFields(View root) {
        predecessorDelayRow = root.findViewById(R.id.row_predecessor_delay);
        predecessorDelayField = root.findViewById(R.id.field_predecessor_delay);
    }

    private void bindCompleteFirstFields(View root) {
        completeFirstRow = root.findViewById(R.id.row_complete_first);
        completeFirstToggle = root.findViewById(R.id.btn_complete_first);
        completeFirstToggle.setOnClickListener(v -> {
            completeFirstEnabled = !completeFirstEnabled;
            updateCompleteFirstButton();
        });
    }

    private void updateButtonGroup(Button[] buttons, int selectedIdx) {
        int accent = ContextCompat.getColor(context, R.color.accent);
        int inactive = ContextCompat.getColor(context, R.color.button_inactive);
        int textPrimary = ContextCompat.getColor(context, R.color.text_primary);
        for (int i = 0; i < buttons.length; i++) {
            if (buttons[i] == null) continue;
            if (i == selectedIdx) {
                buttons[i].setBackgroundColor(accent);
                buttons[i].setTextColor(Color.WHITE);
            } else {
                buttons[i].setBackgroundColor(inactive);
                buttons[i].setTextColor(textPrimary);
            }
        }
    }

    private void updateProgressPerRepButton() {
        int accent = ContextCompat.getColor(context, R.color.accent);
        int inactive = ContextCompat.getColor(context, R.color.button_inactive);
        int textPrimary = ContextCompat.getColor(context, R.color.text_primary);
        if (progressPerRepEnabled) {
            progressPerRepToggle.setBackgroundColor(accent);
            progressPerRepToggle.setTextColor(Color.WHITE);
            progressPerRepToggle.setText("An");
        } else {
            progressPerRepToggle.setBackgroundColor(inactive);
            progressPerRepToggle.setTextColor(textPrimary);
            progressPerRepToggle.setText("Aus");
        }
    }

    private void updateCompleteFirstButton() {
        int accent = ContextCompat.getColor(context, R.color.accent);
        int inactive = ContextCompat.getColor(context, R.color.button_inactive);
        int textPrimary = ContextCompat.getColor(context, R.color.text_primary);
        if (completeFirstEnabled) {
            completeFirstToggle.setBackgroundColor(accent);
            completeFirstToggle.setTextColor(Color.WHITE);
            completeFirstToggle.setText("An");
        } else {
            completeFirstToggle.setBackgroundColor(inactive);
            completeFirstToggle.setTextColor(textPrimary);
            completeFirstToggle.setText("Aus");
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
        // Bei Änderung des Parents: Vorgänger-Spinner aktualisieren
        parentSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                refreshPredecessorSpinner();
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void refreshPredecessorSpinner() {
        availablePredecessors.clear();
        List<String> names = new ArrayList<>();
        names.add("(kein Vorgänger)");

        if (selectedType == ItemType.TASK) {
            int parentIdx = parentSpinner.getSelectedItemPosition();
            if (parentIdx > 0 && parentIdx <= availableParents.size()) {
                Long goalId = availableParents.get(parentIdx - 1).id;
                // Alle Geschwister-Tasks laden (Tasks mit demselben Goal als Parent)
                for (TreeEntry entry : manager.getAllItems()) {
                    if (entry.item().type == ItemType.TASK
                        && goalId.equals(entry.item().parent)
                        && (editingItem == null || !entry.item().id.equals(editingItem.id))) {
                        availablePredecessors.add(entry.item());
                        names.add(entry.item().title);
                    }
                }
            }
        }
        predecessorSpinner.setAdapter(new ArrayAdapter<>(context,
            android.R.layout.simple_spinner_dropdown_item, names));

        // Delay-Row anzeigen wenn Vorgänger ausgewählt
        predecessorSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                boolean hasPredecessor = pos > 0;
                predecessorDelayRow.setVisibility(hasPredecessor ? View.VISIBLE : View.GONE);
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                predecessorDelayRow.setVisibility(View.GONE);
            }
        });
    }

    private void refreshBudgetAccountSpinner() {
        availableAccounts.clear();
        List<String> accountNames = new ArrayList<>();
        accountNames.add("(beliebig)");

        // Alle aktiven Konten laden via manager (der hat Zugriff auf repo)
        for (Account acc : manager.getActiveAccounts()) {
            availableAccounts.add(acc);
            accountNames.add(acc.name);
        }
        budgetAccountSpinner.setAdapter(new ArrayAdapter<>(context,
            android.R.layout.simple_spinner_dropdown_item, accountNames));
    }

    private void refreshBudgetCategorySpinner() {
        expenseCategories.clear();
        List<String> categoryNames = new ArrayList<>();
        categoryNames.add("(keine)");

        // Alle Ausgabe-Kategorien aus DB laden via manager
        for (Category cat : manager.getExpenseCategories()) {
            expenseCategories.add(cat);
            String displayName = (cat.icon != null ? cat.icon + " " : "") + cat.name;
            categoryNames.add(displayName);
        }
        budgetCategorySpinner.setAdapter(new ArrayAdapter<>(context,
            android.R.layout.simple_spinner_dropdown_item, categoryNames));
    }

    private void updateRepDetailsVisibility() {
        boolean show = selectedRepType != RepetitionType.NONE;
        repDetailsSection.setVisibility(show ? View.VISIBLE : View.GONE);
        // Deadline und feste Termine nur bei einmaligen Tasks (NONE) sichtbar
        if (selectedType == ItemType.TASK) {
            boolean showNoneFields = !show;  // show = (repType != NONE)
            deadlineRow.setVisibility(showNoneFields ? View.VISIBLE : View.GONE);
            fixedDateRow.setVisibility(showNoneFields ? View.VISIBLE : View.GONE);
            fixedTimeRow.setVisibility(showNoneFields ? View.VISIBLE : View.GONE);
        }
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
            selectedRepType = RepetitionType.NONE;
            selectedRepUnit = RepUnits.DAY;

            titleField.setText("");
            descriptionField.setText("");
            durationField.setText("");
            cooldownField.setText("");
            deadlineField.setText("");
            fixedDateField.setText("");
            fixedTimeField.setText("");
            repValueField.setText("");
            progressCurrentField.setText("");
            progressTargetField.setText("");
            progressUnitField.setText("");
            progressPerRepEnabled = false;
            goalIconField.setText("");
            selectedGoalColor = null;
            highlightSelectedColor();

            // Budget-Felder zurücksetzen
            budgetAmountField.setText("");
            budgetAccountSpinner.setSelection(0);
            budgetCategorySpinner.setSelection(0);

            // Neue Felder initialisieren
            minDurationField.setText("");
            selectedMinDurationUnit = DurationUnit.MINUTES;
            updateButtonGroup(minDurationUnitButtons, 0);
            selectedMaxDurationUnit = DurationUnit.MINUTES;
            updateButtonGroup(maxDurationUnitButtons, 0);
            prefTimeField.setText("09:00"); // Default: 09:00
            prefTimeField.setEnabled(true);
            predecessorDelayField.setText("");
            completeFirstEnabled = false;

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
            durationField.setText(item.maxDurationValue > 0 ? String.valueOf(item.maxDurationValue) : "");
            cooldownField.setText(item.cooldown > 0 ? String.valueOf(item.cooldown) : "");
            deadlineField.setText(item.deadline != null ? item.deadline.toString() : "");
            fixedDateField.setText(item.fixedDate != null ? item.fixedDate.toString() : "");
            fixedTimeField.setText(item.fixedTime != null ? item.fixedTime.toString() : "");
            progressCurrentField.setText(item.progressCurrent > 0 ? String.valueOf(item.progressCurrent) : "");
            progressTargetField.setText(item.progressTarget > 0 ? String.valueOf(item.progressTarget) : "");
            progressUnitField.setText(item.progressUnit != null ? item.progressUnit : "");
            progressPerRepEnabled = item.progressPerRep;
            goalIconField.setText(item.goalIcon != null ? item.goalIcon : "");
            selectedGoalColor = item.goalColor;
            highlightSelectedColor();

            // Budget-Felder befüllen
            if (item.budgetRequirementCents > 0) {
                budgetAmountField.setText(String.format("%.2f", item.budgetRequirementCents / 100.0));
            } else {
                budgetAmountField.setText("");
            }

            // Neue Felder befüllen
            minDurationField.setText(item.minDurationValue > 0 ? String.valueOf(item.minDurationValue) : "");
            selectedMinDurationUnit = item.minDurationUnit != null ? item.minDurationUnit : DurationUnit.MINUTES;
            updateButtonGroup(minDurationUnitButtons, selectedMinDurationUnit == DurationUnit.MINUTES ? 0 : 1);

            selectedMaxDurationUnit = item.maxDurationUnit != null ? item.maxDurationUnit : DurationUnit.MINUTES;
            updateButtonGroup(maxDurationUnitButtons, selectedMaxDurationUnit == DurationUnit.MINUTES ? 0 : 1);

            // prefTime anzeigen (editierbar - User kann überschreiben, System lernt weiter)
            prefTimeField.setText(item.prefTime != null ? item.prefTime.toString() : "");
            prefTimeField.setEnabled(true);
            predecessorDelayField.setText(item.predecessorDelay > 0 ? String.valueOf(item.predecessorDelay) : "");
            completeFirstEnabled = item.completeFirst != null && item.completeFirst;

            // Typ-Buttons: disabled + korrekt selektiert
            int typeIdx = switch (item.type) {
                case TASK -> 0;
                case GOAL -> 1;
                case PROJECT -> 2;
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
                    case NONE -> 0;
                    case INTERVAL -> 1;
                    case REPS_PER_TIME -> 2;
                    case DAY_OF_TIME -> 3;
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
                selectedRepType = RepetitionType.NONE;
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

        // Vorgänger-Spinner auf aktuellen predecessor setzen
        refreshPredecessorSpinner();
        if (item != null && item.predecessor != null) {
            for (int i = 0; i < availablePredecessors.size(); i++) {
                if (availablePredecessors.get(i).id.equals(item.predecessor)) {
                    predecessorSpinner.setSelection(i + 1); // +1 wegen "(kein Vorgänger)"
                    break;
                }
            }
        }

        // Budget-Account-Spinner auf aktuelles Konto setzen
        refreshBudgetAccountSpinner();
        if (item != null && item.budgetAccountId != null) {
            for (int i = 0; i < availableAccounts.size(); i++) {
                if (availableAccounts.get(i).id.equals(item.budgetAccountId)) {
                    budgetAccountSpinner.setSelection(i + 1); // +1 wegen "(beliebig)"
                    break;
                }
            }
        }

        // Budget-Kategorie-Spinner auf aktuelle Kategorie setzen
        if (item != null && item.budgetCategoryId != null) {
            for (int i = 0; i < expenseCategories.size(); i++) {
                if (expenseCategories.get(i).id.equals(item.budgetCategoryId)) {
                    budgetCategorySpinner.setSelection(i + 1); // +1 wegen "(keine)"
                    break;
                }
            }
        }

        updateRepDetailsVisibility();
        updateWeekdayVisibility();
        updateProgressPerRepButton();
        updateCompleteFirstButton();
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
    // OPENCREATEMODAL - Öffnet Create-Modal von außen (für Widget-Integration)
    // ============================================================================
    /**
     * Öffentliche API um das Create-Modal von außen zu öffnen.
     * Wird von mainActivity aufgerufen wenn der "+" Button im Widget geklickt wird.
     */
    public void openCreateModal() {
        showModal(null);
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

        // Felder via Helper-Methoden anwenden
        applyDurationFields(builder);
        applyDeadlineFields(builder);
        applyCooldownField(builder);
        applyProgressFields(builder);
        applyGoalFields(builder);
        applyParentField(builder);
        applyPrefTimeField(builder);
        applyPredecessorFields(builder);
        builder.completeFirst(completeFirstEnabled);
        applyBudgetFields(builder);
        applyRepetitionFields(builder);

        trackedItem newItem = builder.build();
        persistItem(newItem);

        hideModal();
        refreshTree();
    }

    // ============================================================================
    // SAVEITEM HELPERS - Field application methods
    // ============================================================================

    private void applyDurationFields(trackedItem.Builder builder) {
        String minDurStr = minDurationField.getText().toString().trim();
        if (!minDurStr.isEmpty()) {
            try {
                int minVal = Integer.parseInt(minDurStr);
                builder.minDuration(minVal, selectedMinDurationUnit);
            } catch (NumberFormatException e) { /* ignorieren */ }
        }

        String durStr = durationField.getText().toString().trim();
        if (!durStr.isEmpty()) {
            try {
                int maxVal = Integer.parseInt(durStr);
                builder.maxDuration(maxVal, selectedMaxDurationUnit);
            } catch (NumberFormatException e) { /* ignorieren */ }
        }
    }

    private void applyDeadlineFields(trackedItem.Builder builder) {
        String dlStr = deadlineField.getText().toString().trim();
        if (!dlStr.isEmpty()) {
            try { builder.deadline(dlStr); }
            catch (Exception e) { /* ignorieren */ }
        }

        String fixedDateStr = fixedDateField.getText().toString().trim();
        String fixedTimeStr = fixedTimeField.getText().toString().trim();
        if (!fixedDateStr.isEmpty() && !fixedTimeStr.isEmpty()) {
            try { builder.fixedAppointment(fixedDateStr, fixedTimeStr); }
            catch (Exception e) { /* ignorieren */ }
        }
    }

    private void applyCooldownField(trackedItem.Builder builder) {
        String cdStr = cooldownField.getText().toString().trim();
        if (!cdStr.isEmpty()) {
            try { builder.cooldown(Integer.parseInt(cdStr)); }
            catch (NumberFormatException e) { /* ignorieren */ }
        }
    }

    private void applyProgressFields(trackedItem.Builder builder) {
        String ptStr = progressTargetField.getText().toString().trim();
        if (!ptStr.isEmpty()) {
            try { builder.progressTarget(Integer.parseInt(ptStr)); }
            catch (NumberFormatException e) { /* ignorieren */ }
        }

        String pcStr = progressCurrentField.getText().toString().trim();
        if (!pcStr.isEmpty()) {
            try { builder.progressCurrent(Integer.parseInt(pcStr)); }
            catch (NumberFormatException e) { /* ignorieren */ }
        }

        String puStr = progressUnitField.getText().toString().trim();
        if (!puStr.isEmpty()) builder.progressUnit(puStr);
        builder.progressPerRep(progressPerRepEnabled);
    }

    private void applyGoalFields(trackedItem.Builder builder) {
        String iconStr = goalIconField.getText().toString().trim();
        if (!iconStr.isEmpty()) builder.goalIcon(iconStr);
        if (selectedGoalColor != null) builder.goalColor(selectedGoalColor);
    }

    private void applyParentField(trackedItem.Builder builder) {
        int parentIdx = parentSpinner.getSelectedItemPosition();
        if (parentIdx > 0 && parentIdx <= availableParents.size()) {
            builder.parent(availableParents.get(parentIdx - 1).id);
        }
    }

    private void applyPrefTimeField(trackedItem.Builder builder) {
        String prefTimeStr = prefTimeField.getText().toString().trim();
        if (!prefTimeStr.isEmpty()) {
            try { builder.prefTime(prefTimeStr); }
            catch (Exception e) { /* ignorieren */ }
        }
    }

    private void applyPredecessorFields(trackedItem.Builder builder) {
        int predIdx = predecessorSpinner.getSelectedItemPosition();
        if (predIdx > 0 && predIdx <= availablePredecessors.size()) {
            Long predId = availablePredecessors.get(predIdx - 1).id;
            String delayStr = predecessorDelayField.getText().toString().trim();
            int delay = 0;
            if (!delayStr.isEmpty()) {
                try { delay = Integer.parseInt(delayStr); }
                catch (NumberFormatException e) { /* ignorieren */ }
            }
            if (delay > 0) {
                builder.delayAfter(predId, delay);
            } else {
                builder.chainAfter(predId);
            }
        }
    }

    private void applyBudgetFields(trackedItem.Builder builder) {
        String budgetStr = budgetAmountField.getText().toString().trim();
        if (!budgetStr.isEmpty()) {
            try {
                double euros = Double.parseDouble(budgetStr.replace(",", "."));
                builder.budgetRequirement((int)(euros * 100));
            } catch (NumberFormatException e) { /* ignorieren */ }
        }

        int accountIdx = budgetAccountSpinner.getSelectedItemPosition();
        if (accountIdx > 0 && accountIdx <= availableAccounts.size()) {
            builder.budgetAccount(availableAccounts.get(accountIdx - 1).id);
        }

        int catIdx = budgetCategorySpinner.getSelectedItemPosition();
        if (catIdx > 0 && catIdx <= expenseCategories.size()) {
            builder.budgetCategory(expenseCategories.get(catIdx - 1).id);
        }
    }

    private void applyRepetitionFields(trackedItem.Builder builder) {
        if (selectedType != ItemType.TASK) return;

        if (selectedRepType == RepetitionType.NONE) {
            builder.noRepetition();
        } else {
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
    }

    private void persistItem(trackedItem newItem) {
        if (editingItem != null) {
            // Edit-Modus: ID und persistente Felder übernehmen
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
            manager.updateItem(newItem);
        } else {
            manager.createItem(newItem);
        }
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
     * - Ancestor-Expand-State (gesamte Vorfahren-Kette wird geprüft)
     */
    private void applyFilter() {
        // Lookup-Map: itemId → TreeEntry für Ancestor-Traversal
        Map<Long, TreeEntry> entryById = new HashMap<>();
        for (int i = 0; i < treeContainer.getChildCount(); i++) {
            Object tag = treeContainer.getChildAt(i).getTag();
            if (tag instanceof TreeEntry te && te.item().id != null) {
                entryById.put(te.item().id, te);
            }
        }

        for (int i = 0; i < treeContainer.getChildCount(); i++) {
            View child = treeContainer.getChildAt(i);
            Object tag = child.getTag();
            if (!(tag instanceof TreeEntry entry)) continue;

            trackedItem item = entry.item();
            boolean matchesSearch = searchQuery.isEmpty()
                || (item.title != null && item.title.toLowerCase().contains(searchQuery));
            boolean matchesFilter = activeFilters.contains(item.type);

            // Gesamte Vorfahren-Kette prüfen
            boolean ancestorCollapsed = isAnyAncestorCollapsed(item, entryById);

            child.setVisibility(matchesSearch && matchesFilter && !ancestorCollapsed
                ? View.VISIBLE : View.GONE);
        }
    }

    /**
     * Prüft ob irgendein Vorfahre in der Hierarchie kollabiert ist.
     * Wandert von item.parent aufwärts bis zur Wurzel (parent == null).
     */
    private boolean isAnyAncestorCollapsed(trackedItem item, Map<Long, TreeEntry> entryById) {
        Long currentParentId = item.parent;
        while (currentParentId != null) {
            Boolean expanded = expandedState.getOrDefault(currentParentId, true);
            if (!expanded) return true;

            TreeEntry parentEntry = entryById.get(currentParentId);
            if (parentEntry == null) break;
            currentParentId = parentEntry.item().parent;
        }
        return false;
    }

    // ============================================================================
    // UPDATEFIELDVISIBILITY - Felder ein-/ausblenden nach gewähltem Typ
    // ============================================================================
    /**
     * Setzt visibility der typ-abhängigen Felder:
     * TASK: Dauer ✓, Parent ✓, Cooldown ✓, Wiederholung ✓
     * GOAL: Dauer ✓, Parent ✓
     * PROJECT: minIntervalDays ✓
     */
    private void updateFieldVisibility(ItemType type) {
        // Min/Max Dauer: TASK und GOAL
        minDurationRow.setVisibility(
            (type == ItemType.TASK || type == ItemType.GOAL) ? View.VISIBLE : View.GONE);
        durationRow.setVisibility(
            (type == ItemType.TASK || type == ItemType.GOAL) ? View.VISIBLE : View.GONE);
        parentRow.setVisibility(
            (type != ItemType.PROJECT) ? View.VISIBLE : View.GONE);
        predecessorRow.setVisibility(
            (type == ItemType.TASK) ? View.VISIBLE : View.GONE);
        // predecessorDelayRow wird von predecessorSpinner-Listener gesteuert
        predecessorDelayRow.setVisibility(View.GONE);
        // Bevorzugte Uhrzeit: TASK und GOAL
        prefTimeRow.setVisibility(
            (type == ItemType.TASK || type == ItemType.GOAL) ? View.VISIBLE : View.GONE);
        cooldownRow.setVisibility(
            (type == ItemType.TASK) ? View.VISIBLE : View.GONE);
        deadlineRow.setVisibility(
            (type == ItemType.TASK) ? View.VISIBLE : View.GONE);
        // Feste Termine initial sichtbar für TASK (wird von updateRepDetailsVisibility überschrieben)
        fixedDateRow.setVisibility(
            (type == ItemType.TASK) ? View.VISIBLE : View.GONE);
        fixedTimeRow.setVisibility(
            (type == ItemType.TASK) ? View.VISIBLE : View.GONE);
        progressRow.setVisibility(
            (type == ItemType.TASK || type == ItemType.GOAL) ? View.VISIBLE : View.GONE);
        progressPerRepRow.setVisibility(
            (type == ItemType.TASK) ? View.VISIBLE : View.GONE);
        goalIconRow.setVisibility(
            (type == ItemType.GOAL) ? View.VISIBLE : View.GONE);
        goalColorRow.setVisibility(
            (type == ItemType.GOAL) ? View.VISIBLE : View.GONE);
        repetitionSection.setVisibility(
            (type == ItemType.TASK) ? View.VISIBLE : View.GONE);
        // Erst erledigen: nur TASK
        completeFirstRow.setVisibility(
            (type == ItemType.TASK) ? View.VISIBLE : View.GONE);
        // Budget-Felder nur bei TASK
        budgetRow.setVisibility(
            (type == ItemType.TASK) ? View.VISIBLE : View.GONE);
        budgetAccountRow.setVisibility(
            (type == ItemType.TASK) ? View.VISIBLE : View.GONE);
        budgetCategoryRow.setVisibility(
            (type == ItemType.TASK) ? View.VISIBLE : View.GONE);
    }

    // ============================================================================
    // BUILDCOLORGRID - Erstellt Farb-Buttons programmatisch
    // ============================================================================
    private void buildColorGrid() {
        int size = dp(context, 32);
        int margin = dp(context, 4);
        for (String hex : GOAL_COLORS) {
            View swatch = new View(context);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            lp.setMargins(margin, margin, margin, margin);
            swatch.setLayoutParams(lp);
            try {
                swatch.setBackground(roundedBg(context, Color.parseColor(hex), 4));
            } catch (IllegalArgumentException e) {
                continue;
            }
            swatch.setOnClickListener(v -> {
                selectedGoalColor = hex;
                highlightSelectedColor();
            });
            swatch.setTag(hex);
            colorGrid.addView(swatch);
        }
    }

    private void highlightSelectedColor() {
        for (int i = 0; i < colorGrid.getChildCount(); i++) {
            View child = colorGrid.getChildAt(i);
            String hex = (String) child.getTag();
            float scale = hex.equals(selectedGoalColor) ? 1.3f : 1.0f;
            child.setScaleX(scale);
            child.setScaleY(scale);
        }
    }

}

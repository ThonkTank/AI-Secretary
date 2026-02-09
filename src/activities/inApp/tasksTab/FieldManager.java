package activities.inApp.tasksTab;

import android.content.Context;
import android.graphics.Color;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import static activities.generic.ViewHelper.*;

import androidx.core.content.ContextCompat;

import com.autosecretary.R;

import android.app.DatePickerDialog;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import controller.EditorManager;
import controller.EditorManager.TreeEntry;
import entities.Account;
import entities.Category;
import entities.TrackedItem;
import entities.TrackedItem.DurationUnit;
import entities.TrackedItem.ItemType;
import entities.TrackedItem.Priority;
import entities.TrackedItem.RepetitionType;
import entities.TrackedItem.RepUnits;

/**
 * Verwaltet alle Formular-Felder des Create/Edit-Modals.
 * Bindet Felder aus dem XML-Layout, befuellt sie (populate),
 * liest Werte aus (apply) und steuert Sichtbarkeit.
 */
class FieldManager {

    private final Context context;
    private final EditorManager manager;

    // Basis-Felder
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
    TextView errorText;

    // Container fuer Visibility-Toggling
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

    // Button-Gruppen
    Button[] typeButtons = new Button[3];
    private Button[] priorityButtons = new Button[4];
    private Button[] repTypeButtons = new Button[4];
    private Button[] repUnitButtons = new Button[3];

    // Parent/Predecessor-Daten
    List<TrackedItem> availableParents = new ArrayList<>();
    private View predecessorRow;
    Spinner predecessorSpinner;
    List<TrackedItem> availablePredecessors = new ArrayList<>();

    // Budget-Felder
    private View budgetRow;
    private EditText budgetAmountField;
    private View budgetAccountRow;
    private Spinner budgetAccountSpinner;
    private List<Account> availableAccounts = new ArrayList<>();
    private View budgetCategoryRow;
    private Spinner budgetCategorySpinner;
    private List<Category> expenseCategories = new ArrayList<>();

    // Min/Max Dauer mit Unit
    private View minDurationRow;
    private EditText minDurationField;
    private Button[] minDurationUnitButtons = new Button[2];
    private DurationUnit selectedMinDurationUnit = DurationUnit.MINUTES;
    private Button[] maxDurationUnitButtons = new Button[2];
    private DurationUnit selectedMaxDurationUnit = DurationUnit.MINUTES;

    // Bevorzugte Zeiten (delegiert an PrefScheduleEditor)
    private PrefScheduleEditor prefScheduleEditor;

    // Vorgaenger-Wartezeit
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
    ItemType selectedType = ItemType.TASK;
    Priority selectedPriority = Priority.MODERATE;
    private RepetitionType selectedRepType = RepetitionType.NONE;
    private RepUnits selectedRepUnit = RepUnits.DAY;
    String selectedGoalColor = null;

    // ========================================================================
    // KONSTRUKTOR
    // ========================================================================

    FieldManager(Context context, EditorManager manager, View modalRoot) {
        this.context = context;
        this.manager = manager;
        bindAll(modalRoot);
    }

    // ========================================================================
    // BINDALL - Orchestriert alle Binding-Aufrufe
    // ========================================================================

    private void bindAll(View root) {
        bindBasicFields(root);
        bindDeadlineFields(root);
        bindFixedAppointmentFields(root);
        bindGoalCustomizationFields(root);
        bindProgressFields(root);
        bindTypeButtons(root);
        bindPriorityButtons(root);
        bindRepetitionButtons(root);
        bindBudgetFields(root);
        bindDurationFields(root);
        prefScheduleEditor = new PrefScheduleEditor(context, root,
            new PrefScheduleEditor.RepetitionStateProvider() {
                @Override public RepetitionType getRepType() { return selectedRepType; }
                @Override public RepUnits getRepUnit() { return selectedRepUnit; }
                @Override public int getRepValue() { return parseInt(repValueField, 0); }
            });
        bindPredecessorDelayFields(root);
        bindCompleteFirstFields(root);
    }

    // ========================================================================
    // FIELD BINDING HELPERS
    // ========================================================================

    private void bindBasicFields(View root) {
        titleField = root.findViewById(R.id.field_title);
        descriptionField = root.findViewById(R.id.field_description);
        durationField = root.findViewById(R.id.field_duration);
        cooldownField = root.findViewById(R.id.field_cooldown);
        repValueField = root.findViewById(R.id.field_rep_value);
        repValueField.addTextChangedListener(afterTextChanged(
            () -> { if (prefScheduleEditor != null) prefScheduleEditor.updateSlotCount(); }
        ));
        parentSpinner = root.findViewById(R.id.spinner_parent);
        predecessorSpinner = root.findViewById(R.id.spinner_predecessor);
        weekdaySpinner = root.findViewById(R.id.spinner_weekday);
        errorText = root.findViewById(R.id.text_error);

        durationRow = root.findViewById(R.id.row_duration);
        parentRow = root.findViewById(R.id.row_parent);
        predecessorRow = root.findViewById(R.id.row_predecessor);
        cooldownRow = root.findViewById(R.id.row_cooldown);
        repetitionSection = root.findViewById(R.id.section_repetition);
        weekdayRow = root.findViewById(R.id.row_weekday);

        String[] days = {"Montag", "Dienstag", "Mittwoch", "Donnerstag", "Freitag", "Samstag", "Sonntag"};
        weekdaySpinner.setAdapter(spinnerAdapter(context, days));
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
            }, 12, 0, true).show();
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
                onRepetitionChanged();
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
                onRepetitionChanged();
            });
        }
    }

    private void onRepetitionChanged() {
        boolean wasMonthly = prefScheduleEditor.isMonthlyMode();
        prefScheduleEditor.updateSlotCount();
        if (prefScheduleEditor.isMonthlyMode() != wasMonthly) prefScheduleEditor.rebuildRows();
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

    // ========================================================================
    // UI-STATE METHODEN
    // ========================================================================

    void updateButtonGroup(Button[] buttons, int selectedIdx) {
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

    void updateProgressPerRepButton() {
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

    void updateCompleteFirstButton() {
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

    void updateFieldVisibility(ItemType type) {
        minDurationRow.setVisibility(
            (type == ItemType.TASK || type == ItemType.GOAL) ? View.VISIBLE : View.GONE);
        durationRow.setVisibility(
            (type == ItemType.TASK || type == ItemType.GOAL) ? View.VISIBLE : View.GONE);
        parentRow.setVisibility(
            (type != ItemType.PROJECT) ? View.VISIBLE : View.GONE);
        predecessorRow.setVisibility(
            (type == ItemType.TASK) ? View.VISIBLE : View.GONE);
        predecessorDelayRow.setVisibility(View.GONE);
        prefScheduleEditor.setVisible(type == ItemType.TASK || type == ItemType.GOAL);
        cooldownRow.setVisibility(
            (type == ItemType.TASK) ? View.VISIBLE : View.GONE);
        deadlineRow.setVisibility(
            (type == ItemType.TASK) ? View.VISIBLE : View.GONE);
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
        completeFirstRow.setVisibility(
            (type == ItemType.TASK) ? View.VISIBLE : View.GONE);
        budgetRow.setVisibility(
            (type == ItemType.TASK) ? View.VISIBLE : View.GONE);
        budgetAccountRow.setVisibility(
            (type == ItemType.TASK) ? View.VISIBLE : View.GONE);
        budgetCategoryRow.setVisibility(
            (type == ItemType.TASK) ? View.VISIBLE : View.GONE);
    }

    void updateRepDetailsVisibility() {
        boolean show = selectedRepType != RepetitionType.NONE;
        repDetailsSection.setVisibility(show ? View.VISIBLE : View.GONE);
        if (selectedType == ItemType.TASK) {
            boolean showNoneFields = !show;
            deadlineRow.setVisibility(showNoneFields ? View.VISIBLE : View.GONE);
            fixedDateRow.setVisibility(showNoneFields ? View.VISIBLE : View.GONE);
            fixedTimeRow.setVisibility(showNoneFields ? View.VISIBLE : View.GONE);
        }
    }

    void updateWeekdayVisibility() {
        boolean showWeekday = selectedRepType == RepetitionType.DAY_OF_TIME
            && selectedRepUnit == RepUnits.WEEK;
        weekdayRow.setVisibility(showWeekday ? View.VISIBLE : View.GONE);
    }

    // ========================================================================
    // SPINNER-REFRESH METHODEN
    // ========================================================================

    void refreshParentSpinner() {
        availableParents = manager.getAvailableParents(selectedType);
        List<String> parentNames = new ArrayList<>();
        parentNames.add("(kein Parent)");
        for (TrackedItem p : availableParents) {
            parentNames.add(p.title);
        }
        parentSpinner.setAdapter(spinnerAdapter(context, parentNames));
        parentSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                refreshPredecessorSpinner();
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    void refreshPredecessorSpinner() {
        refreshPredecessorSpinner(null);
    }

    void refreshPredecessorSpinner(TrackedItem editingItem) {
        availablePredecessors.clear();
        List<String> names = new ArrayList<>();
        names.add("(kein Vorg\u00e4nger)");

        if (selectedType == ItemType.TASK) {
            int parentIdx = parentSpinner.getSelectedItemPosition();
            if (parentIdx > 0 && parentIdx <= availableParents.size()) {
                Long goalId = availableParents.get(parentIdx - 1).id;
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
        predecessorSpinner.setAdapter(spinnerAdapter(context, names));

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

    void refreshBudgetAccountSpinner() {
        availableAccounts.clear();
        List<String> accountNames = new ArrayList<>();
        accountNames.add("(beliebig)");

        for (Account acc : manager.getActiveAccounts()) {
            availableAccounts.add(acc);
            accountNames.add(acc.name);
        }
        budgetAccountSpinner.setAdapter(spinnerAdapter(context, accountNames));
    }

    private void refreshBudgetCategorySpinner() {
        expenseCategories.clear();
        List<String> categoryNames = new ArrayList<>();
        categoryNames.add("(keine)");

        for (Category cat : manager.getExpenseCategories()) {
            expenseCategories.add(cat);
            String displayName = (cat.icon != null ? cat.icon + " " : "") + cat.name;
            categoryNames.add(displayName);
        }
        budgetCategorySpinner.setAdapter(spinnerAdapter(context, categoryNames));
    }

    // ========================================================================
    // FARB-GRID
    // ========================================================================

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

    void highlightSelectedColor() {
        for (int i = 0; i < colorGrid.getChildCount(); i++) {
            View child = colorGrid.getChildAt(i);
            String hex = (String) child.getTag();
            float scale = hex.equals(selectedGoalColor) ? 1.3f : 1.0f;
            child.setScaleX(scale);
            child.setScaleY(scale);
        }
    }

    // ========================================================================
    // POPULATE - Felder fuer Create/Edit befuellen
    // ========================================================================

    void populateForCreate() {
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

        budgetAmountField.setText("");
        budgetAccountSpinner.setSelection(0);
        budgetCategorySpinner.setSelection(0);

        minDurationField.setText("");
        selectedMinDurationUnit = DurationUnit.MINUTES;
        updateButtonGroup(minDurationUnitButtons, 0);
        selectedMaxDurationUnit = DurationUnit.MINUTES;
        updateButtonGroup(maxDurationUnitButtons, 0);
        prefScheduleEditor.populateDefault();
        predecessorDelayField.setText("");
        completeFirstEnabled = false;

        for (Button btn : typeButtons) btn.setEnabled(true);
        updateButtonGroup(typeButtons, 0);
        updateButtonGroup(priorityButtons, 1);
        updateButtonGroup(repTypeButtons, 0);
        updateButtonGroup(repUnitButtons, 0);
    }

    void populateForEdit(TrackedItem item) {
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

        if (item.budgetRequirementCents > 0) {
            budgetAmountField.setText(String.format("%.2f", item.budgetRequirementCents / 100.0));
        } else {
            budgetAmountField.setText("");
        }

        minDurationField.setText(item.minDurationValue > 0 ? String.valueOf(item.minDurationValue) : "");
        selectedMinDurationUnit = item.minDurationUnit != null ? item.minDurationUnit : DurationUnit.MINUTES;
        updateButtonGroup(minDurationUnitButtons, selectedMinDurationUnit == DurationUnit.MINUTES ? 0 : 1);

        selectedMaxDurationUnit = item.maxDurationUnit != null ? item.maxDurationUnit : DurationUnit.MINUTES;
        updateButtonGroup(maxDurationUnitButtons, selectedMaxDurationUnit == DurationUnit.MINUTES ? 0 : 1);

        prefScheduleEditor.populateSlots(item.prefSlots);
        predecessorDelayField.setText(item.predecessorDelay > 0 ? String.valueOf(item.predecessorDelay) : "");
        completeFirstEnabled = item.completeFirst != null && item.completeFirst;

        int typeIdx = switch (item.type) {
            case TASK -> 0;
            case GOAL -> 1;
            case PROJECT -> 2;
        };
        for (Button btn : typeButtons) btn.setEnabled(false);
        updateButtonGroup(typeButtons, typeIdx);

        int prioIdx = switch (selectedPriority) {
            case LOW -> 0;
            case MODERATE -> 1;
            case HIGH -> 2;
            case CRITICAL -> 3;
        };
        updateButtonGroup(priorityButtons, prioIdx);

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

    // ========================================================================
    // SPINNER-SELEKTION - Spinner auf aktuellen Wert setzen
    // ========================================================================

    void selectParent(TrackedItem item) {
        if (item != null && item.parent != null) {
            for (int i = 0; i < availableParents.size(); i++) {
                if (availableParents.get(i).id.equals(item.parent)) {
                    parentSpinner.setSelection(i + 1);
                    break;
                }
            }
        }
    }

    void selectPredecessor(TrackedItem item) {
        if (item != null && item.predecessor != null) {
            for (int i = 0; i < availablePredecessors.size(); i++) {
                if (availablePredecessors.get(i).id.equals(item.predecessor)) {
                    predecessorSpinner.setSelection(i + 1);
                    break;
                }
            }
        }
    }

    void selectBudgetAccount(TrackedItem item) {
        if (item != null && item.budgetAccountId != null) {
            for (int i = 0; i < availableAccounts.size(); i++) {
                if (availableAccounts.get(i).id.equals(item.budgetAccountId)) {
                    budgetAccountSpinner.setSelection(i + 1);
                    break;
                }
            }
        }
    }

    void selectBudgetCategory(TrackedItem item) {
        if (item != null && item.budgetCategoryId != null) {
            for (int i = 0; i < expenseCategories.size(); i++) {
                if (expenseCategories.get(i).id.equals(item.budgetCategoryId)) {
                    budgetCategorySpinner.setSelection(i + 1);
                    break;
                }
            }
        }
    }

    // ========================================================================
    // APPLY - Formularwerte in Builder uebernehmen
    // ========================================================================

    void applyDurationFields(TrackedItem.Builder builder) {
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

    void applyDeadlineFields(TrackedItem.Builder builder) {
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

    void applyCooldownField(TrackedItem.Builder builder) {
        int cd = parseInt(cooldownField, 0);
        if (cd > 0) builder.cooldown(cd);
    }

    void applyProgressFields(TrackedItem.Builder builder) {
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

    void applyGoalFields(TrackedItem.Builder builder) {
        String iconStr = goalIconField.getText().toString().trim();
        if (!iconStr.isEmpty()) builder.goalIcon(iconStr);
        if (selectedGoalColor != null) builder.goalColor(selectedGoalColor);
    }

    void applyParentField(TrackedItem.Builder builder) {
        int parentIdx = parentSpinner.getSelectedItemPosition();
        if (parentIdx > 0 && parentIdx <= availableParents.size()) {
            builder.parent(availableParents.get(parentIdx - 1).id);
        }
    }

    void applyPrefScheduleFields(TrackedItem.Builder builder) {
        List<TrackedItem.PrefSlot> slots = prefScheduleEditor.collectSlots();
        if (!slots.isEmpty()) builder.prefSlots(slots);
    }

    void applyPredecessorFields(TrackedItem.Builder builder) {
        int predIdx = predecessorSpinner.getSelectedItemPosition();
        if (predIdx > 0 && predIdx <= availablePredecessors.size()) {
            Long predId = availablePredecessors.get(predIdx - 1).id;
            int delay = parseInt(predecessorDelayField, 0);
            if (delay > 0) {
                builder.delayAfter(predId, delay);
            } else {
                builder.chainAfter(predId);
            }
        }
    }

    void applyBudgetFields(TrackedItem.Builder builder) {
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

    void applyRepetitionFields(TrackedItem.Builder builder) {
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

    // ========================================================================
    // ACCESSORS fuer ItemEditorModal
    // ========================================================================

    String getTitleText() {
        return titleField.getText().toString().trim();
    }

    String getDescriptionText() {
        return descriptionField.getText().toString().trim();
    }

    ItemType getSelectedType() {
        return selectedType;
    }

    Priority getSelectedPriority() {
        return selectedPriority;
    }

    boolean isCompleteFirstEnabled() {
        return completeFirstEnabled;
    }

    void showError(String message) {
        errorText.setText(message);
        errorText.setVisibility(View.VISIBLE);
    }

    void hideError() {
        errorText.setVisibility(View.GONE);
    }
}

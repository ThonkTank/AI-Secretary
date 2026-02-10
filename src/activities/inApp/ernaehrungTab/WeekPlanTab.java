package activities.inApp.ernaehrungTab;

import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import static activities.generic.ViewHelper.buildWeekHeader;
import static activities.generic.ViewHelper.dp;
import static activities.generic.ViewHelper.parseInt;
import static activities.generic.DateTimeHelper.formatTime;
import static activities.generic.ViewHelper.roundedBg;
import static activities.generic.ViewHelper.setupModalOverlay;
import static activities.generic.ViewHelper.spinnerAdapter;

import androidx.core.content.ContextCompat;

import com.autosecretary.R;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import controller.MealManager;
import controller.MealManager.*;
import entities.MealPlan;
import entities.TrackedItem;
import entities.MealType;
import scheduling.CalendarReader;
import scheduling.GenerateMealPlan;

/**
 * Unified "Woche"-Tab: Wochenplan (Listenformat) + Slot-Modal + Haushalt.
 *
 * Ersetzt den alten "Woche"-Tab (4-Karten-Grid) und den "Planung"-Tab.
 * Ein Modal fuer alles: Schedule erstellen/bearbeiten + Rezept zuweisen.
 */
public class WeekPlanTab {

    private final Context context;
    private final MealManager manager;

    // State
    private LocalDate currentWeekStart;
    private List<ScheduleEntry> scheduleEntries = new ArrayList<>();
    private List<MealPlanEntry> weekPlanEntries = new ArrayList<>();
    private List<RecipeEntry> recipesList = new ArrayList<>();

    // Sub-Komponente: Haushalt-Verwaltung
    private MemberTab memberTabInstance;

    // Unified Slot Modal
    private FrameLayout modalMealSlotOverlay;
    private TextView modalMealSlotTitle;
    private Spinner spinnerSlotDay;
    private Spinner spinnerSlotMealType;
    private TextView slotStartTime;
    private TextView slotEndTime;
    private LinearLayout slotRecipeSection;
    private Spinner spinnerSlotRecipe;
    private LinearLayout slotRecipeInfo;
    private TextView slotRecipeTime;
    private TextView slotRecipeCalories;
    private EditText inputSlotServings;
    private TextView btnSlotDelete;
    private TextView btnSlotCancel;
    private TextView btnSlotSave;

    private ScheduleEntry editingSlot;
    private LocalDate editingSlotDate;
    private LocalTime selectedStartTime;
    private LocalTime selectedEndTime;

    // Callback um MealPlanView ueber Aenderungen zu informieren
    private MealTabListener listener;

    // ============================================================================
    // CONSTRUCTOR + INIT
    // ============================================================================

    public WeekPlanTab(Context context, MealManager manager) {
        this.context = context;
        this.manager = manager;
        this.memberTabInstance = new MemberTab(context, manager);
    }

    public void setListener(MealTabListener listener) {
        this.listener = listener;
        this.memberTabInstance.setListener(listener);
    }

    /**
     * Inflated und bindet die Modal-Overlays.
     * Jeder Sub-Tab besitzt sein eigenes Modal (kein Zugriff auf Parent-Layout).
     */
    public void initModals(FrameLayout rootContainer) {
        // === Unified Slot Modal ===
        modalMealSlotOverlay = (FrameLayout) LayoutInflater.from(context)
            .inflate(R.layout.modal_meal_slot, rootContainer, false);
        modalMealSlotOverlay.setVisibility(View.GONE);
        rootContainer.addView(modalMealSlotOverlay);

        modalMealSlotTitle = modalMealSlotOverlay.findViewById(R.id.modal_meal_slot_title);
        spinnerSlotDay = modalMealSlotOverlay.findViewById(R.id.spinner_slot_day);
        spinnerSlotMealType = modalMealSlotOverlay.findViewById(R.id.spinner_slot_meal_type);
        slotStartTime = modalMealSlotOverlay.findViewById(R.id.slot_start_time);
        slotEndTime = modalMealSlotOverlay.findViewById(R.id.slot_end_time);
        slotRecipeSection = modalMealSlotOverlay.findViewById(R.id.slot_recipe_section);
        spinnerSlotRecipe = modalMealSlotOverlay.findViewById(R.id.spinner_slot_recipe);
        slotRecipeInfo = modalMealSlotOverlay.findViewById(R.id.slot_recipe_info);
        slotRecipeTime = modalMealSlotOverlay.findViewById(R.id.slot_recipe_time);
        slotRecipeCalories = modalMealSlotOverlay.findViewById(R.id.slot_recipe_calories);
        inputSlotServings = modalMealSlotOverlay.findViewById(R.id.input_slot_servings);
        btnSlotDelete = modalMealSlotOverlay.findViewById(R.id.btn_slot_delete);
        btnSlotCancel = modalMealSlotOverlay.findViewById(R.id.btn_slot_cancel);
        btnSlotSave = modalMealSlotOverlay.findViewById(R.id.btn_slot_save);

        setupMealSlotModal();

        // === Member Modal (delegiert an MemberTab) ===
        memberTabInstance.initModals(rootContainer);
    }

    // ============================================================================
    // RENDER - Wochenplan (Listenformat)
    // ============================================================================

    /**
     * Rendert den Wochenplan ins gegebene Container-Layout.
     */
    public void render(FrameLayout container, LocalDate weekStart,
                       Runnable onPrevWeek, Runnable onNextWeek) {
        this.currentWeekStart = weekStart;
        container.removeAllViews();

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);

        // === 1. Haushalt (klappbar, delegiert an MemberTab) ===
        memberTabInstance.render(content);

        // === 2. Wochen-Navigation + "+" Button ===
        LinearLayout header = buildWeekHeader(context, weekStart, onPrevWeek, onNextWeek);
        TextView btnAdd = new TextView(context);
        btnAdd.setText("+");
        btnAdd.setTextSize(22);
        btnAdd.setTextColor(ContextCompat.getColor(context, R.color.accent));
        btnAdd.setPadding(dp(context, 12), dp(context, 4), dp(context, 4), dp(context, 4));
        btnAdd.setOnClickListener(v -> showMealSlotModal(null, null));
        header.addView(btnAdd);
        content.addView(header);

        // === 3. Daten laden ===
        scheduleEntries = manager.provideSchedule();
        weekPlanEntries = manager.provideMealPlan(currentWeekStart);

        DateTimeFormatter dateFmt = DateTimeFormatter.ofPattern("EEEE, dd.MM.", Locale.GERMAN);
        DayOfWeek[] days = DayOfWeek.values();

        // === 4. 7 Tage ===
        for (int dayOffset = 0; dayOffset < 7; dayOffset++) {
            LocalDate date = currentWeekStart.plusDays(dayOffset);
            DayOfWeek dayOfWeek = date.getDayOfWeek();

            // Day Header
            TextView dayHeader = new TextView(context);
            dayHeader.setText(dateFmt.format(date).toUpperCase());
            dayHeader.setTextSize(12);
            dayHeader.setTextColor(ContextCompat.getColor(context, R.color.text_secondary));
            dayHeader.setTypeface(null, android.graphics.Typeface.BOLD);
            dayHeader.setPadding(0, dp(context, 12), 0, dp(context, 4));
            content.addView(dayHeader);

            // Slots fuer diesen Tag
            List<ScheduleEntry> daySlots = new ArrayList<>();
            for (ScheduleEntry entry : scheduleEntries) {
                if (entry.day() == dayOfWeek) daySlots.add(entry);
            }

            if (daySlots.isEmpty()) {
                TextView empty = new TextView(context);
                empty.setText("Keine Mahlzeiten geplant");
                empty.setTextSize(12);
                empty.setTextColor(ContextCompat.getColor(context, R.color.text_tertiary));
                empty.setPadding(dp(context, 16), dp(context, 4), 0, dp(context, 4));
                content.addView(empty);
            } else {
                for (ScheduleEntry slot : daySlots) {
                    content.addView(buildSlotRow(slot, date));
                }
            }
        }

        container.addView(content);
    }

    // ============================================================================
    // SLOT ROW
    // ============================================================================

    private View buildSlotRow(ScheduleEntry slot, LocalDate date) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding(dp(context, 12), dp(context, 8), dp(context, 12), dp(context, 8));

        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowParams.setMargins(0, dp(context, 2), 0, dp(context, 2));
        row.setLayoutParams(rowParams);

        // MealPlan fuer diesen Slot finden
        MealPlanEntry planEntry = findMealPlan(date, slot.mealType());
        boolean isCompleted = planEntry != null && planEntry.isCompleted();

        // Hintergrund
        int bgColor = isCompleted
            ? ContextCompat.getColor(context, R.color.surface_complete)
            : ContextCompat.getColor(context, R.color.surface_card);
        row.setBackground(roundedBg(context, bgColor, 6));

        // Icon + Typ
        TextView mealLabel = new TextView(context);
        mealLabel.setText(slot.mealIcon() + " " + slot.mealLabel());
        mealLabel.setTextSize(13);
        mealLabel.setTextColor(ContextCompat.getColor(context, R.color.text_primary));
        mealLabel.setMinWidth(dp(context, 110));
        row.addView(mealLabel);

        // Zeitraum
        TextView timeRange = new TextView(context);
        timeRange.setText(slot.formattedTimeRange());
        timeRange.setTextSize(12);
        timeRange.setTextColor(ContextCompat.getColor(context, R.color.text_secondary));
        timeRange.setPadding(0, 0, dp(context, 8), 0);
        row.addView(timeRange);

        // Rezept oder "+ Hinzufuegen"
        TextView recipeLabel = new TextView(context);
        LinearLayout.LayoutParams recipeLp = new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        recipeLabel.setLayoutParams(recipeLp);
        recipeLabel.setTextSize(12);
        recipeLabel.setGravity(android.view.Gravity.END);

        if (planEntry != null) {
            String text = planEntry.recipeTitle();
            if (planEntry.calories() > 0) {
                text += " (" + planEntry.calories() + " kcal)";
            }
            if (isCompleted) text += " ✓";
            recipeLabel.setText(text);
            recipeLabel.setTextColor(ContextCompat.getColor(context, R.color.text_primary));
        } else {
            recipeLabel.setText("+ Hinzufügen");
            recipeLabel.setTextColor(ContextCompat.getColor(context, R.color.accent));
        }
        row.addView(recipeLabel);

        // Tap = Modal oeffnen (Edit-Modus mit Rezept)
        row.setOnClickListener(v -> showMealSlotModal(slot, date));

        // Long-Press = Loeschen
        row.setOnLongClickListener(v -> {
            String msg = slot.mealLabel() + " " + slot.formattedTimeRange();
            if (planEntry != null) {
                msg += "\n\nDas zugewiesene Rezept wird ebenfalls entfernt.";
            }
            new AlertDialog.Builder(context)
                .setTitle("Mahlzeit löschen?")
                .setMessage(msg)
                .setPositiveButton("Löschen", (d, w) -> {
                    // MealPlan auch loeschen falls vorhanden
                    if (planEntry != null) {
                        MealPlan mp = manager.findMealPlan(date, slot.mealType());
                        if (mp != null && mp.id != null) manager.deleteMealPlan(mp.id);
                    }
                    manager.deleteSchedule(slot.id(), slot.day());
                    notifyChanged();
                })
                .setNegativeButton("Abbrechen", null)
                .show();
            return true;
        });

        return row;
    }

    // ============================================================================
    // UNIFIED SLOT MODAL
    // ============================================================================

    private static final String[] DAY_NAMES = {
        "Montag", "Dienstag", "Mittwoch", "Donnerstag", "Freitag", "Samstag", "Sonntag"
    };
    private static final String[] MEAL_TYPE_NAMES = {
        "Frühstück", "Mittagessen", "Abendessen", "Snack"
    };

    private void setupMealSlotModal() {
        // Day-Spinner
        spinnerSlotDay.setAdapter(spinnerAdapter(context, DAY_NAMES));

        // MealType-Spinner
        spinnerSlotMealType.setAdapter(spinnerAdapter(context, MEAL_TYPE_NAMES));

        // TimePicker Start
        slotStartTime.setOnClickListener(v -> {
            new TimePickerDialog(context, (view, h, m) -> {
                selectedStartTime = LocalTime.of(h, m);
                slotStartTime.setText(String.format("%02d:%02d", h, m));
            }, selectedStartTime != null ? selectedStartTime.getHour() : 12,
               selectedStartTime != null ? selectedStartTime.getMinute() : 0, true).show();
        });

        // TimePicker Ende
        slotEndTime.setOnClickListener(v -> {
            new TimePickerDialog(context, (view, h, m) -> {
                selectedEndTime = LocalTime.of(h, m);
                slotEndTime.setText(String.format("%02d:%02d", h, m));
            }, selectedEndTime != null ? selectedEndTime.getHour() : 12,
               selectedEndTime != null ? selectedEndTime.getMinute() : 30, true).show();
        });

        // Rezept-Spinner Listener
        spinnerSlotRecipe.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int pos, long id) {
                if (pos > 0 && pos <= recipesList.size()) {
                    RecipeEntry recipe = recipesList.get(pos - 1);
                    slotRecipeTime.setText(recipe.totalTime() + " min");
                    slotRecipeCalories.setText(recipe.calories() + " kcal");
                    slotRecipeInfo.setVisibility(View.VISIBLE);
                } else {
                    slotRecipeInfo.setVisibility(View.GONE);
                }
            }
            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });

        // Buttons
        btnSlotCancel.setOnClickListener(v -> hideMealSlotModal());
        btnSlotSave.setOnClickListener(v -> saveMealSlot());
        btnSlotDelete.setOnClickListener(v -> deleteMealSlot());

        setupModalOverlay(modalMealSlotOverlay, this::hideMealSlotModal);
    }

    private void showMealSlotModal(ScheduleEntry existing, LocalDate date) {
        editingSlot = existing;
        editingSlotDate = date;
        boolean isEdit = existing != null;

        modalMealSlotTitle.setText(isEdit ? "Mahlzeit bearbeiten" : "Mahlzeit hinzufügen");

        if (isEdit) {
            // Vorausfuellen aus bestehendem Slot
            spinnerSlotDay.setSelection(existing.day().ordinal());
            spinnerSlotMealType.setSelection(existing.mealType().ordinal());

            selectedStartTime = existing.time() != null ? existing.time() : LocalTime.of(12, 0);
            LocalTime endTime = existing.time() != null
                ? existing.time().plusMinutes(existing.durationMinutes())
                : LocalTime.of(12, 30);
            selectedEndTime = endTime;

            slotStartTime.setText(formatTime(selectedStartTime));
            slotEndTime.setText(formatTime(selectedEndTime));

            // Rezept-Sektion anzeigen
            slotRecipeSection.setVisibility(View.VISIBLE);
            btnSlotDelete.setVisibility(View.VISIBLE);

            // Rezepte fuer diesen MealType laden
            recipesList = manager.provideRecipes(existing.mealType());
            loadRecipeSpinner();

            // Bestehendes MealPlan finden und vorausfuellen
            if (date != null) {
                MealPlan existingPlan = manager.findMealPlan(date, existing.mealType());
                if (existingPlan != null) {
                    // Rezept im Spinner auswaehlen
                    for (int i = 0; i < recipesList.size(); i++) {
                        if (recipesList.get(i).id().equals(existingPlan.recipeId)) {
                            spinnerSlotRecipe.setSelection(i + 1);
                            break;
                        }
                    }
                    inputSlotServings.setText(String.valueOf(existingPlan.plannedServings));
                } else {
                    spinnerSlotRecipe.setSelection(0);
                    inputSlotServings.setText("2");
                    slotRecipeInfo.setVisibility(View.GONE);
                }
            }

            // MealType-Spinner-Aenderung aktualisiert Rezeptliste
            spinnerSlotMealType.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(android.widget.AdapterView<?> parent, View view, int pos, long id) {
                    MealType newType = MealType.values()[pos];
                    recipesList = manager.provideRecipes(newType);
                    loadRecipeSpinner();
                }
                @Override
                public void onNothingSelected(android.widget.AdapterView<?> parent) {}
            });

        } else {
            // Create-Modus: Defaults
            DayOfWeek today = LocalDate.now().getDayOfWeek();
            spinnerSlotDay.setSelection(today.ordinal());
            spinnerSlotMealType.setSelection(1); // Mittagessen

            selectedStartTime = LocalTime.of(12, 0);
            selectedEndTime = LocalTime.of(12, 30);
            slotStartTime.setText(formatTime(selectedStartTime));
            slotEndTime.setText(formatTime(selectedEndTime));

            // Rezept-Sektion verstecken (erst bei bestehendem Slot sichtbar)
            slotRecipeSection.setVisibility(View.GONE);
            btnSlotDelete.setVisibility(View.GONE);

            // MealType-Spinner ohne Rezept-Listener
            spinnerSlotMealType.setOnItemSelectedListener(null);
        }

        modalMealSlotOverlay.setVisibility(View.VISIBLE);
    }

    private void loadRecipeSpinner() {
        List<String> names = new ArrayList<>();
        names.add("— Kein Rezept —");
        for (RecipeEntry r : recipesList) {
            names.add(r.title());
        }
        spinnerSlotRecipe.setAdapter(spinnerAdapter(context, names));
        slotRecipeInfo.setVisibility(View.GONE);
    }

    private void saveMealSlot() {
        // Schedule-Daten
        DayOfWeek day = DayOfWeek.values()[spinnerSlotDay.getSelectedItemPosition()];
        MealType type = MealType.values()[spinnerSlotMealType.getSelectedItemPosition()];

        int duration = (int) ChronoUnit.MINUTES.between(selectedStartTime, selectedEndTime);
        if (duration <= 0) duration = 30; // Fallback

        if (editingSlot != null) {
            manager.updateSchedule(editingSlot.id(), editingSlot.day(), type, selectedStartTime, duration);
        } else {
            manager.createSchedule(day, type, selectedStartTime, duration);
        }

        // Rezept (nur bei Edit-Modus)
        if (editingSlot != null && editingSlotDate != null
                && slotRecipeSection.getVisibility() == View.VISIBLE) {
            int recipePos = spinnerSlotRecipe.getSelectedItemPosition();
            MealPlan existingPlan = manager.findMealPlan(editingSlotDate, editingSlot.mealType());

            if (recipePos > 0) {
                // Rezept zuweisen
                RecipeEntry selectedRecipe = recipesList.get(recipePos - 1);
                int servings = parseInt(inputSlotServings, 2);

                if (existingPlan != null) {
                    existingPlan.recipeId = selectedRecipe.id();
                    existingPlan.plannedServings = servings;
                    manager.updateMealPlan(existingPlan);
                } else {
                    MealPlan newPlan = new MealPlan.Builder(editingSlotDate, type, selectedRecipe.id())
                        .servings(servings)
                        .build();
                    manager.createMealPlan(newPlan);
                }
            } else {
                // "Kein Rezept" gewaehlt → existierendes loeschen
                if (existingPlan != null && existingPlan.id != null) {
                    manager.deleteMealPlan(existingPlan.id);
                }
            }
        }

        hideMealSlotModal();
        notifyChanged();
    }

    private void deleteMealSlot() {
        if (editingSlot == null) return;

        new AlertDialog.Builder(context)
            .setTitle("Mahlzeit löschen?")
            .setMessage(editingSlot.mealLabel() + " " + editingSlot.formattedTimeRange())
            .setPositiveButton("Löschen", (d, w) -> {
                // MealPlan auch loeschen
                if (editingSlotDate != null) {
                    MealPlan mp = manager.findMealPlan(editingSlotDate, editingSlot.mealType());
                    if (mp != null && mp.id != null) manager.deleteMealPlan(mp.id);
                }
                manager.deleteSchedule(editingSlot.id(), editingSlot.day());
                hideMealSlotModal();
                notifyChanged();
            })
            .setNegativeButton("Abbrechen", null)
            .show();
    }

    private void hideMealSlotModal() {
        modalMealSlotOverlay.setVisibility(View.GONE);
        editingSlot = null;
        editingSlotDate = null;
    }

    // ============================================================================
    // HELPERS
    // ============================================================================

    private MealPlanEntry findMealPlan(LocalDate date, MealType type) {
        for (MealPlanEntry entry : weekPlanEntries) {
            if (entry.date().equals(date) && entry.mealType() == type) {
                return entry;
            }
        }
        return null;
    }

    private void notifyChanged() {
        if (listener != null) listener.onDataChanged();
    }

    // ============================================================================
    // AUTO-GENERATE
    // ============================================================================

    public void showAutoGenerateDialog() {
        new AlertDialog.Builder(context)
            .setTitle("Woche automatisch planen")
            .setMessage("Es werden automatisch Rezepte fuer die angezeigte Woche ausgewaehlt.\n\n" +
                       "Bestehende Eintraege fuer diese Woche werden ueberschrieben.")
            .setPositiveButton("Planen", (dialog, which) -> generateWeekPlanAsync())
            .setNegativeButton("Abbrechen", null)
            .show();
    }

    public void generateWeekPlanAsync() {
        // Bestehende MealPlans fuer diese Woche loeschen
        List<MealPlanEntry> existing = manager.provideMealPlan(currentWeekStart);
        for (MealPlanEntry entry : existing) {
            manager.deleteMealPlan(entry.id());
        }

        // Neuen Wochenplan generieren
        GenerateMealPlan generator = new GenerateMealPlan(
            repository.SQLrepo.getInstance(context),
            manager,
            (day) -> CalendarReader.getEventsForDay(context, day,
                LocalTime.of(6, 0), LocalTime.of(22, 0))
        );
        generator.generateWeekPlan(currentWeekStart);

        // UI aktualisieren
        notifyChanged();
    }
}

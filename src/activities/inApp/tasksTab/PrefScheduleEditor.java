package activities.inApp.tasksTab;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import static activities.generic.ViewHelper.*;

import com.autosecretary.R;

import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

import activities.generic.DateTimeHelper;
import entities.TrackedItem;
import entities.TrackedItem.RepetitionType;
import entities.TrackedItem.RepUnits;

/**
 * Verwaltet die PrefSlot-Zeilen (bevorzugte Tage + Zeiten) im Edit-Modal.
 * Inflated rows aus item_pref_slot_row.xml, steuert Hinzufuegen/Entfernen,
 * monthly/weekly Spinner-Labels und fixe Slot-Anzahl bei REPS_PER_TIME.
 */
class PrefScheduleEditor {

    /** Callback zum Lesen des aktuellen RepType/RepUnit/RepValue aus FieldManager. */
    interface RepetitionStateProvider {
        RepetitionType getRepType();
        RepUnits getRepUnit();
        int getRepValue();
    }

    private final Context context;
    private final LayoutInflater inflater;
    private final RepetitionStateProvider repState;

    // UI-Referenzen
    private final View prefScheduleSection;
    private final LinearLayout prefSlotsContainer;
    private final TextView addSlotButton;

    // State
    private final List<View> slotRows = new ArrayList<>();
    private List<TrackedItem.PrefSlot> originalPrefSlots;

    // Labels
    private static final String[] DAY_LABELS = {"Mo", "Di", "Mi", "Do", "Fr", "Sa", "So"};
    private static final String[] MONTH_DAY_LABELS =
        java.util.stream.IntStream.rangeClosed(1, 31)
            .mapToObj(i -> i + ".")
            .toArray(String[]::new);

    PrefScheduleEditor(Context context, View root, RepetitionStateProvider repState) {
        this.context = context;
        this.inflater = LayoutInflater.from(context);
        this.repState = repState;

        prefScheduleSection = root.findViewById(R.id.section_pref_schedule);
        prefSlotsContainer = root.findViewById(R.id.container_pref_slots);
        addSlotButton = root.findViewById(R.id.btn_add_pref_slot);
        addSlotButton.setOnClickListener(v -> addSlotRow(0, null));
    }

    // ========================================================================
    // SICHTBARKEIT
    // ========================================================================

    void setVisible(boolean visible) {
        prefScheduleSection.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    // ========================================================================
    // MODUS-ERKENNUNG (delegiert an TrackedItem.isMonthlyDayMode)
    // ========================================================================

    boolean isMonthlyMode() {
        return TrackedItem.isMonthlyDayMode(repState.getRepType(), repState.getRepUnit());
    }

    private boolean isFixedSlotCount() {
        return repState.getRepType() == RepetitionType.REPS_PER_TIME
            && (repState.getRepUnit() == RepUnits.WEEK || repState.getRepUnit() == RepUnits.MONTH);
    }

    // ========================================================================
    // ROW-MANAGEMENT
    // ========================================================================

    View addSlotRow(int dayKey, LocalTime time) {
        View row = inflater.inflate(R.layout.item_pref_slot_row, prefSlotsContainer, false);

        Spinner daySpinner = row.findViewById(R.id.spinner_pref_day);
        String[] labels = isMonthlyMode() ? MONTH_DAY_LABELS : DAY_LABELS;
        daySpinner.setAdapter(spinnerAdapter(context, labels));
        if (dayKey > 0) daySpinner.setSelection(Math.min(dayKey - 1, labels.length - 1));

        EditText timeField = row.findViewById(R.id.field_pref_time);
        if (time != null) timeField.setText(DateTimeHelper.formatTime(time));
        timeField.setOnClickListener(v -> {
            int h = 9, m = 0;
            String cur = timeField.getText().toString().trim();
            if (!cur.isEmpty()) {
                try { LocalTime p = LocalTime.parse(cur); h = p.getHour(); m = p.getMinute(); }
                catch (DateTimeParseException e) { /* Default */ }
            }
            new android.app.TimePickerDialog(context, (tp, h2, m2) -> {
                timeField.setText(DateTimeHelper.formatTime(LocalTime.of(h2, m2)));
            }, h, m, true).show();
        });

        TextView removeBtn = row.findViewById(R.id.btn_remove_slot);
        removeBtn.setOnClickListener(v -> removeSlotRow(row));

        slotRows.add(row);
        prefSlotsContainer.addView(row);
        updateSlotRemoveButtons();
        updateAddSlotButtonVisibility();
        return row;
    }

    private void removeSlotRow(View row) {
        slotRows.remove(row);
        prefSlotsContainer.removeView(row);
        updateSlotRemoveButtons();
        updateAddSlotButtonVisibility();
    }

    void clearAll() {
        slotRows.clear();
        prefSlotsContainer.removeAllViews();
    }

    /** Baut alle PrefSlot-Rows neu mit korrektem Spinner-Typ (Wochentag/Monatstag). */
    void rebuildRows() {
        record SavedRow(int dayIdx, LocalTime time) {}
        List<SavedRow> saved = new ArrayList<>();
        for (View row : slotRows) {
            Spinner ds = row.findViewById(R.id.spinner_pref_day);
            EditText tf = row.findViewById(R.id.field_pref_time);
            int dayIdx = ds.getSelectedItemPosition();
            String ts = tf.getText().toString().trim();
            LocalTime time = null;
            if (!ts.isEmpty()) {
                try { time = LocalTime.parse(ts); }
                catch (java.time.format.DateTimeParseException ignored) {}
            }
            saved.add(new SavedRow(dayIdx, time));
        }
        clearAll();
        for (SavedRow sr : saved) {
            addSlotRow(sr.dayIdx + 1, sr.time);
        }
        if (slotRows.isEmpty()) addSlotRow(0, null);
    }

    private void updateSlotRemoveButtons() {
        boolean fixed = isFixedSlotCount();
        for (View row : slotRows) {
            View removeBtn = row.findViewById(R.id.btn_remove_slot);
            if (removeBtn != null) {
                removeBtn.setVisibility(fixed ? View.GONE : View.VISIBLE);
            }
        }
    }

    private void updateAddSlotButtonVisibility() {
        int maxSlots = isMonthlyMode() ? 31 : 7;
        addSlotButton.setVisibility(
            (slotRows.size() < maxSlots && !isFixedSlotCount()) ? View.VISIBLE : View.GONE);
    }

    /** Synchronisiert die Slot-Anzahl mit dem RepValue bei fixem Modus. */
    void updateSlotCount() {
        if (!isFixedSlotCount()) {
            updateSlotRemoveButtons();
            updateAddSlotButtonVisibility();
            return;
        }
        int maxSlots = isMonthlyMode() ? 31 : 7;
        int targetCount = repState.getRepValue();
        if (targetCount < 1) targetCount = 1;
        if (targetCount > maxSlots) targetCount = maxSlots;
        while (slotRows.size() < targetCount) addSlotRow(0, null);
        while (slotRows.size() > targetCount) {
            View last = slotRows.get(slotRows.size() - 1);
            removeSlotRow(last);
        }
        updateSlotRemoveButtons();
        updateAddSlotButtonVisibility();
    }

    // ========================================================================
    // POPULATE
    // ========================================================================

    /** Befuellt mit Default-Wert fuer Create (1 Slot, 09:00). */
    void populateDefault() {
        clearAll();
        originalPrefSlots = null;
        addSlotRow(0, LocalTime.of(9, 0));
    }

    /** Befuellt mit vorhandenen PrefSlots fuer Edit. */
    void populateSlots(List<TrackedItem.PrefSlot> prefSlots) {
        clearAll();
        originalPrefSlots = (prefSlots != null) ? new ArrayList<>(prefSlots) : null;
        if (prefSlots != null && !prefSlots.isEmpty()) {
            for (TrackedItem.PrefSlot slot : prefSlots) {
                addSlotRow(slot.dayKey(), slot.time());
            }
        }
        if (slotRows.isEmpty()) addSlotRow(0, null);
    }

    // ========================================================================
    // COLLECT (liest aktuelle Werte aus UI)
    // ========================================================================

    /** Sammelt alle PrefSlots aus den UI-Rows. */
    List<TrackedItem.PrefSlot> collectSlots() {
        List<TrackedItem.PrefSlot> result = new ArrayList<>();
        boolean monthly = isMonthlyMode();
        for (View row : slotRows) {
            Spinner daySpinner = row.findViewById(R.id.spinner_pref_day);
            EditText timeField = row.findViewById(R.id.field_pref_time);
            int dayIdx = daySpinner.getSelectedItemPosition();
            String timeStr = timeField.getText().toString().trim();
            if (dayIdx >= 0 && !timeStr.isEmpty()) {
                try {
                    int dayKey = dayIdx + 1;  // 1-7 (Wochentag) oder 1-31 (Monatstag)
                    LocalTime time = LocalTime.parse(timeStr);
                    int count = findOriginalCount(dayKey);
                    result.add(TrackedItem.PrefSlot.of(dayKey, time, count, monthly));
                } catch (IllegalArgumentException e) { /* ungueliger dayKey oder Zeit */ }
            }
        }
        return result;
    }

    private int findOriginalCount(int dayKey) {
        if (originalPrefSlots == null) return 0;
        for (TrackedItem.PrefSlot orig : originalPrefSlots) {
            if (orig.dayKey() == dayKey) return orig.completionCount();
        }
        return 0;
    }
}

package activities.inApp.tasksTab.editorModal.fields;

import android.app.TimePickerDialog;
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
import java.util.function.BooleanSupplier;

import activities.generic.DateTimeHelper;
import entities.TrackedItem;
import entities.TrackedItem.PrefSlot;
import entities.TrackedItem.RepetitionType;
import entities.TrackedItem.RepUnits;

/**
 * Verwaltet die PrefSlot-Zeilen (bevorzugte Tage + Zeiten) im Edit-Modal.
 * Implementiert FieldGroup. Liest repType/repUnit/repValue aus VisibilityFlags.
 * Slots werden erst bei apply() aus der UI gesammelt.
 */
public class PrefScheduleEditor implements FieldGroup {

    private static final String[] DAY_LABELS = {"Mo", "Di", "Mi", "Do", "Fr", "Sa", "So"};
    private static final String[] MONTH_DAY_LABELS =
        java.util.stream.IntStream.rangeClosed(1, 31)
            .mapToObj(i -> i + ".")
            .toArray(String[]::new);

    private final Context context;
    private final LayoutInflater inflater;
    private final BooleanSupplier suppressCheck;

    private final View prefScheduleSection;
    private final LinearLayout prefSlotsContainer;
    private final TextView addSlotButton;
    private final List<View> slotRows = new ArrayList<>();

    private boolean lastMonthlyMode = false;

    /** Original completionCounts aus dem Item — fuer Beibehaltung bei Save. */
    private List<PrefSlot> originalSlots = List.of();

    public PrefScheduleEditor(Context context, View root, BooleanSupplier suppressCheck) {
        this.context = context;
        this.inflater = LayoutInflater.from(context);
        this.suppressCheck = suppressCheck;

        prefScheduleSection = root.findViewById(R.id.section_pref_schedule);
        prefSlotsContainer = root.findViewById(R.id.container_pref_slots);
        addSlotButton = root.findViewById(R.id.btn_add_pref_slot);
        addSlotButton.setOnClickListener(v -> {
            if (suppressCheck.getAsBoolean()) return;
            addSlotRow(0, "09:00", lastMonthlyMode);
        });
    }

    // ========================================================================
    // FieldGroup
    // ========================================================================

    @Override
    public void populate(TrackedItem item) {
        slotRows.clear();
        prefSlotsContainer.removeAllViews();

        if (item != null && item.prefSlots != null && !item.prefSlots.isEmpty()) {
            originalSlots = item.prefSlots;
            boolean monthly = TrackedItem.isMonthlyDayMode(
                item.repetition != null ? item.repetition.type : RepetitionType.INTERVAL,
                item.repetition != null ? item.repetition.unit : RepUnits.DAY);
            lastMonthlyMode = monthly;
            for (PrefSlot slot : item.prefSlots) {
                addSlotRow(slot.dayKey(), DateTimeHelper.formatTime(slot.time()), monthly);
            }
        } else {
            originalSlots = List.of();
            lastMonthlyMode = false;
            addSlotRow(0, "09:00", false);
        }
    }

    @Override
    public void apply(TrackedItem.Builder builder) {
        // Guard: Wenn PrefSchedule-Sektion unsichtbar → keine Slots setzen
        if (prefScheduleSection.getVisibility() != View.VISIBLE) return;

        List<PrefSlot> slots = collectSlots();
        if (!slots.isEmpty()) builder.prefSlots(slots);
    }

    @Override
    public void updateVisibility(VisibilityFlags flags) {
        prefScheduleSection.setVisibility(flags.showRepetition() ? View.VISIBLE : View.GONE);
        if (!flags.showRepetition()) return;

        boolean monthly = TrackedItem.isMonthlyDayMode(flags.repType(), flags.repUnit());

        // Bei Monthly-Mode-Wechsel: Rows neu aufbauen mit anderem Spinner-Typ
        if (monthly != lastMonthlyMode) {
            rebuildRowsPreservingValues(monthly);
            lastMonthlyMode = monthly;
        }

        // Bei fixem Slot-Count: Slot-Anzahl synchronisieren
        if (isFixedSlotCount(flags)) {
            int maxSlots = monthly ? 31 : 7;
            int targetCount;
            try {
                targetCount = Integer.parseInt(flags.repValue());
            } catch (NumberFormatException e) {
                targetCount = 1;
            }
            if (targetCount < 1) targetCount = 1;
            if (targetCount > maxSlots) targetCount = maxSlots;

            while (slotRows.size() < targetCount) addSlotRow(0, "09:00", monthly);
            while (slotRows.size() > targetCount) {
                View last = slotRows.get(slotRows.size() - 1);
                slotRows.remove(last);
                prefSlotsContainer.removeView(last);
            }
        }

        // Add/Remove-Buttons aktualisieren
        boolean fixed = isFixedSlotCount(flags);
        for (View row : slotRows) {
            View removeBtn = row.findViewById(R.id.btn_remove_slot);
            if (removeBtn != null) removeBtn.setVisibility(fixed ? View.GONE : View.VISIBLE);
        }
        int maxSlots = monthly ? 31 : 7;
        addSlotButton.setVisibility(
            (slotRows.size() < maxSlots && !fixed) ? View.VISIBLE : View.GONE);
    }

    // ========================================================================
    // ROW-MANAGEMENT
    // ========================================================================

    private View addSlotRow(int dayKey, String timeStr, boolean monthly) {
        View row = inflater.inflate(R.layout.item_pref_slot_row, prefSlotsContainer, false);

        Spinner daySpinner = row.findViewById(R.id.spinner_pref_day);
        String[] labels = monthly ? MONTH_DAY_LABELS : DAY_LABELS;
        daySpinner.setAdapter(spinnerAdapter(context, labels));
        if (dayKey > 0) daySpinner.setSelection(Math.min(dayKey - 1, labels.length - 1));

        EditText timeField = row.findViewById(R.id.field_pref_time);
        if (timeStr != null && !timeStr.isEmpty()) timeField.setText(timeStr);
        timeField.setOnClickListener(v -> showTimePicker(timeField));

        TextView removeBtn = row.findViewById(R.id.btn_remove_slot);
        removeBtn.setOnClickListener(v -> {
            if (suppressCheck.getAsBoolean()) return;
            slotRows.remove(row);
            prefSlotsContainer.removeView(row);
        });

        slotRows.add(row);
        prefSlotsContainer.addView(row);
        return row;
    }

    /** Baut Rows neu mit anderem Spinner-Typ, behaelt Werte. */
    private void rebuildRowsPreservingValues(boolean monthly) {
        List<int[]> saved = new ArrayList<>();
        List<String> times = new ArrayList<>();
        for (View row : slotRows) {
            Spinner daySpinner = row.findViewById(R.id.spinner_pref_day);
            EditText timeField = row.findViewById(R.id.field_pref_time);
            saved.add(new int[]{ daySpinner.getSelectedItemPosition() + 1 });
            times.add(timeField.getText().toString().trim());
        }
        slotRows.clear();
        prefSlotsContainer.removeAllViews();
        for (int i = 0; i < saved.size(); i++) {
            addSlotRow(saved.get(i)[0], times.get(i), monthly);
        }
        if (slotRows.isEmpty()) addSlotRow(0, "09:00", monthly);
    }

    // ========================================================================
    // SLOT-COLLECTION (bei Save)
    // ========================================================================

    private List<PrefSlot> collectSlots() {
        List<PrefSlot> result = new ArrayList<>();
        for (View row : slotRows) {
            Spinner daySpinner = row.findViewById(R.id.spinner_pref_day);
            EditText timeField = row.findViewById(R.id.field_pref_time);
            int dayKey = daySpinner.getSelectedItemPosition() + 1;
            String timeStr = timeField.getText().toString().trim();
            if (dayKey > 0 && !timeStr.isEmpty()) {
                try {
                    LocalTime time = LocalTime.parse(timeStr);
                    int count = findOriginalCount(dayKey);
                    result.add(PrefSlot.of(dayKey, time, count, lastMonthlyMode));
                } catch (DateTimeParseException | IllegalArgumentException ignored) {}
            }
        }
        return result;
    }

    private int findOriginalCount(int dayKey) {
        for (PrefSlot orig : originalSlots) {
            if (orig.dayKey() == dayKey) return orig.completionCount();
        }
        return 0;
    }

    // ========================================================================
    // HELPER
    // ========================================================================

    boolean isMonthlyMode() { return lastMonthlyMode; }

    private boolean isFixedSlotCount(VisibilityFlags flags) {
        return flags.repType() == RepetitionType.REPS_PER_TIME
            && (flags.repUnit() == RepUnits.WEEK || flags.repUnit() == RepUnits.MONTH);
    }

    private void showTimePicker(EditText timeField) {
        int h = 9, m = 0;
        String cur = timeField.getText().toString().trim();
        if (!cur.isEmpty()) {
            try {
                LocalTime p = LocalTime.parse(cur);
                h = p.getHour();
                m = p.getMinute();
            } catch (DateTimeParseException e) { /* Default */ }
        }
        new TimePickerDialog(context, (tp, h2, m2) ->
            timeField.setText(DateTimeHelper.formatTime(LocalTime.of(h2, m2))),
            h, m, true).show();
    }
}

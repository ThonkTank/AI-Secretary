package com.autosecretary.features.task.ui.edit.internal.editor;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.autosecretary.R;
import com.autosecretary.features.task.ui.edit.state.PrefSlotEditState;
import com.google.android.material.button.MaterialButton;

import java.time.DayOfWeek;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Dynamically builds the preferred-slot editing UI for TaskEditDialog.
 * Groups prefSlots by repetition (one group per daily rep via repsPerDay) and renders
 * each group with day pickers and time pickers. Rebuilt when repetition fields change.
 */
public class PrefSlotUIBuilder {

    public interface Listener {
        void onDaysClicked(PrefSlotEditState prefSlot, Set<DayOfWeek> takenByOthers);

        void onTimeClicked(PrefSlotEditState prefSlot);
    }

    private final Context context;
    private final int prefSlotHeaderPaddingTopPx;
    private final int prefSlotHeaderPaddingBottomPx;
    private final int prefSlotRowPaddingStartPx;
    private final int prefSlotRowPaddingVerticalPx;
    private final int prefSlotButtonSpacingPx;
    private final int prefSlotButtonMinHeightPx;

    public PrefSlotUIBuilder(Context context) {
        this.context = context;
        prefSlotHeaderPaddingTopPx = context.getResources().getDimensionPixelSize(R.dimen.task_editor_pref_slot_header_padding_top);
        prefSlotHeaderPaddingBottomPx = context.getResources().getDimensionPixelSize(R.dimen.task_editor_pref_slot_header_padding_bottom);
        prefSlotRowPaddingStartPx = context.getResources().getDimensionPixelSize(R.dimen.task_editor_pref_slot_row_padding_start);
        prefSlotRowPaddingVerticalPx = context.getResources().getDimensionPixelSize(R.dimen.task_editor_pref_slot_row_padding_vertical);
        prefSlotButtonSpacingPx = context.getResources().getDimensionPixelSize(R.dimen.task_editor_pref_slot_button_spacing);
        prefSlotButtonMinHeightPx = context.getResources().getDimensionPixelSize(R.dimen.task_editor_pref_slot_button_min_height);
    }

    /**
     * Clears and rebuilds the prefSlot UI from current state.
     * Sorts by time, groups by repetition, renders section headers and rows.
     */
    public void rebuild(LinearLayout prefSlotContainer, List<PrefSlotEditState> editablePrefSlots,
                        int repsPerDay, Listener listener) {
        prefSlotContainer.removeAllViews();

        List<PrefSlotEditState> sorted = new ArrayList<>(editablePrefSlots);
        Collections.sort(sorted, (a, b) -> {
            if (a.start == null && b.start == null) return 0;
            if (a.start == null) return 1;
            if (b.start == null) return -1;
            return a.start.compareTo(b.start);
        });

        Map<Integer, List<PrefSlotEditState>> slotMap = groupByRepetition(sorted, repsPerDay);

        for (int key = 1; key <= repsPerDay; key++) {
            TextView header = new TextView(context);
            header.setText(context.getString(R.string.task_editor_pref_slot_header, key));
            header.setTypeface(null, Typeface.BOLD);
            header.setPadding(0, prefSlotHeaderPaddingTopPx, 0, prefSlotHeaderPaddingBottomPx);
            prefSlotContainer.addView(header);

            List<PrefSlotEditState> slotsInGroup = slotMap.getOrDefault(key, Collections.emptyList());
            for (PrefSlotEditState prefSlot : slotsInGroup) {
                LinearLayout row = new LinearLayout(context);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(prefSlotRowPaddingStartPx, prefSlotRowPaddingVerticalPx, 0, prefSlotRowPaddingVerticalPx);

                String formattedDays = formatDaysAsRanges(prefSlot.days);
                MaterialButton daysView = createInteractiveButton();
                daysView.setText(context.getString(R.string.task_editor_pref_slot_days_button, formattedDays));
                daysView.setContentDescription(context.getString(
                    R.string.task_editor_pref_slot_days_content_description,
                    formattedDays
                ));

                LinearLayout.LayoutParams daysParams = new LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f
                );
                daysParams.setMarginEnd(prefSlotButtonSpacingPx);
                daysView.setLayoutParams(daysParams);

                Set<DayOfWeek> takenByOthers = computeTakenDays(prefSlot, slotsInGroup);
                daysView.setOnClickListener(v -> listener.onDaysClicked(prefSlot, takenByOthers));

                String formattedTime = prefSlot.start != null
                    ? prefSlot.start.format(DateTimeFormatter.ofPattern("HH:mm"))
                    : "--:--";
                MaterialButton timeView = createInteractiveButton();
                timeView.setText(context.getString(R.string.task_editor_pref_slot_time_button, formattedTime));
                timeView.setContentDescription(context.getString(
                    R.string.task_editor_pref_slot_time_content_description,
                    formattedTime
                ));
                timeView.setTypeface(Typeface.MONOSPACE);

                LinearLayout.LayoutParams timeParams = new LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f
                );
                timeView.setLayoutParams(timeParams);
                timeView.setOnClickListener(v -> listener.onTimeClicked(prefSlot));

                row.addView(daysView);
                row.addView(timeView);
                prefSlotContainer.addView(row);
            }
        }
    }

    private String formatDaysAsRanges(Set<DayOfWeek> days) {
        if (days == null || days.isEmpty()) return context.getString(R.string.task_editor_pref_slot_no_days);

        List<DayOfWeek> sorted = new ArrayList<>(days);
        Collections.sort(sorted);

        List<String> parts = new ArrayList<>();
        int i = 0;
        while (i < sorted.size()) {
            DayOfWeek rangeStart = sorted.get(i);
            DayOfWeek rangeEnd = rangeStart;

            while (i + 1 < sorted.size()
                && sorted.get(i + 1).getValue() == sorted.get(i).getValue() + 1) {
                i++;
                rangeEnd = sorted.get(i);
            }

            String startLabel = dayLabel(rangeStart);
            if (rangeStart == rangeEnd) {
                parts.add(startLabel);
            } else {
                parts.add(startLabel + "-" + dayLabel(rangeEnd));
            }
            i++;
        }

        return String.join(" ", parts);
    }

    private static String dayLabel(DayOfWeek day) {
        return day.getDisplayName(TextStyle.SHORT, Locale.GERMAN).replace(".", "");
    }

    /**
     * Groups prefSlots into N groups (one per daily rep, where N = repsPerDay).
     * Uses a greedy algorithm: iterates reps 1..N, and for each rep claims all prefSlots
     * whose days don't overlap with days already taken in that rep's pass. The usedDays
     * set resets between reps, so the same day can appear in different rep groups.
     * Returns a map from rep number (1-based) to the prefSlots assigned to that group.
     */
    private static Map<Integer, List<PrefSlotEditState>> groupByRepetition(List<PrefSlotEditState> sorted,
                                                                       int repsPerDay) {
        Map<Integer, List<PrefSlotEditState>> slotMap = new HashMap<>();
        Set<DayOfWeek> usedDays = new HashSet<>();
        List<PrefSlotEditState> remaining = new ArrayList<>(sorted);

        int currentRep = 1;
        while (currentRep <= repsPerDay) {
            Iterator<PrefSlotEditState> it = remaining.iterator();
            while (it.hasNext()) {
                PrefSlotEditState prefSlot = it.next();
                Set<DayOfWeek> days = prefSlot.days != null ? prefSlot.days : EnumSet.noneOf(DayOfWeek.class);
                if (Collections.disjoint(days, usedDays)) {
                    usedDays.addAll(days);
                    slotMap.computeIfAbsent(currentRep, k -> new ArrayList<>()).add(prefSlot);
                    it.remove();
                }
            }
            usedDays.clear();
            currentRep++;
        }

        return slotMap;
    }

    /** Returns days already claimed by other prefSlots in the same group, used to disable them in the day picker. */
    private static Set<DayOfWeek> computeTakenDays(PrefSlotEditState current, List<PrefSlotEditState> groupSlots) {
        Set<DayOfWeek> taken = EnumSet.noneOf(DayOfWeek.class);
        for (PrefSlotEditState other : groupSlots) {
            if (other != current && other.days != null) {
                taken.addAll(other.days);
            }
        }
        return taken;
    }

    private MaterialButton createInteractiveButton() {
        MaterialButton button = new MaterialButton(context, null,
            com.google.android.material.R.attr.materialButtonOutlinedStyle);
        button.setMinHeight(prefSlotButtonMinHeightPx);
        button.setMinimumHeight(prefSlotButtonMinHeightPx);
        button.setClickable(true);
        button.setFocusable(true);
        button.setAllCaps(false);
        button.setTextAppearance(R.style.TextAppearance_AISecretary_Editor_PrefSlotButton);
        button.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        return button;
    }

}

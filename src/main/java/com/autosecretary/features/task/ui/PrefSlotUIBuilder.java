package com.autosecretary.features.task.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.autosecretary.features.task.ui.model.PrefSlotEditState;

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

public class PrefSlotUIBuilder {

    public interface Listener {
        void onDaysClicked(PrefSlotEditState prefSlot, Set<DayOfWeek> takenByOthers);

        void onTimeClicked(PrefSlotEditState prefSlot, TextView timeView);
    }

    private final Context context;

    public PrefSlotUIBuilder(Context context) {
        this.context = context;
    }

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
            header.setText("Wiederholung " + key);
            header.setTypeface(null, Typeface.BOLD);
            header.setPadding(0, dpToPx(12), 0, dpToPx(4));
            prefSlotContainer.addView(header);

            List<PrefSlotEditState> slotsInGroup = slotMap.getOrDefault(key, Collections.emptyList());
            for (PrefSlotEditState prefSlot : slotsInGroup) {
                LinearLayout row = new LinearLayout(context);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dpToPx(16), dpToPx(4), 0, dpToPx(4));

                TextView daysView = new TextView(context);
                daysView.setText(formatDaysAsRanges(prefSlot.days));
                daysView.setPadding(0, dpToPx(4), dpToPx(16), dpToPx(4));
                daysView.setTextSize(14);

                Set<DayOfWeek> takenByOthers = computeTakenDays(prefSlot, slotsInGroup);
                daysView.setOnClickListener(v -> listener.onDaysClicked(prefSlot, takenByOthers));

                TextView timeView = new TextView(context);
                timeView.setText(prefSlot.start != null
                    ? prefSlot.start.format(DateTimeFormatter.ofPattern("HH:mm"))
                    : "--:--");
                timeView.setPadding(0, dpToPx(4), 0, dpToPx(4));
                timeView.setTextSize(14);
                timeView.setTypeface(Typeface.MONOSPACE);
                timeView.setOnClickListener(v -> listener.onTimeClicked(prefSlot, timeView));

                row.addView(daysView);
                row.addView(timeView);
                prefSlotContainer.addView(row);
            }
        }
    }

    static String formatDaysAsRanges(Set<DayOfWeek> days) {
        if (days == null || days.isEmpty()) return "Keine Tage";

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

    private static Set<DayOfWeek> computeTakenDays(PrefSlotEditState current, List<PrefSlotEditState> groupSlots) {
        Set<DayOfWeek> taken = EnumSet.noneOf(DayOfWeek.class);
        for (PrefSlotEditState other : groupSlots) {
            if (other != current && other.days != null) {
                taken.addAll(other.days);
            }
        }
        return taken;
    }

    private int dpToPx(int dp) {
        return (int) (dp * context.getResources().getDisplayMetrics().density + 0.5f);
    }
}

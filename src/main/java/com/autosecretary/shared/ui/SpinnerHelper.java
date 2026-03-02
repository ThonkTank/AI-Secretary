package com.autosecretary.shared.ui;

import android.content.Context;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Utility class that binds a typed list to an Android {@link Spinner} using a label
 * extractor function, avoiding boilerplate {@link ArrayAdapter} setup in every dialog
 * controller.
 *
 * <p>Typical usage pattern:
 * <ol>
 *   <li>Call {@link #bindList} to populate the spinner with display labels.</li>
 *   <li>Call {@link #setSelection} to restore a previously chosen item by ID.</li>
 *   <li>Call {@link #idAtPosition} to read back the selected item's ID on confirm.</li>
 * </ol>
 */
public class SpinnerHelper {
    private SpinnerHelper() {}

    /**
     * Maps each item in {@code items} to a label string via {@code label} and binds the
     * resulting list to {@code spinner}.
     */
    public static <T> void bindList(Spinner spinner, List<T> items,
                                    Function<T, String> label, Context context) {
        List<String> names = new ArrayList<>(items.size());
        for (T item : items) {
            names.add(label.apply(item));
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(context,
                android.R.layout.simple_spinner_item, names);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
    }

    /**
     * Returns the ID extracted from {@code items} at {@code position}, or {@code null}
     * if {@code position} is out of range (e.g. spinner is empty).
     */
    @Nullable
    public static <T> String idAtPosition(List<T> items, int position,
                                          Function<T, String> idExtractor) {
        if (position < 0 || position >= items.size()) return null;
        return idExtractor.apply(items.get(position));
    }

    /**
     * Scrolls the spinner to the item whose ID (as produced by {@code idExtractor}) equals
     * {@code targetId}. Does nothing if {@code targetId} is {@code null} or not found in
     * {@code items} — the spinner keeps its current (default) selection.
     */
    public static <T> void setSelection(Spinner spinner, List<T> items,
                                        @Nullable String targetId,
                                        Function<T, String> idExtractor) {
        if (targetId == null) return;
        for (int i = 0; i < items.size(); i++) {
            if (targetId.equals(idExtractor.apply(items.get(i)))) {
                spinner.setSelection(i);
                return;
            }
        }
    }

    /**
     * Binds a spinner with a "none" sentinel at position 0 followed by the real items.
     * If {@code selectedId} matches an item, selects it (offset by 1 for the sentinel).
     */
    public static <T> void bindListWithNone(Spinner spinner, List<T> items,
                                            Function<T, String> label, String noneLabel,
                                            @Nullable String selectedId,
                                            Function<T, String> idExtractor,
                                            Context context) {
        List<String> labels = new ArrayList<>(items.size() + 1);
        labels.add(noneLabel);
        for (T item : items) labels.add(label.apply(item));
        bindList(spinner, labels, s -> s, context);
        if (selectedId != null) {
            for (int i = 0; i < items.size(); i++) {
                if (selectedId.equals(idExtractor.apply(items.get(i)))) {
                    spinner.setSelection(i + 1);
                    return;
                }
            }
        }
    }

    /**
     * Returns the enum value at the spinner's current position, or {@code null} if out of range.
     */
    @Nullable
    public static <E extends Enum<E>> E enumAtPosition(Spinner spinner, E[] values) {
        int idx = spinner.getSelectedItemPosition();
        return (idx >= 0 && idx < values.length) ? values[idx] : null;
    }

}

package com.autosecretary.features.budget.ui.internal;

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

    /** Binds a pre-built list of display strings to {@code spinner}. */
    public static void bindNames(Spinner spinner, List<String> names, Context context) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(context,
                android.R.layout.simple_spinner_item, names);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
    }

    /**
     * Convenience overload: maps each item in {@code items} to a label string via
     * {@code label}, then delegates to {@link #bindNames}.
     */
    public static <T> void bindList(Spinner spinner, List<T> items,
                                    Function<T, String> label, Context context) {
        List<String> names = new ArrayList<>(items.size());
        for (T item : items) {
            names.add(label.apply(item));
        }
        bindNames(spinner, names, context);
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
}

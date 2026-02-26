package com.autosecretary.features.budget.ui.internal;

import android.content.Context;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class SpinnerHelper {
    private SpinnerHelper() {}

    public static void bindNames(Spinner spinner, List<String> names, Context context) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(context,
                android.R.layout.simple_spinner_item, names);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
    }

    public static <T> void bindList(Spinner spinner, List<T> items,
                                    Function<T, String> label, Context context) {
        List<String> names = new ArrayList<>(items.size());
        for (T item : items) {
            names.add(label.apply(item));
        }
        bindNames(spinner, names, context);
    }

    @Nullable
    public static <T> String idAtPosition(List<T> items, int position,
                                          Function<T, String> idExtractor) {
        if (position < 0 || position >= items.size()) return null;
        return idExtractor.apply(items.get(position));
    }

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

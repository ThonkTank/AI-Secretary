package com.autosecretary.features.budget.ui.internal;

import android.content.Context;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

import java.util.List;

public class SpinnerHelper {
    private SpinnerHelper() {}

    public static void bindNames(Spinner spinner, List<String> names, Context context) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(context,
                android.R.layout.simple_spinner_item, names);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
    }
}

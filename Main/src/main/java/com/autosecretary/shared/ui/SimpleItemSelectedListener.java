package com.autosecretary.shared.ui;

import android.widget.AdapterView;

/** No-op adapter so subclasses only need to override {@code onItemSelected}. */
public abstract class SimpleItemSelectedListener implements AdapterView.OnItemSelectedListener {
    @Override public void onNothingSelected(AdapterView<?> parent) {}
}

package com.autosecretary.shared.ui;

import com.google.android.material.button.MaterialButtonToggleGroup;

/** No-op adapter that fires only for the checked (not unchecked) event. */
public abstract class SimpleButtonCheckedListener
        implements MaterialButtonToggleGroup.OnButtonCheckedListener {
    @Override
    public final void onButtonChecked(MaterialButtonToggleGroup group,
                                      int checkedId, boolean isChecked) {
        if (isChecked) onChecked(group, checkedId);
    }

    public abstract void onChecked(MaterialButtonToggleGroup group, int checkedId);
}

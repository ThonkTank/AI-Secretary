package com.autosecretary.shared.ui;

import android.text.TextWatcher;

/** No-op adapter so subclasses only need to override {@code afterTextChanged}. */
public abstract class SimpleTextWatcher implements TextWatcher {
    @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
    @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
}

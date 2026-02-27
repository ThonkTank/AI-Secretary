package com.autosecretary.features.budget.ui.state;

import android.content.Context;

import androidx.annotation.StringRes;

/**
 * Deferred string wrapper used in ViewModel LiveData.
 * Resolves a string resource (or literal) only when a Context is available,
 * keeping the ViewModel free of Android Context dependencies.
 */
public class UiText {

    private final @StringRes int resId;
    private final Object[] formatArgs;
    private final String raw;

    private UiText(@StringRes int resId, Object[] formatArgs, String raw) {
        this.resId = resId;
        this.formatArgs = formatArgs;
        this.raw = raw;
    }

    public static UiText of(@StringRes int resId, Object... args) {
        return new UiText(resId, args, null);
    }

    public static UiText raw(String text) {
        return new UiText(0, null, text);
    }

    public String resolve(Context context) {
        if (raw != null) return raw;
        if (formatArgs.length > 0) {
            return context.getString(resId, formatArgs);
        }
        return context.getString(resId);
    }
}

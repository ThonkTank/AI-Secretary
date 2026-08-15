package de.thonktank.autosecretary.presentation;

import android.content.Context;

public final class AndroidUiTextProvider implements UiTextProvider {
    private final Context context;

    public AndroidUiTextProvider(Context context) {
        this.context = context.getApplicationContext();
    }

    @Override public String text(int resourceId) {
        return context.getString(resourceId);
    }
}

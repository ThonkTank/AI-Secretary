package de.thonktank.autosecretary;

import android.appwidget.AppWidgetManager;
import android.os.Bundle;

public final class WidgetSizeClassifier {
    public enum Size { SMALL, WIDE, TALL, LARGE }

    public Size classify(Bundle options) {
        int width = options == null ? 160
                : options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 160);
        int height = options == null ? 160
                : options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 160);
        if (height < 220) return width < 220 ? Size.SMALL : Size.WIDE;
        return width < 310 ? Size.TALL : Size.LARGE;
    }
}

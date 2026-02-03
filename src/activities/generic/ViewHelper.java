package activities.generic;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;

/**
 * Statische Helfer-Methoden für programmatischen View-Aufbau.
 * Eliminiert dp()-Duplikate und wiederkehrende UI-Patterns.
 */
public final class ViewHelper {

    private ViewHelper() {}

    /** Konvertiert dp zu Pixel */
    public static int dp(Context ctx, int value) {
        return (int) TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, value,
            ctx.getResources().getDisplayMetrics());
    }

    /** Erstellt ein GradientDrawable mit Farbe und abgerundeten Ecken */
    public static GradientDrawable roundedBg(Context ctx, int color, int cornerDp) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(dp(ctx, cornerDp));
        return bg;
    }
}

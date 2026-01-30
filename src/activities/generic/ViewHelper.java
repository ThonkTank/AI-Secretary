package activities.generic;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

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

    /** @deprecated Ersetzt durch style/FormLabel in res/values/styles.xml */
    @Deprecated
    public static TextView buildLabel(Context ctx, String text) {
        TextView label = new TextView(ctx);
        label.setText(text);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, UIConstants.SP_SMALL);
        label.setTextColor(UIConstants.TEXT_SECONDARY);
        label.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(ctx, 8);
        label.setLayoutParams(params);
        return label;
    }

    /** @deprecated Ersetzt durch View-Spacer in XML-Layouts */
    @Deprecated
    public static View buildSpacer(Context ctx, int heightDp) {
        View spacer = new View(ctx);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(ctx, heightDp)));
        return spacer;
    }
}

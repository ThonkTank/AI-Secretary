package de.thonktank.autosecretary;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.widget.TextView;

public final class UiStyle {
    public final Context context;
    public final Typeface serif;
    public final Typeface serifItalic;
    public final Typeface sans;
    public final Typeface sansBold;

    public UiStyle(Context context) {
        this.context = context;
        serif = context.getResources().getFont(R.font.newsreader);
        serifItalic = context.getResources().getFont(R.font.newsreader_italic);
        sans = context.getResources().getFont(R.font.alegreya_sans);
        sansBold = context.getResources().getFont(R.font.alegreya_sans_bold);
    }

    public TextView serif(String value, float size, int color, boolean italic, int weight) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(italic ? serifItalic : serif);
        if (Build.VERSION.SDK_INT >= 26) {
            view.setFontVariationSettings("'wght' " + weight);
        }
        if (size >= 30) view.setLetterSpacing(-.02f);
        view.setIncludeFontPadding(false);
        return view;
    }

    public TextView sans(String value, float size, int color, boolean bold) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(bold ? sansBold : sans);
        view.setIncludeFontPadding(false);
        return view;
    }

    public TextView primaryButton(String value, DayPalette palette) {
        TextView view = sans(value, 17, palette.accentText, true);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(28), 0, dp(28), 0);
        view.setMinHeight(dp(52));
        view.setBackground(pill(palette.accent, 26));
        shadow(view, palette, 5, .7f);
        return view;
    }

    public GradientDrawable leaf(int color, int edge, float tl, float tr, float br, float bl) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadii(new float[]{dp(tl), dp(tl), dp(tr), dp(tr),
                dp(br), dp(br), dp(bl), dp(bl)});
        drawable.setStroke(Math.max(1, dp(1)), edge);
        return drawable;
    }

    public GradientDrawable pill(int color, float radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        return drawable;
    }

    public GradientDrawable dashed(DayPalette palette) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.TRANSPARENT);
        drawable.setCornerRadii(new float[]{dp(10), dp(10), dp(64), dp(64),
                dp(10), dp(10), dp(64), dp(64)});
        drawable.setStroke(dp(1), palette.dot, dp(6), dp(5));
        return drawable;
    }

    public int edge(DayPalette palette, int level) {
        if (level == 1) return palette.leaf1Edge;
        if (level == 2) return palette.leaf2Edge;
        return palette.leaf3Edge;
    }

    public void shadow(View view, DayPalette palette, float elevationDp, float strength) {
        view.setElevation(dp(elevationDp));
        if (Build.VERSION.SDK_INT >= 28) {
            int color = alpha(0xff000000, Math.min(1f, palette.shadowAlpha * strength));
            view.setOutlineSpotShadowColor(color);
            view.setOutlineAmbientShadowColor(color);
        }
    }
    public int dimen(int resourceId) { return context.getResources().getDimensionPixelSize(resourceId); }
    public int dp(float value) { return Math.round(value * context.getResources().getDisplayMetrics().density); }
    public static int alpha(int color, float alpha) { return (Math.round(alpha * 255) << 24) | (color & 0x00ffffff); }
}

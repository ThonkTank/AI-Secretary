package de.thonktank.autosecretary;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

public final class YearRingView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final UiStyle style;
    private int weeks;
    private DayPalette palette;

    public YearRingView(Context context) {
        super(context);
        style = new UiStyle(context);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    public void bind(int weeks, DayPalette palette) {
        this.weeks = weeks;
        this.palette = palette;
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        if (palette == null) return;
        float center = getWidth() / 2f;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(style.dp(1.5f));
        paint.setColor(palette.light);
        canvas.drawCircle(center, center, center - style.dp(1), paint);
        paint.setAlpha(153);
        canvas.drawCircle(center, center, center * .62f, paint);
        paint.setAlpha(255);
        paint.setStyle(Paint.Style.FILL);
        paint.setTypeface(style.serif);
        paint.setTextSize(style.dp(17));
        paint.setTextAlign(Paint.Align.CENTER);
        Paint.FontMetrics metrics = paint.getFontMetrics();
        canvas.drawText(String.valueOf(weeks), center,
                center - (metrics.ascent + metrics.descent) / 2, paint);
    }
}

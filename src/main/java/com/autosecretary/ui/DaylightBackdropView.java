package com.autosecretary.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

import com.autosecretary.domain.SolarDaylight;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

/** Clock- and location-driven sun glow plus the three deliberately quiet forest layers. */
public final class DaylightBackdropView extends View {
    public enum Mode { AUTO, LIGHT, DARK }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private double latitude = 51.20;
    private double longitude = 6.69;
    private Mode mode = Mode.AUTO;
    private final Runnable minuteTick = new Runnable() {
        @Override public void run() {
            invalidate();
            postDelayed(this, 60_000L);
        }
    };

    public DaylightBackdropView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void setCoordinates(double latitude, double longitude) {
        this.latitude = latitude;
        this.longitude = longitude;
        invalidate();
    }

    public void setMode(Mode mode) {
        this.mode = mode == null ? Mode.AUTO : mode;
        invalidate();
    }

    public boolean wantsLightCards() {
        if (mode == Mode.LIGHT) return true;
        if (mode == Mode.DARK) return false;
        SolarDaylight.Window window = SolarDaylight.forDate(
                LocalDate.now(), latitude, longitude, ZoneId.systemDefault());
        return window.isLight(LocalTime.now());
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        removeCallbacks(minuteTick);
        postDelayed(minuteTick, 60_000L);
    }

    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(minuteTick);
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) return;

        LocalTime now = LocalTime.now();
        SolarDaylight.Window window = SolarDaylight.forDate(
                LocalDate.now(), latitude, longitude, ZoneId.systemDefault());
        boolean light = wantsLightCards();
        boolean evening = now.isAfter(window.sunset().minusMinutes(90))
                && now.isBefore(window.sunset().plusMinutes(60));
        int base = light ? Color.rgb(238, 240, 230)
                : evening ? Color.rgb(18, 16, 10) : Color.rgb(8, 15, 11);
        canvas.drawColor(base);

        float dayFraction = now.toSecondOfDay() / 86400f;
        float sunX = width * (0.98f - 0.96f * dayFraction);
        if (now.isAfter(window.sunset())) sunX = width * Math.min(0.62f,
                0.02f + (float) java.time.Duration.between(window.sunset(), now).toMinutes() / 300f);
        int glow = light ? Color.argb(180, 255, 240, 206)
                : evening ? Color.argb(142, 248, 168, 74)
                : Color.argb(48, 140, 166, 190);
        paint.setAlpha(255);
        paint.setShader(new RadialGradient(
                sunX, -height * 0.05f, width * (light ? 0.78f : 0.52f),
                glow, Color.TRANSPARENT, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, width, height * 0.68f, paint);
        paint.setShader(null);

        drawForest(canvas, width, height, light ? 0.075f : 0.34f, 0);
        drawForest(canvas, width, height, light ? 0.14f : 0.60f, 1);
        drawForest(canvas, width, height, light ? 0.50f : 0.96f, 2);

        paint.setAlpha(255);
        paint.setShader(new LinearGradient(0, 0, 0, height * 0.12f,
                Color.argb(0, Color.red(base), Color.green(base), Color.blue(base)),
                base, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, width, height * 0.12f, paint);
        paint.setShader(null);
    }

    private void drawForest(Canvas canvas, int width, int height, float alpha, int layer) {
        int color = wantsLightCards() ? Color.rgb(42, 54, 40) : Color.rgb(1, 3, 2);
        paint.setColor(color);
        paint.setAlpha(Math.round(alpha * 255));
        paint.setStyle(Paint.Style.FILL);
        if (layer == 2) {
            Path trunk = new Path();
            trunk.moveTo(-8, height);
            trunk.lineTo(dp(53), height);
            trunk.lineTo(dp(40), 0);
            trunk.lineTo(dp(4), 0);
            trunk.close();
            canvas.drawPath(trunk, paint);
            branch(canvas, dp(32), height * .42f, dp(102), height * .34f, dp(12));
            branch(canvas, dp(28), height * .58f, dp(92), height * .66f, dp(10));
            branch(canvas, dp(36), height * .30f, dp(82), height * .24f, dp(8));
            return;
        }
        int count = layer == 0 ? 5 : 2;
        for (int index = 0; index < count; index++) {
            float x = layer == 0
                    ? width * (0.12f + index * 0.21f)
                    : width * (index == 0 ? 0.17f : 0.82f);
            float half = dp(layer == 0 ? 3 : 7);
            canvas.drawRect(x - half, 0, x + half, height, paint);
            branch(canvas, x, height * (0.38f + index * .05f),
                    x + (index % 2 == 0 ? dp(42) : -dp(42)), height * .32f, dp(4));
        }
    }

    private void branch(Canvas canvas, float x1, float y1, float x2, float y2, float thickness) {
        paint.setStrokeWidth(thickness);
        paint.setStrokeCap(Paint.Cap.SQUARE);
        paint.setStyle(Paint.Style.STROKE);
        canvas.drawLine(x1, y1, x2, y2, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}

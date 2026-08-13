package com.autosecretary.ui;

import android.animation.ValueAnimator;
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
import android.view.animation.AccelerateDecelerateInterpolator;

import com.autosecretary.domain.SolarDaylight;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Clock- and location-driven sun glow plus the three deliberately quiet forest layers. */
public final class DaylightBackdropView extends View {
    public enum Mode { AUTO, LIGHT, DARK }

    static record Scene(float x, float width, float height, int firstColor, int secondColor) { }

    private record Mark(float x, float width, float height, int firstColor, int secondColor) { }
    private record TimedMark(long minute, Mark mark) { }

    private static final Mark DAWN = mark(78, 110, 44, 0x3396B0C4, 0x1A6E8CA4);
    private static final Mark SUNRISE = mark(96, 88, 42, 0x80F4B258, 0x3DDC783A);
    private static final Mark MORNING = mark(66, 130, 52, 0xB8FFF0CE, 0x42F0DCAA);
    private static final Mark NOON = mark(50, 150, 58, 0xCCFFFCEE, 0x4DF6F2DE);
    private static final Mark AFTERNOON = mark(24, 130, 54, 0xA8FCDEA4, 0x3DE8B46E);
    private static final Mark GOLDEN = mark(6, 104, 50, 0x8FF8A84A, 0x47CE602C);
    private static final Mark DUSK = mark(2, 80, 36, 0x42E28C56, 0x33607C9E);
    private static final Mark NIGHT = mark(60, 120, 40, 0x218CA6BE, 0x14506A82);

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private double latitude = 51.20;
    private double longitude = 6.69;
    private Mode mode = Mode.AUTO;
    private ValueAnimator breathingLight;
    private float breathingOffset;
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

    public boolean usesEveningPalette() {
        if (wantsLightCards()) return false;
        SolarDaylight.Window window = SolarDaylight.forDate(
                LocalDate.now(), latitude, longitude, ZoneId.systemDefault());
        LocalTime now = LocalTime.now();
        return now.isAfter(window.sunset().minusMinutes(90))
                && now.isBefore(window.sunset().plusMinutes(60));
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        removeCallbacks(minuteTick);
        postDelayed(minuteTick, 60_000L);
        if (ValueAnimator.areAnimatorsEnabled()) {
            breathingLight = ValueAnimator.ofFloat(-9f, 9f);
            breathingLight.setDuration(11_000L);
            breathingLight.setRepeatCount(ValueAnimator.INFINITE);
            breathingLight.setRepeatMode(ValueAnimator.REVERSE);
            breathingLight.setInterpolator(new AccelerateDecelerateInterpolator());
            breathingLight.addUpdateListener(value -> {
                breathingOffset = dp((float) value.getAnimatedValue());
                invalidate();
            });
            breathingLight.start();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(minuteTick);
        if (breathingLight != null) breathingLight.cancel();
        breathingLight = null;
        breathingOffset = 0f;
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) return;

        LocalDate date = LocalDate.now();
        ZoneId zone = ZoneId.systemDefault();
        Scene scene = sceneAt(date, LocalTime.now(), latitude, longitude, zone);
        boolean light = wantsLightCards();
        boolean evening = usesEveningPalette();
        int base = light ? Color.rgb(238, 240, 230)
                : evening ? Color.rgb(18, 16, 10) : Color.rgb(8, 15, 11);
        canvas.drawColor(base);

        drawGlow(canvas, width, height, scene.x(), -10f, scene.width(), scene.height(),
                scene.firstColor(), 0.58f);
        drawGlow(canvas, width, height, scene.x(), 16f,
                scene.width() * 0.7f, scene.height() * 0.7f,
                scene.secondColor(), 0.62f);

        int forestColor = light ? Color.rgb(42, 54, 40)
                : evening ? Color.rgb(2, 2, 1) : Color.rgb(1, 3, 2);
        drawForest(canvas, width, height, light ? 0.075f : 0.34f, 0, forestColor);
        drawForest(canvas, width, height, light ? 0.14f : 0.60f, 1, forestColor);
        drawForest(canvas, width, height, light ? 0.50f : 0.96f, 2, forestColor);

        // Cover the forest at the status bar, then let it fade in below 10.5%.
        paint.setAlpha(255);
        paint.setShader(new LinearGradient(0, 0, 0, height * 0.105f,
                new int[] {base, base, Color.TRANSPARENT},
                new float[] {0f, 0.52f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, width, height * 0.105f, paint);
        paint.setShader(null);
    }

    static Scene sceneAt(
            LocalDate date,
            LocalTime time,
            double latitude,
            double longitude,
            ZoneId zone) {
        List<TimedMark> marks = new ArrayList<>();
        addLateDayMarks(marks, date.minusDays(1), date, latitude, longitude, zone);
        addDayMarks(marks, date, date, latitude, longitude, zone);
        addEarlyDayMarks(marks, date.plusDays(1), date, latitude, longitude, zone);
        marks.sort(Comparator.comparingLong(TimedMark::minute));
        long current = time.getHour() * 60L + time.getMinute();
        TimedMark before = marks.get(0);
        TimedMark after = marks.get(marks.size() - 1);
        for (int index = 0; index < marks.size() - 1; index++) {
            TimedMark candidate = marks.get(index);
            TimedMark next = marks.get(index + 1);
            if (current >= candidate.minute() && current <= next.minute()) {
                before = candidate;
                after = next;
                break;
            }
        }
        float fraction = before.minute() == after.minute() ? 0f
                : (float) (current - before.minute()) / (after.minute() - before.minute());
        fraction = Math.max(0f, Math.min(1f, fraction));
        return interpolate(before.mark(), after.mark(), fraction);
    }

    private static void addDayMarks(
            List<TimedMark> marks,
            LocalDate date,
            LocalDate origin,
            double latitude,
            double longitude,
            ZoneId zone) {
        SolarDaylight.Window window = SolarDaylight.forDate(date, latitude, longitude, zone);
        long sunrise = relativeMinute(date, window.sunrise(), origin);
        long sunset = relativeMinute(date, window.sunset(), origin);
        long daylight = Math.max(1, sunset - sunrise);
        marks.add(new TimedMark(sunrise - 130, DAWN));
        marks.add(new TimedMark(sunrise, SUNRISE));
        marks.add(new TimedMark(sunrise + Math.round(daylight * 0.242f), MORNING));
        marks.add(new TimedMark(sunrise + Math.round(daylight * 0.503f), NOON));
        marks.add(new TimedMark(sunrise + Math.round(daylight * 0.815f), AFTERNOON));
        marks.add(new TimedMark(sunset, GOLDEN));
        marks.add(new TimedMark(sunset + 125, DUSK));
        marks.add(new TimedMark(sunset + 255, NIGHT));
    }

    private static void addLateDayMarks(
            List<TimedMark> marks,
            LocalDate date,
            LocalDate origin,
            double latitude,
            double longitude,
            ZoneId zone) {
        SolarDaylight.Window window = SolarDaylight.forDate(date, latitude, longitude, zone);
        long sunset = relativeMinute(date, window.sunset(), origin);
        marks.add(new TimedMark(sunset, GOLDEN));
        marks.add(new TimedMark(sunset + 125, DUSK));
        marks.add(new TimedMark(sunset + 255, NIGHT));
    }

    private static void addEarlyDayMarks(
            List<TimedMark> marks,
            LocalDate date,
            LocalDate origin,
            double latitude,
            double longitude,
            ZoneId zone) {
        SolarDaylight.Window window = SolarDaylight.forDate(date, latitude, longitude, zone);
        long sunrise = relativeMinute(date, window.sunrise(), origin);
        marks.add(new TimedMark(sunrise - 130, DAWN));
        marks.add(new TimedMark(sunrise, SUNRISE));
    }

    private static long relativeMinute(LocalDate date, LocalTime time, LocalDate origin) {
        return ChronoUnit.DAYS.between(origin, date) * 1440
                + time.getHour() * 60L + time.getMinute();
    }

    private static Scene interpolate(Mark left, Mark right, float fraction) {
        return new Scene(
                lerp(left.x(), right.x(), fraction),
                lerp(left.width(), right.width(), fraction),
                lerp(left.height(), right.height(), fraction),
                blend(left.firstColor(), right.firstColor(), fraction),
                blend(left.secondColor(), right.secondColor(), fraction));
    }

    private static float lerp(float left, float right, float fraction) {
        return left + (right - left) * fraction;
    }

    private static int blend(int left, int right, float fraction) {
        int alpha = Math.round(lerp(left >>> 24, right >>> 24, fraction));
        int red = Math.round(lerp((left >>> 16) & 0xff, (right >>> 16) & 0xff, fraction));
        int green = Math.round(lerp((left >>> 8) & 0xff, (right >>> 8) & 0xff, fraction));
        int blue = Math.round(lerp(left & 0xff, right & 0xff, fraction));
        return alpha << 24 | red << 16 | green << 8 | blue;
    }

    private static Mark mark(float x, float width, float height, int first, int second) {
        return new Mark(x, width, height, first, second);
    }

    private void drawGlow(
            Canvas canvas,
            int width,
            int height,
            float xPercent,
            float yPercent,
            float widthPercent,
            float heightPercent,
            int color,
            float transparentAt) {
        float centerX = width * xPercent / 100f + breathingOffset;
        float centerY = height * yPercent / 100f;
        float radiusX = Math.max(1f, width * widthPercent / 100f);
        float radiusY = Math.max(1f, height * heightPercent / 100f);
        paint.setAlpha(255);
        paint.setShader(new RadialGradient(centerX, centerY, radiusX,
                new int[] {color, Color.TRANSPARENT, Color.TRANSPARENT},
                new float[] {0f, transparentAt, 1f}, Shader.TileMode.CLAMP));
        canvas.save();
        canvas.scale(1f, radiusY / radiusX, centerX, centerY);
        canvas.drawCircle(centerX, centerY, radiusX, paint);
        canvas.restore();
        paint.setShader(null);
    }

    private void drawForest(
            Canvas canvas, int width, int height, float alpha, int layer, int color) {
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

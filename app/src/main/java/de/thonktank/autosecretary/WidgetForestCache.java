package de.thonktank.autosecretary;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.LruCache;

/** Bounded artwork cache; canonical bitmaps stay within the tested RemoteViews Binder budget. */
final class WidgetForestCache {
    static final int MAX_BITMAP_BYTES = 512 * 512 * 4;
    private final LruCache<String, Bitmap> bitmaps = new LruCache<>(20);
    private int renderCount;

    synchronized Bitmap get(WidgetSizeClassifier.Size size, DayPalette palette) {
        String key = key(size, palette);
        Bitmap cached = bitmaps.get(key);
        if (cached != null) return cached;
        int[] dimensions = dimensions(size);
        Bitmap bitmap = Bitmap.createBitmap(dimensions[0], dimensions[1], Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Path clip = new Path();
        clip.addRoundRect(new RectF(0, 0, dimensions[0], dimensions[1]), 22, 22, Path.Direction.CW);
        canvas.clipPath(clip);
        ForestArtworkRenderer renderer = new ForestArtworkRenderer(1f);
        renderer.setSize(dimensions[0], dimensions[1]);
        renderer.setPalette(palette);
        renderer.draw(canvas);
        bitmaps.put(key, bitmap);
        renderCount++;
        return bitmap;
    }

    synchronized int renderCount() {
        return renderCount;
    }

    synchronized Bitmap dew(boolean done, DayPalette palette) {
        String key = "dew|" + done + '|' + palette.accent + '|' + palette.accentText + '|'
                + palette.dot;
        Bitmap cached = bitmaps.get(key);
        if (cached != null) return cached;
        Bitmap bitmap = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setStrokeWidth(1.5f);
        paint.setStyle(done ? Paint.Style.FILL : Paint.Style.STROKE);
        paint.setColor(done ? palette.accent : palette.dot);
        canvas.drawCircle(24, 24, 13, paint);
        if (done) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(palette.accentText);
            Path drop = new Path();
            drop.moveTo(24, 18.5f);
            drop.cubicTo(31, 22, 30, 28, 24, 30.5f);
            drop.cubicTo(18, 28, 17, 22, 24, 18.5f);
            canvas.drawPath(drop, paint);
        }
        bitmaps.put(key, bitmap);
        return bitmap;
    }

    synchronized Bitmap progress(boolean done, DayPalette palette) {
        String key = "progress|" + done + '|' + palette.accent + '|' + palette.dot;
        Bitmap cached = bitmaps.get(key);
        if (cached != null) return cached;
        Bitmap bitmap = Bitmap.createBitmap(20, 5, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(done ? palette.accent : UiStyle.alpha(palette.dot, .4f));
        canvas.drawRoundRect(0, 0, 20, 5, 3, 3, paint);
        bitmaps.put(key, bitmap);
        return bitmap;
    }

    synchronized Bitmap button(WidgetSizeClassifier.Size size, DayPalette palette) {
        int width = size == WidgetSizeClassifier.Size.SMALL ? 116
                : size == WidgetSizeClassifier.Size.WIDE ? 150 : 230;
        int height = size == WidgetSizeClassifier.Size.TALL ? 40 : 38;
        String key = "button|" + size + '|' + palette.accent;
        Bitmap cached = bitmaps.get(key);
        if (cached != null) return cached;
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(palette.accent);
        canvas.drawRoundRect(0, 0, width, height, height / 2f, height / 2f, paint);
        bitmaps.put(key, bitmap);
        return bitmap;
    }

    synchronized Bitmap leaf(int width, int height, boolean calendar, DayPalette palette) {
        int fill = calendar ? palette.calendar : palette.leaf2;
        int edge = calendar ? palette.calendarEdge : palette.leaf2Edge;
        String key = "leaf|" + width + '|' + height + '|' + calendar + '|' + fill + '|' + edge;
        Bitmap cached = bitmaps.get(key);
        if (cached != null) return cached;
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        RectF bounds = new RectF(.5f, .5f, width - .5f, height - .5f);
        float large = Math.min(56f, Math.min(width, height));
        float small = Math.min(8f, Math.min(width, height));
        float[] radii = calendar
                ? new float[]{small, small, large, large, small, small, large, large}
                : new float[]{large, large, small, small, large, large, small, small};
        Path path = new Path();
        path.addRoundRect(bounds, radii, Path.Direction.CW);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(fill);
        canvas.drawPath(path, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1f);
        paint.setColor(edge);
        canvas.drawPath(path, paint);
        bitmaps.put(key, bitmap);
        return bitmap;
    }

    synchronized Bitmap addButton(DayPalette palette) {
        String key = "add|" + palette.light;
        Bitmap cached = bitmaps.get(key);
        if (cached != null) return cached;
        Bitmap bitmap = Bitmap.createBitmap(30, 30, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(palette.light);
        canvas.drawCircle(15, 15, 15, paint);
        bitmaps.put(key, bitmap);
        return bitmap;
    }

    private static int[] dimensions(WidgetSizeClassifier.Size size) {
        switch (size) {
            case SMALL: return new int[]{160, 160};
            case WIDE: return new int[]{344, 160};
            case TALL: return new int[]{280, 344};
            case LARGE: return new int[]{344, 344};
            default: throw new IllegalArgumentException("Unknown widget size " + size);
        }
    }

    private static String key(WidgetSizeClassifier.Size size, DayPalette p) {
        return size.name() + '|' + p.background + '|' + p.tree + '|'
                + p.sunColor + '|' + Float.floatToIntBits(p.farAlpha) + '|'
                + Float.floatToIntBits(p.middleAlpha) + '|' + Float.floatToIntBits(p.frontAlpha)
                + '|' + Float.floatToIntBits(p.sunX) + '|' + Float.floatToIntBits(p.sunWidth)
                + '|' + Float.floatToIntBits(p.sunHeight);
    }
}

package de.thonktank.autosecretary;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.util.LruCache;

/** Bounded forest bitmap cache; every entry stays below a quarter MiB for Binder safety. */
final class WidgetForestCache {
    static final int MAX_BITMAP_BYTES = 256 * 256 * 4;
    private final LruCache<String, Bitmap> bitmaps = new LruCache<>(8);
    private int renderCount;

    synchronized Bitmap get(WidgetSizeClassifier.Size size, DayPalette palette) {
        String key = key(size, palette);
        Bitmap cached = bitmaps.get(key);
        if (cached != null) return cached;
        int[] dimensions = dimensions(size);
        Bitmap bitmap = Bitmap.createBitmap(dimensions[0], dimensions[1], Bitmap.Config.ARGB_8888);
        ForestArtworkRenderer renderer = new ForestArtworkRenderer(1f);
        renderer.setSize(dimensions[0], dimensions[1]);
        renderer.setPalette(palette);
        renderer.draw(new Canvas(bitmap));
        bitmaps.put(key, bitmap);
        renderCount++;
        return bitmap;
    }

    synchronized int renderCount() {
        return renderCount;
    }

    private static int[] dimensions(WidgetSizeClassifier.Size size) {
        switch (size) {
            case SMALL: return new int[]{160, 160};
            case WIDE: return new int[]{256, 128};
            case TALL: return new int[]{160, 256};
            case LARGE: return new int[]{256, 256};
            default: throw new IllegalArgumentException("Unknown widget size " + size);
        }
    }

    private static String key(WidgetSizeClassifier.Size size, DayPalette p) {
        return size.name() + '|' + p.background + '|' + p.tree + '|'
                + p.sunColor + '|' + Float.floatToIntBits(p.farAlpha) + '|'
                + Float.floatToIntBits(p.middleAlpha) + '|' + Float.floatToIntBits(p.frontAlpha)
                + '|' + Float.floatToIntBits(p.sunX) + '|' + Float.floatToIntBits(p.sunWidth);
    }
}

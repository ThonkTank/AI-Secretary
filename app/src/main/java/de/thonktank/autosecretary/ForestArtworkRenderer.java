package de.thonktank.autosecretary;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.Shader;

import androidx.core.graphics.PathParser;

/** Exact three-depth forest artwork shared by the activity and RemoteViews. */
final class ForestArtworkRenderer {
    private static final float VIEWPORT_WIDTH = 412f;
    private static final float VIEWPORT_HEIGHT = 892f;

    private final Paint sunPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint treePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint maskPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path[] far = paths(ForestPathData.FAR);
    private final Path[] middle = paths(ForestPathData.MIDDLE);
    private final Path[] front = paths(ForestPathData.FRONT);
    private DayPalette palette;
    private int width;
    private int height;
    private float sunBreathOffset;

    ForestArtworkRenderer(float ignoredDensity) {
        treePaint.setStyle(Paint.Style.FILL);
        maskPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
    }

    void setPalette(DayPalette palette) {
        this.palette = palette;
        rebuildSun();
    }

    void setSize(int width, int height) {
        if (this.width == width && this.height == height) return;
        this.width = width;
        this.height = height;
        rebuildSun();
        if (height > 0) {
            maskPaint.setShader(new LinearGradient(0, 0, 0, height,
                    new int[]{0x00ffffff, 0x00ffffff, 0xffffffff, 0xffffffff},
                    new float[]{0f, .055f, .105f, 1f}, Shader.TileMode.CLAMP));
        }
    }

    void setSunBreathOffset(float offsetPixels) {
        sunBreathOffset = offsetPixels;
    }

    void draw(Canvas canvas) {
        if (palette == null || width == 0 || height == 0) return;
        canvas.drawColor(palette.background);
        drawSun(canvas);

        int masked = canvas.saveLayer(0, 0, width, height, null);
        float scale = Math.max(width / VIEWPORT_WIDTH, height / VIEWPORT_HEIGHT);
        float dx = (width - VIEWPORT_WIDTH * scale) * .5f;
        float dy = (height - VIEWPORT_HEIGHT * scale) * .5f;
        int transformed = canvas.save();
        canvas.translate(dx, dy);
        canvas.scale(scale, scale);
        drawLayer(canvas, far, palette.farAlpha);
        drawLayer(canvas, middle, palette.middleAlpha);
        drawLayer(canvas, front, palette.frontAlpha);
        canvas.restoreToCount(transformed);
        canvas.drawRect(0, 0, width, height, maskPaint);
        canvas.restoreToCount(masked);
    }

    private void drawSun(Canvas canvas) {
        float radiusX = width * palette.sunWidth;
        float radiusY = height * palette.sunHeight;
        if (radiusX <= 0 || radiusY <= 0) return;
        int save = canvas.save();
        canvas.translate(width * palette.sunX / 100f, -height * .1f + sunBreathOffset);
        canvas.scale(1f, radiusY / radiusX);
        canvas.drawCircle(0, 0, radiusX, sunPaint);
        canvas.restoreToCount(save);
    }

    private void drawLayer(Canvas canvas, Path[] paths, float alpha) {
        treePaint.setColor(palette.tree);
        treePaint.setAlpha(Math.round(alpha * 255));
        for (Path path : paths) canvas.drawPath(path, treePaint);
    }

    private void rebuildSun() {
        if (palette == null || width == 0) return;
        sunPaint.setShader(new RadialGradient(0, 0, width * palette.sunWidth,
                palette.sunColor, 0x00000000, Shader.TileMode.CLAMP));
    }

    private static Path[] paths(String[] data) {
        Path[] result = new Path[data.length];
        for (int i = 0; i < data.length; i++) result[i] = PathParser.createPathFromPathData(data[i]);
        return result;
    }
}

package de.thonktank.autosecretary;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;

/** Shared allocation-free forest artwork used by the activity and RemoteViews. */
final class ForestArtworkRenderer {
    private final float density;
    private final Paint sunPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint treePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint veilPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path farTrunks = new Path();
    private final Path middleTrunks = new Path();
    private final Path frontTrunk = new Path();
    private final float[][] farBranches = new float[5][5];
    private final float[][] middleBranches = new float[2][5];
    private final float[][] frontBranches = new float[3][5];
    private DayPalette palette;
    private int width;
    private int height;

    ForestArtworkRenderer(float density) {
        this.density = density;
        treePaint.setStyle(Paint.Style.FILL);
        treePaint.setStrokeCap(Paint.Cap.SQUARE);
    }

    void setPalette(DayPalette palette) {
        this.palette = palette;
        rebuildShaders();
    }

    void setSize(int width, int height) {
        if (this.width == width && this.height == height) return;
        this.width = width;
        this.height = height;
        rebuildGeometry();
        rebuildShaders();
    }

    void draw(Canvas canvas) {
        if (palette == null || width == 0 || height == 0) return;
        canvas.drawColor(palette.background);
        canvas.drawRect(0, 0, width, height * .62f, sunPaint);
        drawLayer(canvas, farTrunks, farBranches, palette.farAlpha);
        drawLayer(canvas, middleTrunks, middleBranches, palette.middleAlpha);
        drawLayer(canvas, frontTrunk, frontBranches, palette.frontAlpha);
        canvas.drawRect(0, 0, width, height * .12f, veilPaint);
    }

    private void drawLayer(Canvas canvas, Path trunks, float[][] branches, float alpha) {
        treePaint.setColor(palette.tree);
        treePaint.setAlpha(Math.round(alpha * 255));
        treePaint.setStyle(Paint.Style.FILL);
        canvas.drawPath(trunks, treePaint);
        treePaint.setStyle(Paint.Style.STROKE);
        for (float[] branch : branches) {
            treePaint.setStrokeWidth(branch[4]);
            canvas.drawLine(branch[0], branch[1], branch[2], branch[3], treePaint);
        }
    }

    private void rebuildGeometry() {
        farTrunks.reset();
        middleTrunks.reset();
        frontTrunk.reset();
        float[] farXs = {.12f, .28f, .46f, .66f, .86f};
        for (int i = 0; i < farXs.length; i++) {
            float x = width * farXs[i];
            addTrunk(farTrunks, x, dp(3));
            branch(farBranches[i], x, height * (.38f + i * .045f),
                    x + (i % 2 == 0 ? dp(42) : -dp(42)),
                    height * (.31f + i * .04f), dp(3));
        }
        float[] middleXs = {.19f, .78f};
        for (int i = 0; i < middleXs.length; i++) {
            float x = width * middleXs[i];
            addTrunk(middleTrunks, x, dp(7));
            branch(middleBranches[i], x, height * (.38f + i * .045f),
                    x + (i % 2 == 0 ? dp(42) : -dp(42)),
                    height * (.31f + i * .04f), dp(5));
        }
        frontTrunk.moveTo(-dp(42), height);
        frontTrunk.lineTo(dp(62), height);
        frontTrunk.lineTo(dp(42), -dp(40));
        frontTrunk.lineTo(dp(8), -dp(40));
        frontTrunk.close();
        branch(frontBranches[0], dp(34), height * .39f, dp(98), height * .31f, dp(10));
        branch(frontBranches[1], dp(30), height * .60f, dp(92), height * .68f, dp(9));
        branch(frontBranches[2], dp(37), height * .29f, dp(80), height * .23f, dp(7));
    }

    private void addTrunk(Path path, float x, float half) {
        path.moveTo(x - half, height);
        path.lineTo(x + half, height);
        path.lineTo(x + half - dp(7), -dp(20));
        path.lineTo(x - half - dp(4), -dp(20));
        path.close();
    }

    private static void branch(float[] target, float x1, float y1, float x2, float y2,
                               float strokeWidth) {
        target[0] = x1;
        target[1] = y1;
        target[2] = x2;
        target[3] = y2;
        target[4] = strokeWidth;
    }

    private void rebuildShaders() {
        if (palette == null || width == 0 || height == 0) return;
        sunPaint.setShader(new RadialGradient(width * palette.sunX / 100f, -height * .1f,
                width * palette.sunWidth, palette.sunColor, 0x00000000, Shader.TileMode.CLAMP));
        veilPaint.setShader(new LinearGradient(0, 0, 0, height * .12f,
                palette.background & 0x00ffffff, palette.background, Shader.TileMode.CLAMP));
    }

    private float dp(float value) {
        return value * density;
    }
}

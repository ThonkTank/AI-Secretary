package de.thonktank.autosecretary;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/** Produces the review artifact; it is intentionally not a pixel-diff assertion. */
final class TaskEditorContactSheet {
    private static final int PHONE_WIDTH = 412;
    private static final int PHONE_HEIGHT = 892;
    private static final int LABEL_HEIGHT = 54;
    private static final int GAP = 20;
    private static final int TILE_WIDTH = PHONE_WIDTH * 2 + GAP;
    private static final int TILE_HEIGHT = LABEL_HEIGHT + PHONE_HEIGHT + GAP;

    private TaskEditorContactSheet() { }

    static void write(List<TaskEditorGoldenScenario> scenarios, List<Bitmap> androidRenderings)
            throws IOException {
        int columns = 2;
        int rows = (scenarios.size() + columns - 1) / columns;
        Bitmap sheet = Bitmap.createBitmap(TILE_WIDTH * columns + GAP * 3,
                TILE_HEIGHT * rows + GAP, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(sheet);
        canvas.drawColor(Color.rgb(238, 234, 222));
        Paint title = new Paint(Paint.ANTI_ALIAS_FLAG);
        title.setColor(Color.rgb(28, 38, 25)); title.setTextSize(18f); title.setFakeBoldText(true);
        Paint caption = new Paint(Paint.ANTI_ALIAS_FLAG);
        caption.setColor(Color.rgb(77, 88, 71)); caption.setTextSize(14f);

        for (int index = 0; index < scenarios.size(); index++) {
            TaskEditorGoldenScenario scenario = scenarios.get(index);
            int column = index % columns;
            int row = index / columns;
            int left = GAP + column * (TILE_WIDTH + GAP);
            int top = GAP + row * TILE_HEIGHT;
            canvas.drawText(scenario.id, left, top + 20, title);
            canvas.drawText("HTML-Referenz", left, top + 43, caption);
            canvas.drawText("Android-Rendering", left + PHONE_WIDTH + GAP,
                    top + 43, caption);
            Bitmap reference = reference(scenario.id);
            canvas.drawBitmap(reference, left, top + LABEL_HEIGHT, null);
            Bitmap scaled = Bitmap.createScaledBitmap(androidRenderings.get(index),
                    PHONE_WIDTH, PHONE_HEIGHT, true);
            canvas.drawBitmap(scaled, left + PHONE_WIDTH + GAP, top + LABEL_HEIGHT, null);
            reference.recycle(); scaled.recycle();
        }

        File report = new File("build/reports/goldens/task-editor/contact-sheet-variant-2a.png");
        File parent = report.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs())
            throw new IOException("Could not create " + parent);
        try (FileOutputStream output = new FileOutputStream(report)) {
            if (!sheet.compress(Bitmap.CompressFormat.PNG, 100, output))
                throw new IOException("Could not encode " + report);
        }
        File committed = new File("../docs/reference/task-editor/contact-sheet-variant-2a.png");
        if (!committed.exists())
            committed = new File("docs/reference/task-editor/contact-sheet-variant-2a.png");
        Bitmap reviewed = BitmapFactory.decodeFile(committed.getPath());
        try {
            if (reviewed == null || !sheet.sameAs(reviewed))
                throw new AssertionError("Committed task-editor contact sheet is stale: "
                        + committed);
        } finally {
            if (reviewed != null) reviewed.recycle();
            sheet.recycle();
        }
    }

    private static Bitmap reference(String id) throws IOException {
        String resource = "/reference/task-editor/variant-2a/" + id + ".png";
        try (InputStream input = TaskEditorContactSheet.class.getResourceAsStream(resource)) {
            if (input == null) throw new IOException("Missing reference " + resource);
            Bitmap bitmap = BitmapFactory.decodeStream(input);
            if (bitmap == null) throw new IOException("Invalid reference " + resource);
            return bitmap;
        }
    }
}

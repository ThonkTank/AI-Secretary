package de.thonktank.autosecretary;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

/** Review artifact for the night overview and four compact editor pages. */
final class TaskEditorAdaptiveContactSheet {
    private static final int TILE_WIDTH = 452;
    private static final int TILE_HEIGHT = 966;
    private static final int GAP = 20;

    private TaskEditorAdaptiveContactSheet() { }

    static void write(List<TaskEditorAdaptiveGoldenScenario> scenarios,
                      List<Bitmap> renderings) throws Exception {
        Bitmap sheet = Bitmap.createBitmap(TILE_WIDTH * 2 + GAP * 3,
                TILE_HEIGHT * 3 + GAP, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(sheet);
        canvas.drawColor(Color.rgb(238, 234, 222));
        Paint title = new Paint(Paint.ANTI_ALIAS_FLAG);
        title.setColor(Color.rgb(28, 38, 25));
        title.setTextSize(18f);
        title.setFakeBoldText(true);
        Paint caption = new Paint(Paint.ANTI_ALIAS_FLAG);
        caption.setColor(Color.rgb(77, 88, 71));
        caption.setTextSize(14f);
        for (int index = 0; index < scenarios.size(); index++) {
            TaskEditorAdaptiveGoldenScenario scenario = scenarios.get(index);
            int left = GAP + index % 2 * (TILE_WIDTH + GAP);
            int top = GAP + index / 2 * TILE_HEIGHT;
            canvas.drawText(scenario.id, left, top + 22, title);
            canvas.drawText(scenario.widthDp + "×" + scenario.heightDp + "dp · "
                    + scenario.fontScale + "× Schrift · " + scenario.paletteMode,
                    left, top + 46, caption);
            Bitmap scaled = Bitmap.createScaledBitmap(renderings.get(index),
                    scenario.widthDp, scenario.heightDp, true);
            canvas.drawBitmap(scaled, left, top + 62, null);
            scaled.recycle();
        }

        File report = new File("build/reports/goldens/task-editor-adaptive/"
                + "contact-sheet-adaptive-fidelity.png");
        File committed = new File("../docs/reference/task-editor/"
                + "contact-sheet-adaptive-fidelity.png");
        if (!committed.getParentFile().isDirectory()) committed = new File(
                "docs/reference/task-editor/contact-sheet-adaptive-fidelity.png");
        boolean update = "1".equals(System.getenv("UPDATE_TASK_EDITOR_ADAPTIVE_CONTACT"));
        Bitmap reviewed = committed.isFile()
                ? BitmapFactory.decodeFile(committed.getPath()) : null;
        boolean matches = reviewed != null && sheet.sameAs(reviewed);
        if (reviewed != null) reviewed.recycle();
        if (matches) {
            write(report, sheet);
            sheet.recycle();
            return;
        }
        if (update) {
            if (isCi()) throw new AssertionError("CI may not update the adaptive contact sheet");
            Bitmap prior = BitmapFactory.decodeFile(report.getPath());
            boolean reviewedPrior = prior != null && sheet.sameAs(prior);
            if (prior != null) prior.recycle();
            if (!reviewedPrior) throw new AssertionError("Run once without the update flag, "
                    + "inspect the adaptive contact sheet, then update it explicitly");
            write(committed, sheet);
            sheet.recycle();
            return;
        }
        write(report, sheet);
        sheet.recycle();
        throw new AssertionError("Missing or stale adaptive review contact sheet: " + committed);
    }

    private static boolean isCi() {
        return present("CI") || present("GITHUB_ACTIONS");
    }

    private static boolean present(String name) {
        String value = System.getenv(name);
        return value != null && !value.isEmpty() && !"false".equalsIgnoreCase(value);
    }

    private static void write(File file, Bitmap bitmap) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs())
            throw new IOException("Could not create " + parent);
        try (FileOutputStream output = new FileOutputStream(file)) {
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
                throw new IOException("Could not encode " + file);
        }
    }
}

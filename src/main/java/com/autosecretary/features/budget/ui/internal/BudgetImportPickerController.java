package com.autosecretary.features.budget.ui.internal;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.fragment.app.Fragment;

import java.io.IOException;
import java.io.InputStream;

public class BudgetImportPickerController {

    public interface Listener {
        void onImportPicked(String fileName, byte[] bytes, String mimeType);

        void onImportReadFailed();
    }

    private final Fragment fragment;
    private final Listener listener;
    private ActivityResultLauncher<String[]> csvPickerLauncher;

    public BudgetImportPickerController(Fragment fragment, Listener listener) {
        this.fragment = fragment;
        this.listener = listener;
    }

    // Must be called in Fragment.onCreate(), before the fragment reaches the STARTED state
    // (Android requires ActivityResultLauncher registration before that lifecycle point).
    public void register() {
        csvPickerLauncher = fragment.registerForActivityResult(
                new ActivityResultContracts.OpenDocument(),
                this::handlePickedUri
        );
    }

    public void launchPicker() {
        if (csvPickerLauncher == null) {
            return;
        }
        csvPickerLauncher.launch(new String[]{"text/csv", "text/plain", "application/pdf", "*/*"});
    }

    private void handlePickedUri(Uri uri) {
        if (uri == null) {
            return;
        }
        String fileName = getFileName(uri);
        String mimeType = fragment.requireContext().getContentResolver().getType(uri);
        try {
            byte[] bytes = readUriBytes(uri);
            listener.onImportPicked(fileName, bytes, mimeType);
        } catch (IOException e) {
            listener.onImportReadFailed();
        }
    }

    private String getFileName(Uri uri) {
        if ("content".equals(uri.getScheme())) {
            try (Cursor cursor = fragment.requireContext().getContentResolver()
                    .query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (nameIndex >= 0) {
                        String name = cursor.getString(nameIndex);
                        if (name != null) return name;
                    }
                }
            }
        }
        String segment = uri.getLastPathSegment();
        return segment != null ? segment : "import.csv";
    }

    private byte[] readUriBytes(Uri uri) throws IOException {
        ContentResolver cr = fragment.requireContext().getContentResolver();
        try (InputStream is = cr.openInputStream(uri)) {
            if (is == null) {
                throw new IOException("Dateistream konnte nicht geöffnet werden: " + uri);
            }
            return is.readAllBytes();
        }
    }
}

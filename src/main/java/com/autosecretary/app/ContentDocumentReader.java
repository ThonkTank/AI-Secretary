package com.autosecretary.app;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import java.io.IOException;
import java.io.InputStream;

/**
 * Android content-document seam for background document reads outside the UI layer.
 */
public class ContentDocumentReader {

    public record DocumentContents(
            String displayName,
            String mimeType,
            byte[] bytes) {
    }

    private final Context appContext;

    public ContentDocumentReader(Context appContext) {
        this.appContext = appContext.getApplicationContext();
    }

    public DocumentContents read(Uri uri) throws IOException {
        ContentResolver contentResolver = appContext.getContentResolver();
        return new DocumentContents(
                getFileName(contentResolver, uri),
                contentResolver.getType(uri),
                readUriBytes(contentResolver, uri)
        );
    }

    private static String getFileName(ContentResolver contentResolver, Uri uri) {
        if ("content".equals(uri.getScheme())) {
            try (Cursor cursor = contentResolver.query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (nameIndex >= 0) {
                        String name = cursor.getString(nameIndex);
                        if (name != null) {
                            return name;
                        }
                    }
                }
            }
        }
        String segment = uri.getLastPathSegment();
        return segment != null ? segment : "import.csv";
    }

    private static byte[] readUriBytes(ContentResolver contentResolver, Uri uri) throws IOException {
        try (InputStream inputStream = contentResolver.openInputStream(uri)) {
            if (inputStream == null) {
                throw new IOException("Dateistream konnte nicht geöffnet werden: " + uri);
            }
            return inputStream.readAllBytes();
        }
    }
}

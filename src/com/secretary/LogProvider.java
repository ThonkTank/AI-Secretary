package com.secretary.helloworld;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

/**
 * ContentProvider for exposing application logs to external tools (like Termux/Claude Code).
 *
 * Usage from Termux:
 *   content read --uri content://com.secretary.helloworld.logs/file
 *
 * This provider is exported without permissions since the app is for personal use only.
 * Logs are not sensitive and this enables easy debugging during development.
 */
public class LogProvider extends ContentProvider {
    private static final String TAG = "LogProvider";
    private static final String AUTHORITY = "com.secretary.helloworld.logs";
    private static final int CODE_LOGS_FILE = 1;

    private static final UriMatcher uriMatcher = new UriMatcher(UriMatcher.NO_MATCH);

    static {
        // content://com.secretary.helloworld.logs/file
        uriMatcher.addURI(AUTHORITY, "file", CODE_LOGS_FILE);
    }

    @Override
    public boolean onCreate() {
        Log.i(TAG, "LogProvider initialized");
        return true;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (uriMatcher.match(uri) != CODE_LOGS_FILE) {
            throw new FileNotFoundException("Unknown URI: " + uri);
        }

        if (!"r".equals(mode)) {
            throw new FileNotFoundException("Only read mode supported");
        }

        try {
            // Create temporary file with current logs
            File cacheDir = getContext().getCacheDir();
            File logFile = new File(cacheDir, "current_logs.txt");

            // Write all in-memory logs to temporary file
            AppLogger logger = AppLogger.getInstance(getContext());
            List<String> logs = logger.readLogs();

            FileWriter writer = new FileWriter(logFile);
            for (String line : logs) {
                writer.write(line);
                writer.write("\n");
            }
            writer.close();

            Log.i(TAG, "Logs exported: " + logs.size() + " lines");

            // Return file descriptor for reading
            return ParcelFileDescriptor.open(logFile, ParcelFileDescriptor.MODE_READ_ONLY);

        } catch (IOException e) {
            Log.e(TAG, "Failed to export logs", e);
            throw new FileNotFoundException("Could not create log file: " + e.getMessage());
        }
    }

    // Standard ContentProvider methods (not used, but required)

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                       String[] selectionArgs, String sortOrder) {
        throw new UnsupportedOperationException("Query not supported. Use openFile() instead.");
    }

    @Override
    public String getType(Uri uri) {
        if (uriMatcher.match(uri) == CODE_LOGS_FILE) {
            return "text/plain";
        }
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("Insert not supported");
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("Delete not supported");
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("Update not supported");
    }
}

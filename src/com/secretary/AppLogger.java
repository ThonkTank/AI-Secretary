package com.secretary.helloworld;

import android.content.Context;
import android.util.Log;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class AppLogger {
    private static final String TAG = "AppLogger";
    private static final int MAX_LOG_LINES = 500;
    private static AppLogger instance;
    private SimpleDateFormat dateFormat;
    private List<String> logLines;  // In-Memory Logs (HTTP server access via localhost:8080)

    private AppLogger(Context context) {
        dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
        logLines = new ArrayList<>();

        // Initiale Log-Nachricht
        info(TAG, "AppLogger initialized (IN-MEMORY ONLY)");
        info(TAG, "Logs accessible via HTTP server: curl http://localhost:8080/logs");
    }

    public static synchronized AppLogger getInstance(Context context) {
        if (instance == null) {
            instance = new AppLogger(context.getApplicationContext());
        }
        return instance;
    }

    public void info(String tag, String message) {
        writeLog("INFO", tag, message);
    }

    public void debug(String tag, String message) {
        writeLog("DEBUG", tag, message);
    }

    public void error(String tag, String message) {
        writeLog("ERROR", tag, message);
    }

    public void error(String tag, String message, Throwable throwable) {
        String stackTrace = Log.getStackTraceString(throwable);
        writeLog("ERROR", tag, message + "\n" + stackTrace);
    }

    private synchronized void writeLog(String level, String tag, String message) {
        String timestamp = dateFormat.format(new Date());
        String logEntry = String.format("[%s] [%s] [%s] %s", timestamp, level, tag, message);

        // In Logcat schreiben
        switch (level) {
            case "ERROR":
                Log.e(tag, message);
                break;
            case "DEBUG":
                Log.d(tag, message);
                break;
            default:
                Log.i(tag, message);
                break;
        }

        // In Memory speichern
        logLines.add(logEntry);

        // Automatische Bereinigung wenn zu viele Zeilen
        if (logLines.size() > MAX_LOG_LINES) {
            // Nur die letzten MAX_LOG_LINES behalten
            logLines = new ArrayList<>(logLines.subList(logLines.size() - MAX_LOG_LINES, logLines.size()));
        }
    }

    public synchronized List<String> readLogs() {
        // Direkt aus dem Speicher zurückgeben
        return new ArrayList<>(logLines);
    }

    public synchronized void clearLogs() {
        logLines.clear();
        info(TAG, "Logs manually cleared by user");
    }
}

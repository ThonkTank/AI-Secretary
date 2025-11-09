package com.secretary.helloworld;

import android.content.Context;
import android.os.Environment;
import android.util.Log;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class AppLogger {
    private static final String TAG = "AppLogger";
    private static final String LOG_DIR = "AISecretary";
    private static final String LOG_FILE_NAME = "app_logs.txt";
    private static final int MAX_LOG_LINES = 500;
    private static AppLogger instance;
    private File logFile;
    private SimpleDateFormat dateFormat;

    private AppLogger(Context context) {
        // Logs DIREKT im Downloads-Root schreiben (ohne Unterordner!)
        // KEINE Permissions erforderlich
        // Für Claude Code lesbar unter /sdcard/Download/
        // Funktioniert auf ALLEN Android-Versionen (auch 11+)
        // Unterordner in Downloads sind auf Android 11+ nicht lesbar für Termux!
        File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);

        // Dateiname: AISecretary_app_logs.txt (direkt im Downloads-Root)
        logFile = new File(downloadsDir, "AISecretary_app_logs.txt");
        dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);

        // Initiale Log-Nachricht
        info(TAG, "AppLogger initialized. Log file: " + logFile.getAbsolutePath());
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
        try {
            String timestamp = dateFormat.format(new Date());
            String logEntry = String.format("[%s] [%s] [%s] %s\n", timestamp, level, tag, message);

            // Auch in Android Logcat schreiben
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

            // In Datei schreiben
            BufferedWriter writer = new BufferedWriter(new FileWriter(logFile, true));
            writer.write(logEntry);
            writer.close();

            // Bereinigung nach jedem 50. Log
            if (Math.random() < 0.02) { // ~2% Chance
                cleanupLogs();
            }

        } catch (IOException e) {
            Log.e(TAG, "Error writing log", e);
        }
    }

    private void cleanupLogs() {
        try {
            if (!logFile.exists()) {
                return;
            }

            // Alle Logs lesen
            List<String> lines = new ArrayList<>();
            BufferedReader reader = new BufferedReader(new FileReader(logFile));
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
            reader.close();

            // Nur die letzten MAX_LOG_LINES behalten
            if (lines.size() > MAX_LOG_LINES) {
                List<String> recentLines = lines.subList(lines.size() - MAX_LOG_LINES, lines.size());

                // Datei neu schreiben
                BufferedWriter writer = new BufferedWriter(new FileWriter(logFile, false));
                writer.write("=== Logs automatisch bereinigt ===\n");
                for (String recentLine : recentLines) {
                    writer.write(recentLine + "\n");
                }
                writer.close();

                Log.i(TAG, "Logs cleaned up. Kept last " + MAX_LOG_LINES + " lines.");
            }

        } catch (IOException e) {
            Log.e(TAG, "Error cleaning up logs", e);
        }
    }

    public List<String> readLogs() {
        List<String> logs = new ArrayList<>();
        try {
            if (logFile.exists()) {
                BufferedReader reader = new BufferedReader(new FileReader(logFile));
                String line;
                while ((line = reader.readLine()) != null) {
                    logs.add(line);
                }
                reader.close();
            }
        } catch (IOException e) {
            Log.e(TAG, "Error reading logs", e);
            logs.add("Error reading logs: " + e.getMessage());
        }
        return logs;
    }

    public String getLogFilePath() {
        return logFile.getAbsolutePath();
    }

    public void clearLogs() {
        try {
            if (logFile.exists()) {
                logFile.delete();
            }
            info(TAG, "Logs manually cleared by user");
        } catch (Exception e) {
            Log.e(TAG, "Error clearing logs", e);
        }
    }
}

package com.autosecretary.app.settings;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.autosecretary.database.AppDatabase;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.Locale;

/**
 * Service for managing application backups and factory reset operations.
 *
 * <h2>Overview</h2>
 * This service handles:
 * <ul>
 *   <li>Listing existing backups (sorted by modification time, newest first)</li>
 *   <li>Creating manual backups (with automatic WAL checkpoint)</li>
 *   <li>Restoring from a backup (closes/resets the database, replaces it, reinitializes)</li>
 *   <li>Factory reset (creates a safety backup first, then clears the database)</li>
 * </ul>
 *
 * <h2>SQLite WAL (Write-Ahead Logging)</h2>
 * This app uses SQLite's WAL mode for robustness. WAL mode uses sidecar files:
 * <ul>
 *   <li><code>-wal</code> — Write-Ahead Log file (pending writes)</li>
 *   <li><code>-shm</code> — Shared Memory file (WAL index)</li>
 * </ul>
 * When backing up or restoring, these files must be synchronized or cleared to ensure the
 * database snapshot is complete and valid. See {@link #runCheckpoint()} and {@link #clearSidecarFiles()}.
 *
 * <h2>Thread Safety</h2>
 * This service is not thread-safe. All calls to this service should be serialized via an
 * {@link java.util.concurrent.ExecutorService} (typically the app's single-threaded executor).
 *
 * @see AppDatabase
 * @see SettingsController
 */
public class SettingsDataService {

    private static final String TAG = "SettingsDataService";
    private static final String BACKUP_PREFIX = "backup_";
    private static final String WAL_SUFFIX = "-wal";
    private static final String SHM_SUFFIX = "-shm";

    private final Context appContext;

    /**
     * Create a new SettingsDataService instance.
     *
     * @param context the application context (used for database and file access)
     */
    public SettingsDataService(@NonNull Context context) {
        this.appContext = context.getApplicationContext();
    }

    /**
     * List all existing database backups.
     *
     * Backups are stored in the app's internal files directory under "backups/" with the pattern
     * <code>backup_YYYYMMdd_HHmmss.db</code>.
     *
     * @return array of backup files, sorted by modification time (newest first), or empty array if none exist
     */
    public File[] listBackups() {
        File[] files = getBackupDirectory().listFiles((dir, name) ->
                name.startsWith(BACKUP_PREFIX) && name.endsWith(".db")
        );
        if (files == null) {
            return new File[0];
        }

        Arrays.sort(files, Comparator.comparingLong(File::lastModified).reversed());
        return files;
    }

    /**
     * Create a manual backup of the current database.
     *
     * The backup is created by:
     * <ol>
     *   <li>Running a SQLite WAL checkpoint (flushes pending writes)</li>
     *   <li>Copying the database file to the backup directory with a timestamped name</li>
     * </ol>
     *
     * On failure, logs an error and returns null. The database remains unchanged.
     *
     * @return the backup file created, or null if backup failed
     */
    public File createManualBackup() {
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ROOT).format(new Date());
        File target = new File(getBackupDirectory(), BACKUP_PREFIX + timestamp + ".db");

        try {
            runCheckpoint();
            copyDatabaseFile(getDatabaseFile(), target);
            return target;
        } catch (IOException ex) {
            Log.e(TAG, "Backup failed", ex);
            return null;
        }
    }

    /**
     * Restore the database from a backup.
     *
     * The restore process:
     * <ol>
     *   <li>Close the current database and reset the shared instance</li>
     *   <li>Clear WAL sidecar files ({@code -wal}, {@code -shm})</li>
     *   <li>Copy the backup file to the active database location</li>
     *   <li>Reinitialize the database by calling {@link AppDatabase#getInstance(Context)}</li>
     * </ol>
     *
     * <strong>Important:</strong> This is a destructive operation. All changes since the backup
     * was created will be lost. The caller should confirm with the user before calling this.
     *
     * On failure, logs an error. The database state is undefined; the caller should consider
     * recovering via app restart or another restore attempt.
     *
     * @param backupFile the backup file to restore from (must exist)
     * @return true if restore succeeded, false otherwise
     */
    public boolean restoreBackup(@NonNull File backupFile) {
        try {
            AppDatabase.closeAndReset();
            clearSidecarFiles();
            copyDatabaseFile(backupFile, getDatabaseFile());
            AppDatabase.getInstance(appContext);
            return true;
        } catch (IOException ex) {
            Log.e(TAG, "Restore failed", ex);
            return false;
        }
    }

    /**
     * Perform a factory reset, clearing all application data.
     *
     * As a safety measure, a backup is created before the reset. The process:
     * <ol>
     *   <li>Create a manual backup (for recovery if needed)</li>
     *   <li>Close the current database and reset the shared instance</li>
     *   <li>Delete the database file and WAL sidecar files</li>
     *   <li>Reinitialize the database with a fresh, empty schema</li>
     * </ol>
     *
     * <strong>Important:</strong> This is a destructive and irreversible operation. The caller should
     * confirm with the user and explain the consequences before calling this.
     *
     * @return true if reset succeeded, false if backup creation failed or reset encountered an error
     */
    public boolean factoryReset() {
        try {
            File backup = createManualBackup();
            if (backup == null) {
                return false;
            }
            AppDatabase.closeAndReset();
            appContext.deleteDatabase(AppDatabase.DB_NAME);
            clearSidecarFiles();
            AppDatabase.getInstance(appContext);
            return true;
        } catch (RuntimeException ex) {
            Log.e(TAG, "Factory reset failed", ex);
            return false;
        }
    }

    /**
     * Run a SQLite WAL checkpoint to flush all pending writes to the main database file.
     *
     * <h3>Why is this needed?</h3>
     * SQLite's WAL (Write-Ahead Logging) mode uses two sidecar files:
     * <ul>
     *   <li><code>.db-wal</code> — contains uncommitted or unread transactions</li>
     *   <li><code>.db-shm</code> — shared memory index for the WAL</li>
     * </ul>
     * Without a checkpoint, the main database file may be stale, and the WAL sidecar files
     * are essential. A backup of just the main file would be incomplete.
     *
     * <h3>How does PRAGMA wal_checkpoint(FULL) work?</h3>
     * It synchronously writes all pending transactions from the WAL into the main database file,
     * then truncates the WAL. After a successful FULL checkpoint:
     * <ul>
     *   <li>The main .db file is current and complete</li>
     *   <li>The .db-wal file is empty (or removed)</li>
     *   <li>The .db-shm file may be removed</li>
     * </ul>
     *
     * <h3>Reference</h3>
     * See: <a href="https://www.sqlite.org/wal.html#ckpt">SQLite WAL Checkpoints</a>
     *
     * @see <a href="https://www.sqlite.org/pragma.html#pragma_wal_checkpoint">PRAGMA wal_checkpoint documentation</a>
     */
    private void runCheckpoint() {
        AppDatabase database = AppDatabase.getInstance(appContext);
        database.getOpenHelper().getWritableDatabase().execSQL("PRAGMA wal_checkpoint(FULL)");
    }

    private File getBackupDirectory() {
        File backupDir = new File(appContext.getFilesDir(), "backups");
        ensureDirectoryExists(backupDir);
        return backupDir;
    }

    /**
     * Ensure a directory exists, creating it if necessary.
     *
     * The return value of {@link File#mkdirs()} is ignored because we only care that the
     * directory exists after this call. If mkdirs() returns false, it might mean:
     * <ul>
     *   <li>The directory already exists (not a failure)</li>
     *   <li>A parent path is a file, not a directory (will fail later during file access)</li>
     *   <li>Permissions denied (will fail later during file access)</li>
     * </ul>
     * Deferring error handling to the actual file operation (copy, write, etc.) is simpler and
     * provides better error context to the caller.
     */
    private void ensureDirectoryExists(File dir) {
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
    }

    private File getDatabaseFile() {
        return appContext.getDatabasePath(AppDatabase.DB_NAME);
    }

    /**
     * Clear SQLite WAL sidecar files for the current database.
     *
     * In WAL mode, SQLite maintains two sidecar files:
     * <ul>
     *   <li><code>.db-wal</code> — Write-Ahead Log (pending transactions)</li>
     *   <li><code>.db-shm</code> — Shared Memory (WAL index)</li>
     * </ul>
     *
     * These files must be deleted when:
     * <ul>
     *   <li>Restoring a backup (sidecar files from the backup don't exist; fresh ones will be created)</li>
     *   <li>Performing a factory reset (to ensure a clean slate)</li>
     * </ul>
     *
     * Failure to delete these files can lead to:
     * <ul>
     *   <li>SQLite trying to apply old WAL entries to a new/restored database</li>
     *   <li>Database corruption or unexpected state</li>
     * </ul>
     *
     * Errors during deletion are silently ignored because:
     * <ul>
     *   <li>These files may not exist (fresh restore or reset)</li>
     *   <li>Permissions or filesystem issues will be caught when the database is next accessed</li>
     * </ul>
     */
    private void clearSidecarFiles() {
        File database = getDatabaseFile();
        deleteSilently(new File(database.getAbsolutePath() + WAL_SUFFIX));
        deleteSilently(new File(database.getAbsolutePath() + SHM_SUFFIX));
    }

    /**
     * Delete a file, silently ignoring errors.
     *
     * The return value of {@link File#delete()} is ignored because:
     * <ul>
     *   <li>The file may not exist (not a failure)</li>
     *   <li>Permissions errors will be caught later during actual database operations</li>
     * </ul>
     * This is safe because any real filesystem problems will surface when the database is next accessed.
     */
    private void deleteSilently(File file) {
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }

    /**
     * Copy a database file from source to destination using efficient kernel-level I/O.
     *
     * <h3>Why FileChannel.transferFrom()?</h3>
     * This method uses {@link FileChannel#transferFrom(java.nio.channels.ReadableByteChannel, long, long)}
     * instead of a simple read/write loop because:
     * <ul>
     *   <li><strong>Efficiency:</strong> {@code transferFrom()} leverages kernel-level I/O (sendfile on Linux, etc.),
     *       avoiding repeated context switches and data copies between user space and kernel space</li>
     *   <li><strong>Correctness for databases:</strong> For large database files, a single atomic system call
     *       reduces the risk of partial copies or interruption</li>
     *   <li><strong>Performance:</strong> Typically 2-10x faster than byte-by-byte copying for multi-megabyte files</li>
     * </ul>
     *
     * <h3>Why not File.copy()? (File API)</h3>
     * Android's File API (Java 7+) does not expose {@code Files.copy()}, which is part of {@code java.nio.file}.
     * The NIO approach is equivalent and portable across platforms.
     *
     * @param source      the source database file (must exist and be readable)
     * @param destination the destination path (parent directory is created if needed)
     * @throws IOException if I/O fails (source not found, permission denied, disk full, etc.)
     */
    private void copyDatabaseFile(@NonNull File source, @NonNull File destination) throws IOException {
        File parent = destination.getParentFile();
        if (parent != null) {
            ensureDirectoryExists(parent);
        }

        try (FileInputStream inputStream = new FileInputStream(source);
             FileOutputStream outputStream = new FileOutputStream(destination);
             FileChannel sourceChannel = inputStream.getChannel();
             FileChannel destinationChannel = outputStream.getChannel()) {
            destinationChannel.transferFrom(sourceChannel, 0, sourceChannel.size());
        }
    }
}

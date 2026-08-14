package com.autosecretary.platform.model;

import android.app.DownloadManager;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.os.SystemClock;

import com.google.mediapipe.tasks.genai.llminference.LlmInference;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/** Owns the resumable, verified local model lifecycle; it performs no thread creation. */
public final class LocalModelManager {
    private static final String MANIFEST_ASSET = "model-manifest.json";
    private static final String PREFERENCES = "validated_local_model";
    private static final String VALIDATED_FINGERPRINT = "fingerprint";
    private static final String VALIDATED_SHA256 = "manifest_sha256";
    private static final String PENDING_DOWNLOAD_ID = "pending_download_id";
    private static final String PENDING_MODEL_ID = "pending_model_id";
    private static final long MAX_MODEL_BYTES = 800L * 1024L * 1024L;
    private static final long DOWNLOAD_TIMEOUT_MS = TimeUnit.HOURS.toMillis(2);
    public static final String VALIDATION_PROMPT = """
            Output exactly one JSON object and no other text.
            Example: {"status":"ok"}
            Now output the same object.
            """;

    record Manifest(
            int schemaVersion,
            String modelId,
            String revision,
            String url,
            String fileName,
            long sizeBytes,
            String sha256) {
        Manifest {
            if (schemaVersion != 1) throw new IllegalArgumentException("Unbekanntes Modellmanifest");
            if (modelId == null || modelId.isBlank() || revision == null || revision.isBlank()) {
                throw new IllegalArgumentException("Modellidentität fehlt");
            }
            Uri source = Uri.parse(url);
            if (!"https".equals(source.getScheme()) || source.getHost() == null) {
                throw new IllegalArgumentException("Modell-URL ist nicht sicher");
            }
            if (!"autosecretary-model.task".equals(fileName)) {
                throw new IllegalArgumentException("Modell-Dateiname ist unbekannt");
            }
            if (sizeBytes < 1 || sizeBytes > MAX_MODEL_BYTES) {
                throw new IllegalArgumentException("Modellgröße ist ungültig");
            }
            if (sha256 == null || !sha256.matches("[0-9a-fA-F]{64}")) {
                throw new IllegalArgumentException("Modell-Hash ist ungültig");
            }
            sha256 = sha256.toLowerCase(Locale.ROOT);
        }

        static Manifest parse(JSONObject source) throws Exception {
            return new Manifest(source.getInt("schemaVersion"), source.getString("modelId"),
                    source.getString("revision"), source.getString("url"),
                    source.getString("fileName"), source.getLong("sizeBytes"),
                    source.getString("sha256"));
        }
    }

    private final Context context;
    private final Manifest manifest;

    public LocalModelManager(Context context) {
        this.context = context.getApplicationContext();
        this.manifest = readManifest(this.context);
    }

    public boolean hasModel() {
        File model = file();
        var preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
        return model.isFile() && model.length() == manifest.sizeBytes()
                && manifest.sha256().equals(preferences.getString(VALIDATED_SHA256, ""))
                && fingerprint(model).equals(
                        preferences.getString(VALIDATED_FINGERPRINT, ""));
    }

    public File file() {
        return new File(context.getFilesDir(), manifest.fileName());
    }

    /** Reuses a legacy installed model or resumes the one persisted system download. */
    public void install() throws Exception {
        if (hasModel()) return;
        File existing = file();
        if (existing.isFile() && existing.length() == manifest.sizeBytes()
                && sha256(existing).equals(manifest.sha256())) {
            validate(existing);
            requireNotInterrupted();
            markValidated(existing);
            return;
        }

        File downloaded = downloadTarget();
        long downloadId = pendingDownloadId();
        if (downloadId < 1 || !manifest.modelId().equals(pendingModelId())) {
            clearPendingDownload(false);
            if (downloaded.exists() && !downloaded.delete()) {
                throw new IllegalStateException("Alte Modelldatei konnte nicht entfernt werden");
            }
            downloadId = enqueue(downloaded);
            rememberDownload(downloadId);
        }

        try {
            await(downloadId, downloaded);
            requireNotInterrupted();
            if (downloaded.length() != manifest.sizeBytes()) {
                throw new SecurityException("Heruntergeladenes Modell hat eine unerwartete Größe");
            }
            if (!sha256(downloaded).equals(manifest.sha256())) {
                throw new SecurityException("Heruntergeladenes Modell ist beschädigt");
            }
            File temporary = new File(context.getFilesDir(), manifest.fileName() + ".partial");
            copyVerified(downloaded, temporary);
            validate(temporary);
            requireNotInterrupted();
            replace(temporary, existing);
            markValidated(existing);
            clearPendingDownload(true);
        } catch (InterruptedException cancelled) {
            clearPendingDownload(true);
            Thread.currentThread().interrupt();
            throw cancelled;
        } catch (Exception error) {
            clearPendingDownload(true);
            throw error;
        }
    }

    private long enqueue(File destination) {
        DownloadManager manager = manager();
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(manifest.url()))
                .setTitle("Auto Secretary · Lokale KI")
                .setDescription("Gemma 3 270M wird einmalig heruntergeladen")
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(false)
                .setNotificationVisibility(
                        DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationUri(Uri.fromFile(destination));
        request.addRequestHeader("User-Agent", "AutoSecretary model downloader");
        return manager.enqueue(request);
    }

    private void await(long downloadId, File destination) throws Exception {
        long deadline = SystemClock.elapsedRealtime() + DOWNLOAD_TIMEOUT_MS;
        while (true) {
            DownloadState state = query(manager(), downloadId);
            if (state.status() == DownloadManager.STATUS_SUCCESSFUL) {
                if (!destination.isFile()) {
                    throw new IllegalStateException("Systemdownload meldet keine Modelldatei");
                }
                return;
            }
            if (state.status() == DownloadManager.STATUS_FAILED || state.status() == 0) {
                throw new IllegalStateException(
                        "Modelldownload ist fehlgeschlagen: " + state.reason());
            }
            requireNotInterrupted();
            if (SystemClock.elapsedRealtime() >= deadline) {
                throw new IllegalStateException("Modelldownload hat das Zeitlimit überschritten");
            }
            Thread.sleep(1_000);
        }
    }

    private DownloadManager manager() {
        DownloadManager manager = context.getSystemService(DownloadManager.class);
        if (manager == null) throw new IllegalStateException("DownloadManager ist nicht verfügbar");
        return manager;
    }

    private static DownloadState query(DownloadManager manager, long id) {
        try (Cursor cursor = manager.query(new DownloadManager.Query().setFilterById(id))) {
            if (cursor == null || !cursor.moveToFirst()) return new DownloadState(0, 0);
            return new DownloadState(
                    cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)),
                    cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)));
        }
    }

    private File downloadTarget() {
        File external = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (external == null) throw new IllegalStateException("Modellverzeichnis fehlt");
        File directory = new File(external, "models");
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IllegalStateException("Modellverzeichnis konnte nicht angelegt werden");
        }
        return new File(directory, manifest.modelId() + ".download");
    }

    private long pendingDownloadId() {
        return context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .getLong(PENDING_DOWNLOAD_ID, 0);
    }

    private String pendingModelId() {
        return context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .getString(PENDING_MODEL_ID, "");
    }

    private void rememberDownload(long id) {
        if (!context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit()
                .putLong(PENDING_DOWNLOAD_ID, id)
                .putString(PENDING_MODEL_ID, manifest.modelId()).commit()) {
            manager().remove(id);
            throw new IllegalStateException("Modelldownload konnte nicht gespeichert werden");
        }
    }

    private void clearPendingDownload(boolean removeFile) {
        long id = pendingDownloadId();
        if (id > 0) manager().remove(id);
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit()
                .remove(PENDING_DOWNLOAD_ID).remove(PENDING_MODEL_ID).commit();
        if (removeFile) downloadTarget().delete();
    }

    private void validate(File candidate) throws Exception {
        LlmInference.LlmInferenceOptions options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(candidate.getAbsolutePath()).setMaxTokens(256).setMaxTopK(20).build();
        try (LlmInference inference = LlmInference.createFromOptions(context, options)) {
            String response = inference.generateResponse(VALIDATION_PROMPT);
            if (response == null || response.isBlank()) {
                throw new IllegalArgumentException("Modell liefert keine Ausgabe");
            }
        }
    }

    private void copyVerified(File source, File target) throws Exception {
        target.delete();
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (FileInputStream input = new FileInputStream(source);
             FileOutputStream output = new FileOutputStream(target)) {
            byte[] buffer = new byte[1024 * 1024];
            long total = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                requireNotInterrupted();
                total += read;
                if (total > MAX_MODEL_BYTES) throw new SecurityException("Modell ist zu groß");
                output.write(buffer, 0, read);
                digest.update(buffer, 0, read);
            }
            output.getFD().sync();
        }
        if (!manifest.sha256().equals(hex(digest.digest()))) {
            target.delete();
            throw new SecurityException("Kopiertes Modell ist beschädigt");
        }
    }

    private void markValidated(File model) {
        if (!context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit()
                .putString(VALIDATED_FINGERPRINT, fingerprint(model))
                .putString(VALIDATED_SHA256, manifest.sha256()).commit()) {
            throw new IllegalStateException("Modellvalidierung konnte nicht gespeichert werden");
        }
    }

    private static Manifest readManifest(Context context) {
        try (InputStream input = context.getAssets().open(MANIFEST_ASSET);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int total = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > 64 * 1024) {
                    throw new IllegalArgumentException("Modellmanifest ist zu groß");
                }
                output.write(buffer, 0, read);
            }
            return Manifest.parse(new JSONObject(new String(output.toByteArray(),
                    java.nio.charset.StandardCharsets.UTF_8)));
        } catch (Exception error) {
            throw new IllegalStateException("Modellmanifest ist ungültig", error);
        }
    }

    private static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (FileInputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[1024 * 1024];
            int read;
            while ((read = input.read(buffer)) >= 0) digest.update(buffer, 0, read);
        }
        return hex(digest.digest());
    }

    private static void requireNotInterrupted() throws InterruptedException {
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedException("Modelloperation wurde abgebrochen");
        }
    }

    private static String fingerprint(File model) {
        return model.length() + ":" + model.lastModified();
    }

    private static void replace(File temporary, File target) {
        try {
            Files.move(temporary.toPath(), target.toPath(),
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException error) {
            temporary.delete();
            throw new IllegalStateException("Dateisystem unterstützt keinen atomaren Modellwechsel", error);
        } catch (Exception error) {
            temporary.delete();
            throw new IllegalStateException("Modell konnte nicht atomar gespeichert werden", error);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) result.append(String.format(Locale.ROOT, "%02x", value));
        return result.toString();
    }

    private record DownloadState(int status, int reason) { }
}

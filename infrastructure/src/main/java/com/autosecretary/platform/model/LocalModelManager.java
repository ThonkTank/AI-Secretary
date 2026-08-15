package com.autosecretary.platform.model;

import android.app.DownloadManager;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;

import com.google.mediapipe.tasks.genai.llminference.LlmInference;
import com.autosecretary.application.model.ModelDownloadProgress;
import com.autosecretary.application.model.ModelDownloadTicket;
import com.autosecretary.application.model.ModelRepository;
import com.autosecretary.application.model.ModelStatus;

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

/** Owns the resumable, verified local model lifecycle; it performs no thread creation. */
public final class LocalModelManager implements ModelRepository {
    private static final String MANIFEST_ASSET = "model-manifest.json";
    private static final String PREFERENCES = "validated_local_model";
    private static final String VALIDATED_FINGERPRINT = "fingerprint";
    private static final String VALIDATED_SHA256 = "manifest_sha256";
    private static final String PENDING_DOWNLOAD_ID = "pending_download_id";
    private static final String PENDING_MODEL_ID = "pending_model_id";
    private static final String PENDING_REVISION = "pending_revision";
    private static final long MAX_MODEL_BYTES = 800L * 1024L * 1024L;
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

    @FunctionalInterface
    interface ModelValidator {
        void validate(File candidate) throws Exception;
    }

    interface DownloadAccess {
        long enqueue(String url, File destination);
        ModelDownloadProgress query(long id);
        void remove(long id);
    }

    private final Context context;
    private final Manifest manifest;
    private final ModelValidator validator;
    private final DownloadAccess downloads;

    public LocalModelManager(Context context) {
        this.context = context.getApplicationContext();
        this.manifest = readManifest(this.context);
        this.validator = candidate -> validateWithMediaPipe(this.context, candidate);
        this.downloads = new AndroidDownloadAccess(this.context);
        reconcileStorage();
    }

    LocalModelManager(
            Context context,
            Manifest manifest,
            ModelValidator validator,
            DownloadAccess downloads) {
        this.context = context.getApplicationContext();
        this.manifest = manifest;
        this.validator = validator;
        this.downloads = downloads;
        reconcileStorage();
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

    @Override public ModelStatus status() {
        if (hasModel()) return new ModelStatus.Ready(file().toPath(), manifest.sizeBytes());
        long id = pendingDownloadId();
        if (id > 0 && manifest.modelId().equals(pendingModelId())
                && manifest.revision().equals(pendingRevision())) {
            ModelDownloadTicket ticket = ticket(id);
            ModelDownloadProgress progress = query(ticket);
            return progress instanceof ModelDownloadProgress.Failed failed
                    ? new ModelStatus.Failed(failed.detail(), manifest.sizeBytes())
                    : new ModelStatus.Downloading(ticket, progress, manifest.sizeBytes());
        }
        return new ModelStatus.Missing(manifest.sizeBytes());
    }

    @Override public ModelDownloadTicket enqueue() {
        try {
            File existing = file();
            if (hasModel() || existing.isFile() && existing.length() == manifest.sizeBytes()
                    && sha256(existing).equals(manifest.sha256())) {
                return ticket(0);
            }
            long current = pendingDownloadId();
            if (current > 0 && manifest.modelId().equals(pendingModelId())
                    && manifest.revision().equals(pendingRevision())) {
                ModelDownloadTicket ticket = ticket(current);
                if (!(query(ticket) instanceof ModelDownloadProgress.Failed)) return ticket;
            }
            clearPendingDownload(true);
            File downloaded = downloadTarget();
            if (downloaded.exists() && !downloaded.delete()) {
                throw new IllegalStateException("Alte Modelldatei konnte nicht entfernt werden");
            }
            long id = enqueueDownload(downloaded);
            rememberDownload(id);
            return ticket(id);
        } catch (RuntimeException error) {
            throw error;
        } catch (Exception error) {
            throw new IllegalStateException("Modelldownload konnte nicht gestartet werden", error);
        }
    }

    @Override public ModelDownloadProgress query(ModelDownloadTicket ticket) {
        if (!matches(ticket)) {
            return new ModelDownloadProgress.Failed(
                    "Modelldownload ist verschwunden oder gehört zu einer anderen Revision", true);
        }
        if (ticket.id() == 0) return new ModelDownloadProgress.Complete();
        return downloads.query(ticket.id());
    }

    @Override public java.nio.file.Path verifyAndActivate(ModelDownloadTicket ticket) {
        if (!matches(ticket)) throw new IllegalStateException("Modelldownload-Ticket ist ungültig");
        File active = file();
        File candidate = ticket.id() == 0 ? active : downloadTarget();
        File temporary = new File(context.getFilesDir(), manifest.fileName() + ".partial");
        try {
            if (!(query(ticket) instanceof ModelDownloadProgress.Complete)) {
                throw new IllegalStateException("Modelldownload ist noch nicht vollständig");
            }
            if (!candidate.isFile() || candidate.length() != manifest.sizeBytes()) {
                throw new SecurityException("Heruntergeladenes Modell hat eine unerwartete Größe");
            }
            if (!sha256(candidate).equals(manifest.sha256())) {
                throw new SecurityException("Heruntergeladenes Modell ist beschädigt");
            }
            if (ticket.id() == 0) {
                if (hasModel()) return active.toPath();
                validate(active);
                markValidated(active);
                return active.toPath();
            }
            copyVerified(candidate, temporary);
            validate(temporary);
            replace(temporary, active);
            markValidated(active);
            clearPendingDownload(true);
            cleanupObsolete(active);
            return active.toPath();
        } catch (RuntimeException error) {
            temporary.delete();
            if (ticket.id() > 0) clearPendingDownload(true);
            throw error;
        } catch (Exception error) {
            temporary.delete();
            if (ticket.id() > 0) clearPendingDownload(true);
            throw new IllegalStateException("Modell konnte nicht aktiviert werden", error);
        }
    }

    @Override public void cancel(ModelDownloadTicket ticket) {
        if (ticket.id() > 0 && matches(ticket)) clearPendingDownload(true);
        new File(context.getFilesDir(), manifest.fileName() + ".partial").delete();
    }

    /** Reuses a legacy installed model or resumes the one persisted system download. */
    public void install() throws Exception {
        if (hasModel()) return;
        ModelDownloadTicket ticket = enqueue();
        while (!(query(ticket) instanceof ModelDownloadProgress.Complete)) {
            if (query(ticket) instanceof ModelDownloadProgress.Failed failed) {
                throw new IllegalStateException(failed.detail());
            }
            requireNotInterrupted();
            Thread.sleep(1_000);
        }
        verifyAndActivate(ticket);
    }

    private long enqueueDownload(File destination) {
        return downloads.enqueue(manifest.url(), destination);
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

    private String pendingRevision() {
        return context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .getString(PENDING_REVISION, "");
    }

    private void rememberDownload(long id) {
        if (!context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit()
                .putLong(PENDING_DOWNLOAD_ID, id)
                .putString(PENDING_MODEL_ID, manifest.modelId())
                .putString(PENDING_REVISION, manifest.revision()).commit()) {
            downloads.remove(id);
            throw new IllegalStateException("Modelldownload konnte nicht gespeichert werden");
        }
    }

    private void clearPendingDownload(boolean removeFile) {
        long id = pendingDownloadId();
        if (id > 0) downloads.remove(id);
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit()
                .remove(PENDING_DOWNLOAD_ID).remove(PENDING_MODEL_ID)
                .remove(PENDING_REVISION).commit();
        if (removeFile) downloadTarget().delete();
    }

    private void validate(File candidate) throws Exception {
        validator.validate(candidate);
    }

    private static void validateWithMediaPipe(Context context, File candidate) throws Exception {
        LlmInference.LlmInferenceOptions options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(candidate.getAbsolutePath())
                .setPreferredBackend(LlmInference.Backend.CPU)
                .setMaxTokens(256).setMaxTopK(20).build();
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

    private ModelDownloadTicket ticket(long id) {
        return new ModelDownloadTicket(id, manifest.modelId(), manifest.revision());
    }

    private boolean matches(ModelDownloadTicket ticket) {
        if (!manifest.modelId().equals(ticket.modelId())
                || !manifest.revision().equals(ticket.revision())) return false;
        return ticket.id() == 0 || ticket.id() == pendingDownloadId()
                && manifest.modelId().equals(pendingModelId())
                && manifest.revision().equals(pendingRevision());
    }

    private void cleanupObsolete(File active) {
        File[] files = context.getFilesDir().listFiles((directory, name) ->
                name.endsWith(".task") || name.endsWith(".partial"));
        if (files == null) return;
        for (File candidate : files) if (!candidate.equals(active)) candidate.delete();
    }

    private void reconcileStorage() {
        new File(context.getFilesDir(), manifest.fileName() + ".partial").delete();
        cleanupObsolete(file());
        long pending = pendingDownloadId();
        boolean current = pending > 0 && manifest.modelId().equals(pendingModelId())
                && manifest.revision().equals(pendingRevision());
        if (!current) {
            if (pending > 0) {
                try { downloads.remove(pending); }
                catch (RuntimeException ignored) { }
            }
            context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit()
                    .remove(PENDING_DOWNLOAD_ID).remove(PENDING_MODEL_ID)
                    .remove(PENDING_REVISION).commit();
            File external = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
            File directory = external == null ? null : new File(external, "models");
            File stale = directory == null ? null
                    : new File(directory, manifest.modelId() + ".download");
            if (stale != null) stale.delete();
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) result.append(String.format(Locale.ROOT, "%02x", value));
        return result.toString();
    }

    private static final class AndroidDownloadAccess implements DownloadAccess {
        private final Context context;

        AndroidDownloadAccess(Context context) { this.context = context; }

        @Override public long enqueue(String url, File destination) {
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url))
                    .setTitle("Auto Secretary · Lokale KI")
                    .setDescription("Gemma 3 270M wird einmalig heruntergeladen")
                    .setAllowedOverMetered(true)
                    .setAllowedOverRoaming(false)
                    .setNotificationVisibility(
                            DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setDestinationUri(Uri.fromFile(destination));
            request.addRequestHeader("User-Agent", "AutoSecretary model downloader");
            return manager().enqueue(request);
        }

        @Override public ModelDownloadProgress query(long id) {
            try (Cursor cursor = manager().query(
                    new DownloadManager.Query().setFilterById(id))) {
                if (cursor == null || !cursor.moveToFirst()) {
                    return new ModelDownloadProgress.Failed(
                            "Modelldownload ist verschwunden", true);
                }
                int status = cursor.getInt(cursor.getColumnIndexOrThrow(
                        DownloadManager.COLUMN_STATUS));
                long downloaded = Math.max(0, cursor.getLong(cursor.getColumnIndexOrThrow(
                        DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)));
                long total = cursor.getLong(cursor.getColumnIndexOrThrow(
                        DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
                return switch (status) {
                    case DownloadManager.STATUS_PENDING, DownloadManager.STATUS_PAUSED ->
                            new ModelDownloadProgress.Pending();
                    case DownloadManager.STATUS_RUNNING ->
                            new ModelDownloadProgress.Running(downloaded, total);
                    case DownloadManager.STATUS_SUCCESSFUL ->
                            new ModelDownloadProgress.Complete();
                    case DownloadManager.STATUS_FAILED -> new ModelDownloadProgress.Failed(
                            "Modelldownload ist fehlgeschlagen: " + cursor.getInt(
                                    cursor.getColumnIndexOrThrow(
                                            DownloadManager.COLUMN_REASON)), true);
                    default -> new ModelDownloadProgress.Failed(
                            "Unbekannter Modelldownload-Zustand: " + status, true);
                };
            }
        }

        @Override public void remove(long id) { manager().remove(id); }

        private DownloadManager manager() {
            DownloadManager manager = context.getSystemService(DownloadManager.class);
            if (manager == null) {
                throw new IllegalStateException("DownloadManager ist nicht verfügbar");
            }
            return manager;
        }
    }

}

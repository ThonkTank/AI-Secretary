package com.autosecretary.ai;

import android.content.Context;

import com.google.mediapipe.tasks.genai.llminference.LlmInference;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.Locale;

/** Owns model bytes, integrity checks and safe replacement; it performs no threading. */
final class LocalModelManager {
    private static final String MODEL_FILE = "autosecretary-model.task";
    private static final String PREFERENCES = "validated_local_model";
    private static final String VALIDATED_FINGERPRINT = "fingerprint";
    private static final String BUNDLED_MODEL_ASSET =
            "models/autosecretary-gemma3-270m-it-q8.task";
    private static final String BUNDLED_MODEL_SHA256 =
            "0f7147f1c22eaf758b819bbf7841793e4c90096c9352cde7fbe5c631f2265ef5";
    private static final long MAX_MODEL_BYTES = 800L * 1024L * 1024L;
    static final String VALIDATION_PROMPT = """
            Output exactly one JSON object and no other text.
            Example: {"status":"ok"}
            Now output the same object.
            """;

    private final Context context;

    LocalModelManager(Context context) {
        this.context = context.getApplicationContext();
    }

    boolean hasModel() {
        File model = file();
        return model.isFile() && model.length() > 0
                && fingerprint(model).equals(context.getSharedPreferences(
                PREFERENCES, Context.MODE_PRIVATE).getString(VALIDATED_FINGERPRINT, ""));
    }

    File file() {
        return new File(context.getFilesDir(), MODEL_FILE);
    }

    void installBundled() throws Exception {
        if (hasModel()) return;
        File existing = file();
        if (existing.isFile() && existing.length() > 0) {
            try {
                validate(existing);
                requireNotInterrupted();
                markValidated(existing);
                return;
            } catch (InterruptedException cancelled) {
                throw cancelled;
            } catch (Exception invalidExistingModel) {
                // The existing file stays untouched until the bundled replacement is fully copied.
            }
        }
        File temporary = temporary();
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = context.getAssets().open(BUNDLED_MODEL_ASSET);
                 FileOutputStream output = new FileOutputStream(temporary)) {
                copy(input, output, digest);
            }
            requireNotInterrupted();
            if (!BUNDLED_MODEL_SHA256.equals(hex(digest.digest()))) {
                throw new IllegalStateException("Mitgeliefertes KI-Modell ist beschädigt");
            }
            requireNotInterrupted();
            replace(temporary, file());
            validate(file());
            requireNotInterrupted();
            markValidated(file());
        } catch (Exception error) {
            temporary.delete();
            throw error;
        }
    }

    private File temporary() {
        return new File(context.getFilesDir(), MODEL_FILE + ".partial");
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

    private static void copy(
            InputStream input,
            FileOutputStream output,
            MessageDigest digest) throws Exception {
        byte[] buffer = new byte[1024 * 1024];
        long total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            requireNotInterrupted();
            total += read;
            if (total > MAX_MODEL_BYTES) {
                throw new IllegalArgumentException("Modelldatei ist größer als 800 MiB");
            }
            output.write(buffer, 0, read);
            if (digest != null) digest.update(buffer, 0, read);
        }
        output.getFD().sync();
        requireNotInterrupted();
    }

    private static void requireNotInterrupted() throws InterruptedException {
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedException("Modelloperation wurde abgebrochen");
        }
    }

    private void markValidated(File model) {
        if (!context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit()
                .putString(VALIDATED_FINGERPRINT, fingerprint(model)).commit()) {
            throw new IllegalStateException("Modellvalidierung konnte nicht gespeichert werden");
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
            throw new IllegalStateException(
                    "Dateisystem unterstützt keinen atomaren Modellwechsel", error);
        } catch (Exception error) {
            throw new IllegalStateException("Modell konnte nicht atomar gespeichert werden", error);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) result.append(String.format(Locale.ROOT, "%02x", value));
        return result.toString();
    }
}

package com.autosecretary.ai;

import android.content.Context;
import android.net.Uri;

import com.autosecretary.core.Obligation;
import com.autosecretary.core.RoutineStep;
import com.autosecretary.core.TimePreference;
import com.google.mediapipe.tasks.genai.llminference.LlmInference;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.Locale;

/** Runs the bundled or optionally user-replaced MediaPipe model entirely on the phone. */
public final class OnDeviceBulkEditor {
    private static final String MODEL_FILE = "autosecretary-model.task";
    private static final String BUNDLED_MODEL_ASSET =
            "models/autosecretary-gemma3-270m-it-q8.task";
    private static final String BUNDLED_MODEL_SHA256 =
            "0f7147f1c22eaf758b819bbf7841793e4c90096c9352cde7fbe5c631f2265ef5";

    private final Context context;
    private final Executor executor;

    public OnDeviceBulkEditor(Context context, Executor executor) {
        this.context = context.getApplicationContext();
        this.executor = executor;
    }

    public boolean hasModel() {
        return modelFile().isFile() && modelFile().length() > 0;
    }

    public void installBundledModel(Runnable onSuccess, Consumer<Throwable> onError) {
        executor.execute(() -> {
            if (hasModel()) {
                onSuccess.run();
                return;
            }
            File target = modelFile();
            File temporary = new File(context.getFilesDir(), MODEL_FILE + ".partial");
            try (InputStream input = context.getAssets().open(BUNDLED_MODEL_ASSET);
                 FileOutputStream output = new FileOutputStream(temporary)) {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                byte[] buffer = new byte[1024 * 1024];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    output.write(buffer, 0, read);
                    digest.update(buffer, 0, read);
                }
                String actual = hex(digest.digest());
                if (!BUNDLED_MODEL_SHA256.equals(actual)) {
                    throw new IllegalStateException("Mitgeliefertes KI-Modell ist beschädigt");
                }
                replaceModel(temporary, target);
                onSuccess.run();
            } catch (Throwable error) {
                temporary.delete();
                onError.accept(error);
            }
        });
    }

    public void importModel(Uri source, Runnable onSuccess, Consumer<Throwable> onError) {
        executor.execute(() -> {
            File target = modelFile();
            File temporary = new File(context.getFilesDir(), MODEL_FILE + ".partial");
            try (InputStream input = context.getContentResolver().openInputStream(source);
                 FileOutputStream output = new FileOutputStream(temporary)) {
                if (input == null) throw new IllegalArgumentException("Modelldatei ist nicht lesbar");
                byte[] buffer = new byte[32 * 1024];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    output.write(buffer, 0, read);
                }
                if (temporary.length() == 0) throw new IllegalArgumentException("Modelldatei ist leer");
                replaceModel(temporary, target);
                onSuccess.run();
            } catch (Throwable error) {
                temporary.delete();
                onError.accept(error);
            }
        });
    }

    public void propose(
            String instruction,
            List<Obligation> current,
            Consumer<BulkChangeProposal> onSuccess,
            Consumer<Throwable> onError) {
        executor.execute(() -> {
            try {
                if (!hasModel()) throw new IllegalStateException("Zuerst ein lokales .task-Modell auswählen");
                LlmInference.LlmInferenceOptions options = LlmInference.LlmInferenceOptions.builder()
                        .setModelPath(modelFile().getAbsolutePath())
                        .setMaxTokens(2048)
                        .setMaxTopK(20)
                        .build();
                String response;
                try (LlmInference inference = LlmInference.createFromOptions(context, options)) {
                    response = inference.generateResponse(prompt(instruction, current));
                }
                onSuccess.accept(parse(response, current));
            } catch (Throwable error) {
                onError.accept(error);
            }
        });
    }

    private String prompt(String instruction, List<Obligation> current) throws Exception {
        JSONArray state = new JSONArray();
        for (Obligation item : current) {
            JSONObject value = new JSONObject();
            value.put("id", item.id);
            value.put("kind", item.kind.name());
            value.put("title", item.title);
            value.put("durationMinutes", item.durationMinutes);
            value.put("deadline", item.deadlineAt == null ? JSONObject.NULL : item.deadlineAt.toString());
            value.put("cadenceDays", item.cadenceDays);
            value.put("nextDueDate", item.nextDueDate == null ? JSONObject.NULL : item.nextDueDate.toString());
            value.put("timePreference", item.timePreference == null
                    ? JSONObject.NULL : item.timePreference.name());
            state.put(value);
        }
        return String.format(Locale.ROOT, """
                Du übersetzt ausschließlich Aufgabenänderungen in JSON. Antworte ohne Markdown.
                Nichts wird automatisch gespeichert; deine Ausgabe wird dem Nutzer als Vorschau gezeigt.
                Schema:
                {"summary":"kurz","actions":[
                  {"type":"add|update|delete","id":"bei update/delete vorhandene id",
                   "kind":"TASK|ROUTINE","title":"Titel","durationMinutes":30,
                   "deadline":"YYYY-MM-DDTHH:MM:SS oder null","cadenceDays":7,
                   "nextDueDate":"YYYY-MM-DD oder null",
                   "timePreference":"MORNING|MIDDAY|EVENING oder null",
                   "steps":[{"title":"Schritt","days":["MONDAY","TUESDAY"]}]}
                ]}
                Regeln: Keine nicht verlangten Änderungen. Bei Unsicherheit keine Aktion erzeugen und dies
                in summary erklären. Leere days bedeuten täglich. Routinen haben cadenceDays >= 1.

                Aktueller Zustand: %s
                Nutzeranweisung: %s
                """, state, instruction.trim());
    }

    private BulkChangeProposal parse(String raw, List<Obligation> current) throws Exception {
        String json = extractJsonObject(raw);
        JSONObject root = new JSONObject(json);
        JSONArray actions = root.optJSONArray("actions");
        Map<String, Obligation> existing = new HashMap<>();
        for (Obligation item : current) existing.put(item.id, item);

        List<Obligation> upserts = new ArrayList<>();
        List<String> deletions = new ArrayList<>();
        List<String> preview = new ArrayList<>();
        if (actions != null) {
            for (int index = 0; index < actions.length(); index++) {
                JSONObject action = actions.getJSONObject(index);
                String type = action.optString("type", "").toLowerCase();
                if ("delete".equals(type)) {
                    String id = action.optString("id");
                    Obligation found = existing.get(id);
                    if (found == null) throw new IllegalArgumentException("Unbekannte Lösch-ID");
                    deletions.add(id);
                    preview.add("Löschen: " + found.title);
                    continue;
                }
                if (!"add".equals(type) && !"update".equals(type)) {
                    throw new IllegalArgumentException("Unbekannte Aktion: " + type);
                }
                Obligation item = "update".equals(type)
                        ? copyRequired(existing.get(action.optString("id")))
                        : new Obligation();
                applyFields(item, action);
                validate(item);
                upserts.add(item);
                preview.add(("add".equals(type) ? "Neu: " : "Ändern: ") + describe(item));
            }
        }
        return new BulkChangeProposal(
                root.optString("summary", "Vorgeschlagene Änderungen"),
                upserts,
                deletions,
                preview);
    }

    private void applyFields(Obligation item, JSONObject action) throws Exception {
        if (action.has("kind")) item.kind = Obligation.Kind.valueOf(action.getString("kind"));
        if (action.has("title")) item.title = action.getString("title").trim();
        if (action.has("durationMinutes")) item.durationMinutes = action.getInt("durationMinutes");
        if (action.has("deadline")) {
            item.deadlineAt = action.isNull("deadline") ? null : LocalDateTime.parse(action.getString("deadline"));
        }
        if (action.has("cadenceDays")) item.cadenceDays = action.getInt("cadenceDays");
        if (action.has("nextDueDate")) {
            item.nextDueDate = action.isNull("nextDueDate") ? null : LocalDate.parse(action.getString("nextDueDate"));
        }
        if (action.has("timePreference")) {
            item.timePreference = action.isNull("timePreference")
                    ? null : TimePreference.valueOf(action.getString("timePreference"));
        }
        if (action.has("steps")) {
            List<RoutineStep> previousSteps = item.steps;
            item.steps = new ArrayList<>();
            JSONArray steps = action.getJSONArray("steps");
            for (int index = 0; index < steps.length(); index++) {
                JSONObject encoded = steps.getJSONObject(index);
                EnumSet<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
                JSONArray encodedDays = encoded.optJSONArray("days");
                if (encodedDays != null) {
                    for (int day = 0; day < encodedDays.length(); day++) {
                        days.add(DayOfWeek.valueOf(encodedDays.getString(day)));
                    }
                }
                String title = encoded.getString("title");
                if (index < previousSteps.size()) {
                    RoutineStep previous = previousSteps.get(index);
                    item.steps.add(new RoutineStep(
                            previous.id, title, days, previous.completedFor, previous.completedAt));
                } else {
                    item.steps.add(new RoutineStep(title, days));
                }
            }
        }
        if (item.isRoutine()) {
            item.cadenceDays = Math.max(1, item.cadenceDays);
            if (item.nextDueDate == null) item.nextDueDate = LocalDate.now();
            item.completed = false;
        } else {
            item.cadenceDays = 0;
            item.nextDueDate = null;
            item.steps.clear();
        }
    }

    private void validate(Obligation item) {
        if (item.title == null || item.title.trim().isEmpty()) throw new IllegalArgumentException("Titel fehlt");
        if (item.durationMinutes < 5 || item.durationMinutes > 480) {
            throw new IllegalArgumentException("Dauer muss zwischen 5 und 480 Minuten liegen");
        }
    }

    private Obligation copyRequired(Obligation source) {
        if (source == null) throw new IllegalArgumentException("Unbekannte Änderungs-ID");
        Obligation copy = new Obligation();
        copy.id = source.id;
        copy.kind = source.kind;
        copy.title = source.title;
        copy.durationMinutes = source.durationMinutes;
        copy.deadlineAt = source.deadlineAt;
        copy.cadenceDays = source.cadenceDays;
        copy.nextDueDate = source.nextDueDate;
        copy.timePreference = source.timePreference;
        copy.steps = source.steps.stream().map(RoutineStep::copy).collect(java.util.stream.Collectors.toList());
        copy.createdAt = source.createdAt;
        copy.completed = source.completed;
        copy.currentStreak = source.currentStreak;
        copy.bestStreak = source.bestStreak;
        copy.totalCompletions = source.totalCompletions;
        copy.manualOrderOn = source.manualOrderOn;
        copy.manualOrderRank = source.manualOrderRank;
        return copy;
    }

    private String describe(Obligation item) {
        if (!item.isRoutine()) return item.title;
        return item.title + " · alle " + item.cadenceDays + " Tage · " + item.steps.size() + " Schritte";
    }

    static String extractJsonObject(String value) {
        String candidate = stripCodeFenceStatic(value);
        int start = candidate.indexOf('{');
        if (start < 0) return candidate;
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int index = start; index < candidate.length(); index++) {
            char current = candidate.charAt(index);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (current == '\\') {
                    escaped = true;
                } else if (current == '"') {
                    inString = false;
                }
                continue;
            }
            if (current == '"') {
                inString = true;
            } else if (current == '{') {
                depth++;
            } else if (current == '}' && --depth == 0) {
                return candidate.substring(start, index + 1);
            }
        }
        return candidate.substring(start);
    }

    private static String stripCodeFenceStatic(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (!trimmed.startsWith("```")) return trimmed;
        int firstBreak = trimmed.indexOf('\n');
        int closing = trimmed.lastIndexOf("```");
        return firstBreak >= 0 && closing > firstBreak
                ? trimmed.substring(firstBreak + 1, closing).trim()
                : trimmed;
    }

    private File modelFile() {
        return new File(context.getFilesDir(), MODEL_FILE);
    }

    private void replaceModel(File temporary, File target) {
        if (target.exists() && !target.delete()) {
            throw new IllegalStateException("Vorheriges Modell konnte nicht ersetzt werden");
        }
        if (!temporary.renameTo(target)) {
            throw new IllegalStateException("Modell konnte nicht gespeichert werden");
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) result.append(String.format(Locale.ROOT, "%02x", value));
        return result.toString();
    }
}

package com.autosecretary.features.task.application.assistant;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * In-memory chat state for the assistant, owned as a singleton by {@code AppCompositionRoot} (like
 * {@code TaskChangeUndoHolder}) so the conversation survives Fragment/ViewModel recreation and is
 * cleared on data reload. All access is synchronized because the chat loop runs on the io executor
 * while the UI reads on the main thread.
 *
 * <p>Holds three things: the raw wire-shape message history (echoed verbatim each turn), the last
 * attached bank statement (needed to import its transactions), and the per-model thinking mode that
 * was last accepted by the API (so the thinking-format ladder is only walked once per model).
 */
public class AssistantConversation {

    /** The thinking configuration sent to the API; the ladder steps ADAPTIVE → ENABLED → NONE. */
    public enum ThinkingMode { ADAPTIVE, ENABLED, NONE }

    /** The last PDF statement attached in this conversation, remembered for {@code propose_transaction_import}. */
    public record Statement(String fileName, String fileHash) {
    }

    private final List<JSONObject> messages = new ArrayList<>();
    private final Map<String, ThinkingMode> workingThinking = new HashMap<>();
    private Statement currentStatement;

    /** Appends a wire-shape message ({@code {"role":…,"content":[…]}}) to the history. */
    public synchronized void appendMessage(JSONObject message) {
        messages.add(message);
    }

    /**
     * Returns the current history as a fresh {@link JSONArray} referencing the stored messages.
     * Callers treat stored messages as immutable, so a shallow copy is safe and avoids re-parsing.
     */
    public synchronized JSONArray messagesArray() {
        JSONArray array = new JSONArray();
        for (JSONObject message : messages) {
            array.put(message);
        }
        return array;
    }

    /** Number of messages currently in the history; pair with {@link #rollbackTo(int)}. */
    public synchronized int size() {
        return messages.size();
    }

    /**
     * Truncates the history back to {@code size} messages. Used to roll back a failed turn: a
     * dangling assistant {@code tool_use} without its {@code tool_result} would make every
     * subsequent request fail with HTTP 400.
     */
    public synchronized void rollbackTo(int size) {
        while (messages.size() > size) {
            messages.remove(messages.size() - 1);
        }
    }

    public synchronized boolean isEmpty() {
        return messages.isEmpty();
    }

    /** Clears the message history and the remembered statement (thinking cache is kept — it is model-, not chat-, scoped). */
    public synchronized void clear() {
        messages.clear();
        currentStatement = null;
    }

    public synchronized void rememberStatement(String fileName, String fileHash) {
        currentStatement = new Statement(fileName, fileHash);
    }

    public synchronized Statement currentStatement() {
        return currentStatement;
    }

    public synchronized ThinkingMode workingThinking(String model) {
        return workingThinking.get(model);
    }

    public synchronized void cacheWorkingThinking(String model, ThinkingMode mode) {
        workingThinking.put(model, mode);
    }
}

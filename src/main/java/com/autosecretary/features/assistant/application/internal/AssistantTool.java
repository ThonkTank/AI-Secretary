package com.autosecretary.features.assistant.application.internal;

import com.autosecretary.features.assistant.application.AssistantProposals.PendingProposal;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.function.Function;

/**
 * One tool the assistant may call, bundling everything the engine needs to advertise and run it: its
 * wire {@code name}/{@code description}/{@code input_schema}, the German progress label shown while it
 * runs, and the handler that produces its {@link ToolOutcome}. Read tools ({@code get_*}) return a
 * {@link ToolOutcome.Json} result; proposal tools ({@code propose_*}) return a {@link ToolOutcome.Parked}
 * write-intent the user later confirms. This single object replaces the former parallel
 * name-switch / label-switch / spec-list triplication.
 */
public record AssistantTool(String name, Kind kind, String description, JSONObject inputSchema,
                            String progressLabel, Function<JSONObject, ToolOutcome> run) {

    /** Progress label shared by every {@code propose_*} tool while it builds its proposal. */
    public static final String PROGRESS_PROPOSAL = "Erstelle Vorschläge…";

    /**
     * Read tools ({@code get_*}) run immediately and return data; proposal tools ({@code propose_*})
     * park a write-intent. The registry advertises all reads before all proposals, so the model sees
     * its information-gathering tools first regardless of which domain handler contributed them.
     */
    public enum Kind { READ, PROPOSAL }

    /** The result of running a tool: either data to feed back to the model, or a parked proposal. */
    public sealed interface ToolOutcome {
        /** A read tool's serialized JSON, returned to the model as the tool_result content. */
        record Json(String content) implements ToolOutcome {
        }

        /** A proposal tool's parked write-intent; the model sees only a fixed "registered" reply. */
        record Parked(PendingProposal proposal) implements ToolOutcome {
        }
    }

    /** A read tool: its handler serializes live data to a JSON string. */
    public static AssistantTool read(String name, String description, String schemaJson,
                                     String progressLabel, Function<JSONObject, String> handler) {
        return new AssistantTool(name, Kind.READ, description, schema(schemaJson), progressLabel,
                input -> new ToolOutcome.Json(handler.apply(input)));
    }

    /** A proposal tool: its handler parses and validates the model's input into a parked proposal. */
    public static AssistantTool proposal(String name, String description, String schemaJson,
                                         String progressLabel, Function<JSONObject, PendingProposal> handler) {
        return new AssistantTool(name, Kind.PROPOSAL, description, schema(schemaJson), progressLabel,
                input -> new ToolOutcome.Parked(handler.apply(input)));
    }

    private static JSONObject schema(String json) {
        try {
            return new JSONObject(json);
        } catch (JSONException e) {
            throw new IllegalStateException("Ungültiges Tool-Schema: " + json, e);
        }
    }
}

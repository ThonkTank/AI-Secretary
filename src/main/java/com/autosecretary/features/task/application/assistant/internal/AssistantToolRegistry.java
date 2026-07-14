package com.autosecretary.features.task.application.assistant.internal;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The catalogue of tools the assistant may call, assembled from the per-domain handlers. All read
 * tools are advertised before all proposal tools (each group in the order the handlers contributed
 * them), so the model always sees its information-gathering tools first; the wire {@code tools} array
 * order is prompt-sensitive. Looks tools up by name for dispatch. Built once by
 * {@code AppCompositionRoot} and handed to the chat engine.
 */
public final class AssistantToolRegistry {

    private final List<AssistantTool> tools;
    private final Map<String, AssistantTool> byName = new LinkedHashMap<>();
    private final JSONArray toolsJson;

    public AssistantToolRegistry(List<AssistantTool> tools) {
        this.tools = readsBeforeProposals(tools);
        for (AssistantTool tool : this.tools) {
            byName.put(tool.name(), tool);
        }
        this.toolsJson = buildToolsJson(this.tools);
    }

    /** Reads first, then proposals; stable within each group (preserves handler contribution order). */
    private static List<AssistantTool> readsBeforeProposals(List<AssistantTool> tools) {
        List<AssistantTool> ordered = new ArrayList<>(tools.size());
        for (AssistantTool tool : tools) {
            if (tool.kind() == AssistantTool.Kind.READ) {
                ordered.add(tool);
            }
        }
        for (AssistantTool tool : tools) {
            if (tool.kind() == AssistantTool.Kind.PROPOSAL) {
                ordered.add(tool);
            }
        }
        return List.copyOf(ordered);
    }

    /** The tool that matches {@code name}, or {@code null} if the model asked for an unknown tool. */
    public AssistantTool find(String name) {
        return byName.get(name);
    }

    /** The {@code tools} array for the Messages API request body (cached; immutable in practice). */
    public JSONArray toolsJson() {
        return toolsJson;
    }

    private static JSONArray buildToolsJson(List<AssistantTool> tools) {
        try {
            JSONArray array = new JSONArray();
            for (AssistantTool tool : tools) {
                array.put(new JSONObject()
                        .put("name", tool.name())
                        .put("description", tool.description())
                        .put("input_schema", tool.inputSchema()));
            }
            return array;
        } catch (JSONException e) {
            throw new IllegalStateException("Tool-Definitionen konnten nicht erstellt werden", e);
        }
    }
}

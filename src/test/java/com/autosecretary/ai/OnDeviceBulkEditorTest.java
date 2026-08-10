package com.autosecretary.ai;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class OnDeviceBulkEditorTest {
    @Test
    public void extractsJsonFromModelPreambleAndTrailingText() {
        String result = OnDeviceBulkEditor.extractJsonObject(
                "Hier ist die Vorschau:\n{\"summary\":\"ok\",\"actions\":[]}\nFertig.");

        assertEquals("{\"summary\":\"ok\",\"actions\":[]}", result);
    }

    @Test
    public void bracesInsideStringsDoNotEndJsonEarly() {
        String result = OnDeviceBulkEditor.extractJsonObject(
                "```json\n{\"summary\":\"Nutze {A}\",\"actions\":[]}\n```");

        assertEquals("{\"summary\":\"Nutze {A}\",\"actions\":[]}", result);
    }
}

package com.autosecretary.ai;

import com.autosecretary.core.Obligation;

import java.util.List;

/** Local-model output. It cannot mutate data until the user confirms this exact preview. */
public record BulkChangeProposal(
        String summary,
        List<Obligation> upserts,
        List<String> deletions,
        List<String> previewLines) {
}

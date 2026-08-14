package com.autosecretary.application.ai;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Local-model output. Persistence sees only selected typed changes after confirmation. */
public record BulkChangeProposal(String summary, List<BulkChange> changes) {
    public BulkChangeProposal {
        summary = summary == null ? "" : summary.trim();
        if (summary.isEmpty()) throw new IllegalArgumentException("Vorschlagszusammenfassung fehlt");
        changes = List.copyOf(changes);
        Set<String> changeIds = new HashSet<>();
        Set<String> targets = new HashSet<>();
        for (BulkChange change : changes) {
            if (!changeIds.add(change.changeId())) {
                throw new IllegalArgumentException("Doppelte Change-ID");
            }
            if (!targets.add(change.targetId())) {
                throw new IllegalArgumentException("Ein Ziel darf nur einmal geändert werden");
            }
        }
    }
}

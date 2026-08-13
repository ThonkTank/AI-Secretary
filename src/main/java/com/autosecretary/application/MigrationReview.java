package com.autosecretary.application;

import java.util.List;

public record MigrationReview(
        long id,
        int sourceVersion,
        int importedItems,
        int importedCompletions,
        List<MigrationCandidate> candidates,
        String warningsJson) {
    public MigrationReview {
        candidates = List.copyOf(candidates);
    }
}

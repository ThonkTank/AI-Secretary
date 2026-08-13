package com.autosecretary.application;

public record MigrationCandidateResolution(String candidateId, Kind kind, int cadenceDays) {
    public enum Kind { TASK, ROUTINE, DISCARD }

    public MigrationCandidateResolution {
        if (candidateId == null || candidateId.isBlank()) {
            throw new IllegalArgumentException("Migrationskandidat fehlt");
        }
        if (kind == null) throw new IllegalArgumentException("Migrationsentscheidung fehlt");
        if (kind == Kind.ROUTINE && (cadenceDays < 1 || cadenceDays > 365)) {
            throw new IllegalArgumentException("Routine-Kadenz muss zwischen 1 und 365 liegen");
        }
    }

    public static MigrationCandidateResolution task(String id) {
        return new MigrationCandidateResolution(id, Kind.TASK, 0);
    }

    public static MigrationCandidateResolution routine(String id, int cadenceDays) {
        return new MigrationCandidateResolution(id, Kind.ROUTINE, cadenceDays);
    }

    public static MigrationCandidateResolution discard(String id) {
        return new MigrationCandidateResolution(id, Kind.DISCARD, 0);
    }
}
